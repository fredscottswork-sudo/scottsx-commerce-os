package com.scottsx.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.scottsx.app.ui.theme.ScottsTechXColors

/**
 * Text field with EXPLICIT colors so dark text shows on light panels
 * regardless of the surrounding theme.
 */
@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            enabled = enabled,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ScottsTechXColors.OnLight,
                unfocusedTextColor = ScottsTechXColors.OnLight,
                disabledTextColor = ScottsTechXColors.OnLightSecondary,
                focusedBorderColor = ScottsTechXColors.BluePrimary,
                unfocusedBorderColor = ScottsTechXColors.OnLightSecondary.copy(alpha = 0.3f),
                cursorColor = ScottsTechXColors.BluePrimary,
                focusedContainerColor = ScottsTechXColors.PanelInputLight,
                unfocusedContainerColor = ScottsTechXColors.PanelInputLight,
                errorBorderColor = ScottsTechXColors.ErrorRed,
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelMedium,
                color = ScottsTechXColors.ErrorRed,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}
