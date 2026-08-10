package com.fantasyidler.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.model.ThemeBase
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.ui.theme.WarningAmber
import com.fantasyidler.ui.viewmodel.ThemeEditorViewModel
import com.fantasyidler.ui.viewmodel.ThemeEditorViewModel.Companion.toHex
import com.fantasyidler.util.ColorContrast
import com.fantasyidler.util.toTitleCase

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeEditorScreen(
    source: String,
    blankName: Boolean,
    onBack: () -> Unit,
    viewModel: ThemeEditorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load(source, blankName) }

    val state by viewModel.state.collectAsState()
    val customKeys by viewModel.customThemeKeys.collectAsState()
    val scheme = remember(state) { viewModel.buildScheme(state) }
    val warnings = remember(state) { viewModel.contrastWarnings(state) }

    val slug = state.slug
    val officialClash = slug in viewModel.officialThemeKeys
    val overwrites = slug in customKeys
    val nameValid = state.loaded && slug.isNotBlank() && !officialClash

    var editingColour by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val jsonString = viewModel.exportJson()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(jsonString.toByteArray()) }
        AppBannerCenter.enqueue(context.getString(R.string.theme_editor_exported_ok))
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_theme_editor)) },
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
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Preview stays pinned while the controls below scroll, so colour
            // edits are always visible.
            Text(stringResource(R.string.theme_editor_preview_header), style = MaterialTheme.typography.titleSmall)
            ThemePreviewPane(scheme)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            OutlinedTextField(
                value         = state.displayName,
                onValueChange = viewModel::setDisplayName,
                label         = { Text(stringResource(R.string.theme_editor_name_label)) },
                isError       = state.loaded && officialClash,
                supportingText = when {
                    officialClash -> ({ Text(stringResource(R.string.theme_editor_name_reserved)) })
                    overwrites    -> ({ Text(stringResource(R.string.theme_editor_overwrite_hint, state.displayName.trim())) })
                    else          -> null
                },
                singleLine = true,
                modifier   = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.theme_editor_base_label), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = state.base == ThemeBase.DARK,
                    onClick  = { viewModel.setBase(ThemeBase.DARK) },
                    label    = { Text(stringResource(R.string.settings_theme_dark), style = MaterialTheme.typography.labelSmall) },
                )
                FilterChip(
                    selected = state.base == ThemeBase.LIGHT,
                    onClick  = { viewModel.setBase(ThemeBase.LIGHT) },
                    label    = { Text(stringResource(R.string.settings_theme_light), style = MaterialTheme.typography.labelSmall) },
                )
            }

            Text(stringResource(R.string.theme_editor_palette_header), style = MaterialTheme.typography.titleSmall)
            state.colours.forEach { (name, hex) ->
                // With one entry per role the caption would repeat the title; only
                // show it when an entry drives roles beyond its own name.
                val rolesLabel = state.rolesFor(name).joinToString { it.name.lowercase().toTitleCase() }
                PaletteRow(
                    name  = name,
                    hex   = hex,
                    roles = if (rolesLabel == name.toTitleCase()) "" else rolesLabel,
                    onClick  = { editingColour = name },
                    onRemove = { viewModel.removeColour(name) },
                )
            }

            if (warnings.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.theme_editor_contrast_header), style = MaterialTheme.typography.titleSmall)
                }
                warnings.forEach { warning ->
                    Text(
                        text = stringResource(
                            R.string.theme_editor_low_contrast,
                            warning.foreground.name.lowercase().toTitleCase(),
                            warning.background.name.lowercase().toTitleCase(),
                            "%.1f".format(warning.ratio),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.detachableRoles.isNotEmpty()) {
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(stringResource(R.string.theme_editor_add_roles))
                }
                if (showAdvanced) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.detachableRoles.forEach { role ->
                            AssistChip(
                                onClick = { viewModel.addRole(role) },
                                label   = { Text(role.name.lowercase().toTitleCase(), style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = nameValid,
                    onClick = {
                        viewModel.save { success ->
                            AppBannerCenter.enqueue(
                                context.getString(
                                    if (success) R.string.theme_editor_saved_ok else R.string.settings_theme_imported_fail
                                )
                            )
                            if (success) onBack()
                        }
                    },
                ) { Text(stringResource(R.string.theme_editor_save)) }
                OutlinedButton(
                    enabled = nameValid,
                    onClick = { exportLauncher.launch("${slug}_theme.json") },
                ) { Text(stringResource(R.string.theme_editor_export)) }
            }
            Spacer(Modifier.height(16.dp))
            }
        }
    }

    editingColour?.let { colourName ->
        val initial = state.colours[colourName]
            ?.let { hex -> try { Color(ColorContrast.parseArgb(hex)) } catch (_: Exception) { null } }
            ?: Color.Gray
        ColorPickerSheet(
            title     = colourName.toTitleCase(),
            initial   = initial,
            onPick    = { viewModel.setColour(colourName, it) },
            onDismiss = { editingColour = null },
        )
    }
}

