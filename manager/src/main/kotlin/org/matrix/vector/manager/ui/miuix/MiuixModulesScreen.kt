package org.matrix.vector.manager.ui.miuix

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.matrix.vector.manager.R
import org.matrix.vector.manager.data.InstalledModule
import org.matrix.vector.manager.data.ModuleFacts
import org.matrix.vector.manager.data.ModuleFilter
import org.matrix.vector.manager.data.ModuleKey
import org.matrix.vector.manager.data.ModuleSort
import org.matrix.vector.manager.ui.screens.modules.BatchOutcome
import org.matrix.vector.manager.ui.screens.modules.ModuleFilter
import org.matrix.vector.manager.ui.screens.modules.ModuleFacts
import org.matrix.vector.manager.ui.screens.modules.ModuleKey
import org.matrix.vector.manager.ui.screens.modules.ModuleSort
import org.matrix.vector.manager.ui.screens.modules.ModulesViewModel
import org.matrix.vector.manager.ui.screens.modules.ModulesViewModelFactory
import org.matrix.vector.manager.ui.screens.modules.UserModulesState
import org.matrix.vector.ui.ApiBadge
import org.matrix.vector.ui.AppIcon
import org.matrix.vector.ui.ModuleRow
import org.matrix.vector.ui.SearchField
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val ICON_SIZE = 48.dp

private val REACH_ICON_SIZE = 16.dp

/**
 * Modules, written against miuix rather than Material.
 *
 * A new screen rather than a rewrite of `ModulesScreen`, for the same reason as Home: the two are
 * meant to be compared, so the old one stays intact. Both read the same [ModulesViewModel], so
 * every action here is the same action the other screen performs.
 *
 * The row itself is still the shared one. It is a drawing, not a theme — it takes an icon, a name
 * and a badge and lays them out, and what it is made of never shows. Rewriting it would risk
 * losing the reach strip and the load-failure note for no visible gain.
 */
