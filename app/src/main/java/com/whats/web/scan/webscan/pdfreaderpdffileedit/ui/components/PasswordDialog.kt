package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R

/**
 * Asks for a PDF password. With [confirm] (setting a new password) it asks twice and will not continue
 * until both match and the password has at least [MIN_LENGTH] characters, because a typo here locks
 * the user out of their own copy. [error] shows under the field, e.g. "Wrong password".
 */
@Composable
fun PasswordDialog(
    title: String,
    message: String,
    confirm: Boolean,
    confirmLabel: String,
    error: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val tooShort = confirm && password.isNotEmpty() && password.length < MIN_LENGTH
    val mismatch = confirm && repeat.isNotEmpty() && repeat != password
    val ready = password.isNotEmpty() && (!confirm || (password.length >= MIN_LENGTH && repeat == password))
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.reader_password_hint)) },
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (visible) R.string.cd_hide_password else R.string.cd_show_password,
                                ),
                            )
                        }
                    },
                    isError = tooShort || error != null,
                    supportingText = {
                        when {
                            tooShort -> Text(stringResource(R.string.password_too_short, MIN_LENGTH))
                            error != null -> Text(error)
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (confirm) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.password_repeat)) },
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = mismatch,
                        supportingText = {
                            if (mismatch) Text(stringResource(R.string.password_mismatch))
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = ready) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MIN_LENGTH = 4
