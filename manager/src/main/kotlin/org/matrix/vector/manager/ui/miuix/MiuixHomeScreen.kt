package org.matrix.vector.manager.ui.miuix

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.matrix.vector.manager.R
import org.matrix.vector.manager.data.github.CommunityFeed
import org.matrix.vector.manager.data.github.FeedItem
import org.matrix.vector.manager.data.github.GitHubRepository
import org.matrix.vector.manager.ui.components.ContributorAvatar
import org.matrix.vector.manager.ui.components.TakePartSection
import org.matrix.vector.manager.ui.components.VectorAmbienceSettings
import org.matrix.vector.manager.ui.components.statusWordRes
import org.matrix.vector.manager.ui.components.toTone
import org.matrix.vector.manager.ui.screens.home.HomeAppearanceSheet
import org.matrix.vector.manager.ui.screens.home.HomeViewModel
import org.matrix.vector.manager.ui.screens.splash.WingedVictory
import org.matrix.vector.ui.RepoStatsRow
import org.matrix.vector.ui.StatusHeader
import org.matrix.vector.ui.UpdatableVersion
import org.matrix.vector.ui.ambience.AmbienceKind
import org.matrix.vector.ui.locale.LanguageSheet
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Taps must land within this window of each other to count towards the same run. */
private const val BRAND_TAP_WINDOW_MS = 2600L

private const val BRAND_TAPS_TO_SUMMON = 4

/**
 * Home, written against miuix rather than Material.
 *
 * A rewrite rather than an edit of `HomeScreen`: the two are meant to be looked at side by side,
 * so the old one stays intact and reachable. Both read the same [HomeViewModel], so nothing here
 * carries state of its own beyond what a screen needs in order to draw.
 *
 * The header is the one piece kept from the shared library. It draws its own status-bar inset and
 * its ambience, and none of that has a miuix equivalent worth trading it for.
 */
@Composable
fun MiuixHomeScreen(
    onOpenStatus: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenCanary: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenActivity: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val ambienceKey by viewModel.headerAmbience.collectAsStateWithLifecycle()
    val frameworkUpdate by viewModel.frameworkUpdate.collectAsStateWithLifecycle()
    val hintStatus by viewModel.statusBadgeHint.collectAsStateWithLifecycle()

    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()

    var showAppearance by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(false) }
    var brandTaps by remember { mutableIntStateOf(0) }
    var lastBrandTap by remember { mutableLongStateOf(0L) }

    val twoMore = stringResource(R.string.egg_two_more)
    val oneMore = stringResource(R.string.egg_one_more)
    // The repository the stats belong to. Not a string resource: every locale shows the
    // same address.
    val repoUrl = "https://github.com/xingmihai/Vector"

    LaunchedEffect(Unit) {
        viewModel.refreshPresence()
        viewModel.refreshStatusBadgeHint()
    }

    fun onBrandTap() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBrandTap > BRAND_TAP_WINDOW_MS) brandTaps = 0
        lastBrandTap = now
        brandTaps++
        when {
            brandTaps >= BRAND_TAPS_TO_SUMMON -> {
                brandTaps = 0
                showSplash = true
            }
            brandTaps == 2 -> scope.launch { snackbars.showSnackbar(twoMore) }
            brandTaps == 3 -> scope.launch { snackbars.showSnackbar(oneMore) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        enabled = !refreshing,
                        onClick = { viewModel.refreshFeed(GitHubRepository.Freshness.Force) },
                    ) {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refreshFeed(GitHubRepository.Freshness.Force) },
            topAppBarScrollBehavior = scrollBehavior,
            contentPadding = padding,
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    StatusHeader(
                        brand = stringResource(R.string.app_name),
                        statusWord = stringResource(status.state.statusWordRes()),
                        tone = status.state.toTone(),
                        ambience = AmbienceKind.from(ambienceKey),
                        ambienceSettings = VectorAmbienceSettings,
                        statusContentDescription = stringResource(R.string.status_open_details),
                        hintStatus = hintStatus,
                        onOpenStatus = {
                            // Counted before the navigation, not after arriving: the tap is what
                            // proves the badge was understood, and the page has other ways in that
                            // prove nothing.
                            viewModel.noteStatusBadgeOpened()
                            onOpenStatus()
                        },
                        appearanceLabel = stringResource(R.string.appearance_title),
                        onOpenAppearance = { showAppearance = true },
                        languageLabel = stringResource(R.string.language_title),
                        onOpenLanguage = { showLanguage = true },
                        onBrandTap = ::onBrandTap,
                        detail = { contentColor ->
                            val detailText =
                                buildList {
                                        status.versionLabel?.let { add(it) }
                                        status.apiVersion?.let { add("API $it") }
                                    }
                                    .joinToString("  ·  ")
                            if (detailText.isNotEmpty()) {
                                // Tappable whether or not there is an update, so "you are up to
                                // date" stays reachable.
                                UpdatableVersion(
                                    text = detailText,
                                    hasUpdate = frameworkUpdate.hasUpdate,
                                    color = contentColor.copy(alpha = 0.75f),
                                    markColor = contentColor,
                                    modifier =
                                        Modifier.clickable(
                                            interactionSource =
                                                remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onOpenUpdate,
                                        ),
                                )
                            }
                        },
                    )
                }

                item {
                    TakePartSection(
                        onOpen = onOpenUrl,
                        onCanary = onOpenCanary,
                        onReport = onOpenReport,
                    )
                }

                item {
                    val repo = feed.repo
                    if (repo != null) {
                        RepoStatsRow(
                            stars = repo.stars,
                            forks = repo.forks,
                            openIssues = repo.openIssues,
                            license = repo.license?.spdxId,
                            onClick = { onOpenUrl(repoUrl) },
                        )
                    }
                }

                item { ActivityPreview(feed = feed, items = feedItems, onClick = onOpenActivity) }
            }
        }
    }

    if (showAppearance) {
        HomeAppearanceSheet(onDismiss = { showAppearance = false })
    }
    if (showLanguage) {
        LanguageSheet(onDismiss = { showLanguage = false })
    }
    if (showSplash) {
        Dialog(
            onDismissRequest = { showSplash = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .background(MiuixTheme.colorScheme.background)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            showSplash = false
                        },
            ) {
                WingedVictory()
            }
            LaunchedEffect(Unit) {
                delay(2800)
                showSplash = false
            }
        }
    }
}

/**
 * Three rows and a way in, rather than the rail itself: the rail is open-ended, and as the last
 * thing on Home it made everything after it unreachable. It was also the only part of Home that
 * needed the network, so offline the page ended here with nothing to read.
 */
@Composable
private fun ActivityPreview(
    feed: CommunityFeed,
    items: List<FeedItem>,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_quarter_title),
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            val recent = items.filterIsInstance<FeedItem.Commit>().take(3)
            if (recent.isEmpty()) {
                Text(
                    text =
                        stringResource(
                            if (feed.loaded) R.string.home_no_activity
                            else R.string.home_loading_activity
                        ),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                recent.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContributorAvatar(
                            login = entry.commit.authorLogin,
                            avatarUrl = entry.commit.authors.firstOrNull()?.avatarUrl,
                            size = 20.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            // The subject, not the hash: a hash is a reference for someone who
                            // already knows the commit, and this row is for someone deciding
                            // whether to look.
                            text = entry.commit.subject,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
