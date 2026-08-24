package com.scottsx.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * ScottsTechX input field. Rounded corners, comfortable height,
 * light-gray background (per the brief: "very light gray
 * background, no heavy border").
 *
 * Supports an optional trailing icon (used by the password field
 * for the eye toggle).
 */
@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    errorMessage: String? = null,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = ScottsTechXColors.OnLight,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder?.let {
                @Composable { Text(it, color = ScottsTechXColors.OnLightSecondary, fontSize = 14.sp) }
            },
            singleLine = true,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ScottsTechXColors.PanelInputLight),
            // Force dark text on the light-gray panel — otherwise
            // Material's default text color (inherited from the
            // surrounding composition) is white and disappears on
            // the light-gray background.
            textStyle = androidx.compose.ui.text.TextStyle(
                color = ScottsTechXColors.OnLight,
                fontSize = 15.sp,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ScottsTechXColors.OnLight,
                unfocusedTextColor = ScottsTechXColors.OnLight,
                disabledTextColor = ScottsTechXColors.OnLightSecondary,
                focusedContainerColor = ScottsTechXColors.PanelInputLight,
                unfocusedContainerColor = ScottsTechXColors.PanelInputLight,
                cursorColor = ScottsTechXColors.BluePrimary,
                focusedBorderColor = ScottsTechXColors.BluePrimary,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = errorRed,
                focusedLeadingIconColor = ScottsTechXColors.OnLightSecondary,
                unfocusedLeadingIconColor = ScottsTechXColors.OnLightSecondary,
                focusedTrailingIconColor = ScottsTechXColors.OnLightSecondary,
                unfocusedTrailingIconColor = ScottsTechXColors.OnLightSecondary,
            ),

            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = ScottsTechXColors.OnLightSecondary,
                        )
                    }
                }
            } else null,
        )
        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                color = errorRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private val errorRed = Color(0xFFEF4444)
