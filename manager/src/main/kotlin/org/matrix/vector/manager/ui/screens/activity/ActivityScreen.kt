package org.matrix.vector.manager.ui.screens.activity

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.matrix.vector.manager.R
import org.matrix.vector.manager.data.github.Contributor
import org.matrix.vector.manager.ui.components.ContributorAvatar
import org.matrix.vector.manager.data.github.GitHubRepository
import org.matrix.vector.manager.data.github.TimelineCommit
import org.matrix.vector.manager.ui.screens.home.HomeViewModel
import org.matrix.vector.manager.ui.screens.home.communitySection
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar

/**
 * The activity rail, with the whole screen to itself.
 *
 * This was the lower half of Home. That put an open-ended list — six months can be a hundred
 * rows — after everything short and actionable, so anything placed below it went unread; and it
 * was the only part of Home that needed the network, so offline the page ended in a blank.
 *
 * Here it has the height, its own pull-to-refresh, and its own ViewModel. That last one is
 * deliberate: a nav destination is its own store, so this screen owns a feed rather than
 * borrowing Home's — which means it can be entered cold, from anywhere, and still load.
 */
@Composable
fun ActivityScreen(
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenProfile: (Contributor) -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()
    val loadingHistory by viewModel.loadingHistory.collectAsStateWithLifecycle()
    val historyStalled by viewModel.historyStalled.collectAsStateWithLifecycle()
    val authorFilter by viewModel.authorFilter.collectAsStateWithLifecycle()
    val windowChanged by viewModel.windowChanged.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Long-pressing a name three hundred rows down changes the whole rail underneath the reader,
    // so ride back up to the headline: that is where the count and the chips are.
    LaunchedEffect(authorFilter) {
        if (authorFilter.isNotEmpty() && listState.firstVisibleItemIndex > 1) {
            listState.animateScrollToItem(1)
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.home_quarter_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { insets: PaddingValues ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refreshFeed(GitHubRepository.Freshness.Force) },
            modifier = Modifier.fillMaxSize().padding(insets),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            ) {
                communitySection(
                    feed = feed,
                    items = feedItems,
                    loadingHistory = loadingHistory,
                    historyStalled = historyStalled,
                    windowChanged = windowChanged,
                    authorFilter = authorFilter,
                    onLoadMoreHistory = viewModel::loadMoreHistory,
                    onToggleAuthor = viewModel::toggleAuthorFilter,
                    onClearAuthors = viewModel::clearAuthorFilter,
                    onOpenCommit = { c -> onOpenUrl(c.htmlUrl ?: GitHubRepository.REPO_URL) },
                    onOpenPullRequest = { pr -> onOpenUrl("${GitHubRepository.REPO_URL}/pull/$pr") },
                    onOpenProfile = onOpenProfile,
                )
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

