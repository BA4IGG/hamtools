package com.ham.tools.ui.calc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ham.tools.R
import com.ham.tools.ui.components.ActionButton
import com.ham.tools.ui.components.InfoSurface
import com.ham.tools.ui.components.KeyValueRow
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.ResultCard
import com.ham.tools.ui.components.ResultItem
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.StatusBanner
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.AprsPasscode
import kotlinx.coroutines.delay

/**
 * APRS 验证码：由呼号算出 APRS-IS 登录用的 passcode。
 *
 * 完全离线，不发起任何网络请求，也不保存输入的呼号；
 * 界面同时给出逐对异或过程、登录行示例与「这不是密码」的合规说明。
 */
@Composable
fun AprsPasscodeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("BA4IGG") }
    val result = remember(input) { AprsPasscode.calculate(input) }
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_800)
            copied = false
        }
    }

    ToolScaffold(
        title = stringResource(R.string.tool_aprs_passcode),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        ToolScrollColumn(innerPadding = padding) {
            SectionLabel(text = stringResource(R.string.group_input))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.aprs_input_label)) },
                supportingText = {
                    Text(
                        text = if (result == null) {
                            stringResource(R.string.aprs_invalid_callsign)
                        } else {
                            stringResource(R.string.aprs_input_hint)
                        },
                        color = if (result == null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                isError = result == null,
                singleLine = true,
                shape = AppShapes.small,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                )
            )

            SectionLabel(text = stringResource(R.string.aprs_group_samples))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("BA4IGG", "N0CALL", "9M2PJU", "W1AW").forEach { sample ->
                    FilledTonalButton(
                        onClick = { input = sample },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = sample, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            SectionLabel(text = stringResource(R.string.group_result))
            if (result == null) {
                StatusBanner(text = stringResource(R.string.aprs_invalid_callsign), warning = true)
            }

            result?.let { current ->
                ResultCard(
                    items = listOf(
                        ResultItem(
                            label = stringResource(R.string.aprs_res_passcode),
                            value = current.passcode.toString(),
                            highlight = true
                        ),
                        ResultItem(
                            label = stringResource(R.string.aprs_res_base),
                            value = current.baseCallsign
                        ),
                        ResultItem(
                            label = stringResource(R.string.aprs_res_login_call),
                            value = current.loginCallsign
                        ),
                        ResultItem(
                            label = stringResource(R.string.aprs_res_range),
                            value = "0 ~ 32767"
                        )
                    )
                )

                InfoSurface(title = stringResource(R.string.aprs_res_login_line)) {
                    Text(
                        text = AprsPasscode.loginLine(current),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                ActionButton(
                    text = if (copied) {
                        stringResource(R.string.aprs_action_copied)
                    } else {
                        stringResource(R.string.aprs_action_copy)
                    },
                    onClick = {
                        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        manager?.setPrimaryClip(
                            ClipData.newPlainText("APRS passcode", current.passcode.toString())
                        )
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    tonal = true
                )

                SectionLabel(text = stringResource(R.string.aprs_group_detail))
                InfoSurface(title = stringResource(R.string.aprs_step_title)) {
                    KeyValueRow(
                        label = stringResource(R.string.aprs_step_seed),
                        value = "0x%04X".format(AprsPasscode.SEED)
                    )
                    current.steps.forEach { step ->
                        KeyValueRow(
                            label = stringResource(R.string.aprs_step_pair_label, step.index) +
                                "   ${step.first}${step.second ?: "·"}",
                            value = "0x%04X".format(step.hashAfter)
                        )
                    }
                    KeyValueRow(
                        label = stringResource(R.string.aprs_step_mask),
                        value = current.passcode.toString()
                    )
                    if (current.steps.lastOrNull()?.second == null) {
                        Text(
                            text = stringResource(R.string.aprs_step_odd_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            NoteCard(text = stringResource(R.string.aprs_note))
        }
    }
}
