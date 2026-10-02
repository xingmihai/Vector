package org.matrix.vector.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The floating bar as a piece of glass: the strip refracts what scrolls behind it, and the pill
 * behind the current panel is a second lens laid on top of the first.
 *
 * The pill is the part that moves. It follows a finger across the strip with a damped drag —
 * overshoot, then settle — and presses under it, so switching panels is something the hand does
 * rather than something the eye confirms afterwards. It refracts [combined], which is the strip
 * *and* the labels drawn on it, so the pill bends the icon it sits behind rather than bending only
 * the screen behind the strip.
 *
 * [editing] turns all of it off: rearranging and dragging the pill would both be answering a
 * horizontal drag, and the badge on each item already says which panels are here.
 */
@Composable
fun PanelGlassBar(
    panels: NavPanels,
    current: androidx.navigation3.runtime.NavKey,
    editing: Boolean,
    onSelect: (androidx.navigation3.runtime.NavKey) -> Unit,
    onEdit: () -> Unit,
    onToggleHidden: (key: String, hidden: Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val items = if (editing) panels.all else panels.visible
    val count = items.size
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    var widthPx by remember { mutableFloatStateOf(0f) }
    val insetPx = with(density) { 8.dp.toPx() }
    val tabWidthPx = if (count == 0) 0f else ((widthPx - insetPx) / count).coerceAtLeast(0f)

    val pillShape = remember { CircleShape }
    val tabsBackdrop = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    var currentIndex by remember { mutableIntStateOf(items.indexOfFirst { it.route == current }.coerceAtLeast(0)) }
    val onSelectUpdated by rememberUpdatedState(onSelect)

    LaunchedEffect(current, items) {
        val index = items.indexOfFirst { it.route == current }
        if (index >= 0) currentIndex = index
    }

    // Keyed on the count alone: the callbacks close over the item list, and a stale capture is how
    // a drag ends up selecting yesterday's panel.
    val drag = remember(scope, count) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..(count - 1).coerceAtLeast(0).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { it.x in 0f..widthPx },
            onDragStarted = { },
            onDragStopped = {
                val target = targetValue.roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
                if (target != currentIndex && target < count) {
                    currentIndex = target
                    onSelectUpdated(items[target].route)
                }
                updateValue(target.toFloat())
            },
            onDrag = { _, amount ->
                if (tabWidthPx > 0f && amount.x != 0f) {
                    updateValue(
                        (targetValue + amount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (count - 1).coerceAtLeast(0).toFloat()),
                    )
                }
            },
        )
    }

    // The strip, drawn twice: once to look at, and once into [tabsBackdrop] so the pill has
    // something of its own to refract. The second pass is invisible and carries no gestures —
    // duplicating the slots would duplicate their drag and their semantics with them.
    val tabsContent: @Composable RowScope.() -> Unit = {
        items.forEachIndexed { index, destination ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = if (index == currentIndex) destination.selectedIcon else destination.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(destination.labelRes),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }

    val lensPx = with(density) { 24.dp.toPx() }
    val blurPx = with(density) { 4.dp.toPx() }
    val pillLensPx = with(density) { 10.dp.toPx() }
    val pillAmountPx = with(density) { 14.dp.toPx() }
    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat() }
            // Rearranging and switching panels would both answer a horizontal drag; while
            // rearranging, the slots own the gesture.
            .then(if (editing) Modifier else drag.modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        FloatingNavigationBar(
            modifier = Modifier
                .height(64.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { pillShape },
                    effects = {
                        padding = maxOf(padding, lensPx * 2f)
                        vibrancy()
                        blur(blurPx, blurPx)
                        lens(refractionHeight = lensPx, refractionAmount = lensPx)
                    },
                    onDrawSurface = { drawRect(containerColor) },
                ),
        ) {
            PanelFloatingNavigationBar(
                panels = panels,
                current = current,
                editing = editing,
                onSelect = {
                    currentIndex = items.indexOfFirst { it.route == current }.coerceAtLeast(0)
                    onSelect(it)
                },
                onEdit = onEdit,
                onToggleHidden = onToggleHidden,
                onMove = onMove,
                iosStyle = false,
            )
        }

        if (!editing) {
            Row(
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .height(56.dp)
                    .padding(horizontal = 4.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            padding = maxOf(padding, lensPx * 2f)
                            vibrancy()
                            blur(blurPx, blurPx)
                            lens(refractionHeight = lensPx, refractionAmount = lensPx)
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                content = tabsContent,
            )

            if (tabWidthPx > 0f) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            translationX = drag.value * tabWidthPx
                            // Stretched along the drag and squeezed across it: the pill is being
                            // pulled, not slid.
                            scaleX = drag.scaleX
                            scaleY = drag.scaleY
                            val v = drag.velocity / 10f
                            scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                        }
                        .drawBackdrop(
                            backdrop = combined,
                            shape = { pillShape },
                            effects = {
                                val progress = drag.pressProgress
                                lens(
                                    refractionHeight = pillLensPx * progress,
                                    refractionAmount = pillAmountPx * progress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            onDrawSurface = {
                                val progress = drag.pressProgress
                                drawRect(
                                    color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            InnerShadow(
                                radius = 8.dp * drag.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = drag.pressProgress,
                            )
                        }
                        .height(56.dp)
                        .width(with(density) { tabWidthPx.toDp() }),
                )
            }
        }
    }
}