@Composable
fun MiuixModulesScreen(
    onModuleClick: (packageName: String, userId: Int) -> Unit,
    onOpenStore: (packageName: String) -> Unit,
    viewModel: ModulesViewModel = viewModel(factory = ModulesViewModelFactory()),
) {
    val tabs by viewModel.userModulesTabs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val facts by viewModel.facts.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val daemonAvailable by viewModel.daemonAvailable.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val upgradable by viewModel.upgradable.collectAsStateWithLifecycle()
    val updateQueue by viewModel.updateQueue.collectAsStateWithLifecycle()

    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()

    var confirmUninstall by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var showFilter by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }

    val backedUp = stringResource(R.string.modules_backup_done)
    val backupFailed = stringResource(R.string.modules_backup_failed)
    val restored = stringResource(R.string.modules_restore_done)
    val restoreFailed = stringResource(R.string.modules_restore_failed)

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) {
            uri ->
            if (uri != null) {
                viewModel.backupTo(uri) { count ->
                    scope.launch {
                        snackbars.showSnackbar(
                            if (count != null) String.format(backedUp, count) else backupFailed
                        )
                    }
                }
            }
        }
    val selectionBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) {
            uri ->
            if (uri != null) {
                viewModel.backupSelectedTo(uri) { count ->
                    scope.launch {
                        snackbars.showSnackbar(
                            if (count != null) String.format(backedUp, count) else backupFailed
                        )
                    }
                }
            }
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.restoreFrom(uri) { outcome ->
                    scope.launch {
                        snackbars.showSnackbar(
                            if (outcome != null)
                                String.format(restored, outcome.restored, outcome.skipped)
                            else restoreFailed
                        )
                    }
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                // The count is the visible profile's. Aggregating across profiles made "4 of 6
                // active" describe a set the user was not looking at.
                title =
                    if (selection.isEmpty()) {
                        val visible = tabs.getOrNull(page)
                        val active = visible?.modules?.count { it.isEnabled } ?: counts.first
                        val total = visible?.modules?.size ?: counts.second
                        stringResource(R.string.modules_active_of, active, total)
                    } else {
                        pluralStringResource(R.plurals.modules_selected, selection.size, selection.size)
                    },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (selection.isEmpty()) {
                        IconButton(onClick = { backupLauncher.launch("vector-modules.bak") }) {
                            Icon(Icons.Rounded.Backup, contentDescription = null)
                        }
                        IconButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Rounded.Restore, contentDescription = null)
                        }
                    } else {
                        IconButton(onClick = { selectionBackupLauncher.launch("vector-modules.bak") }) {
                            Icon(Icons.Rounded.Backup, contentDescription = null)
                        }
                        IconButton(onClick = { confirmUninstall = true }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.error,
                            )
                        }
                        IconButton(onClick = viewModel::clearSelection) {
                            Text(text = stringResource(org.matrix.vector.ui.R.string.logs_cancel))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                query = query,
                onQueryChange = viewModel::setQuery,
                placeholder = stringResource(R.string.modules_search_hint),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                trailing = {
                    IconButton(onClick = { showFilter = true }) {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = stringResource(R.string.modules_filter),
                        )
                    }
                    IconButton(onClick = { showSort = true }) {
                        Icon(Icons.Rounded.Sort, contentDescription = null)
                    }
                },
            )

            if (tabs.isEmpty() || tabs.all { it.modules.isEmpty() }) {
                // A filter empties the list exactly as a search does, so both count as narrowing.
                EmptyState(
                    daemonAvailable = daemonAvailable,
                    filtered = query.isNotBlank() || filter != ModuleFilter.All,
                )
                return@Column
            }

            if (tabs.size > 1) {
                TabRow(
                    tabs = tabs.map { it.user.label },
                    selectedTabIndex = page,
                    onTabSelected = { page = it },
                )
            }

            val modules = tabs.getOrNull(page)?.modules.orEmpty()
            PullToRefresh(
                isRefreshing = isLoading,
                onRefresh = viewModel::loadModules,
                topAppBarScrollBehavior = scrollBehavior,
                contentPadding = PaddingValues(0.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    if (updateQueue.isNotEmpty() || modules.any { it.packageName in upgradable }) {
                        item(key = "updates") {
                            UpdateLine(
                                updates = modules.count { it.packageName in upgradable },
                                onClick = viewModel::acknowledgeUpdates,
                            )
                        }
                    }
                    // Sections only make sense when the order is by state. Under any other sort the
                    // groups would interleave, and a header that lies about what follows it is
                    // worse than no header.
                    val sectioned = sort == ModuleSort.EnabledFirst && query.isBlank()
                    if (sectioned) {
                        val active = modules.filter { it.isEnabled }
                        val inactive = modules.filterNot { it.isEnabled }
                        if (active.isNotEmpty()) {
                            item(key = "h:active") {
                                SectionHeader(
                                    stringResource(R.string.modules_section_active),
                                    active.size,
                                )
                            }
                            rows(
                                modules = active,
                                facts = facts,
                                selection = selection,
                                upgradable = upgradable,
                                onModuleClick = onModuleClick,
                                onOpenStore = onOpenStore,
                                onSelect = viewModel::toggleSelected,
                            )
                        }
                        if (inactive.isNotEmpty()) {
                            item(key = "h:inactive") {
                                SectionHeader(
                                    stringResource(R.string.modules_section_inactive),
                                    inactive.size,
                                )
                            }
                            rows(
                                modules = inactive,
                                facts = facts,
                                selection = selection,
                                upgradable = upgradable,
                                onModuleClick = onModuleClick,
                                onOpenStore = onOpenStore,
                                onSelect = viewModel::toggleSelected,
                            )
                        }
                    } else {
                        rows(
                            modules = modules,
                            facts = facts,
                            selection = selection,
                            upgradable = upgradable,
                            onModuleClick = onModuleClick,
                            onOpenStore = onOpenStore,
                            onSelect = viewModel::toggleSelected,
                        )
                    }
                }
            }
        }
    }

    if (confirmUninstall) {
        UninstallDialog(
            count = selection.size,
            onDismiss = { confirmUninstall = false },
            onConfirm = {
                confirmUninstall = false
                viewModel.uninstallSelected { outcome ->
                    scope.launch {
                        snackbars.showSnackbar(
                            batchResult(
                                outcome = outcome,
                                doneRes = R.plurals.modules_batch_uninstalled,
                                alreadyRes = R.plurals.modules_batch_uninstalled,
                                allAlreadyRes = R.plurals.modules_batch_uninstalled,
                            )
                        )
                    }
                }
            },
        )
    }
    if (showFilter) {
        ChoiceDialog(
            options = ModuleFilter.entries.map { stringResource(it.labelRes()) },
            selectedIndex = ModuleFilter.entries.indexOf(filter),
            onSelect = { viewModel.setFilter(ModuleFilter.entries[it]) },
            onDismiss = { showFilter = false },
        )
    }
    if (showSort) {
        ChoiceDialog(
            options = ModuleSort.entries.map { stringResource(it.labelRes()) },
            selectedIndex = ModuleSort.entries.indexOf(sort),
            onSelect = { viewModel.setSort(ModuleSort.entries[it]) },
            onDismiss = { showSort = false },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rows(
    modules: List<InstalledModule>,
    facts: Map<ModuleKey, ModuleFacts>,
    selection: Set<ModuleKey>,
    upgradable: Set<String>,
    onModuleClick: (String, Int) -> Unit,
    onOpenStore: (String) -> Unit,
    onSelect: (InstalledModule) -> Unit,
) {
    items(modules, key = { "${it.packageName}:${it.userId}" }) { module ->
        ModuleRow(
            module = module,
            facts = facts[ModuleKey(module.packageName, module.userId)],
            hasUpdate = module.packageName in upgradable,
            selected = ModuleKey(module.packageName, module.userId) in selection,
            selectionActive = selection.isNotEmpty(),
            onModuleClick = onModuleClick,
            onOpenStore = onOpenStore,
            onSelect = onSelect,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 108.dp, end = 32.dp))
    }
}

/**
 * One module.
 *
 * **The icon is the selection handle.** Tapping it picks the module up; once anything is held the
 * whole row joins the selection, which is what makes enabling, removing or backing up eight
 * modules one act rather than eight.
 */
@Composable
private fun ModuleRow(
    module: InstalledModule,
    facts: ModuleFacts?,
    hasUpdate: Boolean,
    selected: Boolean,
    selectionActive: Boolean,
    onModuleClick: (String, Int) -> Unit,
    onOpenStore: (String) -> Unit,
    onSelect: (InstalledModule) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val incompatible = facts?.incompatible == true
    val nameColor by
        animateColorAsState(
            when {
                incompatible -> scheme.error
                module.isEnabled -> scheme.primary
                else -> scheme.onSurfaceVariant
            },
            label = "moduleNameColor",
        )

    org.matrix.vector.ui.ModuleRow(
        icon = {
            AppIcon(
                applicationInfo = module.applicationInfo,
                contentDescription = null,
                size = ICON_SIZE,
            )
        },
        name = module.appName,
        versionName = module.versionName,
        description = module.description,
        apiBadge = {
            val undeclared = !module.declaresApiVersion
            ApiBadge(
                label =
                    stringResource(
                        if (module.isLegacy) R.string.modules_api_scale_legacy
                        else R.string.modules_api_scale_modern
                    ),
                // An undeclared value shows "?" and takes the error treatment, the same as an
                // incompatible one: a missing number is not a different kind of thing from a
                // wrong one.
                value = if (undeclared) "?" else module.apiVersion.toString(),
                incompatible = incompatible || undeclared,
            )
        },
        nameColor = nameColor,
        hasUpdate = hasUpdate,
        onVersionClick = if (hasUpdate) onOpenStore else null,
        dimmed = !module.isEnabled && !incompatible,
        selected = selected,
        onIconClick = { onSelect(module) },
        onClick = { if (selectionActive) onSelect(module) else onModuleClick(module.packageName, module.userId) },
        onLongClick = { onSelect(module) },
        reachLeading =
            if (facts?.scopeFramework == true) {
                {
                    Icon(
                        Icons.Rounded.Android,
                        contentDescription = stringResource(R.string.modules_scope_framework),
                        tint = scheme.primary,
                        modifier = Modifier.size(REACH_ICON_SIZE),
                    )
                }
            } else null,
        reachIcons =
            facts?.scopePreview.orEmpty().map { info ->
                { AppIcon(applicationInfo = info, contentDescription = null, size = REACH_ICON_SIZE) }
            },
        reachCount = (facts?.scopeCount ?: 0).coerceAtLeast(0),
    )
}

@Composable
private fun UpdateLine(updates: Int, onClick: () -> Unit) {
    if (updates <= 0) return
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = pluralStringResource(R.plurals.modules_updates, updates, updates),
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, color = MiuixTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(text = "$count", color = MiuixTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(daemonAvailable: Boolean, filtered: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.Extension,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    stringResource(
                        when {
                            !daemonAvailable -> R.string.modules_no_daemon
                            filtered -> R.string.modules_no_match
                            else -> R.string.modules_empty
                        }
                    ),
                color = MiuixTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A plain choice, drawn in a dialog: miuix carries no menu that drops from an icon. */
@Composable
private fun ChoiceDialog(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .let {
                                    if (index == selectedIndex) it else it
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option,
                            color =
                                if (index == selectedIndex) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { onSelect(index); onDismiss() }) {
                            Text(text = stringResource(org.matrix.vector.ui.R.string.logs_ok))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UninstallDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = stringResource(R.string.modules_uninstall_title), color = MiuixTheme.colorScheme.error)
                Spacer(Modifier.height(10.dp))
                Text(
                    // Names the consequence rather than asking "are you sure". The backup on this
                    // screen holds the enabled flag and the scope; the module's own stored settings
                    // go with it and nothing here can bring them back.
                    text = pluralStringResource(R.plurals.modules_uninstall_body, count, count),
                    color = MiuixTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) {
                        Text(text = stringResource(org.matrix.vector.ui.R.string.logs_cancel))
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onConfirm) {
                        Text(
                            text = stringResource(R.string.action_uninstall),
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One line for a batch that changed some modules and failed on others.
 *
 * The failure case is why this is not simply the "done" string: a partial batch would otherwise
 * report the successes and leave the rest unaccounted for.
 */
@Composable
private fun batchResult(
    outcome: BatchOutcome,
    doneRes: Int,
    alreadyRes: Int,
    allAlreadyRes: Int,
): String {
    val done = pluralStringResource(doneRes, outcome.changed, outcome.changed)
    val already = pluralStringResource(alreadyRes, outcome.already, outcome.already)
    return when {
        outcome.failed > 0 ->
            String.format(
                stringResource(R.string.modules_batch_partial),
                outcome.changed,
                outcome.changed + outcome.failed,
            )
        outcome.changed == 0 && outcome.already > 0 ->
            pluralStringResource(allAlreadyRes, outcome.already, outcome.already)
        outcome.already > 0 -> "$done · $already"
        else -> done
    }
}

private fun ModuleFilter.labelRes(): Int =
    when (this) {
        ModuleFilter.All -> R.string.modules_filter_all
        ModuleFilter.Active -> R.string.modules_filter_active
        ModuleFilter.Inactive -> R.string.modules_filter_inactive
    }

private fun ModuleSort.labelRes(): Int =
    when (this) {
        ModuleSort.EnabledFirst -> R.string.modules_sort_enabled
        ModuleSort.Name -> R.string.modules_sort_name
        ModuleSort.RecentlyUpdated -> R.string.modules_sort_recent
        ModuleSort.WidestScope -> R.string.modules_sort_scope
    }
