package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.util.GameStrings

// ---------------------------------------------------------------------------
// Arrow/spell pickers for the active combat style, shown inline in the Combat
// Gear tab (CombatScreen.kt) right below its style-only weapon row — never in
// a separate sheet, so switching style and editing its loadout happen in place.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArrowLoadoutPicker(
    selectedArrowKey: String?,
    inventory: Map<String, Int>,
    context: android.content.Context,
    onArrowSelected: (String?) -> Unit,
) {
    val availableArrows = ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
    val arrowOptions = listOf(null) + availableArrows
    var expanded by remember { mutableStateOf(false) }
    Text(
        text     = stringResource(R.string.combat_label_arrow),
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
        modifier         = Modifier.padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value         = if (selectedArrowKey == null) stringResource(R.string.combat_arrow_auto)
                             else GameStrings.itemName(context, selectedArrowKey),
            onValueChange = {},
            readOnly      = true,
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            singleLine    = true,
            modifier      = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            arrowOptions.forEach { key ->
                DropdownMenuItem(
                    text    = { Text(if (key == null) stringResource(R.string.combat_arrow_auto) else GameStrings.itemName(context, key)) },
                    onClick = { onArrowSelected(key); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpellLoadoutPicker(
    selectedSpell: SpellData?,
    availableSpells: List<SpellData>,
    inventory: Map<String, Int>,
    equippedWeapon: EquipmentData?,
    context: android.content.Context,
    onSpellSelected: (SpellData?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text     = stringResource(R.string.label_spell),
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    if (availableSpells.isEmpty()) {
        Text(
            text     = stringResource(R.string.combat_no_spells),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        return
    }
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
        modifier         = Modifier.padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value         = selectedSpell?.let { GameStrings.spellName(context, it.name) } ?: "",
            onValueChange = {},
            readOnly      = true,
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            singleLine    = true,
            modifier      = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            availableSpells.forEach { spell ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text  = GameStrings.spellName(context, spell.name),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text  = "${spell.runeCost}× ${GameStrings.itemName(context, spell.runeType)}  •  ${stringResource(R.string.combat_max_hit)} ${spell.maxHit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val infinite = equippedWeapon?.infiniteRunes == "all" || equippedWeapon?.infiniteRunes == spell.runeType
                            if (infinite) {
                                Text(
                                    text  = stringResource(R.string.combat_infinite_runes, GameStrings.itemName(context, spell.runeType)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                val held = inventory[spell.runeType] ?: 0
                                Text(
                                    text  = stringResource(R.string.combat_you_have_runes, held, GameStrings.itemName(context, spell.runeType)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (held >= spell.runeCost) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    onClick = { onSpellSelected(spell); expanded = false },
                )
            }
        }
    }
}
