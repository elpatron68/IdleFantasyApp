package com.fantasyidler.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.viewmodel.SettingsViewModel
import com.fantasyidler.util.GameStrings

/** Theme selection, editing, and import/export, split out of the main Settings screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    onNavigateToThemeEditor: (source: String, blankName: Boolean) -> Unit = { _, _ -> },
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val customThemes    by viewModel.customThemes.collectAsState()
    val themePreference by viewModel.themePreference.collectAsState()

    val importThemeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: return@rememberLauncherForActivityResult
        viewModel.importTheme(jsonString) { success ->
            AppBannerCenter.enqueue(
                if (success) context.getString(R.string.settings_theme_imported_ok) else context.getString(R.string.settings_theme_imported_fail)
            )
        }
    }

    val exportThemeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportTheme(themePreference) { jsonString ->
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(jsonString.toByteArray()) }
            AppBannerCenter.enqueue(context.getString(R.string.theme_editor_exported_ok))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_theme)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsRow(
                title    = stringResource(R.string.settings_theme),
                subtitle = stringResource(R.string.settings_theme_desc),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                viewModel.officialThemes.forEach { theme ->
                    FilterChip(
                        selected = themePreference == theme,
                        onClick  = { viewModel.setTheme(theme) },
                        label    = { Text(GameStrings.themeName(context, theme), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            if (customThemes.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    customThemes.forEach { theme ->
                        FilterChip(
                            selected = themePreference == theme.name,
                            onClick  = { viewModel.setTheme(theme.name) },
                            label    = { Text(theme.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val customSelected = themePreference in customThemes.map { it.name }
                OutlinedButton(onClick = { onNavigateToThemeEditor(themePreference, true) }) {
                    Text(stringResource(R.string.theme_editor_new_btn))
                }
                OutlinedButton(
                    onClick = { onNavigateToThemeEditor(themePreference, false) },
                    enabled = customSelected,
                ) {
                    Text(stringResource(R.string.theme_editor_edit_btn))
                }
                OutlinedButton(
                    onClick = { exportThemeLauncher.launch("${themePreference}_theme.json") },
                    enabled = customSelected,
                ) {
                    Text(stringResource(R.string.theme_editor_export))
                }
                if (customThemes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.deleteTheme(themePreference) },
                        enabled = customSelected,
                    ) {
                        Text(stringResource(R.string.settings_delete_theme_btn))
                    }
                }
            }
            SettingsRow(
                title    = stringResource(R.string.settings_import_theme),
                subtitle = stringResource(R.string.settings_import_theme_desc),
                trailing = {
                    OutlinedButton(onClick = { importThemeLauncher.launch("*/*") }) {
                        Text(stringResource(R.string.settings_import_btn))
                    }
                }
            )
        }
    }
}
