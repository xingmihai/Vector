package org.matrix.vector.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.matrix.vector.manager.data.github.GitHubRepository
import org.matrix.vector.manager.data.repository.VectorLogSource
import org.matrix.vector.manager.data.repository.VectorStoreInstallHost
import org.matrix.vector.manager.di.ServiceLocator
import org.matrix.vector.manager.ui.navigation.Activity
import org.matrix.vector.manager.ui.navigation.Canary
import org.matrix.vector.manager.ui.navigation.CrashTrace
import org.matrix.vector.manager.ui.navigation.DeepLink
import org.matrix.vector.manager.ui.navigation.FrameworkUpdate
import org.matrix.vector.manager.ui.navigation.LogTrace
import org.matrix.vector.manager.ui.navigation.Scope
import org.matrix.vector.manager.ui.navigation.StoreDetail
import org.matrix.vector.manager.ui.navigation.SystemStatus
import org.matrix.vector.manager.ui.navigation.TOP_LEVEL_DESTINATIONS
import org.matrix.vector.manager.ui.navigation.TopLevelRoute
import org.matrix.vector.manager.ui.navigation.Troubleshoot
import org.matrix.vector.manager.ui.navigation.VectorFloatingNavSettings
import org.matrix.vector.manager.ui.navigation.VectorNavPanelStore
import org.matrix.vector.manager.ui.navigation.Web
import org.matrix.vector.manager.ui.screens.activity.ActivityScreen
import org.matrix.vector.manager.ui.screens.canary.CanaryScreen
import org.matrix.vector.manager.ui.screens.home.CrashTraceScreen
import org.matrix.vector.manager.ui.screens.home.HomeScreen
import org.matrix.vector.manager.ui.screens.home.SystemStatusScreen
import org.matrix.vector.manager.ui.screens.modules.ModulesScreen
import org.matrix.vector.manager.ui.screens.modules.ScopeScreen
import org.matrix.vector.manager.ui.screens.report.TroubleshootScreen
import org.matrix.vector.manager.ui.screens.update.FrameworkUpdateScreen
import org.matrix.vector.manager.ui.screens.web.WebScreen
import org.matrix.vector.manager.ui.screens.web.fetchStoreSubresource
import org.matrix.vector.manager.ui.screens.web.forWebView
import org.matrix.vector.ui.logs.LogTraceScreen
import org.matrix.vector.ui.logs.LogsScreen
import org.matrix.vector.ui.navigation.FloatingPanelNav
import org.matrix.vector.ui.navigation.PanelEditDone
import org.matrix.vector.ui.navigation.PanelFloatingNavigationBar
import org.matrix.vector.ui.navigation.PanelNavigationBar
import org.matrix.vector.ui.navigation.PanelNavigationRail
import org.matrix.vector.ui.navigation.isHorizontal
import org.matrix.vector.ui.navigation.LocalNavigator
import org.matrix.vector.ui.navigation.Navigator
import org.matrix.vector.ui.navigation.rememberNavigator
import org.matrix.vector.ui.store.RepoDetailsScreen
import org.matrix.vector.ui.store.RepoScreen
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import org.matrix.vector.ui.navigation.lens
import org.matrix.vector.ui.navigation.vibrancy
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawRect
import androidx.compose.ui.graphics.shadow.Shadow
import org.matrix.vector.ui.navigation.IosIndicatorSpecular
import org.matrix.vector.ui.navigation.rememberGravityRotatedHighlight
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold

/**
 * The app shell.
 *
 * [NavigationSuiteScaffold] picks the navigation container from the window size — a bottom bar on a
 * phone, a rail when there is width to spare. That is not decoration: from targetSdk 37 an app may
 * no longer lock itself to portrait or declare itself non-resizable on large screens, so the shell
 * has to work unfolded and in landscape regardless. The scaffold also owns where that container
 * sits, so the destinations below it are laid out beside or above it rather than under it.
 *
 * Which panels that container holds, in which order, is the reader's — see NavPanels — and there is
 * a third arrangement it can take, a ball floating over the content with no container at all. The
 * two are not independent: rearranging the panels needs something to rearrange, so edit mode always
 * puts the container back for as long as it lasts.
 */
