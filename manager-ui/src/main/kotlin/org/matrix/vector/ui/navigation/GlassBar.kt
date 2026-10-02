package org.matrix.vector.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * How much the icons and labels grow while the pill is pressed. Read through a composition local
 * because the tabs are drawn twice — once to look at, once into the backdrop the pill samples —
 * and the recording pass is the one that has to carry the scale.
 */
private val LocalIosTabScale = staticCompositionLocalOf { { 1f } }

/**
 * The floating bar as the library builds it: one row of tabs, drawn twice, and a pill of glass
 * that rides over them.
 *
 * The duplication is the whole trick and it is not to be simplified away. The pill refracts what
 * is *under* it, and what is under it is the tabs — not the screen behind the bar. So the tabs are
 * drawn a second time, invisibly, into their own layer ([tabsBackdrop]), and the pill samples that
 * layer composited over the screen ([combinedBackdrop]). Draw the tabs once and the pill has
 * nothing of its own to bend: it refracts the page behind the bar and shows a pill-shaped patch of
 * it, which is a smudge, not glass.
 *
 * Which is why this does not reuse the ordinary bar's item composables. Those carry their own
 * padding, their own weights and their own gestures, and the recording pass has to lay the tabs
 * out *exactly* as the visible pass does or the pill sits beside its tab instead of over it. One
 * [tabsContent] shared by both passes is the only way to guarantee that, so this one draws its own
 * tabs and takes no part in rearranging them — see [PanelGlassBar].
 *
 * Adapted from the library's own example, `example/.../liquid/LiquidGlassNavigationBar.kt`
 * (from Kyant0/AndroidLiquidGlass, Apache-2.0).
 */
