package com.ham.tools.ui.calc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ham.tools.R
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.NumberField
import com.ham.tools.ui.components.ResultCard
import com.ham.tools.ui.components.ResultItem
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.components.UnitSelector
import com.ham.tools.ui.components.rememberDebounced
import com.ham.tools.util.Format
import com.ham.tools.util.HamMath
import kotlin.math.pow

@Composable
fun VswrScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableStateOf(0) }
    var vswrText by rememberSaveable { mutableStateOf("1.5") }
    var returnLossText by rememberSaveable { mutableStateOf("14.0") }
    var forwardPowerText by rememberSaveable { mutableStateOf("100") }

    val vswrInput = rememberDebounced(vswrText)
    val returnLossInput = rememberDebounced(returnLossText)
    val forwardPowerInput = rememberDebounced(forwardPowerText)

    val forwardPowerWatt = forwardPowerInput.toDoubleOrNull() ?: Double.NaN
    val gamma = if (mode == 0) {
        HamMath.reflectionCoefficientFromVswr(vswrInput.toDoubleOrNull() ?: Double.NaN)
    } else {
        val returnLossDb = returnLossInput.toDoubleOrNull() ?: Double.NaN
        if (returnLossDb.isNaN()) Double.NaN else 10.0.pow(-returnLossDb / 20.0)
    }

    val vswr = HamMath.vswrFromReflectionCoefficient(gamma)
    val returnLoss = HamMath.returnLossDb(gamma)
    val mismatchLoss = HamMath.mismatchLossDb(gamma)
    val reflectedRatio = HamMath.reflectedPowerFraction(gamma)
    val reflectedWatt = forwardPowerWatt * reflectedRatio
    val deliveredWatt = forwardPowerWatt - reflectedWatt

    ToolScaffold(title = stringResource(R.string.tool_vswr), onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(innerPadding = padding) {
            SectionLabel(text = stringResource(R.string.group_input))

            UnitSelector(
                units = listOf(
                    stringResource(R.string.mode_from_vswr),
                    stringResource(R.string.mode_from_return_loss)
                ),
                selectedIndex = mode,
                onSelected = { mode = it }
            )

            if (mode == 0) {
                NumberField(
                    label = stringResource(R.string.label_vswr),
                    value = vswrText,
                    onValueChange = { vswrText = it },
                    unit = ":1",
                    isError = gamma.isNaN()
                )
            } else {
                NumberField(
                    label = stringResource(R.string.label_return_loss),
                    value = returnLossText,
                    onValueChange = { returnLossText = it },
                    unit = "dB",
                    isError = gamma.isNaN()
                )
            }

            NumberField(
                label = stringResource(R.string.label_forward_power),
                value = forwardPowerText,
                onValueChange = { forwardPowerText = it },
                unit = "W",
                isError = forwardPowerWatt.isNaN() || forwardPowerWatt <= 0.0
            )

            SectionLabel(text = stringResource(R.string.group_result))
            ResultCard(
                items = listOf(
                    ResultItem(
                        label = stringResource(R.string.res_vswr),
                        value = Format.number(vswr, 3),
                        unit = ":1",
                        highlight = true
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_reflection_coefficient),
                        value = Format.number(gamma, 4)
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_return_loss),
                        value = Format.number(returnLoss, 2),
                        unit = "dB",
                        highlight = true
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_mismatch_loss),
                        value = Format.number(mismatchLoss, 3),
                        unit = "dB"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_reflected_ratio),
                        value = Format.number(reflectedRatio * 100.0, 2),
                        unit = "%"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_reflected_power),
                        value = Format.number(reflectedWatt, 2),
                        unit = "W"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_delivered_power),
                        value = Format.number(deliveredWatt, 2),
                        unit = "W"
                    )
                )
            )

            NoteCard(text = stringResource(R.string.note_vswr))
        }
    }
}
