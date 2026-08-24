package com.scottsx.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsRole
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.annotation.StringAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import com.scottsx.app.ui.components.FuturisticBackdrop
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Role selection — the first screen of the auth flow, recreated from the
 * reference design:
 *
 *   - near-black futuristic backdrop with a low-opacity technical glow,
 *   - a "WELCOME" eyebrow, the large "How will you use ScottsTechX?"
 *     heading and a one-line hint,
 *   - two glassmorphic role cards (Buyer / Seller) with circular letter
 *     avatars; each card carries its own Log in / Sign up actions so the
 *     role flows into both destinations,
 *   - the legal fine print with tappable Terms/Privacy links at the bottom.
 */
@Composable
fun WelcomeScreen(
    onLogin: (role: String) -> Unit,
    onSignUp: (role: String) -> Unit,
    onOpenLegal: (slug: String) -> Unit,
) {
    // Buyer or seller, chosen here and carried into both login and
    // registration. Defaults to buyer because that is the overwhelming
    // majority of new accounts; a seller taps once to switch. The backend
    // still constrains the value to buyer/seller, so this can never be used
    // to self-register as an admin.
    var role by remember { mutableStateOf("buyer") }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF04060C))) {
        FuturisticBackdrop(modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Full-bleed backdrop, but content must not sit under the
                // status bar or the gesture pill.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Text(
                "WELCOME",
                color = ScottsTechXColors.BluePrimaryLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.2.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "How will you use\nScottsTechX?",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 41.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Pick the role that fits you best — you can always switch later.",
                color = ScottsTechXColors.DarkOnSecondary,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(22.dp))

            // The cards take whatever vertical space is left and scroll on
            // small screens instead of being clipped.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                RoleCard(
                    letter = "B",
                    title = "I am a Buyer",
                    description = "Discover products and connect with sellers across Uganda.",
                    selected = role == "buyer",
                    onSelect = { role = "buyer" },
                    onLogin = { onLogin("buyer") },
                    onSignUp = { onSignUp("buyer") },
                )
                Spacer(Modifier.height(14.dp))
                RoleCard(
                    letter = "S",
                    title = "I am a Seller",
                    description = "List your products, reach more customers, and grow your business.",
                    selected = role == "seller",
                    onSelect = { role = "seller" },
                    onLogin = { onLogin("seller") },
                    onSignUp = { onSignUp("seller") },
                )
            }

            Spacer(Modifier.height(18.dp))
            LegalLinks(
                onTerms = { onOpenLegal("terms") },
                onPrivacy = { onOpenLegal("privacy") },
            )
        }
    }
}

/**
 * One of the two role cards. Selected state is expressed three ways at once
 * — fill, border/glow and a subtle scale — because on a dark surface a
 * colour-only cue is easy to miss, and colour alone is not an accessible
 * signal.
 */
@Composable
private fun RoleCard(
    letter: String,
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(30.dp)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.985f,
        animationSpec = tween(durationMillis = 220),
        label = "roleScale",
    )
    val fill by animateColorAsState(
        targetValue = if (selected) Color(0x331E6FFF) else Color(0x0DFFFFFF),
        animationSpec = tween(durationMillis = 220),
        label = "roleFill",
    )
    val edge by animateColorAsState(
        targetValue = if (selected) ScottsTechXColors.BluePrimaryLight.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f),
        animationSpec = tween(durationMillis = 220),
        label = "roleEdge",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = 18.dp,
                shape = shape,
                spotColor = edge.copy(alpha = if (selected) 0.35f else 0f),
                ambientColor = Color.Transparent,
            )
            .clip(shape)
            .background(fill)
            .border(width = 1.5.dp, color = edge, shape = shape)
            .selectable(
                selected = selected,
                role = SemanticsRole.RadioButton,
                onClick = onSelect,
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE9EDF5)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter,
                    color = ScottsTechXColors.BlueDeep,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    description,
                    color = ScottsTechXColors.DarkOnSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onLogin,
                contentColor = Color.White,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text("Log in", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSignUp,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = ScottsTechXColors.BlueDeep,
                ),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 10.dp),
            ) {
                Text("Sign up", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * "By continuing you agree to ScottsTechX's Terms of Service and Privacy
 * Policy." — a single wrapping Text with two tappable spans, so the line
 * reflows on narrow phones instead of overflowing a Row.
 */
@Composable
private fun LegalLinks(onTerms: () -> Unit, onPrivacy: () -> Unit) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val legal: AnnotatedString = remember {
        buildAnnotatedString {
            append("By continuing you agree to ScottsTechX's ")
            val t0 = length
            withStyle(SpanStyle(color = ScottsTechXColors.BluePrimaryLight, textDecoration = TextDecoration.None)) {
                append("Terms of Service")
            }
            val t1 = length
            append(" and ")
            val p0 = length
            withStyle(SpanStyle(color = ScottsTechXColors.BluePrimaryLight, textDecoration = TextDecoration.None)) {
                append("Privacy Policy")
            }
            val p1 = length
            append(".")
            addStringAnnotation(StringAnnotation("terms"), "", t0, t1)
            addStringAnnotation(StringAnnotation("privacy"), "", p0, p1)
        }
    }
    Text(
        text = legal,
        onTextLayout = { layoutResult = it },
        color = Color(0xFF6F7A90),
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .pointerInput(legal) {
                detectTapGestures { tap ->
                    val res = layoutResult ?: return@detectTapGestures
                    val textOffset = res.getOffsetForPosition(tap)
                    legal.getStringAnnotations(StringAnnotation, 0, legal.length)
                        .firstOrNull { textOffset in it.start until it.end }
                        ?.let { ann ->
                            if (ann.tag == "terms") onTerms() else onPrivacy()
                        }
                }
            }
            .semantics { role = SemanticsRole.Button },
    )
}

// Kept for parity with the master doc's route list.
object WelcomeRouteHolder {
    const val ROUTE = com.scottsx.app.navigation.Routes.WELCOME
}
