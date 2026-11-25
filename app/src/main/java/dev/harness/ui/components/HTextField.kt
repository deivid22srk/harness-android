package dev.harness.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/**
 * Branded outlined text field with label, supporting/error text and optional
 * trailing slot. Wraps the Material component so M3 dialogs/sheets look right.
 */
@Composable
fun HTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val colors = HarnessTheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let {
            { Text(it, color = if (isError) colors.toolErrorText else colors.textSecondary) }
        },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = Radii.shapeMd,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.borderStrong,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            disabledContainerColor = colors.surfaceElevated,
            cursorColor = colors.accent,
            focusedLabelColor = colors.accentText,
            unfocusedLabelColor = colors.textSecondary,
        ),
    )
}

/** Password field with a show/hide toggle. */
@Composable
fun HPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visibilityLabel: String,
    hideLabel: String,
) {
    var visible by remember { mutableStateOf(false) }
    HTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            androidx.compose.material3.TextButton(onClick = { visible = !visible }) {
                Text(
                    text = if (visible) hideLabel else visibilityLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = HarnessTheme.accentText,
                )
            }
        },
    )
}

/** Section header used across settings and onboarding. */
@Composable
fun HSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = HarnessTheme.textSecondary,
        modifier = modifier
            .padding(top = Space.lg, bottom = Space.sm)
            .semantics { },
    )
}
