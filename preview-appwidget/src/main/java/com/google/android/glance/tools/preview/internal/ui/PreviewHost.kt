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

package com.google.android.glance.tools.preview.internal.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.widget.RemoteViews
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.updatePadding
import com.google.android.glance.tools.preview.internal.ui.theme.widgetCornerRadiiDp


internal class PreviewHostState(private val state: MutableState<AppWidgetHostView?>) {
    var value: AppWidgetHostView?
        get() = state.value
        set(value) {
            state.value = value
        }

    val isReady: Boolean
        get() = state.value != null

    fun updatePreview(preview: RemoteViews) {
        val host = value ?: return
        host.updateAppWidget(preview)
    }
}

@Composable
internal fun rememberWidgetHost(selectedProvider: AppWidgetProviderInfo) =
    remember(selectedProvider) {
        PreviewHostState(mutableStateOf(null))
    }

@Composable
internal fun PreviewHost(
    appWidgetInfo: AppWidgetProviderInfo,
    widgetSize: DpSize,
    previewState: PreviewHostState,
    modifier: Modifier = Modifier
) {
    val appWidgetId = rememberSaveable(appWidgetInfo) {
        appWidgetInfo.provider.hashCode()
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
        CellGrid(rows = 5, columns = 5, color = color, modifier = Modifier.fillMaxSize())
        AndroidView(
            factory = { context ->
                AppWidgetHostView(context)
            },
            modifier = Modifier
                .size(widgetSize)
                .dashedBorder(1.dp, 1.dp, color)
                .clip(RoundedCornerShape(LocalContext.current.widgetCornerRadiiDp)),
            update = { hostView ->
                if (hostView != previewState.value) {
                    with(hostView) {
                        setAppWidget(appWidgetId, appWidgetInfo)
                        updatePadding(0, 0, 0, 0)
                        updateAppWidget(
                            RemoteViews(
                                context.packageName,
                                appWidgetInfo.initialLayout
                            )
                        )
                    }
                }
                previewState.value = hostView
            }
        )
    }
}

@Composable
private fun CellGrid(rows: Int, columns: Int, color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val cellSize = Size((this.size.width / columns), (this.size.height / rows))
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                translate(
                    left = column * cellSize.width,
                    top = row * cellSize.height
                ) {
                    drawOutline(
                        outline = Outline.Rectangle(
                            Rect(
                                offset = Offset(0f, 0f),
                                size = cellSize
                            )
                        ),
                        color = color,
                        style = Stroke(width = 1f)
                    )
                }
            }
        }
    }
}

private fun Modifier.dashedBorder(width: Dp, radius: Dp, color: Color) =
    drawBehind {
        drawIntoCanvas {
            val paint = Paint()
                .apply {
                    strokeWidth = width.toPx()
                    this.color = color
                    style = PaintingStyle.Stroke
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                }
            it.drawRoundRect(
                width.toPx(),
                width.toPx(),
                size.width - width.toPx(),
                size.height - width.toPx(),
                radius.toPx(),
                radius.toPx(),
                paint
            )
        }
    }

@Preview(showSystemUi = true)
@Composable
fun PreviewCellGrid() {
    CellGrid(rows = 4, columns = 4, color = Color.Red, modifier = Modifier.size(512.dp))
}
