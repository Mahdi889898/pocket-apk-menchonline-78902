package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.GameRoom
import com.example.model.LudoEngine
import com.example.model.RoomMessage
import com.example.model.UiState
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CreamWhite
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.DeepIndigoBg
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.SeatPalettes
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceHighlight
import com.example.ui.theme.SurfaceNavy
import com.example.voice.VoiceStatus

private val QuickReactions = listOf(
    "🎲 تاس ۶ بیاد!",
    "👏 دمت گرم!",
    "😂 شانس آوردی!",
    "🔥 بزن بریم!",
    "🎙️ صدات واضحه!",
)

@Composable
fun GameRoomScreen(
    viewModel: GameRoomViewModel,
    currentUserId: String,
    onExitToLobby: () -> Unit,
) {
    val context = LocalContext.current
    val roomState by viewModel.roomState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val voiceStatus by viewModel.voiceStatus.collectAsStateWithLifecycle()
    val isMicMuted by viewModel.isMicMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val mutedPeerUids by viewModel.mutedPeerUids.collectAsStateWithLifecycle()
    val isDiceRolling by viewModel.isDiceRolling.collectAsStateWithLifecycle()
    val animatedDiceFace by viewModel.animatedDiceFace.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    var permissionDeniedBanner by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedBanner = false
            viewModel.enableVoiceChatWithMicPermission()
        } else {
            permissionDeniedBanner = true
        }
    }

    val requestVoiceToggle = {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.toggleMic()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler {
        viewModel.leaveRoom(onExitToLobby)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF151934), DeepIndigoBg))
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (val state = roomState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = RoyalGold)
                        Text("در حال اتصال به میز منچ...", color = MutedSlate)
                    }
                }
            }
            is UiState.Error -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                        border = BorderStroke(1.dp, CrimsonDanger),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(state.message, color = CreamWhite, textAlign = TextAlign.Center)
                            Button(onClick = onExitToLobby) {
                                Text("بازگشت به لابی")
                            }
                        }
                    }
                }
            }
            is UiState.Success -> {
                val room = state.data
                if (room == null) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("این اتاق بسته شده است.", style = MaterialTheme.typography.titleLarge, color = CreamWhite)
                            Button(onClick = onExitToLobby) {
                                Text("بازگشت به لابی")
                            }
                        }
                    }
                } else {
                    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
                    Column(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize()
                    ) {
                        // Top Bar
                        RoomTopHeader(
                            room = room,
                            voiceStatus = voiceStatus,
                            isMicMuted = isMicMuted,
                            onCopyCode = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("RoomCode", room.roomCode))
                                copiedNotice = true
                            },
                            onExitClick = { viewModel.leaveRoom(onExitToLobby) },
                        )

                        AnimatedVisibility(visible = copiedNotice) {
                            Surface(
                                color = EmeraldOnline.copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("کد اتاق ${room.roomCode} کپی شد!", color = EmeraldOnline, style = MaterialTheme.typography.labelMedium)
                                    TextButton(onClick = { copiedNotice = false }) {
                                        Text("باشه", color = CreamWhite)
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = permissionDeniedBanner) {
                            Surface(
                                color = RoyalGold.copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "دسترسی میکروفون داده نشد. می‌توانید به عنوان شنونده به چت صوتی وصل شوید.",
                                        color = RoyalGoldLight,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = {
                                        permissionDeniedBanner = false
                                        viewModel.joinVoiceListenOnly()
                                    }) {
                                        Text("شنود صدا", color = EmeraldOnline, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (actionError != null) {
                            Surface(
                                color = CrimsonDanger.copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(actionError ?: "", color = Color(0xFFFFBCC4), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    TextButton(onClick = viewModel::clearError) {
                                        Text("بستن", color = RoyalGoldLight)
                                    }
                                }
                            }
                        }

                        if (room.status == "waiting") {
                            WaitingRoomContent(
                                room = room,
                                currentUserId = currentUserId,
                                voiceStatus = voiceStatus,
                                isMicMuted = isMicMuted,
                                isSpeakerOn = isSpeakerOn,
                                onToggleMic = requestVoiceToggle,
                                onToggleSpeaker = viewModel::toggleSpeaker,
                                onStartWithBots = { viewModel.startGame(fillBots = true) },
                                onStartHumansOnly = { viewModel.startGame(fillBots = false) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            ActiveGameContent(
                                room = room,
                                currentUserId = currentUserId,
                                voiceStatus = voiceStatus,
                                isMicMuted = isMicMuted,
                                isSpeakerOn = isSpeakerOn,
                                mutedPeerUids = mutedPeerUids,
                                isDiceRolling = isDiceRolling,
                                animatedDiceFace = animatedDiceFace,
                                messages = messages,
                                onRollDice = viewModel::rollDice,
                                onMovePiece = viewModel::movePiece,
                                onToggleMic = requestVoiceToggle,
                                onToggleSpeaker = viewModel::toggleSpeaker,
                                onTogglePeerMute = viewModel::toggleRemotePeerMute,
                                onSendQuickMessage = viewModel::sendQuickMessage,
                                onExitToLobby = { viewModel.leaveRoom(onExitToLobby) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomTopHeader(
    room: GameRoom,
    voiceStatus: VoiceStatus,
    isMicMuted: Boolean,
    onCopyCode: () -> Unit,
    onExitClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            IconButton(
                onClick = onExitClick,
                modifier = Modifier.size(48.dp).testTag("leave_room_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                    contentDescription = "خروج از اتاق",
                    tint = CrimsonDanger,
                )
            }
            Column {
                Text(
                    text = room.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CreamWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onCopyCode),
                ) {
                    Text(
                        text = "کد اتاق: ${room.roomCode}",
                        style = MaterialTheme.typography.labelMedium,
                        color = RoyalGoldLight,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "کپی کد اتاق",
                        tint = RoyalGoldLight,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Voice badge
        val voiceBadgeColor = when (voiceStatus) {
            is VoiceStatus.Active -> if (isMicMuted) RoyalGold else EmeraldOnline
            is VoiceStatus.Connecting -> RoyalGold
            else -> MutedSlate
        }
        val voiceLabel = when (voiceStatus) {
            is VoiceStatus.Active -> if (isMicMuted) "شنود فعال" else "صدا زنده"
            is VoiceStatus.Connecting -> "در حال اتصال"
            is VoiceStatus.Error -> "بدون صدا"
            is VoiceStatus.Off -> "چت صوتی خاموش"
        }
        Surface(
            color = SurfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, voiceBadgeColor.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (voiceStatus is VoiceStatus.Active && !isMicMuted) Icons.Rounded.GraphicEq else Icons.Rounded.Headset,
                    contentDescription = null,
                    tint = voiceBadgeColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = voiceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = voiceBadgeColor,
                )
            }
        }
    }
}

@Composable
private fun WaitingRoomContent(
    room: GameRoom,
    currentUserId: String,
    voiceStatus: VoiceStatus,
    isMicMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onStartWithBots: () -> Unit,
    onStartHumansOnly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHost = room.hostUid == currentUserId
    val humanCount = room.seatTypes.count { it == "human" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("منتظر ورود بازیکنان...", style = MaterialTheme.typography.headlineMedium, color = CreamWhite)
                Text(
                    text = "کد اتاق ${room.roomCode} را برای دوستانتان بفرستید یا صندلی‌های خالی را با ربات هوشمند پر کنید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlate,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 4 Colored Seat Cards (2x2)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                WaitingSeatCard(room, 0, currentUserId, Modifier.weight(1f))
                WaitingSeatCard(room, 1, currentUserId, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                WaitingSeatCard(room, 3, currentUserId, Modifier.weight(1f))
                WaitingSeatCard(room, 2, currentUserId, Modifier.weight(1f))
            }
        }

        // Voice Chat Lobby Bar
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("چت صوتی لابی", style = MaterialTheme.typography.titleMedium, color = CreamWhite)
                    Text(
                        text = "پیش از شروع بازی با هم‌تیمی‌ها صحبت کنید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SurfaceHighlight)
                            .testTag("lobby_speaker_toggle"),
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = "تغییر بلندگو",
                            tint = if (isSpeakerOn) RoyalGoldLight else MutedSlate,
                        )
                    }
                    Button(
                        onClick = onToggleMic,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (voiceStatus is VoiceStatus.Active && !isMicMuted) EmeraldOnline else RoyalGold,
                            contentColor = Color(0xFF101420),
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp).testTag("lobby_mic_toggle"),
                    ) {
                        Icon(
                            imageVector = if (voiceStatus is VoiceStatus.Active && !isMicMuted) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (voiceStatus is VoiceStatus.Active && !isMicMuted) "میکروفون باز" else "روشن کردن صدا",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (isHost) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartWithBots,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = Color(0xFF1A1405)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("start_with_bots_button"),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("شروع بازی ۴ نفره (تکمیل صندلی‌های خالی با ربات)", fontWeight = FontWeight.Bold)
                }

                if (humanCount >= 2) {
                    OutlinedButton(
                        onClick = onStartHumansOnly,
                        border = BorderStroke(1.dp, EmeraldOnline),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("start_humans_button"),
                    ) {
                        Text("شروع فقط با بازیکنان حاضر ($humanCount نفر)", color = EmeraldOnline, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Surface(
                color = SurfaceNavy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "در انتظار میزبان برای شروع بازی...",
                    style = MaterialTheme.typography.titleMedium,
                    color = RoyalGoldLight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun WaitingSeatCard(
    room: GameRoom,
    seatIdx: Int,
    currentUserId: String,
    modifier: Modifier = Modifier,
) {
    val palette = SeatPalettes[seatIdx]
    val seatType = room.seatTypes.getOrNull(seatIdx) ?: "empty"
    val seatName = room.seatNames.getOrNull(seatIdx).orEmpty()
    val isMe = room.seatUids.getOrNull(seatIdx) == currentUserId

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (seatType != "empty") palette.container else SurfaceNavy
        ),
        border = BorderStroke(
            width = if (isMe) 2.dp else 1.dp,
            color = if (isMe) RoyalGold else if (seatType != "empty") palette.primary else BorderSubtle,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(14.dp).clip(CircleShape).background(palette.primary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "رنگ ${palette.nameFa}",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.light,
                )
            }
            Text(
                text = when (seatType) {
                    "human" -> if (isMe) "$seatName (شما)" else seatName
                    "bot" -> seatName.ifBlank { "ربات هوشمند" }
                    else -> "صندلی خالی..."
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (seatType == "empty") MutedSlate else CreamWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActiveGameContent(
    room: GameRoom,
    currentUserId: String,
    voiceStatus: VoiceStatus,
    isMicMuted: Boolean,
    isSpeakerOn: Boolean,
    mutedPeerUids: Set<String>,
    isDiceRolling: Boolean,
    animatedDiceFace: Int,
    messages: List<RoomMessage>,
    onRollDice: () -> Unit,
    onMovePiece: (Int) -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onTogglePeerMute: (String) -> Unit,
    onSendQuickMessage: (String) -> Unit,
    onExitToLobby: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mySeat = room.seatOf(currentUserId)
    val currentTurn = room.currentTurn.toInt()
    val isMyTurn = room.status == "playing" && currentTurn == mySeat
    val movablePieces = remember(room.pieces, currentTurn, room.diceValue, room.turnPhase, isMyTurn) {
        if (isMyTurn && room.turnPhase == "move") {
            LudoEngine.getMovablePieces(room.pieces, mySeat, room.diceValue.toInt()).toSet()
        } else {
            emptySet()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top 2 Seats (Red=0, Green=1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerHudCard(
                    room = room,
                    seatIdx = 0,
                    currentUserId = currentUserId,
                    locallyMutedPeers = mutedPeerUids,
                    onTogglePeerMute = onTogglePeerMute,
                    modifier = Modifier.weight(1f),
                )
                PlayerHudCard(
                    room = room,
                    seatIdx = 1,
                    currentUserId = currentUserId,
                    locallyMutedPeers = mutedPeerUids,
                    onTogglePeerMute = onTogglePeerMute,
                    modifier = Modifier.weight(1f),
                )
            }

            // 15x15 Interactive Ludo Board
            LudoBoardCanvas(
                pieces = room.pieces,
                movablePieces = movablePieces,
                activeSeat = currentTurn,
                onPieceClick = onMovePiece,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
            )

            // Bottom 2 Seats (Blue=3, Yellow=2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerHudCard(
                    room = room,
                    seatIdx = 3,
                    currentUserId = currentUserId,
                    locallyMutedPeers = mutedPeerUids,
                    onTogglePeerMute = onTogglePeerMute,
                    modifier = Modifier.weight(1f),
                )
                PlayerHudCard(
                    room = room,
                    seatIdx = 2,
                    currentUserId = currentUserId,
                    locallyMutedPeers = mutedPeerUids,
                    onTogglePeerMute = onTogglePeerMute,
                    modifier = Modifier.weight(1f),
                )
            }

            // Dice & Turn Control Card
            val activePalette = SeatPalettes[currentTurn.coerceIn(0, 3)]
            val displayedDice = if (isDiceRolling) animatedDiceFace else room.diceValue.toInt().let { if (it in 1..6) it else animatedDiceFace }
            val canRoll = isMyTurn && room.turnPhase == "roll"

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                border = BorderStroke(
                    width = if (isMyTurn) 2.dp else 1.dp,
                    color = if (isMyTurn) RoyalGold else BorderSubtle,
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = when {
                                room.status == "finished" -> "بازی به پایان رسید!"
                                isMyTurn && room.turnPhase == "roll" -> "نوبت شماست! روی تاس بزنید"
                                isMyTurn && room.turnPhase == "move" -> "مهره چشمک‌زن روی صفحه را لمس کنید"
                                else -> "نوبت ${room.seatNames.getOrNull(currentTurn).orEmpty()}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isMyTurn) RoyalGoldLight else CreamWhite,
                        )
                        Text(
                            text = room.lastActionText.ifBlank { "بازی در حال انجام است" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSlate,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    AnimatedLudoDice(
                        value = displayedDice,
                        isRolling = isDiceRolling,
                        canRoll = canRoll,
                        accentColor = activePalette.primary,
                        onRollClick = onRollDice,
                    )
                }
            }

            // Quick Reaction Strip + Latest Chat Pill
            if (messages.isNotEmpty()) {
                val latest = messages.first()
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "💬 ${latest.senderName}: ${latest.text}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoyalGoldLight,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(QuickReactions) { phrase ->
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .clickable { onSendQuickMessage(phrase) }
                            .height(38.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(phrase, style = MaterialTheme.typography.labelMedium, color = CreamWhite)
                        }
                    }
                }
            }

            // Live Voice Chat Dock
            Surface(
                color = SurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Button(
                        onClick = onToggleMic,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                voiceStatus is VoiceStatus.Active && !isMicMuted -> EmeraldOnline
                                voiceStatus is VoiceStatus.Active -> SurfaceHighlight
                                else -> RoyalGold
                            },
                            contentColor = when {
                                voiceStatus is VoiceStatus.Active && !isMicMuted -> Color(0xFF062619)
                                voiceStatus is VoiceStatus.Active -> CreamWhite
                                else -> Color(0xFF1A1405)
                            },
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("in_game_mic_button"),
                    ) {
                        Icon(
                            imageVector = if (voiceStatus is VoiceStatus.Active && !isMicMuted) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                voiceStatus is VoiceStatus.Off -> "اتصال به چت صوتی"
                                isMicMuted -> "میکروفون بسته (باز کردن)"
                                else -> "در حال صحبت (بستن)"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceHighlight)
                            .testTag("in_game_speaker_button"),
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = "بلندگو",
                            tint = if (isSpeakerOn) RoyalGoldLight else MutedSlate,
                        )
                    }
                }
            }
        }

        // Victory Celebration Modal
        if (room.status == "finished" && room.winnerSeat in 0..3) {
            val winnerIdx = room.winnerSeat.toInt()
            val winnerPalette = SeatPalettes[winnerIdx]
            val winnerName = room.seatNames.getOrNull(winnerIdx).orEmpty()
            val iWon = room.seatUids.getOrNull(winnerIdx) == currentUserId

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xBB080A14)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                    border = BorderStroke(2.dp, RoyalGold),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 380.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(winnerPalette.primary.copy(alpha = 0.25f))
                                .border(2.dp, RoyalGold, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EmojiEvents,
                                contentDescription = "جام قهرمانی",
                                tint = RoyalGold,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                        Text(
                            text = if (iWon) "تبریک! شما برنده شدید!" else "پایان مسابقه!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = RoyalGoldLight,
                        )
                        Text(
                            text = "$winnerName (تیم ${winnerPalette.nameFa}) هر ۴ مهره را به خانه رساند.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CreamWhite,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = onExitToLobby,
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = Color(0xFF1A1405)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("victory_lobby_button"),
                        ) {
                            Text("بازگشت به تالار", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHudCard(
    room: GameRoom,
    seatIdx: Int,
    currentUserId: String,
    locallyMutedPeers: Set<String>,
    onTogglePeerMute: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = SeatPalettes[seatIdx]
    val seatType = room.seatTypes.getOrNull(seatIdx) ?: "empty"
    val seatUid = room.seatUids.getOrNull(seatIdx).orEmpty()
    val seatName = room.seatNames.getOrNull(seatIdx).orEmpty()
    val isTurn = room.status == "playing" && room.currentTurn.toInt() == seatIdx
    val isMe = seatUid == currentUserId && seatUid.isNotBlank()
    val isRemoteHuman = seatType == "human" && !isMe && seatUid.isNotBlank()
    val isLocallyMuted = seatUid in locallyMutedPeers
    val isRemoteMuted = room.seatMuted.getOrNull(seatIdx) == true
    val isSpeaking = room.seatSpeaking.getOrNull(seatIdx) == true && !isRemoteMuted && !isLocallyMuted

    val finishedCount = (0..3).count { room.pieces.getOrNull(seatIdx * 4 + it) == 56L }

    val pulse = rememberInfiniteTransition(label = "speak_$seatIdx")
    val speakAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "speakAlpha",
    )

    Surface(
        color = if (isTurn) palette.container else SurfaceNavy,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isTurn) 2.dp else 1.dp,
            color = when {
                isSpeaking -> EmeraldOnline.copy(alpha = speakAlpha)
                isTurn -> RoyalGold
                else -> BorderSubtle
            },
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (seatType == "empty") SurfaceHighlight else palette.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (seatType == "bot") {
                        Icon(
                            imageVector = Icons.Rounded.SmartToy,
                            contentDescription = "ربات",
                            tint = palette.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            text = "$finishedCount",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = when (seatType) {
                            "empty" -> "خالی"
                            else -> if (isMe) "$seatName (شما)" else seatName
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (seatType == "empty") MutedSlate else CreamWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (seatType == "empty") "-" else "هدف: $finishedCount/۴",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTurn) RoyalGoldLight else MutedSlate,
                    )
                }
            }

            if (seatType == "human") {
                if (isRemoteHuman) {
                    IconButton(
                        onClick = { onTogglePeerMute(seatUid) },
                        modifier = Modifier.size(36.dp).testTag("mute_peer_$seatIdx"),
                    ) {
                        Icon(
                            imageVector = when {
                                isLocallyMuted || isRemoteMuted -> Icons.Rounded.MicOff
                                isSpeaking -> Icons.Rounded.GraphicEq
                                else -> Icons.Rounded.Mic
                            },
                            contentDescription = "تغییر صدای بازیکن",
                            tint = when {
                                isLocallyMuted || isRemoteMuted -> CrimsonDanger
                                isSpeaking -> EmeraldOnline
                                else -> MutedSlate
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = if (isRemoteMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = if (isRemoteMuted) MutedSlate else EmeraldOnline,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
