package com.example.model

import kotlin.random.Random

data class BoardPoint(val row: Float, val col: Float)

data class MoveOutcome(
    val nextPieces: List<Long>,
    val nextTurn: Int,
    val nextPhase: String,
    val nextDice: Int,
    val nextConsecutiveSixes: Int,
    val winnerSeat: Int,
    val nextStatus: String,
    val capturedOpponent: Boolean,
    val reachedGoal: Boolean,
    val actionText: String,
)

object LudoEngine {

    val SAFE_TRACK_INDICES = setOf(0, 8, 13, 21, 26, 34, 39, 47)
    val START_OFFSETS = intArrayOf(0, 13, 26, 39)

    val MAIN_TRACK_COORDS: List<BoardPoint> = listOf(
        // 0..4: Red arm rightwards
        BoardPoint(6f, 1f), BoardPoint(6f, 2f), BoardPoint(6f, 3f), BoardPoint(6f, 4f), BoardPoint(6f, 5f),
        // 5..12: Top arm up -> right -> down
        BoardPoint(5f, 6f), BoardPoint(4f, 6f), BoardPoint(3f, 6f), BoardPoint(2f, 6f),
        BoardPoint(1f, 6f), BoardPoint(0f, 6f), BoardPoint(0f, 7f), BoardPoint(0f, 8f),
        // 13..17: Top arm down
        BoardPoint(1f, 8f), BoardPoint(2f, 8f), BoardPoint(3f, 8f), BoardPoint(4f, 8f), BoardPoint(5f, 8f),
        // 18..25: Right arm right -> down -> left
        BoardPoint(6f, 9f), BoardPoint(6f, 10f), BoardPoint(6f, 11f), BoardPoint(6f, 12f),
        BoardPoint(6f, 13f), BoardPoint(6f, 14f), BoardPoint(7f, 14f), BoardPoint(8f, 14f),
        // 26..30: Right arm leftwards
        BoardPoint(8f, 13f), BoardPoint(8f, 12f), BoardPoint(8f, 11f), BoardPoint(8f, 10f), BoardPoint(8f, 9f),
        // 31..38: Bottom arm down -> left -> up
        BoardPoint(9f, 8f), BoardPoint(10f, 8f), BoardPoint(11f, 8f), BoardPoint(12f, 8f),
        BoardPoint(13f, 8f), BoardPoint(14f, 8f), BoardPoint(14f, 7f), BoardPoint(14f, 6f),
        // 39..43: Bottom arm upwards
        BoardPoint(13f, 6f), BoardPoint(12f, 6f), BoardPoint(11f, 6f), BoardPoint(10f, 6f), BoardPoint(9f, 6f),
        // 44..51: Left arm left -> up -> right
        BoardPoint(8f, 5f), BoardPoint(8f, 4f), BoardPoint(8f, 3f), BoardPoint(8f, 2f),
        BoardPoint(8f, 1f), BoardPoint(8f, 0f), BoardPoint(7f, 0f), BoardPoint(6f, 0f),
    )

    val HOME_YARD_COORDS: List<List<BoardPoint>> = listOf(
        // Seat 0 (Red - Top Left)
        listOf(BoardPoint(1.65f, 1.65f), BoardPoint(1.65f, 3.35f), BoardPoint(3.35f, 1.65f), BoardPoint(3.35f, 3.35f)),
        // Seat 1 (Green - Top Right)
        listOf(BoardPoint(1.65f, 10.65f), BoardPoint(1.65f, 12.35f), BoardPoint(3.35f, 10.65f), BoardPoint(3.35f, 12.35f)),
        // Seat 2 (Yellow - Bottom Right)
        listOf(BoardPoint(10.65f, 10.65f), BoardPoint(10.65f, 12.35f), BoardPoint(12.35f, 10.65f), BoardPoint(12.35f, 12.35f)),
        // Seat 3 (Blue - Bottom Left)
        listOf(BoardPoint(10.65f, 1.65f), BoardPoint(10.65f, 3.35f), BoardPoint(12.35f, 1.65f), BoardPoint(12.35f, 3.35f)),
    )

