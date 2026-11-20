package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MenchRepository
import com.example.model.GameRoom
import com.example.model.LudoEngine
import com.example.model.RoomMessage
import com.example.model.UiState
import com.example.voice.VoiceChatManager
import com.example.voice.VoiceStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameRoomViewModel(
    appContext: Context,
    private val repository: MenchRepository,
    private val roomId: String,
    private val currentUserId: String,
) : ViewModel() {

    val voiceManager = VoiceChatManager(
        context = appContext,
        repository = repository,
        roomId = roomId,
        myUid = currentUserId,
    )

    val voiceStatus: StateFlow<VoiceStatus> = voiceManager.status
    val isMicMuted: StateFlow<Boolean> = voiceManager.isMicMuted
    val isSpeakerOn: StateFlow<Boolean> = voiceManager.isSpeakerOn
    val isLocalSpeaking: StateFlow<Boolean> = voiceManager.isLocalSpeaking
    val mutedPeerUids: StateFlow<Set<String>> = voiceManager.mutedPeerUids

    val roomState: StateFlow<UiState<GameRoom?>> = repository.observeRoom(roomId)
        .map<GameRoom?, UiState<GameRoom?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing room", root)
            emit(UiState.Error(root.message ?: "خطا در اتصال به اتاق بازی"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val messagesState: StateFlow<UiState<List<RoomMessage>>> = repository.observeRoomMessages(roomId, currentUserId)
        .map<List<RoomMessage>, UiState<List<RoomMessage>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing messages", root)
            emit(UiState.Error(root.message ?: "خطا در دریافت پیام‌ها"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _isDiceRolling = MutableStateFlow(false)
    val isDiceRolling: StateFlow<Boolean> = _isDiceRolling.asStateFlow()

    private val _animatedDiceFace = MutableStateFlow(6)
    val animatedDiceFace: StateFlow<Int> = _animatedDiceFace.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var botJob: Job? = null
    private var lastBotTurnKey: String? = null
    private var lastSyncedSpeaking: Boolean? = null
    private var statsRecorded = false

    init {
        // Observe WebRTC incoming signals
        viewModelScope.launch {
            repository.observeIncomingSignals(roomId, currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Signal listener stopped", firestoreError ?: error)
                }
                .collect { signals ->
                    voiceManager.handleIncomingSignals(signals)
                }
        }

        // Sync room peer list & trigger automated bot turns (host executes bot turns)
        viewModelScope.launch {
            roomState.collect { state ->
                val room = (state as? UiState.Success)?.data ?: return@collect
                voiceManager.syncPeers(room.memberUids)

                if (room.status == "finished" && !statsRecorded) {
                    statsRecorded = true
                    val mySeat = room.seatOf(currentUserId)
                    if (mySeat in 0..3) {
                        repository.recordMatchResult(won = room.winnerSeat.toInt() == mySeat)
                    }
                }

                if (room.status == "playing" && room.hostUid == currentUserId) {
                    val turn = room.currentTurn.toInt()
                    val seatType = room.seatTypes.getOrNull(turn)
                    if (seatType == "bot") {
                        val turnKey = "${room.currentTurn}-${room.turnPhase}-${room.diceValue}-${room.pieces.hashCode()}"
                        if (lastBotTurnKey != turnKey) {
                            lastBotTurnKey = turnKey
                            scheduleBotTurn(room)
                        }
                    }
                }
            }
        }

        // Sync speaking indicator to Firestore (debounced)
        viewModelScope.launch {
            isLocalSpeaking.collect { speaking ->
                val room = (roomState.value as? UiState.Success)?.data ?: return@collect
                val mySeat = room.seatOf(currentUserId)
                if (mySeat !in 0..3 || room.status == "finished") return@collect
                if (lastSyncedSpeaking != speaking) {
                    lastSyncedSpeaking = speaking
                    repository.updateSeatVoiceState(
                        room = room,
                        seatIndex = mySeat,
                        muted = isMicMuted.value,
                        speaking = speaking,
                    )
                }
            }
        }
    }

    private fun scheduleBotTurn(room: GameRoom) {
        botJob?.cancel()
        botJob = viewModelScope.launch {
            delay(850L)
            val latest = (roomState.value as? UiState.Success)?.data ?: return@launch
            if (latest.status != "playing") return@launch
            val seat = latest.currentTurn.toInt()
            if (latest.seatTypes.getOrNull(seat) != "bot") return@launch

            if (latest.turnPhase == "roll") {
                val rolled = Random.nextInt(1, 7)
                val outcome = LudoEngine.evaluateDiceRoll(
                    pieces = latest.pieces,
                    seatTypes = latest.seatTypes,
                    seatNames = latest.seatNames,
                    seat = seat,
                    dice = rolled,
                    consecutiveSixes = latest.consecutiveSixes.toInt(),
                )
                repository.submitTurnState(
                    roomCode = latest.roomCode,
                    pieces = outcome.nextPieces,
                    currentTurn = outcome.nextTurn,
                    diceValue = outcome.nextDice,
                    turnPhase = outcome.nextPhase,
                    consecutiveSixes = outcome.nextConsecutiveSixes,
                    winnerSeat = outcome.winnerSeat,
                    status = outcome.nextStatus,
                    actionText = outcome.actionText,
                )
            } else if (latest.turnPhase == "move") {
                val pieceIdx = LudoEngine.pickBestBotMove(
                    pieces = latest.pieces,
                    seat = seat,
                    dice = latest.diceValue.toInt(),
                )
                if (pieceIdx != null) {
                    val outcome = LudoEngine.applyMove(
                        pieces = latest.pieces,
                        seatTypes = latest.seatTypes,
                        seatNames = latest.seatNames,
                        seat = seat,
                        pieceGlobalIndex = pieceIdx,
                        dice = latest.diceValue.toInt(),
                        consecutiveSixes = latest.consecutiveSixes.toInt(),
                    )
                    repository.submitTurnState(
                        roomCode = latest.roomCode,
                        pieces = outcome.nextPieces,
                        currentTurn = outcome.nextTurn,
                        diceValue = outcome.nextDice,
                        turnPhase = outcome.nextPhase,
                        consecutiveSixes = outcome.nextConsecutiveSixes,
                        winnerSeat = outcome.winnerSeat,
                        status = outcome.nextStatus,
                        actionText = outcome.actionText,
                    )
                }
            }
        }
    }

    fun startGame(fillBots: Boolean) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.startGame(room, fillBots)
                .onFailure { _actionError.value = it.localizedMessage ?: "شروع بازی انجام نشد." }
        }
    }

    fun rollDice() {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        val mySeat = room.seatOf(currentUserId)
        if (room.status != "playing" || room.currentTurn.toInt() != mySeat || room.turnPhase != "roll") return
        if (_isDiceRolling.value) return

        viewModelScope.launch {
            _isDiceRolling.value = true
            repeat(7) {
                _animatedDiceFace.value = Random.nextInt(1, 7)
                delay(55L)
            }
            val rolled = Random.nextInt(1, 7)
            _animatedDiceFace.value = rolled
            _isDiceRolling.value = false

            val outcome = LudoEngine.evaluateDiceRoll(
                pieces = room.pieces,
                seatTypes = room.seatTypes,
                seatNames = room.seatNames,
                seat = mySeat,
                dice = rolled,
                consecutiveSixes = room.consecutiveSixes.toInt(),
            )
            repository.submitTurnState(
                roomCode = room.roomCode,
                pieces = outcome.nextPieces,
                currentTurn = outcome.nextTurn,
                diceValue = outcome.nextDice,
                turnPhase = outcome.nextPhase,
                consecutiveSixes = outcome.nextConsecutiveSixes,
                winnerSeat = outcome.winnerSeat,
                status = outcome.nextStatus,
                actionText = outcome.actionText,
            ).onFailure {
                _actionError.value = it.localizedMessage ?: "ثبت تاس انجام نشد."
            }
        }
    }

    fun movePiece(pieceGlobalIndex: Int) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        val mySeat = room.seatOf(currentUserId)
        if (room.status != "playing" || room.currentTurn.toInt() != mySeat || room.turnPhase != "move") return
        val movable = LudoEngine.getMovablePieces(room.pieces, mySeat, room.diceValue.toInt())
        if (pieceGlobalIndex !in movable) return

        viewModelScope.launch {
            val outcome = LudoEngine.applyMove(
                pieces = room.pieces,
                seatTypes = room.seatTypes,
                seatNames = room.seatNames,
                seat = mySeat,
                pieceGlobalIndex = pieceGlobalIndex,
                dice = room.diceValue.toInt(),
                consecutiveSixes = room.consecutiveSixes.toInt(),
            )
            repository.submitTurnState(
                roomCode = room.roomCode,
                pieces = outcome.nextPieces,
                currentTurn = outcome.nextTurn,
                diceValue = outcome.nextDice,
                turnPhase = outcome.nextPhase,
                consecutiveSixes = outcome.nextConsecutiveSixes,
                winnerSeat = outcome.winnerSeat,
                status = outcome.nextStatus,
                actionText = outcome.actionText,
            ).onFailure {
                _actionError.value = it.localizedMessage ?: "حرکت مهره ثبت نشد."
            }
        }
    }

    fun enableVoiceChatWithMicPermission() {
        voiceManager.startVoiceEngine(unmuteMic = true)
        syncMuteToFirestore(muted = false)
    }

    fun joinVoiceListenOnly() {
        voiceManager.startVoiceEngine(unmuteMic = false)
        syncMuteToFirestore(muted = true)
    }

    fun toggleMic() {
        if (voiceStatus.value is VoiceStatus.Off) {
            enableVoiceChatWithMicPermission()
            return
        }
        val nextMuted = !isMicMuted.value
        voiceManager.setMicMuted(nextMuted)
        syncMuteToFirestore(muted = nextMuted)
    }

    private fun syncMuteToFirestore(muted: Boolean) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        val mySeat = room.seatOf(currentUserId)
        if (mySeat !in 0..3 || room.status == "finished") return
        viewModelScope.launch {
            repository.updateSeatVoiceState(
                room = room,
                seatIndex = mySeat,
                muted = muted,
                speaking = !muted && isLocalSpeaking.value,
            )
        }
    }

    fun toggleSpeaker() {
        voiceManager.toggleSpeaker()
    }

    fun toggleRemotePeerMute(peerUid: String) {
        if (peerUid.isNotBlank() && peerUid != currentUserId) {
            voiceManager.togglePeerMute(peerUid)
        }
    }

    fun sendQuickMessage(text: String) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        val mySeat = room.seatOf(currentUserId)
        val senderName = if (mySeat in 0..3) room.seatNames[mySeat] else "بازیکن"
        viewModelScope.launch {
            repository.sendRoomMessage(room, senderName, text)
        }
    }

    fun leaveRoom(onLeft: () -> Unit) {
        val room = (roomState.value as? UiState.Success)?.data
        viewModelScope.launch {
            if (room != null) {
                repository.leaveRoom(room)
            }
            voiceManager.release()
            onLeft()
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        botJob?.cancel()
        voiceManager.release()
    }

    private companion object {
        const val TAG = "GameRoomViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
