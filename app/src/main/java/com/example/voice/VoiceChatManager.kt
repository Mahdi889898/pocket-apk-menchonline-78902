package com.example.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.data.MenchRepository
import com.example.model.VoiceSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

sealed interface VoiceStatus {
    data object Off : VoiceStatus
    data object Connecting : VoiceStatus
    data class Active(val connectedPeers: Int) : VoiceStatus
    data class Error(val reason: String) : VoiceStatus
}

class VoiceChatManager(
    context: Context,
    private val repository: MenchRepository,
    private val roomId: String,
    private val myUid: String,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var factory: PeerConnectionFactory? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private val peerConnections = mutableMapOf<String, PeerConnection>()
    private val pendingIceCandidates = mutableMapOf<String, MutableList<IceCandidate>>()
    private val remoteAudioTracks = mutableMapOf<String, AudioTrack>()
    private val locallyMutedPeers = mutableSetOf<String>()
    private val processedSignalIds = mutableSetOf<String>()
    private var currentMembers: List<String> = emptyList()

    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerState: Boolean = false
    private var isInitialized = false
    private var statsJob: Job? = null

    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Off)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private val _isMicMuted = MutableStateFlow(true)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isLocalSpeaking = MutableStateFlow(false)
    val isLocalSpeaking: StateFlow<Boolean> = _isLocalSpeaking.asStateFlow()

    private val _mutedPeerUids = MutableStateFlow<Set<String>>(emptySet())
    val mutedPeerUids: StateFlow<Set<String>> = _mutedPeerUids.asStateFlow()

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
    )

    fun startVoiceEngine(unmuteMic: Boolean) {
        if (isInitialized) {
            setMicMuted(!unmuteMic)
            return
        }
        _status.value = VoiceStatus.Connecting
        runCatching {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            val adm = JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
            audioDeviceModule = adm

            val pcf = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .createPeerConnectionFactory()
            factory = pcf

            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            }
            val source = pcf.createAudioSource(constraints)
            val track = pcf.createAudioTrack("MENCH_AUDIO_$myUid", source)
            track.setEnabled(unmuteMic)
            localAudioSource = source
            localAudioTrack = track

            audioManager?.let { am ->
                previousAudioMode = am.mode
                previousSpeakerState = am.isSpeakerphoneOn
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isSpeakerphoneOn = _isSpeakerOn.value
            }

            isInitialized = true
            _isMicMuted.value = !unmuteMic
            updatePeerStatus()
            syncPeers(currentMembers)
            startSpeakingMonitor()
        }.onFailure { err ->
            Log.e(TAG, "Failed to initialize WebRTC voice engine", err)
            _status.value = VoiceStatus.Error("سخت‌افزار صوتی در دسترس نیست")
        }
    }

    fun setMicMuted(muted: Boolean) {
        _isMicMuted.value = muted
        localAudioTrack?.setEnabled(!muted)
        if (muted) {
            _isLocalSpeaking.value = false
        }
    }

    fun toggleSpeaker(): Boolean {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        audioManager?.isSpeakerphoneOn = next
        return next
    }

    fun togglePeerMute(peerUid: String) {
        val currentlyMuted = peerUid in locallyMutedPeers
        if (currentlyMuted) {
            locallyMutedPeers.remove(peerUid)
        } else {
            locallyMutedPeers.add(peerUid)
        }
        remoteAudioTracks[peerUid]?.setEnabled(currentlyMuted)
        _mutedPeerUids.value = locallyMutedPeers.toSet()
    }

    fun syncPeers(memberUids: List<String>) {
        currentMembers = memberUids
        if (!isInitialized) return
        val remotePeers = memberUids.filter { it.isNotBlank() && it != myUid }.toSet()

        // Close disconnected peers
        val toRemove = peerConnections.keys.filter { it !in remotePeers }
        toRemove.forEach { peerId ->
            peerConnections.remove(peerId)?.close()
            pendingIceCandidates.remove(peerId)
            remoteAudioTracks.remove(peerId)
        }

        // Connect to new peers (only smaller UID initiates offer to avoid glare)
        remotePeers.forEach { peerUid ->
            if (!peerConnections.containsKey(peerUid)) {
                val pc = createPeerConnection(peerUid)
                if (pc != null) {
                    peerConnections[peerUid] = pc
                    if (myUid < peerUid) {
                        createAndSendOffer(peerUid, pc)
                    }
                }
            }
        }
        updatePeerStatus()
    }

    fun handleIncomingSignals(signals: List<VoiceSignal>) {
        if (!isInitialized) return
        signals.forEach { signal ->
            if (signal.id.isBlank() || !processedSignalIds.add(signal.id)) return@forEach
            val peerUid = signal.senderUid
            if (peerUid.isBlank() || peerUid == myUid) return@forEach

            val pc = peerConnections[peerUid] ?: createPeerConnection(peerUid)?.also {
                peerConnections[peerUid] = it
            } ?: return@forEach

            when (signal.type) {
                "offer" -> {
                    val desc = SessionDescription(SessionDescription.Type.OFFER, signal.sdp)
                    pc.setRemoteDescription(object : SimpleSdpObserver("setRemoteOffer") {
                        override fun onSetSuccess() {
                            drainPendingIce(peerUid, pc)
                            createAndSendAnswer(peerUid, pc)
                        }
                    }, desc)
                }
                "answer" -> {
                    val desc = SessionDescription(SessionDescription.Type.ANSWER, signal.sdp)
                    pc.setRemoteDescription(object : SimpleSdpObserver("setRemoteAnswer") {
                        override fun onSetSuccess() {
                            drainPendingIce(peerUid, pc)
                        }
                    }, desc)
                }
                "ice" -> {
                    val candidate = IceCandidate(signal.sdpMid, signal.sdpMLineIndex.toInt(), signal.sdp)
                    if (pc.remoteDescription != null) {
                        pc.addIceCandidate(candidate)
                    } else {
                        pendingIceCandidates.getOrPut(peerUid) { mutableListOf() }.add(candidate)
                    }
                }
            }

            scope.launch {
                repository.deleteSignal(roomId, signal.id)
            }
        }
    }

    private fun drainPendingIce(peerUid: String, pc: PeerConnection) {
        pendingIceCandidates.remove(peerUid)?.forEach { candidate ->
            pc.addIceCandidate(candidate)
        }
    }

    private fun createPeerConnection(peerUid: String): PeerConnection? {
        val pcf = factory ?: return null
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = pcf.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                updatePeerStatus()
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                scope.launch {
                    repository.sendSignal(
                        roomId = roomId,
                        targetUid = peerUid,
                        memberUids = currentMembers,
                        type = "ice",
                        sdp = candidate.sdp,
                        sdpMid = candidate.sdpMid ?: "",
                        sdpMLineIndex = candidate.sdpMLineIndex,
                    )
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                stream?.audioTracks?.firstOrNull()?.let { track ->
                    track.setEnabled(peerUid !in locallyMutedPeers)
                    remoteAudioTracks[peerUid] = track
                }
            }
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(peerUid !in locallyMutedPeers)
                    remoteAudioTracks[peerUid] = track
                }
            }
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(peerUid !in locallyMutedPeers)
                    remoteAudioTracks[peerUid] = track
                }
            }
        }) ?: return null

        localAudioTrack?.let { track ->
            pc.addTrack(track, listOf("MENCH_STREAM_$myUid"))
        }
        return pc
    }

    private fun createAndSendOffer(peerUid: String, pc: PeerConnection) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        pc.createOffer(object : SimpleSdpObserver("createOffer") {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(object : SimpleSdpObserver("setLocalOffer") {
                    override fun onSetSuccess() {
                        scope.launch {
                            repository.sendSignal(
                                roomId = roomId,
                                targetUid = peerUid,
                                memberUids = currentMembers,
                                type = "offer",
                                sdp = desc.description,
                            )
                        }
                    }
                }, desc)
            }
        }, constraints)
    }

    private fun createAndSendAnswer(peerUid: String, pc: PeerConnection) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        pc.createAnswer(object : SimpleSdpObserver("createAnswer") {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(object : SimpleSdpObserver("setLocalAnswer") {
                    override fun onSetSuccess() {
                        scope.launch {
                            repository.sendSignal(
                                roomId = roomId,
                                targetUid = peerUid,
                                memberUids = currentMembers,
                                type = "answer",
                                sdp = desc.description,
                            )
                        }
                    }
                }, desc)
            }
        }, constraints)
    }

    private fun updatePeerStatus() {
        if (!isInitialized) return
        val connected = peerConnections.values.count {
            val state = it.iceConnectionState()
            state == PeerConnection.IceConnectionState.CONNECTED ||
                state == PeerConnection.IceConnectionState.COMPLETED
        }
        _status.value = VoiceStatus.Active(connected)
    }

    private fun startSpeakingMonitor() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                delay(550L)
                if (_isMicMuted.value) {
                    _isLocalSpeaking.value = false
                    continue
                }
                val pc = peerConnections.values.firstOrNull()
                if (pc != null) {
                    pc.getStats { report ->
                        val level = report.statsMap.values
                            .firstOrNull { it.type == "media-source" }
                            ?.members?.get("audioLevel") as? Number
                        _isLocalSpeaking.value = (level?.toDouble() ?: 0.0) > 0.03
                    }
                } else {
                    // Solo mic test indicator when waiting in room
                    _isLocalSpeaking.value = false
                }
            }
        }
    }

    fun release() {
        statsJob?.cancel()
        peerConnections.values.forEach { runCatching { it.close(); it.dispose() } }
        peerConnections.clear()
        remoteAudioTracks.clear()
        pendingIceCandidates.clear()
        runCatching { localAudioTrack?.dispose() }
        runCatching { localAudioSource?.dispose() }
        runCatching { audioDeviceModule?.release() }
        runCatching { factory?.dispose() }
        localAudioTrack = null
        localAudioSource = null
        audioDeviceModule = null
        factory = null
        isInitialized = false
        _status.value = VoiceStatus.Off
        audioManager?.let { am ->
            runCatching {
                am.mode = previousAudioMode
                am.isSpeakerphoneOn = previousSpeakerState
            }
        }
    }

    private open class SimpleSdpObserver(private val label: String) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(err: String?) {
            Log.w(TAG, "$label create failure: $err")
        }
        override fun onSetFailure(err: String?) {
            Log.w(TAG, "$label set failure: $err")
        }
    }

    companion object {
        private const val TAG = "VoiceChatManager"
    }
}