    val HOME_STRETCH_COORDS: List<List<BoardPoint>> = listOf(
        // Seat 0 (Red): row 7, cols 1..5
        listOf(BoardPoint(7f, 1f), BoardPoint(7f, 2f), BoardPoint(7f, 3f), BoardPoint(7f, 4f), BoardPoint(7f, 5f)),
        // Seat 1 (Green): rows 1..5, col 7
        listOf(BoardPoint(1f, 7f), BoardPoint(2f, 7f), BoardPoint(3f, 7f), BoardPoint(4f, 7f), BoardPoint(5f, 7f)),
        // Seat 2 (Yellow): row 7, cols 13..9
        listOf(BoardPoint(7f, 13f), BoardPoint(7f, 12f), BoardPoint(7f, 11f), BoardPoint(7f, 10f), BoardPoint(7f, 9f)),
        // Seat 3 (Blue): rows 13..9, col 7
        listOf(BoardPoint(13f, 7f), BoardPoint(12f, 7f), BoardPoint(11f, 7f), BoardPoint(10f, 7f), BoardPoint(9f, 7f)),
    )

    val GOAL_COORDS: List<BoardPoint> = listOf(
        BoardPoint(7f, 6.2f), // Red
        BoardPoint(6.2f, 7f), // Green
        BoardPoint(7f, 7.8f), // Yellow
        BoardPoint(7.8f, 7f), // Blue
    )

    fun getBoardPoint(pieceGlobalIndex: Int, progress: Int): BoardPoint {
        val seat = pieceGlobalIndex / 4
        val localIdx = pieceGlobalIndex % 4
        return when {
            progress < 0 -> HOME_YARD_COORDS[seat][localIdx]
            progress in 0..50 -> {
                val trackIdx = (START_OFFSETS[seat] + progress) % 52
                MAIN_TRACK_COORDS[trackIdx]
            }
            progress in 51..55 -> HOME_STRETCH_COORDS[seat][progress - 51]
            else -> GOAL_COORDS[seat]
        }
    }

    fun getMovablePieces(pieces: List<Long>, seat: Int, dice: Int): List<Int> {
        if (dice !in 1..6 || seat !in 0..3 || pieces.size != 16) return emptyList()
        val movable = mutableListOf<Int>()
        for (local in 0..3) {
            val idx = seat * 4 + local
            val p = pieces[idx].toInt()
            if (p == -1 && dice == 6) {
                movable.add(idx)
            } else if (p in 0..55 && p + dice <= 56) {
                movable.add(idx)
            }
        }
        return movable
    }

    fun nextActiveSeat(seatTypes: List<String>, fromSeat: Int): Int {
        for (offset in 1..4) {
            val candidate = (fromSeat + offset) % 4
            if (candidate < seatTypes.size && seatTypes[candidate] != "empty") {
                return candidate
            }
        }
        return fromSeat
    }

    fun evaluateDiceRoll(
        pieces: List<Long>,
        seatTypes: List<String>,
        seatNames: List<String>,
        seat: Int,
        dice: Int,
        consecutiveSixes: Int,
    ): MoveOutcome {
        val playerName = seatNames.getOrNull(seat)?.ifBlank { "بازیکن" } ?: "بازیکن"
        if (dice == 6 && consecutiveSixes + 1 >= 3) {
            val nextSeat = nextActiveSeat(seatTypes, seat)
            return MoveOutcome(
                nextPieces = pieces,
                nextTurn = nextSeat,
                nextPhase = "roll",
                nextDice = 0,
                nextConsecutiveSixes = 0,
                winnerSeat = -1,
                nextStatus = "playing",
                capturedOpponent = false,
                reachedGoal = false,
                actionText = "$playerName سه بار پیاپی ۶ آورد و نوبتش سوخت!",
            )
        }

        val movable = getMovablePieces(pieces, seat, dice)
        if (movable.isEmpty()) {
            val nextSeat = nextActiveSeat(seatTypes, seat)
            return MoveOutcome(
                nextPieces = pieces,
                nextTurn = nextSeat,
                nextPhase = "roll",
                nextDice = 0,
                nextConsecutiveSixes = 0,
                winnerSeat = -1,
                nextStatus = "playing",
                capturedOpponent = false,
                reachedGoal = false,
                actionText = "$playerName عدد $dice آورد؛ حرکتی ممکن نیست.",
            )
        }

        val nextSixes = if (dice == 6) consecutiveSixes + 1 else 0
        return MoveOutcome(
            nextPieces = pieces,
            nextTurn = seat,
            nextPhase = "move",
            nextDice = dice,
            nextConsecutiveSixes = nextSixes,
            winnerSeat = -1,
            nextStatus = "playing",
            capturedOpponent = false,
            reachedGoal = false,
            actionText = "$playerName عدد $dice آورد! یک مهره را حرکت دهید.",
        )
    }

