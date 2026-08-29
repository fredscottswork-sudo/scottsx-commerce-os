package com.scottsx.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.R
import com.scottsx.app.data.domain.Role
import com.scottsx.app.ui.theme.ScottsTechXColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * THE company-branded auth backdrop — mixed DARK + BLUE, matching the
 * rest of the app (home, chat, dashboards are all dark navy + brand
 * blue). Every sign-in surface rides it:
 * Login, Sign-Up, Forgot Password, Reset Password, Verification.
 *
 * Design language:
 *  - deep navy → brand blue gradient with three drifting glow orbs and
 *    a sweeping light beam so the background breathes
 *  - the transparent brand lockup up top, PLUS the company logo picture
 *    + wordmark INSIDE the card — identity from two angles
 *  - a floating DARK SLATE card (rounded 32dp) that slides up + fades
 *    in; fields are dark with blue focus rings
 *  - role pill ("Buying"/"Selling") so the account lane is always clear
 *
 * Every colour is a fixed value — no theme-token surprises.
 */
@Composable
fun BrandedAuthScaffold(
    role: Role,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val slideIn = remember { Animatable(64f) }
    val fadeIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { slideIn.animateTo(0f, tween(560, easing = EaseOutCubic)) }
        launch { fadeIn.animateTo(1f, tween(560, easing = EaseOutCubic)) }
    }

    val drift by rememberInfiniteTransition(label = "orbs").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(7000, easing = EaseOutCubic),
            RepeatMode.Reverse,
        ),
        label = "orb-drift",
    )
    val beam by rememberInfiniteTransition(label = "beam").animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            tween(9500, easing = EaseOutCubic),
            RepeatMode.Reverse,
        ),
        label = "beam-sweep",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF071021),
                    0.4f to Color(0xFF0A1830),
                    1f to ScottsTechXColors.BluePrimaryDark,
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Glow orb — top right
        Box(
            modifier = Modifier
                .size(270.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-70 + drift * 26).dp)
                .clip(CircleShape)
                .background(Color(0xFF60A5FA).copy(alpha = 0.20f)),
        )
        // Glow orb — mid left
        Box(
            modifier = Modifier
                .size(190.dp)
                .align(Alignment.CenterStart)
                .offset(x = (-80 + drift * 18).dp, y = (-40).dp)
                .clip(CircleShape)
                .background(Color(0xFF38BDF8).copy(alpha = 0.14f)),
        )
        // Glow orb — bottom left
        Box(
            modifier = Modifier
                .size(230.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-90).dp, y = (70 - drift * 22).dp)
                .clip(CircleShape)
                .background(Color(0xFF93C5FD).copy(alpha = 0.13f)),
        )
        // Light beam sweeping across the header area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .offset(y = 30.dp)
                .graphicsLayer { translationX = size.width * (beam - 0.5f) }
                .alpha(0.10f)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to Color(0xFFBFDBFE),
                        1f to Color.Transparent,
                    ),
                ),
        )

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        // Role badge pill — top right
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (role == Role.SELLER) "Selling" else "Buying",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
        }

        // Brand lockup — upper third
        Image(
            painter = painterResource(R.drawable.brand_lockup),
            contentDescription = "ScottsTechX",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 44.dp)
                .fillMaxWidth()
                .padding(horizontal = 76.dp)
                .alpha(fadeIn.value),
        )

        // DARK content card — mixed dark + blue per the brand brief.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = slideIn.value.dp.toPx()
                    alpha = fadeIn.value
                }
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFF101E3A),
                        1f to Color(0xFF0A1428),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF22335C).copy(alpha = 0.7f),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                )
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            // Company logo INSIDE the form card — picture + wordmark.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(ScottsTechXColors.BrandGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.brand_mark),
                        contentDescription = "ScottsTechX logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = "ScottsTechX",
                    color = Color(0xFFEFF4FF),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.3.sp,
                )
            }
            content()
        }
    }
}

/** Title + subtitle inside the dark card. */
@Composable
fun BrandedAuthHeader(title: String, sub: String) {
    Column {
        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF3F7FF),
            letterSpacing = (-0.5).sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = sub,
            fontSize = 14.sp,
            color = Color(0xFFA9B7D6),
            lineHeight = 20.sp,
        )
    }
}

