package com.example

import com.example.model.LudoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoEngineTest {

    @Test
    fun pawnInYardNeedsSixToExit() {
        val pieces = List(16) { -1L }
        assertTrue(LudoEngine.getMovablePieces(pieces, seat = 0, dice = 5).isEmpty())
        assertEquals(listOf(0, 1, 2, 3), LudoEngine.getMovablePieces(pieces, seat = 0, dice = 6))
    }

    @Test
    fun captureOpponentOnUnsafeSquareResetsToYardAndGrantsBonusTurn() {
        val pieces = MutableList(16) { -1L }
        // Red (seat 0, offset 0) at progress 3 -> global cell 3
        // Green (seat 1, offset 13) at progress 44 -> (13 + 44) % 52 = 5 -> global cell 5 (unsafe!)
        pieces[0] = 3L
        pieces[4] = 44L

        val outcome = LudoEngine.applyMove(
            pieces = pieces,
            seatTypes = listOf("human", "human", "bot", "bot"),
            seatNames = listOf("سارا", "علی", "ربات ۳", "ربات ۴"),
            seat = 0,
            pieceGlobalIndex = 0,
            dice = 2,
            consecutiveSixes = 0,
        )

        assertTrue(outcome.capturedOpponent)
        assertEquals(5L, outcome.nextPieces[0])
        assertEquals(-1L, outcome.nextPieces[4])
        assertEquals(0, outcome.nextTurn) // bonus turn
    }

    @Test
    fun safeStarSquarePreventsCapture() {
        val pieces = MutableList(16) { -1L }
        // Global cell 8 is safe star! Red at 6 + dice 2 = 8. Green at 47 -> (13+47)%52 = 8.
        pieces[0] = 6L
        pieces[4] = 47L

        val outcome = LudoEngine.applyMove(
            pieces = pieces,
            seatTypes = listOf("human", "human", "bot", "bot"),
            seatNames = listOf("سارا", "علی", "ربات ۳", "ربات ۴"),
            seat = 0,
            pieceGlobalIndex = 0,
            dice = 2,
            consecutiveSixes = 0,
        )

        assertFalse(outcome.capturedOpponent)
        assertEquals(47L, outcome.nextPieces[4])
        assertEquals(1, outcome.nextTurn) // turn passes
    }

    @Test
    fun fourthPawnReaching56WinsMatch() {
        val pieces = MutableList(16) { -1L }
        pieces[0] = 56L
        pieces[1] = 56L
        pieces[2] = 56L
        pieces[3] = 54L

        val outcome = LudoEngine.applyMove(
            pieces = pieces,
            seatTypes = listOf("human", "human", "bot", "bot"),
            seatNames = listOf("سارا", "علی", "ربات ۳", "ربات ۴"),
            seat = 0,
            pieceGlobalIndex = 3,
            dice = 2,
            consecutiveSixes = 0,
        )

        assertEquals(0, outcome.winnerSeat)
        assertEquals("finished", outcome.nextStatus)
    }
}
