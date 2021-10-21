package org.readium.r2.testapp.reader.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.readium.r2.navigator.ExperimentalPresentation
import org.readium.r2.navigator.presentation.PresentationController
import org.readium.r2.navigator.presentation.PresentationKey
import org.readium.r2.shared.publication.ReadingProgression
import org.readium.r2.shared.publication.presentation.Presentation.Overflow
import org.readium.r2.testapp.utils.compose.ToggleButtonGroup

@OptIn(ExperimentalPresentation::class)
typealias UpdatePresentation = PresentationController.(PresentationController.Settings) -> Unit

@OptIn(ExperimentalPresentation::class)
typealias CommitPresentation = (UpdatePresentation) -> Unit

@Composable
@OptIn(ExperimentalPresentation::class)
fun FixedSettingsView(presentation: PresentationController) {
    val settings by presentation.settings.collectAsState()
    FixedSettingsView(
        settings = settings,
        commit = { presentation.commit(it) }
    )
}

@Composable
@OptIn(ExperimentalPresentation::class)
private fun FixedSettingsView(settings: PresentationController.Settings, commit: CommitPresentation) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "User settings",
            style = MaterialTheme.typography.subtitle1,
        )

        PresetsButton(commit,
            "Reset to defaults" to {
                reset()
            },
            "Scrolled" to { settings ->
                set(settings.readingProgression, ReadingProgression.TTB)
                set(settings.overflow, Overflow.SCROLLED)
            },
            "Paginated" to { settings ->
                set(settings.readingProgression, ReadingProgression.LTR)
                set(settings.overflow, Overflow.PAGINATED)
            },
            "Manga" to { settings ->
                set(settings.readingProgression, ReadingProgression.RTL)
                set(settings.overflow, Overflow.PAGINATED)
            },
        )

        settings.readingProgression?.let { readingProgression ->
            val values = readingProgression.supportedValues ?: return@let

            Section("Reading progression", isActive = readingProgression.isActive) {
                ToggleButtonGroup(
                    options = values,
                    activeOption = readingProgression.effectiveValue,
                    selectedOption = readingProgression.userValue,
                    onSelectOption = { value ->
                        commit {
                            toggle(readingProgression, value)
                        }
                    }) { option ->
                    Icon(
                        imageVector = when (option) {
                            ReadingProgression.LTR -> Icons.Default.KeyboardArrowRight
                            ReadingProgression.RTL -> Icons.Default.KeyboardArrowLeft
                            ReadingProgression.TTB -> Icons.Default.KeyboardArrowDown
                            ReadingProgression.BTT -> Icons.Default.KeyboardArrowUp
                            ReadingProgression.AUTO -> Icons.Default.Clear
                        },
                        contentDescription = readingProgression.labelForValue(context, option)
                    )
                }
            }
        }
        
        settings.overflow?.let { overflow ->
            val values = overflow.supportedValues ?: return@let

            Section("Overflow") {
                ToggleButtonGroup(
                    options = values,
                    activeOption = overflow.effectiveValue,
                    selectedOption = overflow.userValue,
                    onSelectOption = { value ->
                        commit {
                            toggle(overflow, value)
                        }
                    }) { option ->
                    Text(overflow.labelForValue(context, option))
                }
            }
        }
        
        settings.pageSpacing?.let { pageSpacing ->
            Section("Page spacing", isActive = pageSpacing.isActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DecrementButton {
                        commit {
                            decrement(pageSpacing)
                        }
                    }
                    Text(pageSpacing.labelForValue(context, pageSpacing.userValue ?: pageSpacing.effectiveValue ?: 0.5))
                    IncrementButton {
                        commit {
                            increment(pageSpacing)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DecrementButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Remove, contentDescription = "Less")
    }
}

@Composable
fun IncrementButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = "More")
    }
}

@Composable
@OptIn(ExperimentalPresentation::class)
fun PresetsButton(commit: CommitPresentation, vararg presets: Pair<String, UpdatePresentation>) {
    var isExpanded by remember { mutableStateOf(false) }
    fun dismiss() { isExpanded = false }

    Button(
        onClick = { isExpanded = true },
    ) {
        Text("Presets")
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            for ((title, changes) in presets) {
                DropdownMenuItem(
                    onClick = {
                        commit(changes)
                        dismiss()
                    }
                ) {
                    Text(title)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, isActive: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val alpha = if (isActive) 1.0f else ContentAlpha.disabled
    CompositionLocalProvider(LocalContentAlpha provides alpha) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle2,
            )
            content()
        }
    }
}

@Composable
@Preview(showBackground = true)
@OptIn(ExperimentalPresentation::class)
fun PreviewFixedSettingsView() {
    FixedSettingsView(settings = PresentationController.Settings(
        PresentationKey.OVERFLOW to PresentationController.EnumSetting(
            Overflow,
            key = PresentationKey.OVERFLOW,
            userValue = Overflow.PAGINATED,
            effectiveValue = null,
            supportedValues = listOf(Overflow.PAGINATED, Overflow.SCROLLED),
            isActive = true,
            isAvailable = true,
            labelForValue = { _, v -> v.name }
        ).stringSetting,
        PresentationKey.PAGE_SPACING to PresentationController.RangeSetting(
            key = PresentationKey.PAGE_SPACING,
            userValue = 0.3,
            effectiveValue = 0.6,
            stepCount = 20,
            isActive = true,
            isAvailable = true,
            labelForValue = { _, v -> v.toString() }
        ),
        PresentationKey.READING_PROGRESSION to PresentationController.EnumSetting(
            ReadingProgression,
            key = PresentationKey.READING_PROGRESSION,
            userValue = ReadingProgression.TTB,
            effectiveValue = null,
            supportedValues = listOf(
                ReadingProgression.LTR, ReadingProgression.RTL,
                ReadingProgression.TTB, ReadingProgression.BTT
            ),
            isActive = true,
            isAvailable = true,
            labelForValue = { _, v -> v.name }
        ).stringSetting,
    ), commit = {})
}
