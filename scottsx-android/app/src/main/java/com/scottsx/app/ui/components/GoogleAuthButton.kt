package com.scottsx.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.GoogleSignInHelper
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.launch

/**
 * "Login with Google" — one shared button for the login and sign-up
 * screens, so both flows behave identically.
 *
 * The button owns the whole flow: it launches the system account sheet,
 * exchanges the ID token with the backend, and only then fires [onSuccess].
 * Errors surface through [onError] so each screen shows them in its own
 * error slot; a user dismissing the sheet reports nothing at all.
 */
@Composable
fun GoogleAuthButton(
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Continue with Google",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(27.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(Color(0xFFF4F6FA))
            .border(width = 1.dp, color = Color(0xFFE1E6EF), shape = shape)
            .clickable(enabled = !busy) {
                busy = true
                scope.launch {
                    when (val outcome = GoogleSignInHelper.signIn(context)) {
                        is GoogleSignInHelper.Outcome.Success -> onSuccess()
                        is GoogleSignInHelper.Outcome.Cancelled -> Unit // user backed out — say nothing
                        is GoogleSignInHelper.Outcome.Unavailable -> onError(outcome.message)
                        is GoogleSignInHelper.Outcome.Failed -> onError(outcome.message)
                    }
                    busy = false
                }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = ScottsTechXColors.BluePrimary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                GoogleG(modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF101828),
            )
        }
    }
}

/** "──── or continue with ────" divider between the form and social buttons. */
@Composable
fun OrDivider(modifier: Modifier = Modifier, label: String = "or") {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Divider(modifier = Modifier.weight(1f), color = ScottsTechXColors.Divider)
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp),
            fontSize = 12.5.sp,
            color = ScottsTechXColors.OnCardTertiary,
        )
        Divider(modifier = Modifier.weight(1f), color = ScottsTechXColors.Divider)
    }
}
