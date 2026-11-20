package com.example.data

import android.content.Context
import com.example.R
import com.example.model.GameRoom
import com.example.model.RoomMessage
import com.example.model.UserProfile
import com.example.model.VoiceSignal
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class MenchRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("کاربر باید وارد حساب گوگل شده باشد.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "دسترسی غیرمجاز به نمایه کاربر دیگر" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(defaultName: String): Result<UserProfile> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                val parsed = existing.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (parsed != null) return Result.success(parsed)
            }
            val safeName = defaultName.trim().take(30).ifBlank { "بازیکن منچ" }
            val avatar = Random.nextInt(0, 8).toLong()
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "avatarIndex" to avatar,
                "wins" to 0L,
                "gamesPlayed" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(UserProfile(id = uid, userId = uid, displayName = safeName, avatarIndex = avatar))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(displayName: String, avatarIndex: Long): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val safeName = displayName.trim().take(30).ifBlank { "بازیکن" }
            val safeAvatar = avatarIndex.coerceIn(0L, 7L)
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "displayName" to safeName,
                    "avatarIndex" to safeAvatar,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordMatchResult(won: Boolean): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val updates = mutableMapOf<String, Any>(
                "gamesPlayed" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (won) {
                updates["wins"] = FieldValue.increment(1L)
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observePublicWaitingRooms(): Flow<List<GameRoom>> = flow {
        val uid = requireUserId()
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereEqualTo("visibility", "public")
                .whereEqualTo("status", "waiting")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(GameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyRooms(userId: String): Flow<List<GameRoom>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "شناسه کاربر نامعتبر است." }
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(GameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status != "finished" }
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomId: String): Flow<GameRoom?> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId"
        emitAll(
            db.collection("rooms").document(roomId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(GameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            repeat(6) { append(chars[Random.nextInt(chars.length)]) }
        }
    }

    suspend fun createRoom(
        title: String,
        visibility: String,
        hostName: String,
        fillWithBotsImmediately: Boolean = false,
    ): Result<String> {
        var docPath = "rooms"
        return try {
            val uid = requireUserId()
            val code = generateRoomCode()
            val docRef = db.collection("rooms").document(code)
            docPath = docRef.path
            val safeTitle = title.trim().take(40).ifBlank { "میز منچ $code" }
            val safeHostName = hostName.trim().take(30).ifBlank { "میزبان" }
            val safeVis = if (visibility == "private") "private" else "public"

            val seatTypes = if (fillWithBotsImmediately) {
                listOf("human", "bot", "bot", "bot")
            } else {
                listOf("human", "empty", "empty", "empty")
            }
            val seatNames = if (fillWithBotsImmediately) {
                listOf(safeHostName, "ربات سبز", "ربات زرد", "ربات آبی")
            } else {
                listOf(safeHostName, "", "", "")
            }
            val status = if (fillWithBotsImmediately) "playing" else "waiting"

            val payload = mapOf(
                "roomCode" to code,
                "hostUid" to uid,
                "title" to safeTitle,
                "visibility" to safeVis,
                "status" to "waiting",
                "memberUids" to listOf(uid),
                "seatUids" to listOf(uid, "", "", ""),
                "seatNames" to seatNames,
                "seatTypes" to seatTypes,
                "seatMuted" to listOf(false, false, false, false),
                "seatSpeaking" to listOf(false, false, false, false),
                "pieces" to List(16) { -1L },
                "currentTurn" to 0L,
                "diceValue" to 0L,
                "turnPhase" to "roll",
                "consecutiveSixes" to 0L,
                "winnerSeat" to -1L,
                "lastActionText" to if (fillWithBotsImmediately) "بازی با ربات‌ها شروع شد!" else "$safeHostName اتاق را ساخت.",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            if (fillWithBotsImmediately) {
                docRef.update(
                    mapOf(
                        "status" to status,
                        "seatNames" to seatNames,
                        "seatTypes" to seatTypes,
                        "currentTurn" to 0L,
                        "diceValue" to 0L,
                        "turnPhase" to "roll",
                        "lastActionText" to "بازی چهارنفره با ربات‌ها آغاز شد!",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinRoomByCode(roomCodeInput: String, playerName: String): Result<String> {
        val code = roomCodeInput.trim().uppercase()
        val docPath = "rooms/$code"
        return try {
            val uid = requireUserId()
            require(code.length in 4..16) { "کد اتاق معتبر نیست." }
            val docRef = db.collection("rooms").document(code)
            val safeName = playerName.trim().take(30).ifBlank { "بازیکن" }

            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (!snap.exists()) {
                    throw IllegalStateException("اتاقی با این کد پیدا نشد.")
                }
                val room = snap.toObject(GameRoom::class.java)
                    ?: throw IllegalStateException("اطلاعات اتاق نامعتبر است.")

                if (uid in room.memberUids) {
                    return@runTransaction code
                }
                if (room.status != "waiting") {
                    throw IllegalStateException("بازی این اتاق قبلاً شروع شده است.")
                }
                val emptySeat = room.seatTypes.indexOfFirst { it == "empty" }
                if (emptySeat == -1) {
                    throw IllegalStateException("ظرفیت این اتاق تکمیل است.")
                }

                val nextMembers = (room.memberUids + uid).distinct()
                val nextSeatUids = room.seatUids.toMutableList().apply { this[emptySeat] = uid }
                val nextSeatNames = room.seatNames.toMutableList().apply { this[emptySeat] = safeName }
                val nextSeatTypes = room.seatTypes.toMutableList().apply { this[emptySeat] = "human" }
                val nextSeatMuted = room.seatMuted.toMutableList().apply { this[emptySeat] = false }

                tx.update(
                    docRef,
                    mapOf(
                        "memberUids" to nextMembers,
                        "seatUids" to nextSeatUids,
                        "seatNames" to nextSeatNames,
                        "seatTypes" to nextSeatTypes,
                        "seatMuted" to nextSeatMuted,
                        "lastActionText" to "$safeName وارد میز بازی شد.",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                code
            }.await()
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun startGame(room: GameRoom, fillEmptyWithBots: Boolean): Result<Unit> {
        val docPath = "rooms/${room.roomCode}"
        return try {
            val uid = requireUserId()
            check(room.hostUid == uid) { "فقط میزبان می‌تواند بازی را شروع کند." }
            val defaultBotNames = listOf("ربات قرمز", "ربات سبز", "ربات زرد", "ربات آبی")
            val nextTypes = room.seatTypes.mapIndexed { index, type ->
                if (type == "empty" && fillEmptyWithBots) "bot" else type
            }
            val nextNames = room.seatNames.mapIndexed { index, name ->
                if (room.seatTypes[index] == "empty" && fillEmptyWithBots) defaultBotNames[index] else name
            }
            val activeCount = nextTypes.count { it != "empty" }
            require(activeCount >= 2) { "برای شروع حداقل ۲ بازیکن یا ربات لازم است." }

            val firstTurn = nextTypes.indexOfFirst { it != "empty" }.coerceAtLeast(0)
            db.collection("rooms").document(room.roomCode).update(
                mapOf(
                    "status" to "playing",
                    "seatNames" to nextNames,
                    "seatTypes" to nextTypes,
                    "currentTurn" to firstTurn.toLong(),
                    "diceValue" to 0L,
                    "turnPhase" to "roll",
                    "lastActionText" to "بازی آغاز شد! نوبت ${nextNames[firstTurn]}",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitTurnState(
        roomCode: String,
        pieces: List<Long>,
        currentTurn: Int,
        diceValue: Int,
        turnPhase: String,
        consecutiveSixes: Int,
        winnerSeat: Int,
        status: String,
        actionText: String,
    ): Result<Unit> {
        val docPath = "rooms/$roomCode"
        return try {
            requireUserId()
            db.collection("rooms").document(roomCode).update(
                mapOf(
                    "pieces" to pieces,
                    "currentTurn" to currentTurn.toLong(),
                    "diceValue" to diceValue.toLong(),
                    "turnPhase" to turnPhase,
                    "consecutiveSixes" to consecutiveSixes.toLong(),
                    "winnerSeat" to winnerSeat.toLong(),
                    "status" to status,
                    "lastActionText" to actionText.take(120),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSeatVoiceState(
        room: GameRoom,
        seatIndex: Int,
        muted: Boolean,
        speaking: Boolean,
    ): Result<Unit> {
        val docPath = "rooms/${room.roomCode}"
        return try {
            requireUserId()
            if (seatIndex !in 0..3) return Result.success(Unit)
            val nextMuted = room.seatMuted.toMutableList().apply { this[seatIndex] = muted }
            val nextSpeaking = room.seatSpeaking.toMutableList().apply { this[seatIndex] = speaking }
            db.collection("rooms").document(room.roomCode).update(
                mapOf(
                    "seatMuted" to nextMuted,
                    "seatSpeaking" to nextSpeaking,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(room: GameRoom): Result<Unit> {
        val docPath = "rooms/${room.roomCode}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("rooms").document(room.roomCode)
            if (room.status == "waiting" && (room.hostUid == uid || room.memberUids.size <= 1)) {
                docRef.delete().await()
                return Result.success(Unit)
            }
            if (room.status == "finished") {
                return Result.success(Unit)
            }
            val mySeat = room.seatOf(uid)
            val nextMembers = room.memberUids.filter { it != uid }
            if (nextMembers.isEmpty() && room.status == "waiting") {
                docRef.delete().await()
                return Result.success(Unit)
            }
            val nextSeatUids = room.seatUids.toMutableList()
            val nextSeatNames = room.seatNames.toMutableList()
            val nextSeatTypes = room.seatTypes.toMutableList()
            val nextSeatMuted = room.seatMuted.toMutableList()
            val nextSeatSpeaking = room.seatSpeaking.toMutableList()

            val leavingName = if (mySeat in 0..3) room.seatNames[mySeat] else "بازیکن"
            if (mySeat in 0..3) {
                nextSeatUids[mySeat] = ""
                nextSeatMuted[mySeat] = false
                nextSeatSpeaking[mySeat] = false
                if (room.status == "waiting") {
                    nextSeatTypes[mySeat] = "empty"
                    nextSeatNames[mySeat] = ""
                } else {
                    nextSeatTypes[mySeat] = "bot"
                    nextSeatNames[mySeat] = "ربات (${leavingName.take(10)})"
                }
            }

            if (nextMembers.isEmpty()) {
                // Only bots remain or solo player exited
                if (room.status == "waiting") {
                    docRef.delete().await()
                    return Result.success(Unit)
                }
            }

            if (room.status == "waiting") {
                docRef.update(
                    mapOf(
                        "memberUids" to nextMembers,
                        "seatUids" to nextSeatUids,
                        "seatNames" to nextSeatNames,
                        "seatTypes" to nextSeatTypes,
                        "seatMuted" to nextSeatMuted,
                        "lastActionText" to "$leavingName اتاق را ترک کرد.",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                if (nextMembers.isEmpty()) {
                    docRef.delete().await()
                } else {
                    docRef.update(
                        mapOf(
                            "memberUids" to nextMembers,
                            "seatUids" to nextSeatUids,
                            "seatNames" to nextSeatNames,
                            "seatTypes" to nextSeatTypes,
                            "seatMuted" to nextSeatMuted,
                            "seatSpeaking" to nextSeatSpeaking,
                            "lastActionText" to "$leavingName خارج شد و ربات جایگزین شد.",
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeIncomingSignals(roomId: String, myUid: String): Flow<List<VoiceSignal>> = flow {
        val uid = requireUserId()
        check(uid == myUid)
        val path = "rooms/$roomId/signals"
        emitAll(
            db.collection("rooms").document(roomId).collection("signals")
                .whereEqualTo("targetUid", myUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(VoiceSignal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendSignal(
        roomId: String,
        targetUid: String,
        memberUids: List<String>,
        type: String,
        sdp: String,
        sdpMid: String = "",
        sdpMLineIndex: Int = 0,
    ): Result<Unit> {
        var docPath = "rooms/$roomId/signals"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("rooms").document(roomId).collection("signals").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "roomId" to roomId,
                    "senderUid" to uid,
                    "targetUid" to targetUid,
                    "memberUids" to memberUids,
                    "type" to type,
                    "sdp" to sdp.take(12000),
                    "sdpMid" to sdpMid.take(64),
                    "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 10).toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSignal(roomId: String, signalId: String): Result<Unit> {
        val docPath = "rooms/$roomId/signals/$signalId"
        return try {
            requireUserId()
            db.collection("rooms").document(roomId).collection("signals").document(signalId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeRoomMessages(roomId: String, myUid: String): Flow<List<RoomMessage>> = flow {
        val uid = requireUserId()
        check(uid == myUid)
        val path = "rooms/$roomId/messages"
        emitAll(
            db.collection("rooms").document(roomId).collection("messages")
                .whereArrayContains("memberUids", myUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(RoomMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                        .take(12)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendRoomMessage(
        room: GameRoom,
        senderName: String,
        text: String,
    ): Result<Unit> {
        var docPath = "rooms/${room.roomCode}/messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(120)
            require(cleanText.isNotEmpty())
            val docRef = db.collection("rooms").document(room.roomCode).collection("messages").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "roomId" to room.roomCode,
                    "senderUid" to uid,
                    "senderName" to senderName.trim().take(30).ifBlank { "بازیکن" },
                    "memberUids" to room.memberUids,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }
}