/** Status line with a tiny spinner (silent sign-in, "Opening Google…"). */
@Composable
fun AuthStatusSlot(message: String?) {
    if (message == null) return
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = ScottsTechXColors.BluePrimaryLight,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            color = Color(0xFFA9B7D6),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Staggered entrance wrapper — fields cascade in one after another. */
@Composable
private fun StaggerIn(index: Int, content: @Composable () -> Unit) {
    val fade = remember { Animatable(0f) }
    val rise = remember { Animatable(18f) }
    LaunchedEffect(Unit) {
        delay(90L * (index + 1) + 140)
        launch { fade.animateTo(1f, tween(420, easing = EaseOutCubic)) }
        launch { rise.animateTo(0f, tween(420, easing = EaseOutCubic)) }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = fade.value
            translationY = rise.value.dp.toPx()
        },
    ) { content() }
}

/** The company-styled DARK field — deep navy fill, blue focus ring. */
@Composable
fun StyledAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    isPassword: Boolean = false,
    index: Int = 0,
) {
    var hidden by remember { mutableStateOf(isPassword) }
    StaggerIn(index = index) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 13.sp) },
            placeholder = if (placeholder.isBlank()) null else ({
                Text(placeholder, color = Color(0xFF5F7099), fontSize = 14.sp)
            }),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (isPassword && hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction?.invoke() },
                onDone = { onImeAction?.invoke() },
                onGo = { onImeAction?.invoke() },
            ),
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color(0xFF7C8DB0),
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            trailingIcon = if (isPassword) ({
                Icon(
                    imageVector = if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (hidden) "Show password" else "Hide password",
                    tint = Color(0xFF7C8DB0),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { hidden = !hidden }
                        .padding(8.dp),
                )
            }) else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ScottsTechXColors.BluePrimaryLight,
                unfocusedBorderColor = Color(0xFF22335C),
                focusedContainerColor = Color(0xFF14254A),
                unfocusedContainerColor = Color(0xFF111F3B),
                cursorColor = ScottsTechXColors.BluePrimaryLight,
                focusedTextColor = Color(0xFFF3F7FF),
                unfocusedTextColor = Color(0xFFF3F7FF),
                focusedLabelColor = ScottsTechXColors.BluePrimaryLight,
                unfocusedLabelColor = Color(0xFF8FA3CC),
            ),
        )
    }
}

/** Password shorthand over [StyledAuthField]. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Password",
    leadingIcon: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    index: Int = 0,
) = StyledAuthField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    leadingIcon = leadingIcon,
    keyboardType = KeyboardType.Password,
    imeAction = imeAction,
    onImeAction = onImeAction,
    isPassword = true,
    index = index,
)

/** "───── or continue with ─────" divider (dark skin). */
@Composable
fun AuthDivider(text: String, index: Int = 0) {
    StaggerIn(index = index) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color(0xFF22335C)),
            )
            Text(
                text = text,
                color = Color(0xFF7C8DB0),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color(0xFF22335C)),
            )
        }
    }
}

/** The big brand-blue primary CTA. */
@Composable
fun PrimaryCtaButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    index: Int = 0,
) {
    StaggerIn(index = index) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(
                    if (enabled) Brush.horizontalGradient(
                        listOf(ScottsTechXColors.BluePrimaryDark, ScottsTechXColors.BluePrimary),
                    ) else Brush.horizontalGradient(
                        listOf(Color(0xFF3B4A6B), Color(0xFF3B4A6B)),
                    ),
                )
                .clickable(enabled = enabled && !loading, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = label,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}

/** THE Google button — white, authentic 4-colour G (legible on navy). */
@Composable
fun GoogleAuthButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
    index: Int = 0,
) {
    StaggerIn(index = index) {
        val shape = RoundedCornerShape(27.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, Color(0xFFE1E6EF), shape)
                .clickable(enabled = !loading, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = ScottsTechXColors.BluePrimary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    GoogleG(modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF101828),
                )
            }
        }
    }
}

/** Error text slot (soft red — readable on navy). */
@Composable
fun AuthErrorSlot(message: String?) {
    if (message == null) return
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = message,
        color = Color(0xFFFCA5A5),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Footer "question — link" row (dark skin). */
@Composable
fun BrandedFooterLink(text: String, linkText: String, onClick: () -> Unit) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$text ",
            color = Color(0xFF8FA3CC),
            fontSize = 13.sp,
        )
        Text(
            text = linkText,
            color = ScottsTechXColors.AccentLink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}