/**
 * Compact sample rendered in the draft scheme. Every colour role the official
 * themes map is exercised explicitly (Material component defaults would pull
 * surfaceContainer* roles that themes typically leave unmapped, making edits
 * to surface or container colours invisible here).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePreviewPane(scheme: ColorScheme) {
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // background / on_background
                Text(
                    stringResource(R.string.theme_editor_sample_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // surface / on_surface / on_surface_variant (the app's dominant text pairing)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.theme_editor_sample_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "AaBbCc 123",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp),
                ) {
                    // primary / on_primary
                    Button(onClick = {}, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                        Text(stringResource(R.string.theme_editor_sample_button), style = MaterialTheme.typography.labelSmall)
                    }
                    SampleSwatch(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    SampleSwatch(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
                    SampleSwatch(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    SampleSwatch(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    SampleSwatch(
                        MaterialTheme.colorScheme.error,
                        MaterialTheme.colorScheme.onError,
                        stringResource(R.string.theme_editor_sample_error),
                    )
                }
            }
        }
    }
}

/** A small bg/fg pairing specimen, e.g. a container colour with its on-colour. */
@Composable
private fun SampleSwatch(bg: Color, fg: Color, text: String = "Aa") {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(6.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PaletteRow(
    name: String,
    hex: String,
    roles: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colour = remember(hex) {
        try { Color(ColorContrast.parseArgb(hex)) } catch (_: Exception) { Color.Transparent }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(colour, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        )
        Column(Modifier.weight(1f)) {
            Text(name.toTitleCase(), style = MaterialTheme.typography.bodyMedium)
            if (roles.isNotEmpty()) {
                Text(
                    stringResource(R.string.theme_editor_used_for, roles),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.theme_editor_remove_colour),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerSheet(
    title: String,
    initial: Color,
    onPick: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialHsv = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var sat by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    val colour = Color.hsv(hue, sat, value)
    var hexText by remember { mutableStateOf(colour.toHex()) }

    fun apply(h: Float, s: Float, v: Float) {
        hue = h; sat = s; value = v
        val picked = Color.hsv(h, s, v)
        hexText = picked.toHex()
        onPick(picked)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
    ) {
        ScaledSheetContent {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(colour, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    )
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }

                SaturationValueBox(
                    hue   = hue,
                    sat   = sat,
                    value = value,
                    onChange = { s, v -> apply(hue, s, v) },
                )

                // Hue slider over a rainbow gradient track
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .background(
                                Brush.horizontalGradient(
                                    (0..360 step 60).map { Color.hsv(it.coerceAtMost(359).toFloat(), 1f, 1f) }
                                ),
                                RoundedCornerShape(6.dp),
                            )
                    )
                    Slider(
                        value = hue,
                        onValueChange = { apply(it, sat, value) },
                        valueRange = 0f..359f,
                        colors = SliderDefaults.colors(
                            activeTrackColor   = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                        ),
                    )
                }

                val clipboard = LocalClipboardManager.current
                fun onHexInput(text: String) {
                    hexText = text
                    // Only complete 6- or 8-digit values apply; partial input while
                    // typing must never land as a garbage or transparent colour.
                    val parsed = ColorContrast.parseHexColorOrNull(text)?.let { Color(it) } ?: return
                    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(parsed.toArgb(), it) }
                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                    onPick(parsed)
                }
                OutlinedTextField(
                    value         = hexText,
                    onValueChange = { onHexInput(it) },
                    label      = { Text(stringResource(R.string.theme_editor_hex_label)) },
                    singleLine = true,
                    modifier   = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { clipboard.setText(AnnotatedString(colour.toHex())) }) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.theme_editor_copy_hex),
                                )
                            }
                            IconButton(onClick = { clipboard.getText()?.text?.let { onHexInput(it.trim()) } }) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = stringResource(R.string.theme_editor_paste_hex),
                                )
                            }
                        }
                    },
                )

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.btn_confirm))
                }
            }
        }
    }
}

/** 2D saturation (x) and value (y) picker for the given [hue]. */
@Composable
private fun SaturationValueBox(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (sat: Float, value: Float) -> Unit,
) {
    val density = LocalDensity.current
    var boxWidth by remember { mutableStateOf(1) }
    var boxHeight by remember { mutableStateOf(1) }

    fun update(x: Float, y: Float) {
        val s = (x / boxWidth).coerceIn(0f, 1f)
        val v = 1f - (y / boxHeight).coerceIn(0f, 1f)
        onChange(s, v)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(
                Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))),
                RoundedCornerShape(8.dp),
            )
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                RoundedCornerShape(8.dp),
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .onSizeChanged { boxWidth = it.width; boxHeight = it.height }
            .pointerInput(Unit) {
                detectTapGestures { offset -> update(offset.x, offset.y) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    update(change.position.x, change.position.y)
                }
            }
    ) {
        val xDp = with(density) { (sat * boxWidth).toDp() }
        val yDp = with(density) { ((1f - value) * boxHeight).toDp() }
        Box(
            Modifier
                .offset(x = xDp - 8.dp, y = yDp - 8.dp)
                .size(16.dp)
                .border(2.dp, Color.White, CircleShape)
                .border(3.dp, Color.Black.copy(alpha = 0.4f), CircleShape)
        )
    }
}
