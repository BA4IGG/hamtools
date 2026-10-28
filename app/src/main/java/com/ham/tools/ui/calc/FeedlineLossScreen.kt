package com.ham.tools.ui.calc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ham.tools.R
import com.ham.tools.data.reference.CoaxTable
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.NumberField
import com.ham.tools.ui.components.RadioOptions
import com.ham.tools.ui.components.ResultCard
import com.ham.tools.ui.components.ResultItem
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.components.UnitSelector
import com.ham.tools.ui.components.rememberDebounced
import com.ham.tools.util.Format
import com.ham.tools.util.HamMath

private val FREQUENCY_UNITS = listOf("MHz" to 1.0, "kHz" to 0.001)
private val LENGTH_UNITS = listOf("m" to 1.0, "ft" to HamMath.METERS_PER_FOOT)

@Composable
fun FeedlineLossScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var frequencyText by rememberSaveable { mutableStateOf("14.174") }
    var frequencyUnit by rememberSaveable { mutableStateOf(0) }
    var lengthText by rememberSaveable { mutableStateOf("20") }
    var lengthUnit by rememberSaveable { mutableStateOf(0) }
    var powerText by rememberSaveable { mutableStateOf("100") }
    var swrText by rememberSaveable { mutableStateOf("1.5") }
    var cableIndex by rememberSaveable { mutableStateOf(4) }

    val frequencyInput = rememberDebounced(frequencyText)
    val lengthInput = rememberDebounced(lengthText)
    val powerInput = rememberDebounced(powerText)
    val swrInput = rememberDebounced(swrText)

    val frequencyMHz = (frequencyInput.toDoubleOrNull() ?: Double.NaN) * FREQUENCY_UNITS[frequencyUnit].second
    val lengthMeters = (lengthInput.toDoubleOrNull() ?: Double.NaN) * LENGTH_UNITS[lengthUnit].second
    val powerWatt = powerInput.toDoubleOrNull() ?: Double.NaN
    val vswr = swrInput.toDoubleOrNull() ?: Double.NaN

    val cable = CoaxTable.cables[cableIndex]
    val lossPer100m = HamMath.coaxLossDbPer100Meters(cable, frequencyMHz)
    val lossPer100ft = HamMath.coaxLossDbPer100Feet(cable, frequencyMHz)
    val matchedLoss = HamMath.matchedLineLossDb(cable, frequencyMHz, lengthMeters)
    val totalLoss = HamMath.totalLineLossDb(matchedLoss, vswr)
    val powerToAntenna = HamMath.powerAfterLossWatt(powerWatt, totalLoss)
    val powerLost = powerWatt - powerToAntenna
    val efficiency = if (powerWatt > 0.0 && !powerToAntenna.isNaN()) {
        powerToAntenna / powerWatt * 100.0
    } else {
        Double.NaN
    }

    ToolScaffold(
        title = stringResource(R.string.tool_feedline),
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        ToolScrollColumn(innerPadding = padding) {
            SectionLabel(text = stringResource(R.string.group_input))

            UnitSelector(
                units = FREQUENCY_UNITS.map { it.first },
                selectedIndex = frequencyUnit,
                onSelected = { frequencyUnit = it }
            )
            NumberField(
                label = stringResource(R.string.label_frequency),
                value = frequencyText,
                onValueChange = { frequencyText = it },
                unit = FREQUENCY_UNITS[frequencyUnit].first,
                isError = frequencyMHz.isNaN()
            )

            UnitSelector(
                units = LENGTH_UNITS.map { it.first },
                selectedIndex = lengthUnit,
                onSelected = { lengthUnit = it }
            )
            NumberField(
                label = stringResource(R.string.label_cable_length),
                value = lengthText,
                onValueChange = { lengthText = it },
                unit = LENGTH_UNITS[lengthUnit].first,
                isError = lengthMeters.isNaN()
            )

            NumberField(
                label = stringResource(R.string.label_tx_output_power),
                value = powerText,
                onValueChange = { powerText = it },
                unit = "W",
                isError = powerWatt.isNaN() || powerWatt <= 0.0
            )

            NumberField(
                label = stringResource(R.string.label_antenna_swr),
                value = swrText,
                onValueChange = { swrText = it },
                unit = ":1",
                isError = vswr.isNaN() || vswr < 1.0
            )

            SectionLabel(text = stringResource(R.string.label_cable_type))
            RadioOptions(
                options = CoaxTable.cables.map { it.name },
                selectedIndex = cableIndex,
                onSelected = { cableIndex = it },
                supporting = { index ->
                    val candidate = CoaxTable.cables[index]
                    val per100m = HamMath.coaxLossDbPer100Meters(candidate, frequencyMHz)
                    stringResource(
                        R.string.cable_subtitle,
                        candidate.impedanceOhm,
                        Format.number(per100m, 2)
                    )
                }
            )

            SectionLabel(text = stringResource(R.string.group_result))
            ResultCard(
                items = listOf(
                    ResultItem(
                        label = stringResource(R.string.res_loss_per_100m),
                        value = Format.number(lossPer100m, 3),
                        unit = "dB"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_loss_per_100ft),
                        value = Format.number(lossPer100ft, 3),
                        unit = "dB"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_matched_loss),
                        value = Format.number(matchedLoss, 3),
                        unit = "dB"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_total_loss),
                        value = Format.number(totalLoss, 3),
                        unit = "dB",
                        highlight = true
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_power_to_antenna),
                        value = Format.number(powerToAntenna, 2),
                        unit = "W",
                        highlight = true
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_power_lost),
                        value = Format.number(powerLost, 2),
                        unit = "W"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_efficiency),
                        value = Format.number(efficiency, 1),
                        unit = "%"
                    )
                )
            )

            NoteCard(text = stringResource(R.string.note_feedline))
        }
    }
}
