package org.matrix.vector.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import org.matrix.vector.ui.navigation.PanelEditDone
import org.matrix.vector.ui.navigation.rememberNavigator
import org.matrix.vector.ui.store.RepoDetailsScreen
import org.matrix.vector.ui.store.RepoScreen
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationRail

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

        if (horizontal) {
            // The floating strip is not a row of the column. Miuix draws it over the content — its
            // own column fills the width and then asks for whatever height its items need, which
            // inside a [Column] that is handing out the window resolves to all of it: the strip
            // takes the whole screen and the panels sit at the top of it with nothing underneath.
            // Given a layer of its own it keeps the size it asks for and the content keeps the
            // window, which is what "floating" means here and in the library's own examples.
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { content() }
                    if (showContainer && !useFloatingBar) {
                        if (editing) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                PanelEditDone(onDone = { navigator.editingPanels = false })
                            }
                        }
                        NavigationBar(
                            // Whatever the preference holds, it is an index into Miuix's enum, and
                            // the enum is theirs to lengthen: an unknown one falls back to the
                            // first rather than taking the bar down with it.
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
                // Last child, so it draws over the content the way the ball does.
                if (showContainer && useFloatingBar) {
                    Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                        if (editing) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                PanelEditDone(onDone = { navigator.editingPanels = false })
                            }
                        }
                        FloatingNavigationBar(
                            // A capsule has no alignment of its own; a corner radius this large is
                            // what makes the strip one.
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
                            )
                        }
                    }
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
