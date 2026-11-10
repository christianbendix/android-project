package com.example.splendit.ui.group

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.splendit.R
import com.example.splendit.ui.components.AccentOutlineButton
import com.example.splendit.ui.components.Avatar
import com.example.splendit.ui.components.CircleIconButton
import com.example.splendit.ui.components.FadeDivider
import com.example.splendit.ui.components.PrimaryButton
import com.example.splendit.ui.components.SwIcon
import com.example.splendit.ui.components.SwTextField
import com.example.splendit.ui.components.Tag
import com.example.splendit.ui.create.StarBadge
import com.example.splendit.ui.theme.Sw

@Composable
fun GroupScreen(
    onBack: () -> Unit,
    onOpenJackpot: (String) -> Unit,
    viewModel: GroupViewModel = viewModel(factory = GroupViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.sendImage(uri.toString())
        }

    BackHandler(enabled = state.sheet != null) { viewModel.closeSheet() }
    BackHandler(enabled = state.game != null) { viewModel.closeGame() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            GroupHeader(state, onBack)
            BalanceBanner(state, onSettle = viewModel::settleAll)
            state.jackpot?.let { JackpotBannerCard(it) { onOpenJackpot(viewModel.groupId) } }
            Chat(state.items, onPay = viewModel::pay, modifier = Modifier.weight(1f))
            Composer(
                text = state.chatText,
                onTextChange = viewModel::onChatTextChange,
                onSend = viewModel::sendText,
                onPhoto = {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onExpense = viewModel::openSheet
            )
        }
        state.sheet?.let { ExpenseSheet(it, viewModel) }
        state.game?.let { GameOverlay(it, viewModel) }
    }
}

@Composable
private fun GroupHeader(state: GroupUiState, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircleIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack, size = 38.dp)
        Avatar(
            state.initials,
            size = 40.dp,
            shape = RoundedCornerShape(13.dp),
            contentColor = Sw.colors.accent300,
            photoUri = state.photoUri
        )
        Column(Modifier.weight(1f)) {
            Text(
                state.name,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                state.memberNames,
                fontSize = 12.sp,
                color = Sw.colors.neutral500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row {
            state.memberStack.forEachIndexed { i, p ->
                Avatar(
                    p.initials,
                    size = 26.dp,
                    fontSize = 9.5.sp,
                    modifier = Modifier.offset(x = (-8 * i).dp)
                )
            }
        }
    }
}

@Composable
private fun BalanceBanner(state: GroupUiState, onSettle: () -> Unit) {
    Row(
        Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (state.balanceTone) {
                        BalanceTone.OWE -> Sw.colors.neutral300
                        BalanceTone.OWED -> Sw.colors.accent
                        BalanceTone.SETTLED -> Sw.colors.neutral700
                    }
                )
        )
        Text(
            state.balanceText,
            fontSize = 13.sp,
            color = Sw.colors.neutral300,
            modifier = Modifier.weight(1f)
        )
        state.settleLabel?.let {
            AccentOutlineButton(
                it,
                onSettle,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            )
        }
    }
}

@Composable
private fun JackpotBannerCard(banner: JackpotBanner, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
            .fillMaxWidth()
            .shadow(if (banner.full) 8.dp else 1.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Sw.colors.accent800, MaterialTheme.colorScheme.surface)
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StarBadge(size = 34)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.jackpot),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    banner.label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Sw.colors.accent300
                )
            }
            LinearProgressIndicator(
                progress = { banner.progress },
                color = Sw.colors.accent400,
                trackColor = Sw.colors.accent700,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(5.dp)
            )
            Text(banner.subtitle, fontSize = 11.5.sp, color = Sw.colors.neutral500)
        }
        SwIcon(R.drawable.ic_chevron_right, size = 14.dp, tint = Sw.colors.neutral500)
    }
}

@Composable
private fun Chat(items: List<ChatItem>, onPay: (Long) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            when (item) {
                is ChatItem.System -> Text(
                    item.text,
                    fontSize = 11.5.sp,
                    color = Sw.colors.neutral500,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                )

                is ChatItem.Bubble -> Aligned(item.mine, item.sender) {
                    val surface = MaterialTheme.colorScheme.surface
                    Text(
                        item.text,
                        fontSize = 14.sp,
                        color = if (item.mine) surface else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .shadow(if (item.mine) 0.dp else 1.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (item.mine) Sw.colors.accent400 else surface
                            )
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                    )
                }

                is ChatItem.Photo -> Aligned(item.mine, item.sender) {
                    Box(
                        Modifier
                            .size(200.dp, 150.dp)
                            .shadow(1.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Sw.colors.accent600, Sw.colors.accent300)
                                )
                            )
                    ) {
                        if (item.uri != null) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = stringResource(R.string.photo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                is ChatItem.Bill -> Aligned(item.mine, item.sender) { BillCard(item, onPay) }
            }
        }
    }
}

@Composable
private fun Aligned(mine: Boolean, sender: String?, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (sender != null) {
            Text(
                sender,
                fontSize = 11.sp,
                color = Sw.colors.neutral500,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
        content()
    }
}

@Composable
private fun BillCard(bill: ChatItem.Bill, onPay: (Long) -> Unit) {
    Column(
        Modifier
            .width(272.dp)
            .shadow(1.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Sw.colors.accent800, MaterialTheme.colorScheme.surface)
                )
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SwIcon(R.drawable.ic_receipt, size = 13.dp, tint = Sw.colors.accent300)
            Text(
                bill.method,
                style = MaterialTheme.typography.labelSmall,
                color = Sw.colors.accent300,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                bill.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(bill.total, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Avatar(
                bill.payerInitials,
                size = 30.dp,
                background = Sw.colors.accent400,
                contentColor = MaterialTheme.colorScheme.surface,
                fontSize = 10.5.sp
            )
            Column {
                Text(bill.payerLine, fontSize = 13.sp)
                Text(bill.shareLine, fontSize = 11.5.sp, color = Sw.colors.neutral500)
            }
        }
        FadeDivider()
        Row(
            Modifier.height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                bill.status,
                fontSize = 12.5.sp,
                color = Sw.colors.neutral300,
                modifier = Modifier.weight(1f)
            )
            bill.payLabel?.let {
                PrimaryButton(
                    it,
                    onClick = { onPay(bill.id) },
                    height = 30.dp,
                    modifier = Modifier.width(110.dp)
                )
            }
            bill.doneLabel?.let { Tag(it) }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onPhoto: () -> Unit,
    onExpense: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircleIconButton(R.drawable.ic_photo, stringResource(R.string.send_photo), onPhoto)
        SwTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = stringResource(R.string.message),
            background = MaterialTheme.colorScheme.background,
            shape = CircleShape,
            height = 40.dp,
            onDone = onSend,
            modifier = Modifier.weight(1f),
            trailing = if (text.isBlank()) {
                null
            } else {
                {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Sw.colors.accent400)
                            .clickable(onClick = onSend),
                        contentAlignment = Alignment.Center
                    ) {
                        SwIcon(
                            R.drawable.ic_send,
                            size = 15.dp,
                            tint = MaterialTheme.colorScheme.surface,
                            contentDescription = stringResource(R.string.send)
                        )
                    }
                }
            }
        )
        AccentOutlineButton(
            stringResource(R.string.expense),
            onExpense,
            icon = R.drawable.ic_plus,
            modifier = Modifier.height(40.dp)
        )
    }
}
