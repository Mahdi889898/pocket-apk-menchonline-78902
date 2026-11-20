package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.LudoEngine
import com.example.ui.theme.BoardFrameDark
import com.example.ui.theme.BoardFrameLight
import com.example.ui.theme.BoardTileAlt
import com.example.ui.theme.BoardTileBorder
import com.example.ui.theme.BoardTileLight
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.SeatPalettes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun LudoBoardCanvas(
    pieces: List<Long>,
    movablePieces: Set<Int>,
    activeSeat: Int,
    onPieceClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pawn_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Smooth animated coordinates for all 16 pawns
    val animatedPoints = (0 until 16).map { idx ->
        val progress = pieces.getOrNull(idx)?.toInt() ?: -1
        val target = LudoEngine.getBoardPoint(idx, progress)
        val animRow by animateFloatAsState(
            targetValue = target.row,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.72f),
            label = "row_$idx",
        )
        val animCol by animateFloatAsState(
            targetValue = target.col,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.72f),
            label = "col_$idx",
        )
        animRow to animCol
    }

    // Group overlapping pawns on the same cell so they fan out cleanly
    val stackOffsets = computeStackOffsets(pieces)

    // Board geometry must stay fixed LTR so Red=TopLeft, Green=TopRight, Yellow=BottomRight, Blue=BottomLeft
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(16.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(listOf(BoardFrameLight, BoardFrameDark))
                )
                .padding(8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .testTag("ludo_board_canvas")
                    .pointerInput(pieces, movablePieces) {
                        detectTapGestures { tapOffset ->
                            if (movablePieces.isEmpty()) return@detectTapGestures
                            val cell = size.width / 15f
                            var bestIdx: Int? = null
                            var bestDist = Float.MAX_VALUE
                            for (idx in movablePieces) {
                                val (row, col) = animatedPoints[idx]
                                val (dx, dy) = stackOffsets[idx]
                                val center = Offset(
                                    x = (col + 0.5f) * cell + dx * cell * 0.24f,
                                    y = (row + 0.5f) * cell + dy * cell * 0.24f,
                                )
                                val dist = hypot(tapOffset.x - center.x, tapOffset.y - center.y)
                                if (dist < cell * 1.15f && dist < bestDist) {
                                    bestDist = dist
                                    bestIdx = idx
                                }
                            }
                            if (bestIdx != null) {
                                onPieceClick(bestIdx)
                            }
                        }
                    }
            ) {
                val boardSize = size.width
                val cell = boardSize / 15f

                // 1. Parchment base background
                drawRect(color = BoardTileLight, size = Size(boardSize, boardSize))

                // 2. Draw 15x15 track squares
                for (r in 0 until 15) {
                    for (c in 0 until 15) {
                        val inCornerYard = (r < 6 && c < 6) || (r < 6 && c > 8) || (r > 8 && c > 8) || (r > 8 && c < 6)
                        val inCenter = r in 6..8 && c in 6..8
                        if (inCornerYard || inCenter) continue

                        val tileColor = getTrackCellColor(r, c)
                        drawRect(
                            color = tileColor,
                            topLeft = Offset(c * cell, r * cell),
                            size = Size(cell, cell),
                        )
                        drawRect(
                            color = BoardTileBorder,
                            topLeft = Offset(c * cell, r * cell),
                            size = Size(cell, cell),
                            style = Stroke(width = 1.2f),
                        )
                    }
                }

                // 3. Draw Safe Star markers on the 8 safe track cells
                LudoEngine.SAFE_TRACK_INDICES.forEach { trackIdx ->
                    val pt = LudoEngine.MAIN_TRACK_COORDS[trackIdx]
                    val center = Offset((pt.col + 0.5f) * cell, (pt.row + 0.5f) * cell)
                    val isStartSquare = trackIdx in LudoEngine.START_OFFSETS
                    drawStar(
                        center = center,
                        outerRadius = cell * 0.34f,
                        innerRadius = cell * 0.16f,
                        color = if (isStartSquare) Color.White.copy(alpha = 0.92f) else RoyalGold.copy(alpha = 0.78f),
                    )
                }

                // 4. Draw 4 Home Yards (6x6 each)
                drawHomeYard(row = 0, col = 0, cell = cell, paletteIdx = 0, isActive = activeSeat == 0)
                drawHomeYard(row = 0, col = 9, cell = cell, paletteIdx = 1, isActive = activeSeat == 1)
                drawHomeYard(row = 9, col = 9, cell = cell, paletteIdx = 2, isActive = activeSeat == 2)
                drawHomeYard(row = 9, col = 0, cell = cell, paletteIdx = 3, isActive = activeSeat == 3)

                // 5. Draw Center Victory Home (3x3 triangles meeting at center)
                drawCenterGoalTriangles(cell = cell)

                // 6. Draw all 16 pawns (non-movable first, movable on top with pulsing glow!)
                val drawOrder = (0 until 16).sortedBy { if (it in movablePieces) 1 else 0 }
                drawOrder.forEach { idx ->
                    val seat = idx / 4
                    val (row, col) = animatedPoints[idx]
                    val (dx, dy) = stackOffsets[idx]
                    val isSameSquareGroup = dx != 0f || dy != 0f
                    val spread = if (isSameSquareGroup) cell * 0.21f else 0f
                    val center = Offset(
                        x = (col + 0.5f) * cell + dx * spread,
                        y = (row + 0.5f) * cell + dy * spread,
                    )
                    val isMovable = idx in movablePieces
                    val radius = if (isSameSquareGroup && !isMovable) cell * 0.29f else cell * 0.35f
                    drawPawn(
                        center = center,
                        radius = radius,
                        paletteIdx = seat,
                        isMovable = isMovable,
                        pulseScale = pulseScale,
                    )
                }
            }
        }
    }
}

