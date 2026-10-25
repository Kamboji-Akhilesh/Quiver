package com.kamboji.quiver.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kamboji.quiver.R
import com.kamboji.quiver.ai.AiModel
import com.kamboji.quiver.ui.components.Aurora
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.Display
import com.kamboji.quiver.ui.theme.Quiver

private val AiA = Color(0xFFA78BFA)
private val AiB = Color(0xFF22D3EE)

/**
 * First-run flow: what Quiver is → why it needs notifications (asked HERE, with
 * context, instead of ambushing from onCreate) → the AI model offer. Finishing
 * with [onFinish]`(openAi = true)` drops the user into the AI panel's installer.
 */
@Composable
fun OnboardingOverlay(onFinish: (openAi: Boolean) -> Unit) {
    val colors = Quiver.colors
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }

    // Notifications make alerts/cleanup work; media read powers the screenshot
    // tools. Both exist as runtime prompts only on Android 13+.
    val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        emptyArray()
    }
    fun allGranted() = needed.all {
        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(allGranted()) }
    // Advance whatever the answer — the point is context, not nagging.
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = allGranted()
        page = 2
    }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Aurora(Accents.Hub)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (page < 2) {
                    Text(
                        stringResource(R.string.onboarding_skip), color = colors.dim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(100.dp))
                            .clickable { onFinish(false) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))

            AnimatedContent(page, label = "onboarding") { p ->
                when (p) {
                    0 -> Page(
                        icon = null,
                        title = stringResource(R.string.onboarding_p1_title),
                        body = stringResource(R.string.onboarding_p1_body),
                    )
                    1 -> Page(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.onboarding_p2_title),
                        body = stringResource(R.string.onboarding_p2_body),
                    )
                    else -> Page(
                        icon = Icons.Filled.AutoAwesome,
                        title = stringResource(R.string.onboarding_p3_title),
                        body = stringResource(R.string.onboarding_p3_body, AiModel.DISPLAY_NAME, AiModel.SIZE_LABEL),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // page dots
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier.size(if (i == page) 22.dp else 7.dp, 7.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (i == page) AiA else colors.border2),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            when (page) {
                0 -> PrimaryButton(stringResource(R.string.onboarding_continue)) { page = 1 }
                1 -> {
                    val ready = granted || needed.isEmpty()
                    PrimaryButton(stringResource(if (ready) R.string.onboarding_continue else R.string.onboarding_allow_notifications)) {
                        if (ready) page = 2 else permissions.launch(needed)
                    }
                    GhostButton(stringResource(R.string.onboarding_not_now)) { page = 2 }
                }
                else -> {
                    PrimaryButton(stringResource(R.string.onboarding_install_ai)) { onFinish(true) }
                    GhostButton(stringResource(R.string.onboarding_maybe_later)) { onFinish(false) }
                }
            }
        }
    }
}

@Composable
private fun Page(icon: ImageVector?, title: String, body: String) {
    val colors = Quiver.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(AiA, AiB))),
            contentAlignment = Alignment.Center,
        ) {
            if (icon == null) {
                Text("Q", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp, fontFamily = Display)
            } else {
                Icon(icon, null, Modifier.size(40.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            title, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Display,
            color = colors.text, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            body, fontSize = 14.5.sp, color = colors.dim, lineHeight = 21.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(AiA, AiB)))
            .clickable(onClick = onClick).padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color(0xFF06121A), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun GhostButton(label: String, onClick: () -> Unit) {
    val colors = Quiver.colors
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = colors.dim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}
