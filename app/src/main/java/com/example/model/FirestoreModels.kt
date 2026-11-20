package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "بازیکن",
    val avatarIndex: Long = 0L,
    val wins: Long = 0L,
    val gamesPlayed: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class GameRoom(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val hostUid: String = "",
    val title: String = "میز منچ",
    val visibility: String = "public",
    val status: String = "waiting",
    val memberUids: List<String> = emptyList(),
    val seatUids: List<String> = listOf("", "", "", ""),
    val seatNames: List<String> = listOf("", "", "", ""),
    val seatTypes: List<String> = listOf("empty", "empty", "empty", "empty"),
    val seatMuted: List<Boolean> = listOf(false, false, false, false),
    val seatSpeaking: List<Boolean> = listOf(false, false, false, false),
    val pieces: List<Long> = List(16) { -1L },
    val currentTurn: Long = 0L,
    val diceValue: Long = 0L,
    val turnPhase: String = "roll",
    val consecutiveSixes: Long = 0L,
    val winnerSeat: Long = -1L,
    val lastActionText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun seatOf(uid: String): Int = seatUids.indexOf(uid)
}

@Keep
@IgnoreExtraProperties
data class VoiceSignal(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderUid: String = "",
    val targetUid: String = "",
    val memberUids: List<String> = emptyList(),
    val type: String = "offer",
    val sdp: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class RoomMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)