@Composable
fun PanelGlassBar(
    panels: NavPanels,
    current: androidx.navigation3.runtime.NavKey,
    onSelect: (androidx.navigation3.runtime.NavKey) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val items = panels.visible
    val tabsCount = items.size

    val isDark = isSystemInDarkTheme()
    val pillShape = remember { CircleShape }
    val accentColor = MiuixTheme.colorScheme.primary
    val tabContentColor = MiuixTheme.colorScheme.onSurface
    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember {
        mutableIntStateOf(items.indexOfFirst { it.route == current }.coerceAtLeast(0))
    }
    val onSelectUpdated by rememberUpdatedState(onSelect)

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx).toInt().coerceIn(0, tabsCount - 1)
    }

    val dampedDrag = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { position -> position.x in 0f..totalWidthPx },
            onDragStarted = { position -> updateValue(indexAt(position.x).toFloat()) },
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    onSelectUpdated(items[targetIndex].route)
                }
                updateValue(targetIndex.toFloat())
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                }
            },
        )
    }

    LaunchedEffect(current, items) {
        val selected = items.indexOfFirst { it.route == current }
        if (selected >= 0 && currentIndex != selected) {
            currentIndex = selected
            dampedDrag.animateToValue(selected.toFloat())
        }
    }

    fun activateTab(index: Int) {
        if (currentIndex != index) {
            currentIndex = index
            onSelectUpdated(items[index].route)
        }
        dampedDrag.animateToValue(index.toFloat())
    }

    // Keyed on dampedDrag: the position lambda captures it; a stale capture freezes the press spot.
    val interactiveHighlight = remember(animationScope, isLtr, dampedDrag) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { layerSize, _ ->
                Offset(
                    x = if (isLtr) {
                        (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                    } else {
                        layerSize.width - (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                    },
                    y = layerSize.height / 2f,
                )
            },
        )
    }

    // Read .value only inside highlight lambdas (draw phase), never in composition.
    val baseHighlight: State<top.yukonga.miuix.kmp.blur.highlight.Highlight> =
        rememberGravityRotatedHighlight(IosIndicatorSpecular, extraDegrees = -45f)
    val pillHighlight: State<top.yukonga.miuix.kmp.blur.highlight.Highlight> =
        rememberGravityRotatedHighlight(IosIndicatorSpecular, extraDegrees = 90f)

    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    val navBarBottomPadding = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    val bottomPaddingValue = if (navBarBottomPadding != 0.dp) 8.dp + navBarBottomPadding else 36.dp

    val tabsContent: @Composable RowScope.() -> Unit = {
        val tabScale = LocalIosTabScale.current
        items.forEachIndexed { index, destination ->
            Column(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        selected = index == currentIndex
                        role = Role.Tab
                        onClick {
                            activateTab(index)
                            true
                        }
                    }
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val s = tabScale()
                        scaleX = s
                        scaleY = s
                    },
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                horizontalAlignment = CenterHorizontally,
            ) {
                androidx.compose.material3.Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = if (index == currentIndex) destination.selectedIcon else destination.icon,
                    // Decorative: the adjacent label names the item; avoids TalkBack double-read.
                    contentDescription = null,
                    tint = LocalContentColor.current,
                )
                androidx.compose.material3.Text(
                    text = stringResource(destination.labelRes),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current,
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = bottomPaddingValue),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        // Measured before the 4dp padding below it, so what arrives here is the
                        // full width and the padding is still to come off — which is exactly how
                        // tabWidthPx below is derived, and why the pill and the tab agree.
                        .onSizeChanged { coords ->
                            totalWidthPx = coords.width.toFloat()
                            val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                            tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                        }
                        .graphicsLayer { translationX = panelOffset }
                        // A sheet of glass lifted off the screen, and it is the shadow underneath
                        // that says so; without it the refraction reads as a smudge on the surface.
                        .dropShadow(
                            shape = pillShape,
                            shadow = Shadow(
                                radius = 10.dp,
                                color = Color.Black,
                                // Lighter in light theme to avoid a visible grey fringe.
                                alpha = if (isDark) 0.2f else 0.1f,
                            ),
                        )
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                // 24dp lens refraction + 16dp press-scale reach, raised before
                                // blur() reads it.
                                padding = maxOf(padding, with(density) { 40.dp.toPx() })
                                vibrancy()
                                blur(with(density) { 4.dp.toPx() }, with(density) { 4.dp.toPx() })
                                lens(
                                    refractionHeight = with(density) { 24.dp.toPx() },
                                    refractionAmount = with(density) { 24.dp.toPx() },
                                )
                            },
                            highlight = { baseHighlight.value.copy(alpha = 0.75f) },
                            layerBlock = {
                                val width = size.width.coerceAtLeast(1f)
                                val s = lerp(
                                    1f,
                                    1f + with(density) { 16.dp.toPx() } / width,
                                    dampedDrag.pressProgress,
                                )
                                scaleX = s
                                scaleY = s
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier.then(interactiveHighlight.gestureModifier))
                        .then(dampedDrag.modifier)
                        .height(64.dp)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsContent,
                )
            }

            CompositionLocalProvider(
                LocalIosTabScale provides { lerp(1f, 1.2f, dampedDrag.pressProgress) },
                LocalContentColor provides accentColor,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {}
                        // Invisible and gestureless: duplicating the tabs would duplicate their
                        // drag and their semantics with them.
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(with(density) { 4.dp.toPx() }, with(density) { 4.dp.toPx() })
                                lens(
                                    refractionHeight = with(density) { 24.dp.toPx() },
                                    refractionAmount = with(density) { 24.dp.toPx() },
                                )
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier)
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsContent,
                )
            }

            if (tabWidthPx > 0f) {
                val tabWidthDp = with(density) { tabWidthPx.toDp() }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDrag.value * tabWidthPx
                            translationX =
                                if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                            scaleX = dampedDrag.scaleX
                            scaleY = dampedDrag.scaleY
                            val v = dampedDrag.velocity / 10f
                            scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                        }
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = dampedDrag.pressProgress
                                lens(
                                    refractionHeight = with(density) { 10.dp.toPx() } * progress,
                                    refractionAmount = with(density) { 14.dp.toPx() } * progress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = { pillHighlight.value.copy(alpha = dampedDrag.pressProgress) },
                            layerBlock = {
                                scaleX = dampedDrag.scaleX
                                scaleY = dampedDrag.scaleY
                                val v = dampedDrag.velocity / 10f
                                scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = dampedDrag.pressProgress
                                drawRect(
                                    color = if (!isDark) Color.Black.copy(alpha = 0.1f)
                                    else Color.White.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            InnerShadow(
                                radius = 8.dp * dampedDrag.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = dampedDrag.pressProgress,
                            )
                        }
                        .height(56.dp)
                        .width(tabWidthDp),
                )
            }
        }
    }
}
