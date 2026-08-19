package com.scottsx.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.R
import com.scottsx.app.SessionCache
import com.scottsx.app.navigation.Routes
import com.scottsx.app.ui.theme.ScottsTechXColors

/** Landing screen — auto-forwards to the role home when a session exists. */
@Composable
fun WelcomeScreen(
    onLogin: () -> Unit,
    onSignUp: (role: String) -> Unit,
) {
    // Buyer or seller, chosen here and carried into registration. Defaults to
    // buyer because that is the overwhelming majority of new accounts; a
    // seller taps once to switch. The backend still constrains the value to
    // buyer/seller, so this can never be used to self-register as an admin.
    var role by remember { mutableStateOf("buyer") }
    // If we already have a session, jump straight to the role home.
    LaunchedEffect(Unit) {
        SessionCache.user.value?.let {
            if (SessionCache.isLoggedIn()) {
                // navigation handled by the NavHost-level effect in AppNavigation
            }
        }
    }

    val gradient = Brush.verticalGradient(
        listOf(ScottsTechXColors.BluePrimary, ScottsTechXColors.PurpleAccent, ScottsTechXColors.PinkAccent),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Full-bleed gradient, but the buttons must not sit under the
                // status bar or the gesture pill.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The full brand lockup, transparent and at its true 720x475
            // aspect ratio.
            //
            // This used to be `logo.png` — the complete artwork including the
            // "SCOTTSTECHX ENTERPRISES (U) LTD" wordmark and tagline, on its own
            // black square — crushed into a 112dp box. At that size the company
            // name was an illegible smudge, the black square sat as a visible
            // panel on top of the gradient, and the word "ScottsTechX" was then
            // printed AGAIN underneath it. One brand, shown twice, both badly.
            //
            // fillMaxWidth(0.82f) + aspectRatio keeps it sharp on a 360dp phone
            // and stops it dominating a tablet.
            Image(
                painter = painterResource(id = R.drawable.brand_lockup),
                contentDescription = "ScottsTechX Enterprises (U) Ltd",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(720f / 475f),
            )
            Text(
                "Uganda's marketplace. Buy from real local sellers with Mobile Money, cash on delivery, and an AI assistant that knows the live catalog.",
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(28.dp))

            // ---- Buyer / seller choice ----------------------------------
            Text(
                "I want to",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoleChoiceCard(
                    emoji = "🛍️",
                    title = "Buy",
                    subtitle = "Shop from local sellers",
                    selected = role == "buyer",
                    onClick = { role = "buyer" },
                    modifier = Modifier.weight(1f),
                )
                RoleChoiceCard(
                    emoji = "🏪",
                    title = "Sell",
                    subtitle = "Open my own store",
                    selected = role == "seller",
                    onClick = { role = "seller" },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onSignUp(role) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ScottsTechXColors.BluePrimary),
            ) {
                Text(
                    if (role == "seller") "Create seller account" else "Create buyer account",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
            ) {
                Text("I already have an account", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "🇺🇬 Kampala • Entebbe • Jinja • Mbarara • Gulu • Mbale",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            if (SessionCache.isLoggedIn()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Session active — sign in as ${SessionCache.user.value?.displayName ?: "you"}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * One of the two buyer/seller tiles.
 *
 * Selection is shown three ways at once — fill, border and a check — because
 * on the gradient a colour-only cue is easy to miss, and colour alone is not
 * an accessible signal.
 */
@Composable
private fun RoleChoiceCard(
    emoji: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(200),
        label = "roleBg",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 0.95f else 0.28f,
        animationSpec = tween(200),
        label = "roleBorder",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(14.dp),
            )
            .selectable(
                selected = selected,
                role = SemanticsRole.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            if (selected) {
                Spacer(Modifier.width(4.dp))
                Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
        )
    }
}

// Kept for parity with the master doc's route list.
object WelcomeRouteHolder {
    const val ROUTE = Routes.WELCOME
}
