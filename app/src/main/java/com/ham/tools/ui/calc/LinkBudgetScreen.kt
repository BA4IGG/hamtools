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
import java.util.Locale

private val FREQUENCY_UNITS = listOf("MHz" to 1.0, "kHz" to 0.001)
private val DISTANCE_UNITS = listOf("km" to 1.0, "m" to 0.001, "mi" to 1.609344)
private val POWER_UNITS = listOf("W" to 1.0, "mW" to 0.001, "dBm" to 0.0)

@Composable
fun LinkBudgetScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var frequencyText by rememberSaveable { mutableStateOf("14.174") }
    var frequencyUnit by rememberSaveable { mutableStateOf(0) }
    var distanceText by rememberSaveable { mutableStateOf("3000") }
    var distanceUnit by rememberSaveable { mutableStateOf(0) }
    var powerText by rememberSaveable { mutableStateOf("100") }
    var powerUnit by rememberSaveable { mutableStateOf(0) }
    var txGainText by rememberSaveable { mutableStateOf("0") }
    var rxGainText by rememberSaveable { mutableStateOf("0") }
    var sensitivityText by rememberSaveable { mutableStateOf("-121") }
    var extraLossText by rememberSaveable { mutableStateOf("1.5") }

    val frequencyInput = rememberDebounced(frequencyText)
    val distanceInput = rememberDebounced(distanceText)
    val powerInput = rememberDebounced(powerText)
    val txGainInput = rememberDebounced(txGainText)
    val rxGainInput = rememberDebounced(rxGainText)
    val sensitivityInput = rememberDebounced(sensitivityText)
    val extraLossInput = rememberDebounced(extraLossText)

    val frequencyMHz = (frequencyInput.toDoubleOrNull() ?: Double.NaN) * FREQUENCY_UNITS[frequencyUnit].second
    val distanceKm = (distanceInput.toDoubleOrNull() ?: Double.NaN) * DISTANCE_UNITS[distanceUnit].second
    val txPowerDbm = when (powerUnit) {
        0 -> HamMath.wattToDbm(powerInput.toDoubleOrNull() ?: Double.NaN)
        1 -> HamMath.wattToDbm((powerInput.toDoubleOrNull() ?: Double.NaN) * 0.001)
        else -> powerInput.toDoubleOrNull() ?: Double.NaN
    }
    val txGain = txGainInput.toDoubleOrNull() ?: 0.0
    val rxGain = rxGainInput.toDoubleOrNull() ?: 0.0
    val sensitivity = sensitivityInput.toDoubleOrNull() ?: Double.NaN
    val extraLoss = extraLossInput.toDoubleOrNull() ?: 0.0

    val fspl = HamMath.freeSpacePathLossDb(distanceKm, frequencyMHz)
    val rxPowerDbm = HamMath.receivedPowerDbm(txPowerDbm, txGain, rxGain, fspl, extraLoss)
    val rxVoltage = HamMath.dbmToMicrovolt(rxPowerDbm)
    val sMeter = HamMath.sMeterUnits(rxPowerDbm)
    val fadeMargin = rxPowerDbm - sensitivity

    ToolScaffold(
        title = stringResource(R.string.tool_link_budget),
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
                units = DISTANCE_UNITS.map { it.first },
                selectedIndex = distanceUnit,
                onSelected = { distanceUnit = it }
            )
            NumberField(
                label = stringResource(R.string.label_distance),
                value = distanceText,
                onValueChange = { distanceText = it },
                unit = DISTANCE_UNITS[distanceUnit].first,
                isError = distanceKm.isNaN()
            )

            UnitSelector(
                units = POWER_UNITS.map { it.first },
                selectedIndex = powerUnit,
                onSelected = { powerUnit = it }
            )
            NumberField(
                label = stringResource(R.string.label_tx_power),
                value = powerText,
                onValueChange = { powerText = it },
                unit = POWER_UNITS[powerUnit].first,
                isError = txPowerDbm.isNaN()
            )

            NumberField(
                label = stringResource(R.string.label_tx_gain),
                value = txGainText,
                onValueChange = { txGainText = it },
                unit = "dBi"
            )

            NumberField(
                label = stringResource(R.string.label_rx_gain),
                value = rxGainText,
                onValueChange = { rxGainText = it },
                unit = "dBi"
            )

            NumberField(
                label = stringResource(R.string.label_rx_sensitivity),
                value = sensitivityText,
                onValueChange = { sensitivityText = it },
                unit = "dBm"
            )

            NumberField(
                label = stringResource(R.string.label_extra_loss),
                value = extraLossText,
                onValueChange = { extraLossText = it },
                unit = "dB"
            )

            SectionLabel(text = stringResource(R.string.group_result))
            ResultCard(
                items = listOf(
                    ResultItem(
                        label = stringResource(R.string.res_fspl),
                        value = Format.number(fspl, 2),
                        unit = "dB"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_rx_power),
                        value = Format.number(rxPowerDbm, 2),
                        unit = "dBm",
                        highlight = true
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_rx_power_watt),
                        value = Format.auto(HamMath.dbmToWatt(rxPowerDbm) * 1_000_000_000_000.0),
                        unit = "pW"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_rx_voltage),
                        value = Format.number(rxVoltage, 3),
                        unit = "µV"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_s_meter),
                        value = formatSMeter(sMeter),
                        highlight = true
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_fade_margin),
                        value = Format.number(fadeMargin, 1),
                        unit = "dB"
                    )
                )
            )

            NoteCard(text = stringResource(R.string.note_link_budget))
        }
    }
}

private fun formatSMeter(units: Double): String = when {
    units.isNaN() -> Format.PLACEHOLDER
    units <= 0.0 -> "< S1"
    units < 9.0 -> String.format(Locale.US, "S%.1f", units)
    else -> String.format(Locale.US, "> S9 +%.0f dB", (units - 9.0) * HamMath.DB_PER_S_UNIT)
}
