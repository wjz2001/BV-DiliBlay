package dev.aaa1115910.bv.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.wjzfocus.WjzDialogFocusHost
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusNodeId
import dev.aaa1115910.bv.component.wjzfocus.WjzFocusScopeId
import dev.aaa1115910.bv.ui.theme.BVTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
    sourceScopeId: WjzFocusScopeId? = null,
    dialogScopeId: WjzFocusScopeId? = null,
    containerNodeId: WjzFocusNodeId? = null,
    locked: Boolean = false
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties
    ) {
        TvAlertDialogFocusHost(
            sourceScopeId = sourceScopeId,
            dialogScopeId = dialogScopeId,
            containerNodeId = containerNodeId,
            locked = locked
        ) {
            Surface(
                shape = shape,
                color = containerColor,
                tonalElevation = tonalElevation
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    icon?.invoke()
                    title?.let {
                        ProvideTextStyle(value = MaterialTheme.typography.headlineSmall) {
                            it()
                        }
                    }
                    text?.let {
                        ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                            it()
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = androidx.compose.ui.Alignment.End)
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
private fun TvAlertDialogFocusHost(
    sourceScopeId: WjzFocusScopeId?,
    dialogScopeId: WjzFocusScopeId?,
    containerNodeId: WjzFocusNodeId?,
    locked: Boolean,
    content: @Composable () -> Unit
) {
    when {
        dialogScopeId != null && containerNodeId != null -> {
            WjzDialogFocusHost(
                sourceScopeId = sourceScopeId,
                dialogScopeId = dialogScopeId,
                containerNodeId = containerNodeId,
                locked = locked,
                content = content
            )
        }

        dialogScopeId != null -> {
            WjzDialogFocusHost(
                sourceScopeId = sourceScopeId,
                dialogScopeId = dialogScopeId,
                locked = locked,
                content = content
            )
        }

        containerNodeId != null -> {
            WjzDialogFocusHost(
                sourceScopeId = sourceScopeId,
                containerNodeId = containerNodeId,
                locked = locked,
                content = content
            )
        }

        else -> WjzDialogFocusHost(
            sourceScopeId = sourceScopeId,
            locked = locked,
            content = content
        )
    }
}

@Preview
@Composable
private fun DialogPreview() {
    BVTheme {
        TvAlertDialog(
            title = {
                Text(text = "Dialog Title")
            },
            text = {
                Column {
                    Text(text = "This is a sample dialog text. It can be used to display information or ask for user input.")
                    Text(
                        text = "This is a sample dialog text. It can be used to display information or ask for user input.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            onDismissRequest = {},
            confirmButton = {
                OutlinedButton(onClick = {}) {
                    Text(text = "Confirm")
                }
            },
        )
    }
}
