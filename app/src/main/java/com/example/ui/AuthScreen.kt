package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.onGoogleSignInClicked
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
import com.example.ui.theme.SurfaceNavy

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showOfflineMode by remember { mutableStateOf(false) }

    if (showOfflineMode) {
        OfflineLudoScreen(onBackToAuth = { showOfflineMode = false })
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF151934), DeepIndigoBg, Color(0xFF090B14))
                )
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Hero Artwork Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner),
                        contentDescription = "تصویر تخته بازی منچ آنلاین",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xCC0E1120))
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SeatPalettes.forEach { seat ->
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(seat.primary)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "تالار منچ چهارنفره",
                            style = MaterialTheme.typography.labelLarge,
                            color = RoyalGoldLight,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "منچ آنلاین",
                    style = MaterialTheme.typography.displayLarge,
                    color = CreamWhite,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "بازی کلاسیک منچ را با دوستان یا بازیکنان آنلاین بازی کنید و در حین حرکت مهره‌ها به صورت زنده گپ صوتی بزنید.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MutedSlate,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        FeatureRow(
                            icon = Icons.Rounded.Groups,
                            tint = SeatPalettes[3].light,
                            title = "رقابت ۴ نفره آنلاین + ربات هوشمند",
                            subtitle = "ساخت میز خصوصی با کد دعوت یا پیوستن به میزهای باز",
                        )
                        FeatureRow(
                            icon = Icons.Rounded.Mic,
                            tint = EmeraldOnline,
                            title = "چت صوتی زنده روی میز بازی",
                            subtitle = "گفتگوی همزمان با میکروفون و کنترل صدای هر بازیکن",
                        )
                        FeatureRow(
                            icon = Icons.Rounded.Casino,
                            tint = RoyalGold,
                            title = "بدون نیاز به ثبت‌نام و فرم ایمیل",
                            subtitle = "ورود فوری و یک‌لمسی با حساب گوگل گوشی شما",
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = CrimsonDanger.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFFB4BE),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = { detail ->
                                isLoading = false
                                errorMessage = if (!detail.isNullOrBlank()) {
                                    "ورود با گوگل تکمیل نشد: $detail"
                                } else {
                                    "ورود با گوگل لغو شد. لطفاً دوباره روی دکمه ورود بزنید."
                                }
                            },
                        )
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalGold,
                        contentColor = Color(0xFF1A1405),
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button"),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF1A1405),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "ورود سریع با گوگل (بدون فرم ایمیل)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showOfflineMode = true },
                    border = BorderStroke(1.dp, EmeraldOnline.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("play_offline_no_login_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = EmeraldOnline,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "بازی فوری بدون ورود (آفلاین با ربات یا ۴ نفره)",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldOnline,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "بخش آنلاین و چت صوتی نیازمند تایید حساب گوگل است",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedSlate,
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = CreamWhite,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSlate,
            )
        }
    }
}
