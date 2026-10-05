package com.anonymus09.carsensors.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow

/*
 * Small pieces the screen's sections share, so each says the same kind of thing
 * the same way.
 */

/** Takes its colour from the theme (outlineVariant), as every colour here must. */
@Composable
internal fun SectionDivider() = HorizontalDivider()

/**
 * Something that needs the user's attention: a setting that keeps the logger
 * from working, or a state that is not what it should be.
 *
 * Drawn in the theme's `error` colour because that is Material's one colour
 * role for attention - the name is Material's, not a claim that anything has
 * failed.
 */
@Composable
internal fun WarningText(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error
)

@Composable
internal fun Muted(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
internal fun LabelledValue(label: String, value: String, small: Boolean = false) {
    Text(text = label, style = MaterialTheme.typography.labelMedium)
    Text(
        text = value,
        style = if (small) {
            MaterialTheme.typography.bodySmall
        } else {
            MaterialTheme.typography.bodyMedium
        }
    )
}

/**
 * A switch with the sentence that says what it does.
 *
 * The description is required rather than optional on purpose: these options
 * interact - motion, power and network all gate each other - and a bare label
 * is not enough to remember which does what.
 */
@Composable
internal fun SettingRow(
    title: String,
    description: AnnotatedString,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    /*
     * Six options that gate each other need more explanation than fits on a
     * phone at once, so each is trimmed to a line and opens on a tap. The text
     * is kept whole rather than shortened to fit, because the parts that would
     * be cut are the interactions that make an option confusing.
     */
    var expanded by rememberSaveable(title) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { expanded = !expanded }
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
