package com.scottsx.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
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
    onSignUp: () -> Unit,
) {
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
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The official brand mark — the same artwork the web app serves at
            // /brand/scottstechx-mark.png and the launcher uses as its icon.
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "ScottsTechX",
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(26.dp)),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "ScottsTechX",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Uganda's marketplace. Buy from real local sellers with Mobile Money, cash on delivery, and an AI assistant that knows the live catalog.",
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onSignUp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ScottsTechXColors.BluePrimary),
            ) {
                Text("Create account", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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

// Kept for parity with the master doc's route list.
object WelcomeRouteHolder {
    const val ROUTE = Routes.WELCOME
}