    fun applyMove(
        pieces: List<Long>,
        seatTypes: List<String>,
        seatNames: List<String>,
        seat: Int,
        pieceGlobalIndex: Int,
        dice: Int,
        consecutiveSixes: Int,
    ): MoveOutcome {
        val nextPieces = pieces.toMutableList()
        val currentP = nextPieces[pieceGlobalIndex].toInt()
        val nextP = if (currentP == -1) 0 else currentP + dice
        nextPieces[pieceGlobalIndex] = nextP.toLong()

        var captured = false
        if (nextP in 0..50) {
            val globalCell = (START_OFFSETS[seat] + nextP) % 52
            if (globalCell !in SAFE_TRACK_INDICES) {
                for (otherSeat in 0..3) {
                    if (otherSeat == seat) continue
                    for (local in 0..3) {
                        val otherIdx = otherSeat * 4 + local
                        val otherP = nextPieces[otherIdx].toInt()
                        if (otherP in 0..50) {
                            val otherCell = (START_OFFSETS[otherSeat] + otherP) % 52
                            if (otherCell == globalCell) {
                                nextPieces[otherIdx] = -1L
                                captured = true
                            }
                        }
                    }
                }
            }
        }

        val reachedGoal = nextP == 56
        val won = (0..3).all { nextPieces[seat * 4 + it] == 56L }
        val playerName = seatNames.getOrNull(seat)?.ifBlank { "بازیکن" } ?: "بازیکن"

        if (won) {
            return MoveOutcome(
                nextPieces = nextPieces,
                nextTurn = seat,
                nextPhase = "roll",
                nextDice = dice,
                nextConsecutiveSixes = 0,
                winnerSeat = seat,
                nextStatus = "finished",
                capturedOpponent = captured,
                reachedGoal = true,
                actionText = "🏆 $playerName برنده بازی منچ شد!",
            )
        }

        val bonusTurn = dice == 6 || captured || reachedGoal
        val nextTurn = if (bonusTurn) seat else nextActiveSeat(seatTypes, seat)
        val nextSixes = if (dice == 6) consecutiveSixes else 0

        val msg = when {
            captured -> "💥 $playerName مهره حریف را زد و جایزه نوبت گرفت!"
            reachedGoal -> "🎯 مهره $playerName به خانه رسید! نوبت جایزه!"
            currentP == -1 -> "✨ $playerName مهره جدید وارد زمین کرد!"
            dice == 6 -> "🎲 $playerName با ۶ دوباره نوبت دارد!"
            else -> "$playerName مهره خود را $dice خانه جلو برد."
        }

        return MoveOutcome(
            nextPieces = nextPieces,
            nextTurn = nextTurn,
            nextPhase = "roll",
            nextDice = 0,
            nextConsecutiveSixes = nextSixes,
            winnerSeat = -1,
            nextStatus = "playing",
            capturedOpponent = captured,
            reachedGoal = reachedGoal,
            actionText = msg,
        )
    }

    fun pickBestBotMove(pieces: List<Long>, seat: Int, dice: Int): Int? {
        val movable = getMovablePieces(pieces, seat, dice)
        if (movable.isEmpty()) return null
        if (movable.size == 1) return movable.first()

        return movable.maxByOrNull { idx ->
            val currentP = pieces[idx].toInt()
            val nextP = if (currentP == -1) 0 else currentP + dice
            var score = nextP * 2

            if (nextP == 56) score += 500
            if (currentP == -1 && dice == 6) score += 320
            if (nextP in 51..55 && currentP <= 50) score += 180

            if (nextP in 0..50) {
                val targetCell = (START_OFFSETS[seat] + nextP) % 52
                if (targetCell in SAFE_TRACK_INDICES) {
                    score += 120
                } else {
                    for (otherSeat in 0..3) {
                        if (otherSeat == seat) continue
                        for (local in 0..3) {
                            val otherP = pieces[otherSeat * 4 + local].toInt()
                            if (otherP in 0..50 && (START_OFFSETS[otherSeat] + otherP) % 52 == targetCell) {
                                score += 450
                            }
                        }
                    }
                }
            }
            score + Random.nextInt(0, 15)
        }
    }
}
