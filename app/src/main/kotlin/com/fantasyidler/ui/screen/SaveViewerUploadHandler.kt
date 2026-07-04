package com.fantasyidler.ui.screen

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.fantasyidler.R
import com.fantasyidler.ui.viewmodel.SettingsViewModel
import com.fantasyidler.util.SaveViewerClient
import com.fantasyidler.util.SaveViewerError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun openSaveViewer(
    viewerUrl: String,
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onNavigateToSettings: () -> Unit = {},
) {
    if (viewerUrl.isBlank()) {
        scope.launch {
            when (
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.settings_viewer_upload_empty),
                    actionLabel = context.getString(R.string.settings_title),
                    duration = SnackbarDuration.Long,
                    withDismissAction = true,
                )
            ) {
                SnackbarResult.ActionPerformed -> onNavigateToSettings()
                else -> {}
            }
        }
        return
    }
    val target = SaveViewerClient.parseViewerUrl(viewerUrl).getOrElse {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.settings_viewer_upload_invalid),
                withDismissAction = true,
            )
        }
        return
    }
    CustomTabsIntent.Builder()
        .build()
        .launchUrl(context, Uri.parse(target.viewerUrl))
}

fun triggerSaveViewerUpload(
    viewerUrl: String,
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    settingsViewModel: SettingsViewModel,
    onUploadingChange: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit = {},
) {
    if (viewerUrl.isBlank()) {
        scope.launch {
            when (
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.settings_viewer_upload_empty),
                    actionLabel = context.getString(R.string.settings_title),
                    duration = SnackbarDuration.Long,
                    withDismissAction = true,
                )
            ) {
                SnackbarResult.ActionPerformed -> onNavigateToSettings()
                else -> {}
            }
        }
        return
    }
    if (SaveViewerClient.parseViewerUrl(viewerUrl).isFailure) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.settings_viewer_upload_invalid),
                withDismissAction = true,
            )
        }
        return
    }
    onUploadingChange(true)
    settingsViewModel.uploadToViewer { result ->
        onUploadingChange(false)
        scope.launch {
            result.fold(
                onSuccess = { response ->
                    if (response.imported) {
                        val openLabel = context.getString(R.string.settings_viewer_open)
                        when (
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.settings_viewer_upload_success),
                                actionLabel = openLabel,
                                duration = SnackbarDuration.Long,
                                withDismissAction = true,
                            )
                        ) {
                            SnackbarResult.ActionPerformed -> {
                                SaveViewerClient.parseViewerUrl(viewerUrl)
                                    .getOrNull()
                                    ?.viewerUrl
                                    ?.let { url ->
                                        CustomTabsIntent.Builder()
                                            .build()
                                            .launchUrl(context, Uri.parse(url))
                                    }
                            }
                            else -> {}
                        }
                    } else {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.settings_viewer_upload_duplicate),
                            withDismissAction = true,
                        )
                    }
                },
                onFailure = { error ->
                    val message = when (error) {
                        is SaveViewerError.InvalidUrl ->
                            context.getString(R.string.settings_viewer_upload_invalid)
                        is SaveViewerError.Network ->
                            context.getString(R.string.settings_viewer_upload_network)
                        is SaveViewerError.NotFound ->
                            context.getString(R.string.settings_viewer_upload_not_found)
                        is SaveViewerError.ParseError ->
                            context.getString(R.string.settings_viewer_upload_parse)
                        is SaveViewerError.RateLimit ->
                            context.getString(R.string.settings_viewer_upload_rate_limit)
                        is SaveViewerError.ServerError ->
                            context.getString(R.string.settings_viewer_upload_parse)
                        else ->
                            context.getString(R.string.settings_viewer_upload_network)
                    }
                    snackbarHostState.showSnackbar(message, withDismissAction = true)
                },
            )
        }
    }
}
