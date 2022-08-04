/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.glance.appwidget.configuration

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.GlanceStateDefinition
import com.google.android.glance.appwidget.host.AppWidgetHost
import com.google.android.glance.appwidget.host.AppWidgetHostState

/**
 * Use to manage the [AppWidgetConfigurationScaffold] state.
 */
class AppWidgetConfigurationState(val instance: GlanceAppWidget, private val activity: Activity) {

    /**
     * The [GlanceId] obtained from the intent that launched the given activity
     *
     * @see AppWidgetManager.EXTRA_APPWIDGET_ID
     * @see GlanceAppWidgetManager.getGlanceIdBy
     */
    var glanceId: GlanceId? by mutableStateOf(null)
        internal set

    /**
     * Current state for the configuration. Use [getCurrentState] and [updateCurrentState] instead.
     */
    var state: Any? by mutableStateOf(instance)

    init {
        glanceId = GlanceAppWidgetManager(activity).getGlanceIdBy(activity.intent)

        // Set the result to canceled
        activity.setResult(Activity.RESULT_CANCELED)
        if (glanceId == null) {
            // invalid invocation of the configuration activity, skip.
            activity.finish()
        }
    }

    /**
     * Retrieve the current state of the given [GlanceAppWidget] instance
     *
     * Note: before calling [updateCurrentState] it will be the actual state of the instance.
     *
     * @param T - The state type as defined in the [GlanceAppWidget.stateDefinition] for this instance
     * @return The current [GlanceAppWidget] state for the configuration preview
     *
     * @see updateCurrentState
     */
    inline fun <reified T> getCurrentState(): T? = state as? T

    /**
     * Updates the [GlanceAppWidget] state for the configuration preview without modifying the
     * actual state. Similar to [androidx.glance.appwidget.state.updateAppWidgetState] but without
     * storing the new state.
     *
     * @param T - The state type as defined in the [GlanceAppWidget.stateDefinition] for this instance
     * @param update - The lambda used to update the state
     *
     * @see getCurrentState
     * @see androidx.glance.appwidget.state.updateAppWidgetState
     */
    inline fun <reified T> updateCurrentState(update: (T) -> T) {
        requireNotNull(state)
        state = update(state as T)
    }

    /**
     * Updates the actual [GlanceAppWidget] instance state and finish the activity with
     * [Activity.RESULT_OK].
     *
     * This method will persist the latest value provided by [updateCurrentState] as defined by the
     * [GlanceAppWidget.stateDefinition] and calls [GlanceAppWidget.update].
     */
    suspend fun applyConfiguration() {
        activity.setResult(Activity.RESULT_OK, activity.intent)
        @Suppress("UNCHECKED_CAST")
        updateAppWidgetState(
            activity,
            instance.stateDefinition as GlanceStateDefinition<Any?>,
            glanceId!!
        ) {
            state
        }
        instance.update(activity, glanceId!!)
        activity.finish()
    }

    /**
     * Discard any state provided by [updateCurrentState] and finishes the current activity with
     * [Activity.RESULT_CANCELED].
     *
     * If the configuration activity was initiated by an appwidget placement, that will cancel the
     * placement of the widget. Otherwise it will just finishes the configuration activity.
     *
     * Note: this will be the same result as if users performs a back-press action.
     */
    fun discardConfiguration() {
        activity.setResult(Activity.RESULT_CANCELED)
        activity.finish()
    }
}

/**
 * Get and remember an [AppWidgetConfigurationState] for the given [GlanceAppWidget] instance
 *
 * Use this method to provide the [GlanceAppWidget] to display in the
 * [AppWidgetConfigurationScaffold] and update the state.
 *
 * @param configurationInstance - The instance of the [GlanceAppWidget] to configure.
 * @return a new or cached [AppWidgetConfigurationState] instance
 */
@Composable
fun rememberAppWidgetConfigurationState(configurationInstance: GlanceAppWidget): AppWidgetConfigurationState {
    val activity = LocalContext.current as Activity
    return remember(configurationInstance) {
        AppWidgetConfigurationState(configurationInstance, activity)
    }
}

