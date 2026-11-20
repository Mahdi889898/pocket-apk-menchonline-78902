package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.GameRoom
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.theme.AvatarColors
import com.example.ui.theme.AvatarEmojis
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

@Composable
fun LobbyScreen(
    viewModel: LobbyViewModel,
    onEnterRoom: (String) -> Unit,
    onSignOutClick: () -> Unit,
) {
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val publicRoomsState by viewModel.publicRoomsState.collectAsStateWithLifecycle()
    val myRoomsState by viewModel.myRoomsState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data
    val playerName = profile?.displayName?.ifBlank { "بازیکن" } ?: "بازیکن"

    var roomCodeInput by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF151934), DeepIndigoBg))
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Top Profile & Sign Out Bar
            item {
                ProfileHeaderCard(
                    profile = profile,
                    onEditClick = { showEditProfileDialog = true },
                    onSignOutClick = onSignOutClick,
                )
            }

            // Error Alert Banner
            if (actionError != null) {
                item {
                    Surface(
                        color = CrimsonDanger.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = actionError ?: "",
                                color = Color(0xFFFFBCC4),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::clearError) {
                                Text("بستن", color = RoyalGoldLight)
                            }
                        }
                    }
                }
            }

            // 2. Active / Rejoin Rooms Banner
            val activeMyRooms = (myRoomsState as? UiState.Success)?.data.orEmpty()
            if (activeMyRooms.isNotEmpty()) {
                item {
                    val room = activeMyRooms.first()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16382E)),
                        border = BorderStroke(1.dp, EmeraldOnline.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "شما یک بازی فعال دارید!",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldOnline,
                                )
                                Text(
                                    text = "${room.title} • کد: ${room.roomCode}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CreamWhite,
                                )
                            }
                            Button(
                                onClick = { onEnterRoom(room.roomCode) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldOnline,
                                    contentColor = Color(0xFF062619),
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("rejoin_room_button"),
                            ) {
                                Text("بازگشت به میز", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. Hero Banner & Quick Actions
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(138.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_hero_banner),
                                contentDescription = "بنر تالار منچ",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color(0x330E1120), Color(0xEE171C30))
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "تالار منچ چهارنفره",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = CreamWhite,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = null,
                                        tint = EmeraldOnline,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "همراه با چت صوتی زنده بین بازیکنان",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RoyalGoldLight,
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { showCreateDialog = true },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RoyalGold,
                                        contentColor = Color(0xFF1A1405),
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("create_room_button"),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("ساخت اتاق آنلاین", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.createRoom(
                                            title = "بازی سریع $playerName",
                                            visibility = "private",
                                            playerName = playerName,
                                            fillWithBots = true,
                                            onRoomReady = onEnterRoom,
                                        )
                                    },
                                    enabled = !isBusy,
                                    border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.65f)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("quick_bot_match_button"),
                                ) {
                                    Icon(
                                        Icons.Rounded.SmartToy,
                                        contentDescription = null,
                                        tint = RoyalGoldLight,
                                        modifier = Modifier.size(19.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("شروع فوری با ربات", color = RoyalGoldLight, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Join by room code
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = roomCodeInput,
                                    onValueChange = { roomCodeInput = it.uppercase().take(8) },
                                    placeholder = { Text("کد ۶ رقمی اتاق را وارد کنید...", color = MutedSlate) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = RoyalGold,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedTextColor = CreamWhite,
                                        unfocusedTextColor = CreamWhite,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("join_code_input"),
                                )
                                Button(
                                    onClick = {
                                        viewModel.joinRoom(roomCodeInput, playerName, onEnterRoom)
                                    },
                                    enabled = !isBusy && roomCodeInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceHighlight,
                                        contentColor = CreamWhite,
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("join_by_code_button"),
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = "ورود به اتاق")
                                    Spacer(Modifier.width(6.dp))
                                    Text("ورود")
                                }
                            }
                        }
                    }
                }
            }

            // 4. Public Rooms Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            tint = EmeraldOnline,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "اتاق‌های باز آنلاین",
                            style = MaterialTheme.typography.titleLarge,
                            color = CreamWhite,
                        )
                    }
                    Text(
                        text = "۴ نفره • زنده",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSlate,
                    )
                }
            }

            when (val rooms = publicRoomsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = RoyalGold)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Surface(
                            color = SurfaceNavy,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = rooms.message,
                                color = CrimsonDanger,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (rooms.data.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                                border = BorderStroke(1.dp, BorderSubtle),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Groups,
                                        contentDescription = null,
                                        tint = MutedSlate,
                                        modifier = Modifier.size(40.dp),
                                    )
                                    Text(
                                        text = "فعلاً اتاق عمومی باز نیست",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = CreamWhite,
                                    )
                                    Text(
                                        text = "یک اتاق جدید بسازید یا بازی فوری با ربات‌ها را شروع کنید!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedSlate,
                                    )
                                }
                            }
                        }
                    } else {
                        items(rooms.data, key = { it.roomCode }) { room ->
                            PublicRoomCard(
                                room = room,
                                isBusy = isBusy,
                                onJoinClick = {
                                    viewModel.joinRoom(room.roomCode, playerName, onEnterRoom)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            defaultTitle = "میز منچ $playerName",
            isBusy = isBusy,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, visibility ->
                showCreateDialog = false
                viewModel.createRoom(
                    title = title,
                    visibility = visibility,
                    playerName = playerName,
                    fillWithBots = false,
                    onRoomReady = onEnterRoom,
                )
            },
        )
    }

    if (showEditProfileDialog && profile != null) {
        EditProfileDialog(
            initialName = profile.displayName,
            initialAvatar = profile.avatarIndex,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, avatar ->
                showEditProfileDialog = false
                viewModel.updateProfile(name, avatar)
            },
        )
    }
}

