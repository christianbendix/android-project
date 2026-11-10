package com.example.splendit.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.splendit.R
import com.example.splendit.ui.components.Avatar
import com.example.splendit.ui.components.CircleIconButton
import com.example.splendit.ui.components.SegmentedPills
import com.example.splendit.ui.components.SwIcon
import com.example.splendit.ui.components.SwTextField
import com.example.splendit.ui.components.Tag
import com.example.splendit.ui.theme.SplenditTheme
import com.example.splendit.ui.theme.Sw

@Composable
fun HomeScreen(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        contentPadding = contentPadding,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onOpenGroup = onOpenGroup,
        onCreateGroup = onCreateGroup
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onFilterChange: (HomeFilter) -> Unit,
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { Greeting() }
        item {
            SwTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.search_groups),
                leadingIcon = R.drawable.ic_search,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            BalanceCard(
                state = state,
                onCreateGroup = onCreateGroup,
                onSettleUp = { state.owingGroupId?.let(onOpenGroup) }
            )
        }
        item { RecentBills(state.recent, onOpenGroup) }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.your_groups),
                    style = MaterialTheme.typography.titleMedium
                )
                SegmentedPills(
                    options = HomeFilter.entries.map { it to it.label },
                    selected = state.filter,
                    onSelect = onFilterChange
                )
            }
        }
        items(state.groups, key = { it.id }) { row ->
            GroupRowItem(row, onClick = { onOpenGroup(row.id) })
        }
        if (state.groups.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_groups_match),
                    fontSize = 13.sp,
                    color = Sw.colors.neutral500
                )
            }
        }
    }
}

@Composable
private fun Greeting() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar("PH", size = 42.dp, fontSize = 14.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.good_evening),
                fontSize = 12.sp,
                color = Sw.colors.neutral500
            )
            Text("Peter", fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        CircleIconButton(R.drawable.ic_bell, stringResource(R.string.notifications), onClick = {})
    }
}

@Composable
private fun BalanceCard(state: HomeUiState, onCreateGroup: () -> Unit, onSettleUp: () -> Unit) {
    val onCard = MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = Sw.colors.accent400)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Sw.colors.accent300, Sw.colors.accent400, Sw.colors.accent600)
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.net_balance).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Sw.colors.accent900
        )
        Text(state.netLabel, style = MaterialTheme.typography.headlineLarge, color = onCard)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Owed to you ${state.owedLabel}", fontSize = 12.sp, color = Sw.colors.accent900)
            Text("You owe ${state.oweLabel}", fontSize = 12.sp, color = Sw.colors.accent900)
        }
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardPill(stringResource(R.string.new_group), onCreateGroup, R.drawable.ic_plus)
            CardPill(stringResource(R.string.settle_up), onSettleUp)
        }
    }
}

@Composable
private fun CardPill(text: String, onClick: () -> Unit, icon: Int? = null) {
    val fg = MaterialTheme.colorScheme.surface
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = fg
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            SwIcon(icon, size = 15.dp, tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun RecentBills(recent: List<RecentBill>, onOpenGroup: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.recent_subtitle),
                fontSize = 12.sp,
                color = Sw.colors.neutral500
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(recent) { bill ->
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .shadow(1.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpenGroup(bill.groupId) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        bill.groupName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Sw.colors.accent300
                    )
                    Text(
                        bill.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(bill.amount, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Tag(bill.status, accent = bill.highlight)
                }
            }
        }
    }
}

@Composable
private fun GroupRowItem(row: GroupRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Avatar(
            row.initials,
            size = 50.dp,
            shape = RoundedCornerShape(16.dp),
            contentColor = Sw.colors.accent300,
            fontSize = 15.sp,
            photoUri = row.photoUri
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (row.jackpotTag != null) {
                    Spacer(Modifier.width(6.dp))
                    Tag("★ ${row.jackpotTag}")
                }
            }
            Text(
                row.preview,
                fontSize = 12.5.sp,
                color = Sw.colors.neutral500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.hasUnread) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Sw.colors.accent)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(row.whenLabel, fontSize = 11.sp, color = Sw.colors.neutral500)
            }
            Text(
                row.balanceLabel,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = when (row.balanceKind) {
                    BalanceKind.OWED -> Sw.colors.accent300
                    BalanceKind.OWE -> Sw.colors.neutral200
                    BalanceKind.SETTLED -> Sw.colors.neutral600
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    SplenditTheme {
        HomeContent(
            state = HomeUiState(
                netLabel = "+1.430 kr",
                owedLabel = "1.880 kr",
                oweLabel = "450 kr",
                recent = listOf(
                    RecentBill(
                        "g1",
                        "Roskilde 2026",
                        "Groceries — Netto",
                        "840 kr",
                        "You owe 210 kr",
                        true
                    )
                ),
                groups = listOf(
                    GroupRow(
                        "g1",
                        "Roskilde 2026",
                        "R6",
                        null,
                        "Emil: Rigged wheel. Paying later.",
                        "14:02",
                        true,
                        "96%",
                        "−210 kr",
                        BalanceKind.OWE
                    )
                )
            ),
            contentPadding = PaddingValues(),
            onQueryChange = {},
            onFilterChange = {},
            onOpenGroup = {},
            onCreateGroup = {}
        )
    }
}
