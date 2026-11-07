package com.ham.tools.ui.network

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ham.tools.R
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.ResultCard
import com.ham.tools.ui.components.ResultItem
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.HamMath

@Composable
fun AprsPasscodeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var callsignInput by rememberSaveable { mutableStateOf("") }

    val passcode = if (callsignInput.isNotBlank()) HamMath.aprsPasscode(callsignInput) else null
    val loginCallsign = callsignInput.substringBefore('-').trim().uppercase().take(8)

    ToolScaffold(title = stringResource(R.string.tool_aprs_passcode), onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(innerPadding = padding) {
            SectionLabel(text = stringResource(R.string.group_input))

            OutlinedTextField(
                value = callsignInput,
                onValueChange = { callsignInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.aprs_passcode_input_label)) },
                placeholder = { Text(text = stringResource(R.string.aprs_passcode_input_hint)) },
                singleLine = true,
                shape = AppShapes.small
            )

            SectionLabel(text = stringResource(R.string.group_result))
            ResultCard(
                items = listOf(
                    ResultItem(
                        label = stringResource(R.string.aprs_passcode_result_label),
                        value = passcode?.toString() ?: "—",
                        highlight = passcode != null
                    ),
                    ResultItem(
                        label = stringResource(R.string.aprs_passcode_result_hex),
                        value = passcode?.let { "0x%04X".format(it) } ?: "—"
                    ),
                    ResultItem(
                        label = stringResource(R.string.aprs_passcode_result_login),
                        value = if (passcode != null) {
                            "user $loginCallsign pass $passcode vers hamtools 1.0"
                        } else {
                            "—"
                        }
                    )
                )
            )

            NoteCard(text = stringResource(R.string.note_aprs_passcode))
        }
    }
}
