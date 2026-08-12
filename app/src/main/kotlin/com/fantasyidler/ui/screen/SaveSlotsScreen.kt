package com.fantasyidler.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fantasyidler.R
import com.fantasyidler.repository.SlotInfo
import com.fantasyidler.ui.viewmodel.SaveSlotsViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.totalLevelFrom
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSlotsScreen(
    onBack: () -> Unit,
    onSwitched: () -> Unit,
    viewModel: SaveSlotsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var switchSlot by remember { mutableStateOf<SlotInfo?>(null) }
    var createSlot by remember { mutableStateOf<SlotInfo?>(null) }
    var deleteSlot by remember { mutableStateOf<SlotInfo?>(null) }

    LaunchedEffect(state.switchCompleted) {
        if (state.switchCompleted) {
            if (state.ironmanDemoted) {
                AppBannerCenter.enqueue(context.getString(R.string.settings_imported_demoted))
            }
            viewModel.switchCompletedConsumed()
            onSwitched()
        }
    }
    LaunchedEffect(state.switchFailed) {
        if (state.switchFailed) {
            viewModel.switchFailedConsumed()
            AppBannerCenter.enqueue(context.getString(R.string.save_slot_switch_failed))
        }
    }

    switchSlot?.let { slot ->
        val name = slot.flags?.characterName?.ifBlank { null }
            ?: stringResource(R.string.home_adventurer)
        AlertDialog(
            onDismissRequest = { switchSlot = null },
            title = { Text(stringResource(R.string.save_slot_switch_title)) },
            text  = { Text(stringResource(R.string.save_slot_switch_body, name)) },
            confirmButton = {
                Button(onClick = { switchSlot = null; viewModel.switchTo(slot.slot) }) {
                    Text(stringResource(R.string.save_slot_switch_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { switchSlot = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    createSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { createSlot = null },
            title = { Text(stringResource(R.string.save_slot_create_title)) },
            text  = { Text(stringResource(R.string.save_slot_create_body, slot.slot)) },
            confirmButton = {
                Button(onClick = { createSlot = null; viewModel.switchTo(slot.slot) }) {
                    Text(stringResource(R.string.save_slot_create_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { createSlot = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    deleteSlot?.let { slot ->
        val name = slot.flags?.characterName?.ifBlank { null }
            ?: stringResource(R.string.home_adventurer)
        AlertDialog(
            onDismissRequest = { deleteSlot = null },
            title = { Text(stringResource(R.string.save_slot_delete_title)) },
            text  = { Text(stringResource(R.string.save_slot_delete_body, name)) },
            confirmButton = {
                Button(
                    onClick = { deleteSlot = null; viewModel.deleteSlot(slot.slot) },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.save_slot_delete_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSlot = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.save_slots_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text  = stringResource(R.string.save_slots_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.slots.forEach { slot ->
                        SaveSlotCard(
                            slot     = slot,
                            enabled  = !state.isSwitching,
                            onClick  = {
                                when {
                                    slot.isActive -> {}
                                    slot.exists   -> switchSlot = slot
                                    else          -> createSlot = slot
                                }
                            },
                            onDelete = { deleteSlot = slot },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (state.isSwitching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun SaveSlotCard(
    slot: SlotInfo,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flags = slot.flags
    Card(
        modifier = modifier.clickable(enabled = enabled && !slot.isActive, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (slot.isActive) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text  = stringResource(R.string.save_slot_label, slot.slot),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!slot.exists || flags == null) {
                Icon(
                    imageVector        = Icons.Filled.Add,
                    contentDescription = null,
                    modifier           = Modifier.size(48.dp).padding(vertical = 8.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text      = stringResource(R.string.save_slot_new),
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }

            CharacterSprite(
                race       = flags.characterRace.ifBlank { "human" },
                skinTone   = flags.characterSkinTone,
                hairStyle  = flags.characterHairStyle,
                hairColor  = flags.characterHairColor,
                eyeStyle   = flags.characterEyeStyle,
                beardStyle = flags.characterBeardStyle,
                beardColor = flags.characterBeardColor,
                modifier   = Modifier.height(56.dp).aspectRatio(64f / 36f),
            )

            Text(
                text       = flags.characterName.ifBlank { stringResource(R.string.home_adventurer) },
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                textAlign  = TextAlign.Center,
            )

            if (flags.ironman) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Shield,
                        contentDescription = null,
                        modifier           = Modifier.size(12.dp),
                        tint               = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text  = stringResource(R.string.save_slot_ironman),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Text(
                text  = stringResource(R.string.save_slot_combat_level, combatLevelFrom(slot.skillLevels)),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text  = stringResource(R.string.save_slot_total_level, totalLevelFrom(slot.skillLevels)),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text  = slot.coins.formatCoins(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (slot.isActive) {
                Text(
                    text       = stringResource(R.string.save_slot_playing),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                )
            } else {
                val elapsed = (System.currentTimeMillis() - slot.lastPlayedAt).coerceAtLeast(60_000L)
                Text(
                    text      = stringResource(R.string.save_slot_last_played, elapsed.formatDurationMs()),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector        = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.save_slot_delete_btn),
                        modifier           = Modifier.size(18.dp),
                        tint               = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
