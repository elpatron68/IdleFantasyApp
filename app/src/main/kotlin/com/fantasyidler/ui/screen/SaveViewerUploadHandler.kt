package com.fantasyidler.ui.screen

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.fantasyidler.R
import com.fantasyidler.ui.viewmodel.SettingsViewModel
import com.fantasyidler.util.SaveViewerClient
import com.fantasyidler.util.SaveViewerError

fun openSaveViewer(
    viewerUrl: String,
    context: Context,
    onNavigateToSettings: () -> Unit = {},
) {
    if (viewerUrl.isBlank()) {
        AppBannerCenter.enqueue(
            message     = context.getString(R.string.settings_viewer_upload_empty),
            actionLabel = context.getString(R.string.settings_title),
            onAction    = onNavigateToSettings,
        )
        return
    }
    val target = SaveViewerClient.parseViewerUrl(viewerUrl).getOrElse {
        AppBannerCenter.enqueue(context.getString(R.string.settings_viewer_upload_invalid))
        return
    }
    CustomTabsIntent.Builder()
        .build()
        .launchUrl(context, Uri.parse(target.viewerUrl))
}

fun triggerSaveViewerUpload(
    viewerUrl: String,
    context: Context,
    settingsViewModel: SettingsViewModel,
    onUploadingChange: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit = {},
) {
    if (viewerUrl.isBlank()) {
        AppBannerCenter.enqueue(
            message     = context.getString(R.string.settings_viewer_upload_empty),
            actionLabel = context.getString(R.string.settings_title),
            onAction    = onNavigateToSettings,
        )
        return
    }
    if (SaveViewerClient.parseViewerUrl(viewerUrl).isFailure) {
        AppBannerCenter.enqueue(context.getString(R.string.settings_viewer_upload_invalid))
        return
    }
    onUploadingChange(true)
    settingsViewModel.uploadToViewer { result ->
        onUploadingChange(false)
        result.fold(
            onSuccess = { response ->
                if (response.imported) {
                    AppBannerCenter.enqueue(
                        message     = context.getString(R.string.settings_viewer_upload_success),
                        actionLabel = context.getString(R.string.settings_viewer_open),
                        onAction    = {
                            SaveViewerClient.parseViewerUrl(viewerUrl)
                                .getOrNull()
                                ?.viewerUrl
                                ?.let { url ->
                                    CustomTabsIntent.Builder()
                                        .build()
                                        .launchUrl(context, Uri.parse(url))
                                }
                        },
                    )
                } else {
                    AppBannerCenter.enqueue(context.getString(R.string.settings_viewer_upload_duplicate))
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
                AppBannerCenter.enqueue(message)
            },
        )
    }
}
