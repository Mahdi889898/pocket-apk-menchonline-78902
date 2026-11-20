package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.MenchRepository
import com.example.data.attemptAutoSignIn
import com.example.data.signOut
import com.example.ui.AuthScreen
import com.example.ui.GameRoomScreen
import com.example.ui.GameRoomViewModel
import com.example.ui.LobbyScreen
import com.example.ui.LobbyViewModel
import com.example.ui.theme.MenchTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

@Serializable
data object LobbyRoute

@Serializable
data class RoomRoute(val roomId: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MenchTheme {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope,
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
        )
    } else {
        val uid = user.uid
        val defaultName = user.displayName?.takeIf { it.isNotBlank() } ?: "بازیکن منچ"
        key(uid) {
            AuthenticatedNavHost(
                currentUserId = uid,
                defaultDisplayName = defaultName,
                onSignOutClick = {
                    signOut(
                        credentialManager = credentialManager,
                        scope = scope,
                    )
                },
            )
        }
    }
}

@Composable
private fun AuthenticatedNavHost(
    currentUserId: String,
    defaultDisplayName: String,
    onSignOutClick: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = LobbyRoute,
    ) {
        composable<LobbyRoute> {
            val lobbyViewModel: LobbyViewModel = viewModel(
                key = "lobby_$currentUserId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        LobbyViewModel(
                            repository = MenchRepository(db),
                            currentUserId = currentUserId,
                            defaultDisplayName = defaultDisplayName,
                        )
                    }
                },
            )
            LobbyScreen(
                viewModel = lobbyViewModel,
                onEnterRoom = { roomId ->
                    navController.navigate(RoomRoute(roomId = roomId)) {
                        launchSingleTop = true
                    }
                },
                onSignOutClick = onSignOutClick,
            )
        }

        composable<RoomRoute> { backStackEntry ->
            val route: RoomRoute = backStackEntry.toRoute()
            val roomViewModel: GameRoomViewModel = viewModel(
                key = "room_${currentUserId}_${route.roomId}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        GameRoomViewModel(
                            appContext = app,
                            repository = MenchRepository(db),
                            roomId = route.roomId,
                            currentUserId = currentUserId,
                        )
                    }
                },
            )
            GameRoomScreen(
                viewModel = roomViewModel,
                currentUserId = currentUserId,
                onExitToLobby = {
                    navController.popBackStack(LobbyRoute, inclusive = false)
                },
            )
        }
    }
}