private fun getTrackCellColor(r: Int, c: Int): Color {
    // Red start & home lane
    if (r == 6 && c == 1) return SeatPalettes[0].primary
    if (r == 7 && c in 1..5) return SeatPalettes[0].light.copy(alpha = 0.45f)
    // Green start & home lane
    if (r == 1 && c == 8) return SeatPalettes[1].primary
    if (c == 7 && r in 1..5) return SeatPalettes[1].light.copy(alpha = 0.45f)
    // Yellow start & home lane
    if (r == 8 && c == 13) return SeatPalettes[2].primary
    if (r == 7 && c in 9..13) return SeatPalettes[2].light.copy(alpha = 0.45f)
    // Blue start & home lane
    if (r == 13 && c == 6) return SeatPalettes[3].primary
    if (c == 7 && r in 9..13) return SeatPalettes[3].light.copy(alpha = 0.45f)

    return if ((r + c) % 2 == 0) BoardTileLight else BoardTileAlt
}

private fun DrawScope.drawHomeYard(
    row: Int,
    col: Int,
    cell: Float,
    paletteIdx: Int,
    isActive: Boolean,
) {
    val palette = SeatPalettes[paletteIdx]
    val topLeft = Offset(col * cell, row * cell)
    val yardSize = Size(6f * cell, 6f * cell)

    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(palette.primary, palette.dark),
            start = topLeft,
            end = Offset(topLeft.x + yardSize.width, topLeft.y + yardSize.height),
        ),
        topLeft = topLeft,
        size = yardSize,
    )

    if (isActive) {
        drawRect(
            color = RoyalGold,
            topLeft = topLeft,
            size = yardSize,
            style = Stroke(width = 4.5f),
        )
    }

    // Inner parchment rounded pedestal
    val pad = cell * 0.85f
    drawRoundRect(
        color = BoardTileLight,
        topLeft = Offset(topLeft.x + pad, topLeft.y + pad),
        size = Size(yardSize.width - pad * 2f, yardSize.height - pad * 2f),
        cornerRadius = CornerRadius(cell * 0.65f, cell * 0.65f),
    )

    // 4 circular bases
    LudoEngine.HOME_YARD_COORDS[paletteIdx].forEach { pt ->
        val center = Offset((pt.col + 0.5f) * cell, (pt.row + 0.5f) * cell)
        drawCircle(
            color = palette.primary.copy(alpha = 0.22f),
            radius = cell * 0.55f,
            center = center,
        )
        drawCircle(
            color = palette.primary.copy(alpha = 0.65f),
            radius = cell * 0.55f,
            center = center,
            style = Stroke(width = 2.5f),
        )
    }
}

private fun DrawScope.drawCenterGoalTriangles(cell: Float) {
    val left = 6f * cell
    val top = 6f * cell
    val right = 9f * cell
    val bottom = 9f * cell
    val center = Offset(7.5f * cell, 7.5f * cell)

    // Left (Red - 0)
    drawPath(
        path = Path().apply {
            moveTo(left, top); lineTo(center.x, center.y); lineTo(left, bottom); close()
        },
        color = SeatPalettes[0].primary,
    )
    // Top (Green - 1)
    drawPath(
        path = Path().apply {
            moveTo(left, top); lineTo(right, top); lineTo(center.x, center.y); close()
        },
        color = SeatPalettes[1].primary,
    )
    // Right (Yellow - 2)
    drawPath(
        path = Path().apply {
            moveTo(right, top); lineTo(right, bottom); lineTo(center.x, center.y); close()
        },
        color = SeatPalettes[2].primary,
    )
    // Bottom (Blue - 3)
    drawPath(
        path = Path().apply {
            moveTo(left, bottom); lineTo(center.x, center.y); lineTo(right, bottom); close()
        },
        color = SeatPalettes[3].primary,
    )

    drawRect(
        color = BoardFrameDark,
        topLeft = Offset(left, top),
        size = Size(3f * cell, 3f * cell),
        style = Stroke(width = 2.5f),
    )
    drawCircle(
        color = RoyalGold,
        radius = cell * 0.42f,
        center = center,
    )
    drawCircle(
        color = BoardFrameDark,
        radius = cell * 0.42f,
        center = center,
        style = Stroke(width = 2f),
    )
}

