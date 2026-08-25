package com.fantasyidler.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.BlessingType
import com.fantasyidler.repository.ChurchRepository
import kotlin.math.roundToInt
import com.fantasyidler.ui.screen.StatInline
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatDurationMs

private const val MAX_VISIBLE_BOOST_LINES = 3

private data class BoostLine(val tint: Color, val text: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerStatsBar(
    context: Context,
    combatLevel: Int,
    totalLevel: Int,
    coins: Long,
    activeBlessingKey: String,
    prayerCapeMult: Float,
    activeBlessingRemainingMs: Long,
    xpBoostRemainingMs: Long,
    prestigeBoostsRemainingMs: Map<String, Long> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val blessingActive = activeBlessingKey.isNotEmpty() && activeBlessingRemainingMs > 0
    val boostActive = xpBoostRemainingMs > 0

    Column(modifier) {
        FlowRow(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement   = Arrangement.spacedBy(4.dp),
        ) {
            StatInline(
                label = stringResource(R.string.label_combat_level),
                value = combatLevel.toString(),
            )
            StatInline(
                label = stringResource(R.string.label_total_level),
                value = totalLevel.toString(),
            )
            StatInline(
                label      = stringResource(R.string.label_coins),
                value      = coins.formatCoins(),
                valueColor = MaterialTheme.colorScheme.primary,
            )
        }
        val primaryTint = MaterialTheme.colorScheme.primary
        val tertiaryTint = MaterialTheme.colorScheme.tertiary
        val boostLines = buildList {
            if (blessingActive) {
                val nameResId = context.resources.getIdentifier(
                    "blessing_${activeBlessingKey}_name", "string", context.packageName,
                )
                val blessingName = if (nameResId != 0) stringResource(nameResId) else activeBlessingKey
                val blessingData = ChurchRepository.ALL_BLESSINGS.firstOrNull { it.key == activeBlessingKey }
                val boostDesc = blessingData?.let { b ->
                    val eff = ChurchRepository.effectiveMagnitude(b, prayerCapeMult)
                    when (b.type) {
                        BlessingType.XP      -> "${(eff * 100).roundToInt() / 100f}x XP"
                        BlessingType.DEFENSE -> "+${eff.toInt()} DEF"
                        BlessingType.COINS   -> "+${(eff * 100).roundToInt()}% coins"
                    }
                }
                val timeLeft = activeBlessingRemainingMs.formatDurationMs(context)
                val blessingText = if (boostDesc != null) "$blessingName ($boostDesc) - $timeLeft"
                                  else "$blessingName - $timeLeft"
                add(BoostLine(tint = primaryTint, text = blessingText))
            }
            if (boostActive) {
                add(
                    BoostLine(
                        tint = tertiaryTint,
                        text = stringResource(R.string.home_xp_boost_active, xpBoostRemainingMs.formatDurationMs(context)),
                    )
                )
            }
            prestigeBoostsRemainingMs.entries.sortedBy { it.key }.forEach { (skill, remainingMs) ->
                add(
                    BoostLine(
                        tint = tertiaryTint,
                        text = stringResource(
                            R.string.home_prestige_xp_boost_active,
                            GameStrings.skillName(context, skill),
                            remainingMs.formatDurationMs(context),
                        ),
                    )
                )
            }
        }

        if (boostLines.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            val scrollable = boostLines.size > MAX_VISIBLE_BOOST_LINES
            Column(
                modifier = if (scrollable) {
                    Modifier
                        .heightIn(max = 16.dp * MAX_VISIBLE_BOOST_LINES + 5.dp * (MAX_VISIBLE_BOOST_LINES - 1))
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                boostLines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Filled.Star,
                            contentDescription = null,
                            tint               = line.tint,
                            modifier           = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text  = line.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = line.tint,
                        )
                    }
                }
            }
        }
    }
}
