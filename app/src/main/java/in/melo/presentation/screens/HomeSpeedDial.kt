package `in`.melo.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import `in`.melo.R
import `in`.melo.data.HomeRecommendationPick
import `in`.melo.data.Music
import `in`.melo.data.YouTubeHomeChip
import `in`.melo.presentation.components.SmartImage
import `in`.melo.presentation.components.MeloIconButton
import `in`.melo.presentation.components.artworkRim
import `in`.melo.presentation.components.homePress
import `in`.melo.presentation.components.LocalHomeGradients
import `in`.melo.presentation.components.moodColors
import `in`.melo.data.HomeMood
import `in`.melo.ui.theme.MeloLayout
import `in`.melo.ui.theme.LocalMeloDarkTheme
import `in`.melo.ui.theme.MeloAlpha
import `in`.melo.presentation.utils.LocalReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class HomeSpeedDialEntry(
    val item: Music,
    val title: String = item.title,
    val artworkUrl: String? = item.imageUrl,
    val recommendation: HomeRecommendationPick? = null,
    val isNewRelease: Boolean = false,
)

private const val SPEED_DIAL_COLUMNS = 3
private const val SLOT_BLANK = -1
private const val SLOT_LUCKY = -2
private const val SLOT_HISTORY = -3
internal const val SPEED_DIAL_MAX_ITEMS = 54
internal const val SPEED_DIAL_MAX_PAGES = 3
private val SPEED_DIAL_GAP = 8.dp
private val SPEED_DIAL_SHAPE = MeloLayout.CardShape
internal val HOME_DIAL_PAGER_HEADER_HEIGHT = 76.dp
internal val HOME_DIAL_PAGER_GRID_RESERVE = 148.dp

private enum class SpeedDialAction { LUCKY, HISTORY }

private data class SpeedDialTileVisual(
    val title: String,
    val artworkUrl: String?,
    val isNewRelease: Boolean,
)

@Composable
private fun artworkForeground(): Color = if (LocalMeloDarkTheme.current) {
    MaterialTheme.colorScheme.onSurface
} else {
    MaterialTheme.colorScheme.onPrimary
}

