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

private const val END_EFFECT_FACTOR = 0.95
private const val INVERTED_V_SHORTENING = 0.96

private val FREQUENCY_UNITS = listOf("MHz" to 1_000_000.0, "kHz" to 1_000.0)

private enum class AntennaType(
    val labelRes: Int,
    val wavelengthFraction: Double,
    val velocityFactor: Double
) {
    DIPOLE_TOTAL(R.string.antenna_dipole_total, 0.5, END_EFFECT_FACTOR),
    DIPOLE_LEG(R.string.antenna_dipole_leg, 0.25, END_EFFECT_FACTOR),
    INVERTED_V_LEG(
        R.string.antenna_inverted_v_leg,
        0.25,
        END_EFFECT_FACTOR * INVERTED_V_SHORTENING
    ),
    VERTICAL_QUARTER(R.string.antenna_vertical_quarter, 0.25, END_EFFECT_FACTOR)
}

@Composable
fun AntennaLengthScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var frequencyText by rememberSaveable { mutableStateOf("7.050") }
    var frequencyUnit by rememberSaveable { mutableStateOf(0) }
    var typeIndex by rememberSaveable { mutableStateOf(0) }

    val frequencyInput = rememberDebounced(frequencyText)
    val frequencyHz = (frequencyInput.toDoubleOrNull() ?: Double.NaN) * FREQUENCY_UNITS[frequencyUnit].second
    val type = AntennaType.entries[typeIndex]
    val freeSpaceWave = HamMath.wavelengthMeters(frequencyHz)
    val elementMeters = HamMath.antennaLengthMeters(
        frequencyHz = frequencyHz,
        wavelengthFraction = type.wavelengthFraction,
        velocityFactor = type.velocityFactor
    )

    ToolScaffold(
        title = stringResource(R.string.tool_antenna_length),
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
                isError = frequencyHz.isNaN()
            )

            SectionLabel(text = stringResource(R.string.label_antenna_type))
            RadioOptions(
                options = AntennaType.entries.map { stringResource(it.labelRes) },
                selectedIndex = typeIndex,
                onSelected = { typeIndex = it }
            )

            SectionLabel(text = stringResource(R.string.group_result))
            ResultCard(
                items = listOf(
                    ResultItem(
                        label = stringResource(R.string.res_element_length),
                        value = Format.number(elementMeters, 3),
                        unit = "m",
                        highlight = true
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_element_length),
                        value = Format.number(HamMath.metersToFeet(elementMeters), 2),
                        unit = "ft"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_free_space_wave),
                        value = Format.number(freeSpaceWave, 3),
                        unit = "m"
                    ),
                    ResultItem(
                        label = stringResource(R.string.res_free_space_half_wave),
                        value = Format.number(freeSpaceWave / 2.0, 3),
                        unit = "m"
                    )
                )
            )

            NoteCard(text = stringResource(R.string.note_antenna))
        }
    }
}