@Composable
fun VectorApp() {
    val navigator = rememberNavigator(VectorNavPanelStore, TOP_LEVEL_DESTINATIONS)

    // Where the launch intent asked to open. The activity has no back stack to act on, so it leaves
    // the destination here and this is the first place there is one — on a cold start the splash is
    // still playing when the intent arrives.
    val pending by DeepLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val destination = DeepLink.consume() ?: return@LaunchedEffect
        // Already there, so nothing to do — and doing it anyway would not be nothing: switching
        // tabs empties the back stack and builds it again, and the scope editor's draft lives in a
        // ViewModel scoped to the entry that would be thrown away with it. The reader who taps the
        // notification of the module already open in front of them is the case this covers.
        //
        // It is not what keeps a rotation harmless. Whether an offer is a launch or a recreation
        // replaying the intent it was created with is decided in DeepLink, which knows what it last
        // applied; here there is only where the reader is standing.
        if (navigator.current == (destination.detail ?: destination.tab)) return@LaunchedEffect
        // The tab goes down first and the screen on top of it: a notification about a module opens
        // that module's scope editor, and back from there should be the module list rather than the
        // door out of the app it just opened. Switching also discards whatever detail screen was
        // already up, so the reader is not left with a stale one buried underneath.
        navigator.switchTo(destination.tab)
        destination.detail?.let { navigator.go(it) }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        val settings = ServiceLocator.settings
        val floating by settings.floatingNav.collectAsStateWithLifecycle()
        val navBarMode by settings.navBarMode.collectAsStateWithLifecycle()
        val useFloatingBar by settings.useFloatingBar.collectAsStateWithLifecycle()
        val floatingBarStyle by settings.floatingBarStyle.collectAsStateWithLifecycle()
        val floatingBarPosition by settings.floatingBarPosition.collectAsStateWithLifecycle()
        val editing = navigator.editingPanels
        // The container shows only at the root of a panel. On a detail screen none of the items is
        // the current destination, and a navigation bar highlighting nothing is worse than none.
        val atRoot = !navigator.canGoBack

        // Which axis the container runs along is still the suite's answer — it is the one that
        // knows the window — but the container is Miuix's now, so the type only decides whether a
        // bar goes under the content or a rail beside it.
        val horizontal =
            isHorizontal(
                NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
            )
        // Present at the root of a panel, where one of its items is the current destination; on a
        // detail screen a container highlighting nothing is worse than none. The floating style has
        // no container at all — except while rearranging, when there would be nothing to rearrange.
        // Two settings, one of which makes the other moot: while the strip is floating the panels
        // are not in the ball, so the ball is the one that gives way.
        val ball = floating && !useFloatingBar
        val showContainer = atRoot && (!ball || editing)

        // The destinations, and the ball that replaces the container when there is none.
        val content: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = navigator.backStack,
                    onBack = { navigator.back() },
                    // Naming any decorator replaces NavDisplay's default, which is the
                    // saveable-state one alone, so it is repeated here; the scene-setup decorator
                    // NavDisplay applies internally is untouched. The ViewModel one is what this
                    // list is for: it scopes a ViewModelStore per entry, so opening the scope
                    // editor for a second module builds a second ViewModel instead of reusing the
                    // first (they would otherwise share one default key under the activity's
                    // store).
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    entryProvider = entryProvider { registerRoutes(navigator) },
                )
                // Last child of the Box so it draws over the destination, and inside the app window
                // rather than in one of its own: parasitically this app is com.android.shell, which
                // must never ask for SYSTEM_ALERT_WINDOW. It follows the same rule the container
                // does — present at the root of a panel, gone on a detail screen that has its own
                // back affordance.
                if (ball && !editing && atRoot) {
                    FloatingPanelNav(
                        panels = navigator.panels,
                        current = navigator.currentTopLevel,
                        onSelect = { route -> navigator.switchTo(route) },
                        settings = VectorFloatingNavSettings,
                    )
                }
            }
        }

        // The glass strip needs to see what is behind it, which in Compose means one thing: the
        // destination has to be drawn into a layer the bar can then sample. That layer is the
        // cost of the effect -- the whole screen is composited once more -- so it is created only
        // when the strip that needs it is actually on screen, and only where the runtime shaders
        // exist. Below API 33 the same setting draws the flat capsule it always drew.
        val glass = useFloatingBar && floatingBarStyle == 1 && isRuntimeShaderSupported()
        val glassBackdrop = rememberLayerBackdrop()
        val glassSurface = MiuixTheme.colorScheme.surfaceContainer
        // The effect block is not a density scope, and it runs per frame: the radii are converted
        // once here rather than on every draw.
        val glassHighlight = rememberGravityRotatedHighlight(IosIndicatorSpecular, extraDegrees = -45f)
        val glassDark = isSystemInDarkTheme()
        val glassRadius = with(LocalDensity.current) { 4.dp.toPx() }
        val glassRefraction = with(LocalDensity.current) { 24.dp.toPx() }
        val glassPadding = with(LocalDensity.current) { 40.dp.toPx() }

        if (horizontal) {
            // Miuix's own scaffold rather than a column of ours. A column shares the window's
            // height between its children, and a bar that fills the width and asks for no
            // particular height of its own is handed every pixel that is going: the bar ends up
            // filling the screen with the panels at the top of it and the destination nowhere.
            // The scaffold subcomposes the bar, measures it with loose constraints and places it
            // at the bottom of the window, which is what a bar that "floats" needs and what the
            // library's own examples do.
            MiuixScaffold(
                bottomBar = {
                    if (showContainer && !useFloatingBar) {
                        Column {
                            if (editing) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PanelEditDone(onDone = { navigator.editingPanels = false })
                                }
                            }
                            NavigationBar(
                                // Whatever the preference holds, it is an index into Miuix's enum,
                                // and the enum is theirs to lengthen: an unknown one falls back to
                                // the first rather than taking the bar down with it.
                                mode = NavigationBarDisplayMode.entries.getOrElse(navBarMode) {
                                    NavigationBarDisplayMode.IconAndText
                                },
                            ) {
                                PanelNavigationBar(
                                    panels = navigator.panels,
                                    current = navigator.currentTopLevel,
                                    editing = editing,
                                    onSelect = { route -> navigator.switchTo(route) },
                                    onEdit = { navigator.editingPanels = true },
                                    onToggleHidden = { key, hidden -> navigator.setPanelHidden(key, hidden) },
                                    onMove = { from, to -> navigator.movePanel(from, to) },
                                )
                            }
                        }
                    }
                },
                // The floating strip is a floating toolbar as far as the scaffold is concerned: it
                // belongs over the content, not under it, and the scaffold places it there and
                // keeps the snackbar clear of it.
                floatingToolbar = {
                    if (showContainer && useFloatingBar) {
                        Column(Modifier.fillMaxWidth()) {
                            if (editing) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PanelEditDone(onDone = { navigator.editingPanels = false })
                                }
                            }
                            FloatingNavigationBar(
                                // A capsule has no alignment of its own; a corner radius this large
                                // is what makes the strip one — and it is what the default already
                                // is at this height, so the radius is not where the two styles
                                // differ. The height and the pill behind the current tab are.
                                modifier =
                                    (if (floatingBarStyle == 1) Modifier.height(IosBarHeight)
                                    else Modifier)
                                    .then(
                                    if (glass) {
                                        Modifier
                                            // The strip is a sheet of glass lifted off the screen,
                                            // and it is the shadow underneath that says so; without
                                            // it the refraction reads as a smudge on the surface.
                                            .dropShadow(
                                                shape = GlassShape,
                                                shadow = Shadow(
                                                    radius = 10.dp,
                                                    color = Color.Black,
                                                    alpha = if (glassDark) 0.2f else 0.1f,
                                                ),
                                            )
                                            .drawBackdrop(
                                            backdrop = glassBackdrop,
                                            shape = { GlassShape },
                                            effects = {
                                                // Wide enough for the lens to reach outside the
                                                // shape; it sets its own floor but the blur needs
                                                // the same margin to have anything to read.
                                                // The lens reaches outside the shape for the
                                                // pixels it bends inwards, so the margin has to
                                                // cover that before it covers the blur.
                                                padding = maxOf(padding, glassPadding)
                                                vibrancy()
                                                blur(glassRadius, glassRadius)
                                                lens(
                                                    refractionHeight = glassRefraction,
                                                    refractionAmount = glassRefraction,
                                                )
                                            },
                                            // The rim: a stroke lit from where the light would
                                            // come from, rotated as the device tilts. It is the
                                            // part that makes an edge look like an edge rather
                                            // than a rounded rectangle.
                                            highlight = { glassHighlight.value.copy(alpha = 0.75f) },
                                            // A tinted sheet over the refraction, so the strip
                                            // keeps a colour of its own and stays legible over
                                            // whatever happens to be scrolling behind it.
                                            onDrawSurface = { drawRect(glassSurface.copy(alpha = 0.4f)) },
                                        )
                                    } else {
                                        Modifier
                                    }),
                                cornerRadius =
                                    if (floatingBarStyle == 1) 100.dp
                                    else FloatingToolbarDefaults.CornerRadius,
                                horizontalAlignment =
                                    when (floatingBarPosition) {
                                        1 -> Alignment.Start
                                        2 -> Alignment.End
                                        else -> Alignment.CenterHorizontally
                                    },
                            ) {
                                PanelFloatingNavigationBar(
                                    panels = navigator.panels,
                                    current = navigator.currentTopLevel,
                                    editing = editing,
                                    onSelect = { route -> navigator.switchTo(route) },
                                    onEdit = { navigator.editingPanels = true },
                                    onToggleHidden = { key, hidden -> navigator.setPanelHidden(key, hidden) },
                                    onMove = { from, to -> navigator.movePanel(from, to) },
                                    iosStyle = floatingBarStyle == 1,
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .then(if (glass) Modifier.layerBackdrop(glassBackdrop) else Modifier)
                ) {
                    content()
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                if (showContainer) {
                    NavigationRail(
                        header = {
                            if (editing) PanelEditDone(onDone = { navigator.editingPanels = false })
                        },
                    ) {
                        PanelNavigationRail(
                            panels = navigator.panels,
                            current = navigator.currentTopLevel,
                            editing = editing,
                            onSelect = { route -> navigator.switchTo(route) },
                            onEdit = { navigator.editingPanels = true },
                            onToggleHidden = { key, hidden -> navigator.setPanelHidden(key, hidden) },
                            onMove = { from, to -> navigator.movePanel(from, to) },
                        )
                    }
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }

        // After the scaffold on purpose. Back callbacks are dispatched last-registered-first and
        // BackHandler registers from an effect, which run in composition order, so this one
        // outranks the handler NavDisplay installs and edit mode ends before the stack is touched.
        BackHandler(enabled = editing) { navigator.editingPanels = false }
    }
}

/**
 * Every destination, registered.
 *
 * All four panels keep their entry whether or not the reader has hidden them. A saved stack names
 * its keys by class, and entryProvider throws for one it was never given, so dropping the
 * registration of a hidden panel would turn a stale saved stack into a crash.
 */
private fun EntryProviderScope<NavKey>.registerRoutes(navigator: Navigator) {
    entry<TopLevelRoute.Home> {
        HomeScreen(
            onOpenStatus = { navigator.go(SystemStatus) },
            onOpenUrl = { url -> navigator.go(Web(url)) },
            onOpenCanary = { navigator.go(Canary) },
            onOpenReport = { navigator.go(Troubleshoot) },
            onOpenUpdate = { navigator.go(FrameworkUpdate()) },
            onOpenActivity = { navigator.go(Activity) },
        )
    }
    entry<Activity> {
        ActivityScreen(
            onNavigateBack = { navigator.back() },
            onOpenUrl = { url -> navigator.go(Web(url)) },
            onOpenProfile = { c -> navigator.go(Web(c.profileUrl ?: GitHubRepository.REPO_URL)) },
        )
    }
    entry<TopLevelRoute.Modules> {
        ModulesScreen(
            onModuleClick = { packageName, userId -> navigator.go(Scope(packageName, userId)) },
            onOpenStore = { packageName -> navigator.go(StoreDetail(packageName)) },
        )
    }
    entry<TopLevelRoute.Store> {
        RepoScreen(
            onModuleClick = { packageName -> navigator.go(StoreDetail(packageName)) },
            dataSource = ServiceLocator.store,
            settings = ServiceLocator.settings,
        )
    }
    entry<TopLevelRoute.Logs> {
        val logSource = remember { VectorLogSource() }
        LogsScreen(source = logSource, onOpenTrace = { text -> navigator.go(LogTrace(text)) })
    }

    entry<Scope> { route ->
        ScopeScreen(
            packageName = route.packageName,
            userId = route.userId,
            onNavigateBack = { navigator.back() },
        )
    }
    entry<StoreDetail> { route ->
        RepoDetailsScreen(
            packageName = route.packageName,
            onNavigateBack = { navigator.back() },
            onOpenUrl = { url -> navigator.go(Web(url)) },
            dataSource = ServiceLocator.store,
            settings = ServiceLocator.settings,
            host = remember(route.packageName) { VectorStoreInstallHost(route.packageName) },
            fetchSubresource = { fetchStoreSubresource(ServiceLocator.http, it) },
            contextForWebView = { ctx, dark -> ctx.forWebView(dark) },
        )
    }
    entry<SystemStatus> {
        SystemStatusScreen(
            onNavigateBack = { navigator.back() },
            onOpenCrash = { navigator.go(CrashTrace) },
        )
    }
    entry<CrashTrace> { CrashTraceScreen(onNavigateBack = { navigator.back() }) }
    entry<LogTrace> { route ->
        LogTraceScreen(text = route.text, onNavigateBack = { navigator.back() })
    }
    entry<Troubleshoot> {
        TroubleshootScreen(
            onNavigateBack = { navigator.back() },
            onOpenUrl = { url -> navigator.go(Web(url)) },
            onOpenCanary = { navigator.go(Canary) },
        )
    }
    entry<Canary> {
        CanaryScreen(
            onNavigateBack = { navigator.back() },
            onOpenUrl = { url -> navigator.go(Web(url)) },
            onInstall = { versionCode -> navigator.go(FrameworkUpdate(versionCode)) },
            onOpenReport = { navigator.go(Troubleshoot) },
        )
    }
    entry<FrameworkUpdate> { route ->
        FrameworkUpdateScreen(
            openOnVersionCode = route.versionCode.takeIf { it > 0 },
            onNavigateBack = { navigator.back() },
            onOpenUrl = { url -> navigator.go(Web(url)) },
        )
    }
    entry<Web> { route -> WebScreen(url = route.url, onNavigateBack = { navigator.back() }) }
}

/** The strip in the iOS arrangement is taller than the default one. */
private val IosBarHeight = 64.dp

/** The silhouette the glass is cut to: a capsule, matching the strip's own radius. */
private val GlassShape = RoundedCornerShape(50.dp)