private fun speedDialSlots(
    count: Int,
    lucky: Boolean,
    history: Boolean,
    rows: Int,
    maxPages: Int?,
): IntArray {
    val pageSize = SPEED_DIAL_COLUMNS * rows
    val actionCount = (if (lucky) 1 else 0) + (if (history) 1 else 0)
    val itemCount = maxPages?.let { count.coerceAtMost((pageSize * it - actionCount).coerceAtLeast(0)) } ?: count
    val needed = itemCount + actionCount
    if (needed == 0) return IntArray(0)
    val pageCount = (needed + pageSize - 1) / pageSize
    val slots = IntArray(pageCount * pageSize) { SLOT_BLANK }

    // Lucky always owns the home page's bottom-right tile.
    if (lucky) slots[pageSize - 1] = SLOT_LUCKY

    // History owns the final page's bottom-right tile. On a one-page dial,
    // place it immediately before Lucky so both actions remain available.
    if (history) {
        val historySlot = if (pageCount == 1 && lucky) pageSize - 2 else slots.lastIndex
        slots[historySlot] = SLOT_HISTORY
    }

    // Balance pages when History is pinned to the final page, avoiding a
    // nearly empty last page while retaining the item order.
    var nextEntry = 0
    for (page in 0 until pageCount) {
        val pagesRemaining = pageCount - page
        val remainingEntries = itemCount - nextEntry
        val reservedOnPage =
            (if (lucky && page == 0) 1 else 0) +
                (if (history && page == pageCount - 1) 1 else 0)
        val pageCapacity = pageSize - reservedOnPage
        val desiredEntriesOnPage = if (history && pageCount > 1) {
            (remainingEntries + pagesRemaining - 1) / pagesRemaining
        } else {
            remainingEntries
        }
        val entriesOnPage = minOf(pageCapacity, desiredEntriesOnPage)
        var pageSlot = page * pageSize
        var placed = 0
        while (placed < entriesOnPage) {
            if (slots[pageSlot] == SLOT_BLANK) {
                slots[pageSlot] = nextEntry++
                placed++
            }
            pageSlot++
        }
    }
    return slots
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeSpeedDial(
    entries: List<HomeSpeedDialEntry>,
    pinnedIds: Set<String>,
    categoryChips: List<YouTubeHomeChip>,
    selectedCategory: YouTubeHomeChip?,
    onCategoryClick: (YouTubeHomeChip) -> Unit,
    onItemClick: (HomeSpeedDialEntry) -> Unit,
    onItemLongPress: (HomeSpeedDialEntry) -> Unit,
    hasLuckyTile: Boolean,
    onLuckyClick: () -> Job?,
    onHistoryClick: () -> Unit,
    onReorderPins: (() -> Unit)?,
    onCustomize: (() -> Unit)? = null,
    title: String? = null,
    sharedPagerHeader: Boolean = false,
    maxPages: Int? = null,
    rows: Int = 4,
    /** The first Speed Dial / Artist Dial page fills the visible Home viewport. */
    pageHeight: Dp? = null,
    footerReserve: Dp = 0.dp,
    /** Available height for square tiles after the mood row and page controls. */
    maxGridHeight: Dp? = null,
    showLabels: Boolean = true,
    showPinIcon: Boolean = true,
    tileStyle: String = `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_GLASS,
    showNewReleaseTags: Boolean = true,
    showHistoryTile: Boolean = true,
    onMissingArtwork: ((Music) -> Unit)? = null,
    loading: Boolean = false,
    onScrollToFeed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (!loading && entries.isEmpty() && categoryChips.isEmpty() && title == null && !hasLuckyTile && !showHistoryTile) return
    val safeRows = rows.coerceIn(3, 4)
    val pageSize = SPEED_DIAL_COLUMNS * safeRows
    val reservedActionCount = (if (hasLuckyTile) 1 else 0) + (if (showHistoryTile) 1 else 0)
    val visibleEntries = maxPages?.let {
        entries.take((pageSize * it - reservedActionCount).coerceAtLeast(0))
    } ?: entries
    val slots = if (loading) IntArray(pageSize) { it }
        else if (visibleEntries.isEmpty() && !hasLuckyTile && !showHistoryTile) IntArray(0)
        else speedDialSlots(visibleEntries.size, hasLuckyTile, history = showHistoryTile, rows = safeRows, maxPages = maxPages)
    val pageCount = slots.size / pageSize
    val pagerState = rememberPagerState(pageCount = { pageCount })

    val columnModifier = modifier.fillMaxWidth().then(
        if (pageHeight != null) Modifier.height(pageHeight) else Modifier.padding(bottom = 24.dp),
    )
    Column(columnModifier.padding(bottom = footerReserve), verticalArrangement = Arrangement.spacedBy(MeloLayout.Space3)) {
        if (sharedPagerHeader) {
            Box(Modifier.fillMaxWidth().height(HOME_DIAL_PAGER_HEADER_HEIGHT)) {
                if (title != null || onReorderPins != null || onCustomize != null) {
                    HomeSpeedDialHeading(
                        title = title,
                        onReorderPins = onReorderPins,
                        onCustomize = onCustomize,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                if (categoryChips.isNotEmpty()) {
                    HomeCategoryChips(
                        chips = categoryChips,
                        selected = selectedCategory,
                        onClick = onCategoryClick,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        } else {
            if (title != null || onReorderPins != null || onCustomize != null) {
                HomeSpeedDialHeading(
                    title = title,
                    onReorderPins = onReorderPins,
                    onCustomize = onCustomize,
                )
            } else {
                // Dial leaves a little breathing room above the first row of art.
                Spacer(Modifier.height(MeloLayout.Space4))
            }

            if (categoryChips.isNotEmpty()) {
                HomeCategoryChips(categoryChips, selectedCategory, onCategoryClick)
            }
        }

        if (loading || slots.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tileWidth = (maxWidth - MeloLayout.PageGutter * 2 - SPEED_DIAL_GAP * (SPEED_DIAL_COLUMNS - 1)) / SPEED_DIAL_COLUMNS
            val fitTileHeight = maxGridHeight?.let { (it - SPEED_DIAL_GAP * (safeRows - 1)) / safeRows }
            val tileSide = fitTileHeight?.let { minOf(tileWidth, maxOf(it, tileWidth * 0.8f)) } ?: tileWidth
            val gridHeight = tileSide * safeRows + SPEED_DIAL_GAP * (safeRows - 1)
            val sideInset = (maxWidth - tileSide * SPEED_DIAL_COLUMNS - SPEED_DIAL_GAP * (SPEED_DIAL_COLUMNS - 1)) / 2
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(gridHeight),
                contentPadding = PaddingValues(horizontal = sideInset),
                pageSpacing = 8.dp,
            ) { page ->
                val pageSlots = slots.drop(page * pageSize).take(pageSize)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(SPEED_DIAL_GAP),
                ) {
                    repeat(safeRows) { rowIndex ->
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(SPEED_DIAL_GAP),
                        ) {
                            repeat(SPEED_DIAL_COLUMNS) { columnIndex ->
                                val slot = pageSlots[rowIndex * SPEED_DIAL_COLUMNS + columnIndex]
                                when {
                                    loading -> HomeSkeletonTile(Modifier.weight(1f).fillMaxHeight())
                                    slot >= 0 -> {
                                        val entry = visibleEntries[slot]
                                        LaunchedEffect(entry.item.id, entry.artworkUrl) {
                                            if (entry.artworkUrl.isNullOrBlank()) onMissingArtwork?.invoke(entry.item)
                                        }
                                        SpeedDialTile(
                                            entry = entry,
                                            pinned = entry.item.id in pinnedIds,
                                            showLabel = showLabels,
                                            showPinIcon = showPinIcon,
                                            tileStyle = tileStyle,
                                            showNewReleaseTag = showNewReleaseTags,
                                            animationStaggerIndex = rowIndex * SPEED_DIAL_COLUMNS + columnIndex,
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            onClick = { onItemClick(entry) },
                                            onLongPress = { onItemLongPress(entry) },
                                        )
                                    }
                                    slot == SLOT_LUCKY -> SpeedDialActionTile(
                                        action = SpeedDialAction.LUCKY,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        tileStyle = tileStyle,
                                        onClick = onLuckyClick,
                                    )
                                    slot == SLOT_HISTORY -> SpeedDialActionTile(
                                        action = SpeedDialAction.HISTORY,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        tileStyle = tileStyle,
                                        onClick = { onHistoryClick(); null },
                                    )
                                    else -> Spacer(Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!loading && entries.isEmpty() && title != null) {
            Text(
                text = stringResource(R.string.home_dial_mixes_empty),
                modifier = Modifier.fillMaxWidth().padding(horizontal = MeloLayout.PageGutter, vertical = 28.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (pageCount > 1) {
            val pageLabel = stringResource(R.string.home_speed_dial_page, pagerState.currentPage + 1, pageCount)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = pageLabel },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { index ->
                    Box(
                        Modifier
                            .padding(horizontal = MeloLayout.Space1)
                            .width(6.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = MeloAlpha.Divider)
                            )
                    )
                }
            }
        }
        if (onScrollToFeed != null) {
            Spacer(Modifier.weight(1f))
            MeloIconButton(
                Icons.Rounded.KeyboardArrowDown,
                stringResource(R.string.home_dial_scroll_to_feed),
                onScrollToFeed,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun HomeSpeedDialHeading(
    title: String?,
    onReorderPins: (() -> Unit)?,
    onCustomize: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = MeloLayout.PageGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (onReorderPins != null) {
            MeloIconButton(Icons.Rounded.SwapVert, stringResource(R.string.pins_reorder), onReorderPins)
        }
        if (onCustomize != null) {
            MeloIconButton(Icons.Rounded.Tune, stringResource(R.string.home_dial_customize), onCustomize)
        }
    }
}

/** The Mixes Dial sits one horizontal swipe beside Speed Dial, as in Dial. */
@Composable
internal fun HomeDialPager(
    mixesAvailable: Boolean,
    speedDial: @Composable () -> Unit,
    mixesDial: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageCount = if (mixesAvailable) 2 else 1
    if (pageCount == 1) {
        Box(modifier.fillMaxWidth()) { speedDial() }
        return
    }
    // Mixes sits to the left; Speed Dial is the page Home opens on.
    val state = rememberPagerState(initialPage = if (mixesAvailable) 1 else 0, pageCount = { pageCount })
    LaunchedEffect(mixesAvailable) {
        if (mixesAvailable && state.currentPage != 1) state.scrollToPage(1)
    }
    HorizontalPager(
        state = state,
        beyondViewportPageCount = 1,
        pageSpacing = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) { page ->
        if (page == 0) mixesDial() else speedDial()
    }
}

@Composable
internal fun HomeCategoryChips(
    chips: List<YouTubeHomeChip>,
    selected: YouTubeHomeChip?,
    onClick: (YouTubeHomeChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(chips, selected?.params) {
        val index = chips.indexOfFirst { it.params == selected?.params }
        if (selected != null && index >= 0) rowState.animateScrollToItem(index)
    }
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = MeloLayout.PageGutter),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        items(chips, key = YouTubeHomeChip::params) { chip ->
            val isSelected = chip.params == selected?.params
            val interactions = remember { MutableInteractionSource() }
            val background by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                animationSpec = tween(240),
                label = "homeCategoryBackground",
            )
            val foreground by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                label = "homeCategoryForeground",
            )
            Box(
                modifier = Modifier
                    .homePress(interactions)
                    .height(48.dp)
                    .selectable(selected = isSelected, role = Role.Tab, interactionSource = interactions, indication = null) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick(chip)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.height(40.dp).clip(MeloLayout.CardShape).background(background)
                        .border(0.75.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, MeloLayout.CardShape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(chip.title, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = foreground, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialTile(
    entry: HomeSpeedDialEntry,
    pinned: Boolean,
    showLabel: Boolean,
    showPinIcon: Boolean,
    tileStyle: String,
    showNewReleaseTag: Boolean,
    animationStaggerIndex: Int,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val foreground = artworkForeground()
    val interactions = remember { MutableInteractionSource() }
    val reducedMotion = LocalReducedMotion.current
    val visual = SpeedDialTileVisual(
        title = entry.title,
        artworkUrl = entry.artworkUrl,
        isNewRelease = entry.isNewRelease || entry.recommendation?.type == `in`.melo.data.HomeRecommendationType.LATEST_RELEASE,
    )
    Box(
        modifier = modifier
            .homePress(interactions)
            .aspectRatio(1f)
            .clip(SPEED_DIAL_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                role = Role.Button,
                interactionSource = interactions,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .semantics {
                contentDescription = listOf(entry.title, entry.item.subtitle)
                    .filter(String::isNotBlank)
                    .joinToString(", ")
                role = Role.Button
            },
    ) {
        AnimatedContent(
            targetState = visual,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val stagger = if (reducedMotion) 0 else animationStaggerIndex * 38
                val enter = if (reducedMotion) {
                    fadeIn(tween(durationMillis = 160))
                } else {
                    val timing = tween<Float>(
                        durationMillis = 380,
                        delayMillis = stagger,
                        easing = FastOutSlowInEasing,
                    )
                    fadeIn(timing) + scaleIn(initialScale = 0.9f, animationSpec = timing) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 380, delayMillis = stagger, easing = FastOutSlowInEasing),
                            initialOffsetY = { height -> height / 16 },
                        )
                }
                val exit = if (reducedMotion) {
                    fadeOut(tween(durationMillis = 120))
                } else {
                    val timing = tween<Float>(
                        durationMillis = 170,
                        delayMillis = stagger,
                        easing = FastOutSlowInEasing,
                    )
                    fadeOut(timing) + scaleOut(targetScale = 0.98f, animationSpec = timing)
                }
                enter togetherWith exit
            },
            label = "speedDialRefreshTile",
        ) { displayedTile ->
            Box(Modifier.fillMaxSize()) {
                SmartImage(
                    model = displayedTile.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (showLabel) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(MeloLayout.ArtHero)
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))),
                    )
                    Text(
                        text = displayedTile.title,
                        modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = MeloLayout.Space2, vertical = MeloLayout.Space2),
                        style = MaterialTheme.typography.labelSmall,
                        color = foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (pinned && showPinIcon) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(MeloLayout.Space2)
                            .size(MeloLayout.IconDefault)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = MeloAlpha.Tertiary)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(MeloLayout.IconSmall),
                            tint = foreground,
                        )
                    }
                }
                if (showNewReleaseTag && displayedTile.isNewRelease) {
                    Text(
                        text = stringResource(R.string.home_new_music_tag),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(MeloLayout.Space2)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = MeloAlpha.Scrim))
                            .padding(horizontal = MeloLayout.Space2, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = foreground,
                        maxLines = 1,
                    )
                }
                when (tileStyle) {
                    `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_EDGE -> {
                        Box(Modifier.fillMaxSize().artworkRim(SPEED_DIAL_SHAPE))
                    }
                    `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_GLASS -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .artworkRim(SPEED_DIAL_SHAPE, glass = true),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialActionTile(
    action: SpeedDialAction,
    modifier: Modifier,
    tileStyle: String,
    onClick: () -> Job?,
) {
    val lucky = action == SpeedDialAction.LUCKY
    val label = stringResource(if (lucky) R.string.home_speed_dial_lucky else R.string.history_title)
    val description = stringResource(
        if (lucky) R.string.home_speed_dial_lucky_description else R.string.home_speed_dial_history_description,
    )
    val findingDescription = stringResource(R.string.home_speed_dial_lucky_finding)
    val icon = if (lucky) Icons.Rounded.Shuffle else Icons.Rounded.History
    val reducedMotion = LocalReducedMotion.current
    var lookupJob by remember(action) { mutableStateOf<Job?>(null) }
    LaunchedEffect(lookupJob) {
        val job = lookupJob ?: return@LaunchedEffect
        job.join()
        delay(320L)
        if (lookupJob === job) lookupJob = null
    }
    val mood = if (lucky) HomeMood.PARTY else HomeMood.RELAX
    val (baseColor, accentColor) = moodColors(mood)
    val deeperColor = baseColor.copy(
        red = baseColor.red * 0.72f,
        green = baseColor.green * 0.72f,
        blue = baseColor.blue * 0.72f,
    )
    val endColor = if (LocalHomeGradients.current) deeperColor else baseColor
    val interactions = remember { MutableInteractionSource() }
    val rim = when (tileStyle) {
        `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_EDGE -> Modifier.artworkRim(SPEED_DIAL_SHAPE)
        `in`.melo.data.preferences.AppSettings.DIAL_TILE_STYLE_GLASS -> Modifier.artworkRim(SPEED_DIAL_SHAPE, glass = true)
        else -> Modifier
    }
    BoxWithConstraints(
        modifier = modifier
            .homePress(interactions)
            .aspectRatio(1f)
            .clip(SPEED_DIAL_SHAPE)
            .background(Brush.verticalGradient(listOf(baseColor, endColor)))
            .then(rim)
            .combinedClickable(
                enabled = lookupJob == null,
                role = Role.Button,
                interactionSource = interactions,
                indication = null,
                onClick = { if (lookupJob == null) lookupJob = onClick() },
                onLongClick = null,
            )
            .semantics {
                contentDescription = "$label. $description"
                role = Role.Button
                if (lucky && lookupJob != null) stateDescription = findingDescription
            },
    ) {
        val compact = maxHeight < 100.dp || maxWidth < 100.dp
        val dense = maxHeight < 112.dp || maxWidth < 112.dp
        val contentPadding = if (dense) 6.dp else 8.dp
        val badgeSize = when {
            compact -> 22.dp
            dense -> 24.dp
            else -> 30.dp
        }
        val badgeIconSize = when {
            compact -> 14.dp
            dense -> 16.dp
            else -> 18.dp
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MeloLayout.Space1),
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.22f))
                        .border(1.dp, Color.White.copy(alpha = 0.36f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (lucky && lookupJob != null && !reducedMotion) {
                        val transition = rememberInfiniteTransition(label = "luckyFinding")
                        val rotation by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "luckyFindingRotation",
                        )
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(badgeIconSize).rotate(rotation),
                            tint = accentColor,
                        )
                    } else {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(badgeIconSize), tint = accentColor)
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
