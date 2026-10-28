package com.ham.tools.ui.calc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ham.tools.R
import com.ham.tools.data.reference.BandTable
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

private val FREQUENCY_UNITS = listOf("MHz" to 1_000_000.0, "kHz" to 1_000.0, "Hz" to 1.0)
private val LENGTH_UNITS = listOf(
    "m" to 1.0,
    "cm" to 0.01,
    "ft" to HamMath.METERS_PER_FOOT,
    "in" to 0.0254
)

@Composable
fun WavelengthScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableStateOf(0) }
    var frequencyText by rememberSaveable { mutableStateOf("14.174") }
    var frequencyUnit by rememberSaveable { mutableStateOf(0) }
    var wavelengthText by rememberSaveable { mutableStateOf("21.15") }
    var wavelengthUnit by rememberSaveable { mutableStateOf(0) }

    // 输入防抖：连续敲字时只更新输入框，停手后再重算结果
    val frequencyInput = rememberDebounced(frequencyText)
    val wavelengthInput = rememberDebounced(wavelengthText)

    val frequencyHz = if (mode == 0) {
        (frequencyInput.toDoubleOrNull() ?: Double.NaN) * FREQUENCY_UNITS[frequencyUnit].second
    } else {
        val meters = (wavelengthInput.toDoubleOrNull() ?: Double.NaN) * LENGTH_UNITS[wavelengthUnit].second
        HamMath.frequencyHz(meters)
    }
    val wavelengthMeters = HamMath.wavelengthMeters(frequencyHz)
    val frequencyMHz = frequencyHz / 1_000_000.0

    val inputInvalid = frequencyHz.isNaN()
    val band = if (!frequencyMHz.isNaN() && frequencyMHz > 0) {
        BandTable.bandFor(frequencyMHz)
    } else {
        null
    }

    ToolScaffold(title = stringResource(R.string.tool_wavelength), onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(innerPadding = padding) {
            SectionLabel(text = stringResource(R.string.group_input))

            UnitSelector(
                units = listOf(
                    stringResource(R.string.mode_freq_to_wave),
                    stringResource(R.string.mode_wave_to_freq)
                ),
                selectedIndex = mode,
                onSelected = { mode = it }
            )

            if (mode == 0) {
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
                    isError = inputInvalid
                )
            } else {
                UnitSelector(
                    units = LENGTH_UNITS.map { it.first },
                    selectedIndex = wavelengthUnit,
                    onSelected = { wavelengthUnit = it }
                )
                NumberField(
                    label = stringResource(R.string.label_wavelength),
                    value = wavelengthText,
                    onValueChange = { wavelengthText = it },
                    unit = LENGTH_UNITS[wavelengthUnit].first,
                    isError = inputInvalid
                )
            }

            SectionLabel(text = stringResource(R.string.group_result))
            ResultCard(
                items = listOf(
                    ResultItem(
                        label = stringResource(R.string.res_wavelength),
                        value = Format.number(wavelengthMeters, 4),
                        unit = "m",
                        highlight = true
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_wavelength),
                        value = Format.number(HamMath.metersToFeet(wavelengthMeters), 3),
                        unit = "ft"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_frequency),
                        value = Format.number(frequencyMHz, 6),
                        unit = "MHz",
                        highlight = true
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_frequency),
                        value = Format.number(frequencyHz / 1_000.0, 3),
                        unit = "kHz"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_half_wave),
                        value = Format.number(wavelengthMeters / 2.0, 4),
                        unit = "m"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_quarter_wave),
                        value = Format.number(wavelengthMeters / 4.0, 4),
                        unit = "m"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_band),
                        value = band?.name ?: stringResource(R.string.out_of_amateur_band),
                        unit = null,
                        highlight = band != null
                    )
                )
            )

            val note = listOfNotNull(
                stringResource(R.string.note_wavelength),
                band?.note
            ).joinToString(separator = " ")
            NoteCard(text = note)
        }
    }
}