private fun DrawScope.drawPawn(
    center: Offset,
    radius: Float,
    paletteIdx: Int,
    isMovable: Boolean,
    pulseScale: Float,
) {
    val palette = SeatPalettes[paletteIdx]

    if (isMovable) {
        drawCircle(
            color = RoyalGold.copy(alpha = 0.48f),
            radius = radius * 1.42f * pulseScale,
            center = center,
        )
        drawCircle(
            color = Color.White,
            radius = radius * 1.24f,
            center = center,
            style = Stroke(width = 2.8f),
        )
    }

    // Drop shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.36f),
        radius = radius,
        center = Offset(center.x + 1.5f, center.y + 3f),
    )

    // Outer metallic rim
    drawCircle(
        color = Color.White,
        radius = radius,
        center = center,
    )

    // Jewel body
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.light, palette.primary, palette.dark),
            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.28f),
            radius = radius * 1.2f,
        ),
        radius = radius * 0.84f,
        center = center,
    )

    // Inner crown ring
    drawCircle(
        color = Color.White.copy(alpha = 0.65f),
        radius = radius * 0.38f,
        center = center,
        style = Stroke(width = 1.6f),
    )

    // Gloss specular dot
    drawCircle(
        color = Color.White.copy(alpha = 0.8f),
        radius = radius * 0.18f,
        center = Offset(center.x - radius * 0.26f, center.y - radius * 0.26f),
    )
}

private fun DrawScope.drawStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    color: Color,
) {
    val path = Path()
    for (i in 0 until 10) {
        val angle = (-90.0 + i * 36.0) * PI / 180.0
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + (cos(angle) * r).toFloat()
        val y = center.y + (sin(angle) * r).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color)
}

private fun computeStackOffsets(pieces: List<Long>): List<Pair<Float, Float>> {
    val result = MutableList(16) { 0f to 0f }
    val groups = mutableMapOf<String, MutableList<Int>>()
    for (idx in 0 until 16) {
        val p = pieces.getOrNull(idx)?.toInt() ?: -1
        if (p < 0) continue // Home yard already has 4 fixed spots
        val pt = LudoEngine.getBoardPoint(idx, p)
        val key = "${pt.row}_${pt.col}"
        groups.getOrPut(key) { mutableListOf() }.add(idx)
    }
    val offsets = listOf(-0.85f to -0.85f, 0.85f to -0.85f, -0.85f to 0.85f, 0.85f to 0.85f)
    groups.values.forEach { list ->
        if (list.size > 1) {
            list.forEachIndexed { i, pieceIdx ->
                result[pieceIdx] = offsets[i % offsets.size]
            }
        }
    }
    return result
}

@Composable
fun AnimatedLudoDice(
    value: Int,
    isRolling: Boolean,
    canRoll: Boolean,
    accentColor: Color,
    onRollClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isRolling) 360f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.55f),
        label = "dice_rot",
    )
    val scale by animateFloatAsState(
        targetValue = if (isRolling) 1.16f else if (canRoll) 1.04f else 0.96f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.6f),
        label = "dice_scale",
    )

    val displayFace = if (value in 1..6) value else 6

    Box(
        modifier = modifier
            .size((62 * scale).dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (canRoll) accentColor.copy(alpha = 0.25f) else Color.Transparent
            )
            .border(
                width = if (canRoll) 2.5.dp else 1.dp,
                color = if (canRoll) RoyalGold else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(enabled = canRoll && !isRolling, onClick = onRollClick)
            .semantics { contentDescription = "تاس بازی عدد $displayFace" }
            .testTag("dice_roll_button"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(46.dp)) {
            rotate(degrees = rotation) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFFFCF5), Color(0xFFEADDC4))
                    ),
                    cornerRadius = CornerRadius(12f, 12f),
                    size = size,
                )
                drawRoundRect(
                    color = RoyalGold,
                    cornerRadius = CornerRadius(12f, 12f),
                    size = size,
                    style = Stroke(width = 3f),
                )

                val pipColor = if (displayFace == 6) Color(0xFFD62839) else Color(0xFF1B1F36)
                val r = size.width * 0.095f
                val p1 = size.width * 0.26f
                val p2 = size.width * 0.5f
                val p3 = size.width * 0.74f

                val pips = when (displayFace) {
                    1 -> listOf(Offset(p2, p2))
                    2 -> listOf(Offset(p1, p1), Offset(p3, p3))
                    3 -> listOf(Offset(p1, p1), Offset(p2, p2), Offset(p3, p3))
                    4 -> listOf(Offset(p1, p1), Offset(p3, p1), Offset(p1, p3), Offset(p3, p3))
                    5 -> listOf(Offset(p1, p1), Offset(p3, p1), Offset(p2, p2), Offset(p1, p3), Offset(p3, p3))
                    else -> listOf(
                        Offset(p1, p1), Offset(p3, p1),
                        Offset(p1, p2), Offset(p3, p2),
                        Offset(p1, p3), Offset(p3, p3),
                    )
                }
                pips.forEach { offset ->
                    drawCircle(color = pipColor, radius = r, center = offset)
                }
            }
        }
    }
}
