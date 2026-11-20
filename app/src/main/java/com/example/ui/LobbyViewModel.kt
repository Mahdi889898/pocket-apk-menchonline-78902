package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MenchRepository
import com.example.model.GameRoom
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LobbyViewModel(
    private val repository: MenchRepository,
    private val currentUserId: String,
    defaultDisplayName: String,
) : ViewModel() {

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing profile", root)
            emit(UiState.Error(root.message ?: "خطا در دریافت نمایه کاربر"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val publicRoomsState: StateFlow<UiState<List<GameRoom>>> = repository.observePublicWaitingRooms()
        .map<List<GameRoom>, UiState<List<GameRoom>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing public rooms", root)
            emit(UiState.Error(root.message ?: "خطا در بارگذاری اتاق‌های عمومی"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val myRoomsState: StateFlow<UiState<List<GameRoom>>> = repository.observeMyRooms(currentUserId)
        .map<List<GameRoom>, UiState<List<GameRoom>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing user rooms", root)
            emit(UiState.Error(root.message ?: "خطا در بارگذاری بازی‌های فعال"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(defaultDisplayName)
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    fun updateProfile(displayName: String, avatarIndex: Long) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateUserProfile(displayName, avatarIndex)
                .onFailure { _actionError.value = it.localizedMessage ?: "ذخیره نمایه انجام نشد." }
            _isBusy.value = false
        }
    }

    fun createRoom(
        title: String,
        visibility: String,
        playerName: String,
        fillWithBots: Boolean = false,
        onRoomReady: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            repository.createRoom(
                title = title,
                visibility = visibility,
                hostName = playerName,
                fillWithBotsImmediately = fillWithBots,
            ).onSuccess { code ->
                _isBusy.value = false
                onRoomReady(code)
            }.onFailure { err ->
                _isBusy.value = false
                _actionError.value = err.localizedMessage ?: "ساخت اتاق ناموفق بود."
            }
        }
    }

    fun joinRoom(roomCode: String, playerName: String, onRoomReady: (String) -> Unit) {
        val clean = roomCode.trim().uppercase()
        if (clean.length < 4) {
            _actionError.value = "لطفاً کد معتبر اتاق (حداقل ۴ کاراکتر) را وارد کنید."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _actionError.value = null
            repository.joinRoomByCode(clean, playerName)
                .onSuccess { code ->
                    _isBusy.value = false
                    onRoomReady(code)
                }.onFailure { err ->
                    _isBusy.value = false
                    _actionError.value = err.localizedMessage ?: "ورود به اتاق انجام نشد."
                }
        }
    }

    private companion object {
        const val TAG = "LobbyViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