@Composable
private fun ProfileHeaderCard(
    profile: UserProfile?,
    onEditClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    val avatarIdx = (profile?.avatarIndex ?: 0L).toInt().coerceIn(0, AvatarColors.lastIndex)
    Surface(
        color = SurfaceNavy,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(AvatarColors[avatarIdx].copy(alpha = 0.25f))
                    .border(2.dp, AvatarColors[avatarIdx], CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = AvatarEmojis[avatarIdx], fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile?.displayName ?: "در حال بارگذاری...",
                        style = MaterialTheme.typography.titleMedium,
                        color = CreamWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_profile_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "ویرایش نام",
                            tint = RoyalGoldLight,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = RoyalGold,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${profile?.wins ?: 0} برد",
                            style = MaterialTheme.typography.labelMedium,
                            color = RoyalGoldLight,
                        )
                    }
                    Text(
                        text = "• ${profile?.gamesPlayed ?: 0} بازی",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSlate,
                    )
                }
            }
            IconButton(
                onClick = onSignOutClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("sign_out_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "خروج از حساب",
                    tint = MutedSlate,
                )
            }
        }
    }
}

@Composable
private fun PublicRoomCard(
    room: GameRoom,
    isBusy: Boolean,
    onJoinClick: () -> Unit,
) {
    val filledSeats = room.seatTypes.count { it != "empty" }
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
        border = BorderStroke(1.dp, BorderSubtle),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("room_card_${room.roomCode}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = room.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = CreamWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = room.roomCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = RoyalGoldLight,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    room.seatTypes.forEachIndexed { idx, type ->
                        val active = type != "empty"
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) SeatPalettes[idx].primary else SurfaceHighlight
                                )
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "$filledSeats از ۴ بازیکن",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate,
                    )
                }
            }

            Button(
                onClick = onJoinClick,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldOnline,
                    contentColor = Color(0xFF062619),
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("join_room_${room.roomCode}"),
            ) {
                Text("پیوستن", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CreateRoomDialog(
    defaultTitle: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (title: String, visibility: String) -> Unit,
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var visibility by remember { mutableStateOf("public") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceNavy,
        title = {
            Text("ساخت اتاق منچ", style = MaterialTheme.typography.titleLarge, color = CreamWhite)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    label = { Text("نام میز بازی") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_title_input"),
                )
                Text("نوع اتاق:", style = MaterialTheme.typography.labelLarge, color = MutedSlate)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = visibility == "public",
                        onClick = { visibility = "public" },
                        label = { Text("عمومی (در لیست لابی)") },
                        leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldOnline.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldOnline,
                        ),
                    )
                    FilterChip(
                        selected = visibility == "private",
                        onClick = { visibility = "private" },
                        label = { Text("خصوصی (با کد)") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoyalGold.copy(alpha = 0.2f),
                            selectedLabelColor = RoyalGoldLight,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, visibility) },
                enabled = !isBusy && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = Color(0xFF1A1405)),
                modifier = Modifier.testTag("confirm_create_room_button"),
            ) {
                Text("ساخت میز", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = MutedSlate)
            }
        },
    )
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialAvatar: Long,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedAvatar by remember { mutableLongStateOf(initialAvatar) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceNavy,
        title = {
            Text("ویرایش نمایه بازیکن", style = MaterialTheme.typography.titleLarge, color = CreamWhite)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("نام نمایشی در بازی") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                )
                Text("آواتار خود را انتخاب کنید:", style = MaterialTheme.typography.labelLarge, color = MutedSlate)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(AvatarEmojis.indices.toList()) { idx ->
                        val isSelected = selectedAvatar.toInt() == idx
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AvatarColors[idx].copy(alpha = if (isSelected) 0.35f else 0.14f))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) RoyalGold else BorderSubtle,
                                    shape = CircleShape,
                                )
                                .clickable { selectedAvatar = idx.toLong() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(AvatarEmojis[idx], fontSize = 22.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedAvatar) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalGold, contentColor = Color(0xFF1A1405)),
                modifier = Modifier.testTag("save_profile_button"),
            ) {
                Text("ذخیره", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("لغو", color = MutedSlate)
            }
        },
    )
}