/**
 * AppWidgetConfigurationScaffold extends the Material 3 [Scaffold] providing the structure to
 * display an AppWidget Configuration screen.
 *
 * @param appWidgetConfigurationState the [AppWidgetConfigurationState] used to display the preview
 * @param modifier the [Modifier] to be applied to this scaffold
 * @param topBar top app bar of the screen, typically a [SmallTopAppBar]
 * @param bottomBar bottom bar of the screen, typically a [NavigationBar]
 * @param snackbarHost component to host [Snackbar]s that are pushed to be shown via
 * [SnackbarHostState.showSnackbar], typically a [SnackbarHost]
 * @param floatingActionButton Main action button of the screen, typically a [FloatingActionButton]
 * @param floatingActionButtonPosition position of the FAB on the screen. See [FabPosition].
 * @param previewColor the color used for the background of the appwidget preview container.
 * By default it will use the Secondary Container color from the [MaterialTheme].
 * @param displaySize the size of the appwidget preview. By default it will use the size provided by
 * the placed appwidget or the target size as defined in the appwidget metadata.
 * @param containerColor the color used for the background of this scaffold. Use [Color.Transparent]
 * to have no color.
 * @param contentColor the preferred color for content inside this scaffold. Defaults to either the
 * matching content color for [containerColor], or to the current [LocalContentColor] if
 * [containerColor] is not a color from the theme.
 * @param content content of the configuration screen (under the appwidget preview).
 * The lambda receives a [PaddingValues] that should be applied to the content root via
 * [Modifier.padding] to properly offset top and bottom bars. If using [Modifier.verticalScroll],
 * apply this modifier to the child of the scroll, and not on the scroll itself.
 *
 * @see androidx.compose.material3.Scaffold
 */
@ExperimentalGlanceRemoteViewsApi
@ExperimentalMaterial3Api
@Composable
fun AppWidgetConfigurationScaffold(
    appWidgetConfigurationState: AppWidgetConfigurationState,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    previewColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    displaySize: DpSize = DpSize.Unspecified,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable (PaddingValues) -> Unit
) {
    val context = LocalContext.current
    val glanceId = appWidgetConfigurationState.glanceId ?: return

    LaunchedEffect(glanceId) {
        appWidgetConfigurationState.state = getAppWidgetState(
            context = context,
            definition = appWidgetConfigurationState.instance.stateDefinition as GlanceStateDefinition<*>,
            glanceId = glanceId
        )
    }

    val orientation = LocalConfiguration.current.orientation
    var widgetSize by remember {
        mutableStateOf(displaySize)
    }

    // If no display size specified get the launcher available size
    if (displaySize == DpSize.Unspecified) {
        LaunchedEffect(widgetSize, glanceId, orientation) {
            val sizes = GlanceAppWidgetManager(context).getAppWidgetSizes(glanceId)
            widgetSize = when (orientation) {
                Configuration.ORIENTATION_PORTRAIT -> sizes.first()
                Configuration.ORIENTATION_LANDSCAPE -> sizes.getOrElse(1) {
                    // Edge case: only one size available. Swap size values
                    val portraitSize = sizes.first()
                    portraitSize.copy(width = portraitSize.height, height = portraitSize.width)
                }
                else -> throw IllegalArgumentException("Unknown orientation value")
            }
        }
    }

    val remoteViews = remember {
        GlanceRemoteViews()
    }
    val previewState = remember {
        AppWidgetHostState(mutableStateOf(null))
    }
    if (previewState.isReady && widgetSize != DpSize.Unspecified) {
        LaunchedEffect(
            previewState,
            appWidgetConfigurationState.state,
            appWidgetConfigurationState.instance,
            widgetSize
        ) {
            previewState.updateAppWidget(
                remoteViews.compose(
                    context = context,
                    size = widgetSize,
                    state = appWidgetConfigurationState.state,
                    content = {
                        appWidgetConfigurationState.instance.Content()
                    }
                ).remoteViews
            )
        }
    }

    Scaffold(
        modifier,
        topBar,
        bottomBar,
        snackbarHost,
        floatingActionButton,
        floatingActionButtonPosition,
        containerColor,
        contentColor,
    ) {
        Column(Modifier.fillMaxSize()) {
            AppWidgetHost(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(previewColor)
                    .padding(it)
                    .padding(16.dp),
                widgetSize = widgetSize,
                state = previewState
            )
            content(it)
        }
    }
}
