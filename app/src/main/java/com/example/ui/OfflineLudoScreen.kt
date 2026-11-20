package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.LudoEngine
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CreamWhite
import com.example.ui.theme.DeepIndigoBg
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalGoldLight
import com.example.ui.theme.SeatPalettes
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceNavy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun OfflineLudoScreen(
    onBackToAuth: () -> Unit,
) {
    BackHandler(onBack = onBackToAuth)

    val scope = rememberCoroutineScope()
    var humanPlayerCount by remember { mutableIntStateOf(1) } // 1 = vs 3 bots, 4 = 4 local friends
    var pieces by remember { mutableStateOf(List(16) { -1L }) }
    var currentTurn by remember { mutableIntStateOf(0) }
    var diceValue by remember { mutableIntStateOf(0) }
    var turnPhase by remember { mutableStateOf("roll") }
    var consecutiveSixes by remember { mutableIntStateOf(0) }
    var winnerSeat by remember { mutableIntStateOf(-1) }
    var isRolling by remember { mutableStateOf(false) }
    var animatedDiceFace by remember { mutableIntStateOf(6) }
    var actionText by remember { mutableStateOf("بازی آفلاین آماده است! تاس بیندازید.") }

    val seatTypes = remember(humanPlayerCount) {
        List(4) { idx -> if (idx < humanPlayerCount) "human" else "bot" }
    }
    val seatNames = remember(humanPlayerCount) {
        listOf(
            if (humanPlayerCount == 1) "شما (قرمز)" else "بازیکن ۱ (قرمز)",
            if (1 < humanPlayerCount) "بازیکن ۲ (سبز)" else "ربات سبز",
            if (2 < humanPlayerCount) "بازیکن ۳ (زرد)" else "ربات زرد",
            if (3 < humanPlayerCount) "بازیکن ۴ (آبی)" else "ربات آبی",
        )
    }

    fun resetBoard(humans: Int) {
        humanPlayerCount = humans
        pieces = List(16) { -1L }
        currentTurn = 0
        diceValue = 0
        turnPhase = "roll"
        consecutiveSixes = 0
        winnerSeat = -1
        isRolling = false
        actionText = "میز جدید چیده شد! نوبت ${if (humans == 1) "شما" else "بازیکن ۱"} است."
    }

    // Automatic bot turns
    LaunchedEffect(currentTurn, turnPhase, winnerSeat, humanPlayerCount, pieces) {
        if (winnerSeat != -1) return@LaunchedEffect
        if (seatTypes[currentTurn] != "bot") return@LaunchedEffect

        delay(700L)
        if (turnPhase == "roll") {
            val rolled = Random.nextInt(1, 7)
            animatedDiceFace = rolled
            val outcome = LudoEngine.evaluateDiceRoll(
                pieces = pieces,
                seatTypes = seatTypes,
                seatNames = seatNames,
                seat = currentTurn,
                dice = rolled,
                consecutiveSixes = consecutiveSixes,
            )
            pieces = outcome.nextPieces
            currentTurn = outcome.nextTurn
            turnPhase = outcome.nextPhase
            diceValue = outcome.nextDice
            consecutiveSixes = outcome.nextConsecutiveSixes
            actionText = outcome.actionText
        } else if (turnPhase == "move") {
            val pieceIdx = LudoEngine.pickBestBotMove(pieces, currentTurn, diceValue)
            if (pieceIdx != null) {
                val outcome = LudoEngine.applyMove(
                    pieces = pieces,
                    seatTypes = seatTypes,
                    seatNames = seatNames,
                    seat = currentTurn,
                    pieceGlobalIndex = pieceIdx,
                    dice = diceValue,
                    consecutiveSixes = consecutiveSixes,
                )
                pieces = outcome.nextPieces
                currentTurn = outcome.nextTurn
                turnPhase = outcome.nextPhase
                diceValue = outcome.nextDice
                consecutiveSixes = outcome.nextConsecutiveSixes
                winnerSeat = outcome.winnerSeat
                actionText = outcome.actionText
            }
        }
    }

    val isHumanTurn = winnerSeat == -1 && seatTypes[currentTurn] == "human"
    val movablePieces = remember(pieces, currentTurn, diceValue, turnPhase, isHumanTurn) {
        if (isHumanTurn && turnPhase == "move") {
            LudoEngine.getMovablePieces(pieces, currentTurn, diceValue).toSet()
        } else {
            emptySet()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF151934), DeepIndigoBg)))
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackToAuth,
                        modifier = Modifier.size(48.dp).testTag("offline_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = CreamWhite,
                        )
                    }
                    Column {
                        Text("منچ آفلاین (بدون نیاز به ورود)", style = MaterialTheme.typography.titleMedium, color = CreamWhite)
                        Text("تک‌نفره با ربات یا ۴ نفره روی یک گوشی", style = MaterialTheme.typography.labelMedium, color = MutedSlate)
                    }
                }
                IconButton(
                    onClick = { resetBoard(humanPlayerCount) },
                    modifier = Modifier.size(48.dp).testTag("offline_reset_button"),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "شروع مجدد", tint = RoyalGoldLight)
                }
            }

            // Mode selector chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = humanPlayerCount == 1,
                    onClick = { resetBoard(1) },
                    label = { Text("۱ نفره با ۳ ربات") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RoyalGold.copy(alpha = 0.22f),
                        selectedLabelColor = RoyalGoldLight,
                    ),
                )
                FilterChip(
                    selected = humanPlayerCount == 4,
                    onClick = { resetBoard(4) },
                    label = { Text("۴ نفره روی یک گوشی") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldOnline.copy(alpha = 0.22f),
                        selectedLabelColor = EmeraldOnline,
                    ),
                )
            }

            // Top seat badges
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OfflineSeatBadge(seatNames[0], 0, pieces, currentTurn == 0 && winnerSeat == -1, seatTypes[0] == "bot", Modifier.weight(1f))
                OfflineSeatBadge(seatNames[1], 1, pieces, currentTurn == 1 && winnerSeat == -1, seatTypes[1] == "bot", Modifier.weight(1f))
            }

            LudoBoardCanvas(
                pieces = pieces,
                movablePieces = movablePieces,
                activeSeat = currentTurn,
                onPieceClick = { idx ->
                    if (!isHumanTurn || turnPhase != "move" || idx !in movablePieces) return@LudoBoardCanvas
                    val outcome = LudoEngine.applyMove(
                        pieces = pieces,
                        seatTypes = seatTypes,
                        seatNames = seatNames,
                        seat = currentTurn,
                        pieceGlobalIndex = idx,
                        dice = diceValue,
                        consecutiveSixes = consecutiveSixes,
                    )
                    pieces = outcome.nextPieces
                    currentTurn = outcome.nextTurn
                    turnPhase = outcome.nextPhase
                    diceValue = outcome.nextDice
                    consecutiveSixes = outcome.nextConsecutiveSixes
                    winnerSeat = outcome.winnerSeat
                    actionText = outcome.actionText
                },
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            )

            // Bottom seat badges
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OfflineSeatBadge(seatNames[3], 3, pieces, currentTurn == 3 && winnerSeat == -1, seatTypes[3] == "bot", Modifier.weight(1f))
                OfflineSeatBadge(seatNames[2], 2, pieces, currentTurn == 2 && winnerSeat == -1, seatTypes[2] == "bot", Modifier.weight(1f))
            }

            // Dice control card
            val activePalette = SeatPalettes[currentTurn]
            val canRoll = isHumanTurn && turnPhase == "roll" && !isRolling
            val displayedDice = if (isRolling) animatedDiceFace else if (diceValue in 1..6) diceValue else animatedDiceFace

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                border = BorderStroke(if (isHumanTurn) 2.dp else 1.dp, if (isHumanTurn) RoyalGold else BorderSubtle),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = when {
                                winnerSeat != -1 -> "برنده: ${seatNames[winnerSeat]}"
                                isHumanTurn && turnPhase == "roll" -> "نوبت ${seatNames[currentTurn]} — تاس بیندازید"
                                isHumanTurn && turnPhase == "move" -> "مهره چشمک‌زن را لمس کنید"
                                else -> "در حال بازی ${seatNames[currentTurn]}..."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isHumanTurn) RoyalGoldLight else CreamWhite,
                        )
                        Text(actionText, style = MaterialTheme.typography.bodyMedium, color = MutedSlate)
                    }
                    Spacer(Modifier.width(10.dp))
                    AnimatedLudoDice(
                        value = displayedDice,
                        isRolling = isRolling,
                        canRoll = canRoll,
                        accentColor = activePalette.primary,
                        onRollClick = {
                            if (!canRoll) return@AnimatedLudoDice
                            scope.launch {
                                isRolling = true
                                repeat(6) {
                                    animatedDiceFace = Random.nextInt(1, 7)
                                    delay(50L)
                                }
                                val rolled = Random.nextInt(1, 7)
                                animatedDiceFace = rolled
                                isRolling = false
                                val outcome = LudoEngine.evaluateDiceRoll(
                                    pieces = pieces,
                                    seatTypes = seatTypes,
                                    seatNames = seatNames,
                                    seat = currentTurn,
                                    dice = rolled,
                                    consecutiveSixes = consecutiveSixes,
                                )
                                pieces = outcome.nextPieces
                                currentTurn = outcome.nextTurn
                                turnPhase = outcome.nextPhase
                                diceValue = outcome.nextDice
                                consecutiveSixes = outcome.nextConsecutiveSixes
                                actionText = outcome.actionText
                            }
                        },
                    )
                }
            }
        }

        if (winnerSeat in 0..3) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xBB080A14)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                    border = BorderStroke(2.dp, RoyalGold),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(24.dp).widthIn(max = 360.dp).fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(48.dp))
                        Text("پایان بازی!", style = MaterialTheme.typography.headlineMedium, color = RoyalGoldLight)
                        Text("${seatNames[winnerSeat]} برنده شد!", style = MaterialTheme.typography.bodyLarge, color = CreamWhite, textAlign = TextAlign.Center)
                        Button(
                            onClick = { resetBoard(humanPlayerCount) },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = Color(0xFF1A1405)),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("بازی دوباره", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineSeatBadge(
    name: String,
    seatIdx: Int,
    pieces: List<Long>,
    isTurn: Boolean,
    isBot: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = SeatPalettes[seatIdx]
    val done = (0..3).count { pieces[seatIdx * 4 + it] == 56L }
    Surface(
        color = if (isTurn) palette.container else SurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isTurn) 2.dp else 1.dp, if (isTurn) RoyalGold else BorderSubtle),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(22.dp).clip(CircleShape).background(palette.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBot) {
                        Icon(Icons.Rounded.SmartToy, contentDescription = null, tint = palette.onPrimary, modifier = Modifier.size(13.dp))
                    } else {
                        Text("$done", style = MaterialTheme.typography.labelSmall, color = palette.onPrimary)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(name, style = MaterialTheme.typography.labelMedium, color = CreamWhite, maxLines = 1)
            }
            Text("$done/۴", style = MaterialTheme.typography.labelSmall, color = if (isTurn) RoyalGoldLight else MutedSlate)
        }
    }
}
