package com.example.splendit.ui.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.splendit.R
import com.example.splendit.ui.components.AccentOutlineButton
import com.example.splendit.ui.components.Avatar
import com.example.splendit.ui.components.CheckCircle
import com.example.splendit.ui.components.PrimaryButton
import com.example.splendit.ui.components.ScreenHeader
import com.example.splendit.ui.components.SegmentedPills
import com.example.splendit.ui.components.SwIcon
import com.example.splendit.ui.components.SwTextField
import com.example.splendit.ui.components.Tag
import com.example.splendit.ui.theme.Sw

@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onCreated: (groupId: String, addExpense: Boolean) -> Unit,
    viewModel: CreateGroupViewModel = viewModel(factory = CreateGroupViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        viewModel.onPhotoPicked(it?.toString())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        ScreenHeader(stringResource(R.string.new_group), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhotoButton(state.photoUri) {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(
                        stringResource(R.string.group_name),
                        fontSize = 12.sp,
                        color = Sw.colors.neutral400
                    )
                    SwTextField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        placeholder = "e.g. Roskilde 2027",
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                    )
                }
            }
            if (state.selected.isNotEmpty()) SelectedChips(state, viewModel::remove)
            InviteSection(state, viewModel)
            JackpotCard(state, viewModel)
        }
        BottomBar(state, viewModel, onCreated)
    }
}

@Composable
private fun PhotoButton(photoUri: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Sw.colors.accent600, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SwIcon(
            R.drawable.ic_camera,
            size = 26.dp,
            tint = Sw.colors.accent400,
            contentDescription = "Group photo"
        )
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SelectedChips(state: CreateGroupUiState, onRemove: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${state.selected.size} selected", fontSize = 12.sp, color = Sw.colors.neutral400)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.selectedPeople.forEach { p ->
                Column(
                    Modifier
                        .width(54.dp)
                        .clickable { onRemove(p.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box {
                        Avatar(p.initials, size = 48.dp)
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-2).dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Sw.colors.neutral300),
                            contentAlignment = Alignment.Center
                        ) {
                            SwIcon(
                                R.drawable.ic_close,
                                size = 10.dp,
                                tint = MaterialTheme.colorScheme.surface,
                                contentDescription = "Remove ${p.shortName}"
                            )
                        }
                    }
                    Text(
                        if (p.id.startsWith("tel")) p.fullName else p.shortName,
                        fontSize = 11.sp,
                        color = Sw.colors.neutral300,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteSection(state: CreateGroupUiState, viewModel: CreateGroupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.invite_people), style = MaterialTheme.typography.titleMedium)
        SegmentedPills(
            options = InviteTab.entries.map { it to it.label },
            selected = state.tab,
            onSelect = viewModel::onTabChange,
            fill = true,
            selectedBackground = Sw.colors.accent800,
            selectedContent = Sw.colors.accent200,
            modifier = Modifier.fillMaxWidth().shadow(1.dp, CircleShape)
        )
        when (state.tab) {
            InviteTab.FRIENDS -> {
                SwTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.search_friends),
                    leadingIcon = R.drawable.ic_search,
                    height = 42.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    state.friends.forEach { f ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.toggleFriend(f.person.id) }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Avatar(
                                f.person.initials,
                                background = MaterialTheme.colorScheme.surface,
                                contentColor = Sw.colors.neutral300,
                                modifier = Modifier.shadow(1.dp, CircleShape)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(f.person.fullName, fontSize = 14.5.sp)
                                Text(f.person.phone, fontSize = 12.sp, color = Sw.colors.neutral500)
                            }
                            CheckCircle(f.selected)
                        }
                    }
                }
            }

            InviteTab.PHONE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwTextField(
                        value = state.phone,
                        onValueChange = viewModel::onPhoneChange,
                        placeholder = "12 34 56 78",
                        leadingIcon = R.drawable.ic_phone,
                        prefix = "+45",
                        keyboardType = KeyboardType.Phone,
                        onDone = viewModel::addPhone,
                        modifier = Modifier.weight(1f)
                    )
                    AccentOutlineButton(
                        text = stringResource(R.string.add),
                        onClick = viewModel::addPhone,
                        enabled = state.phoneValid,
                        modifier = Modifier.height(44.dp)
                    )
                }
                Text(
                    stringResource(R.string.sms_invite_hint),
                    fontSize = 12.sp,
                    color = Sw.colors.neutral500
                )
                state.invitedPeople.forEach { p ->
                    Row(
                        Modifier.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .shadow(1.dp, CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            SwIcon(R.drawable.ic_phone, size = 18.dp, tint = Sw.colors.neutral400)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.fullName, fontSize = 14.5.sp)
                            Text("SMS invite", fontSize = 12.sp, color = Sw.colors.neutral500)
                        }
                        Tag("Added")
                    }
                }
            }
        }
    }
}

@Composable
private fun JackpotCard(state: CreateGroupUiState, viewModel: CreateGroupViewModel) {
    val bg = if (state.jackpot) {
        Brush.linearGradient(listOf(Sw.colors.accent800, MaterialTheme.colorScheme.surface))
    } else {
        Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.clickable(onClick = viewModel::toggleJackpot),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StarBadge()
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.jackpot_pool),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.jackpot_pool_sub),
                    fontSize = 12.sp,
                    color = Sw.colors.neutral500
                )
            }
            Toggle(state.jackpot)
        }
        if (state.jackpot) {
            Text(
                stringResource(R.string.jackpot_explainer),
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = Sw.colors.neutral400
            )
            Text(stringResource(R.string.pot_size), fontSize = 12.sp, color = Sw.colors.neutral400)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.goalOptions.forEach { goal ->
                    val on = state.goal == goal
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (on) Sw.colors.accent800 else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                1.dp,
                                if (on) Sw.colors.accent400 else Sw.colors.neutral800,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.onGoalChange(goal) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            viewModel.format(goal),
                            fontSize = 13.sp,
                            color = if (on) Sw.colors.accent200 else Sw.colors.neutral300
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StarBadge(size: Int = 38) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(Sw.colors.accent400),
        contentAlignment = Alignment.Center
    ) {
        Text("★", fontSize = (size / 2).sp, color = MaterialTheme.colorScheme.surface)
    }
}

@Composable
private fun Toggle(on: Boolean) {
    val knob by animateDpAsState(if (on) 18.dp else 0.dp, label = "knob")
    Box(
        Modifier
            .size(44.dp, 26.dp)
            .clip(CircleShape)
            .background(if (on) Sw.colors.accent400 else Sw.colors.neutral700)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(20.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        )
    }
}

@Composable
private fun BottomBar(
    state: CreateGroupUiState,
    viewModel: CreateGroupViewModel,
    onCreated: (String, Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val on = state.addExpenseFirst
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (on) Sw.colors.accent900 else Color.Transparent)
                .border(
                    1.dp,
                    if (on) Sw.colors.accent400 else Sw.colors.neutral800,
                    RoundedCornerShape(14.dp)
                )
                .clickable(onClick = viewModel::toggleAddExpenseFirst)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Sw.colors.accent800),
                contentAlignment = Alignment.Center
            ) {
                SwIcon(R.drawable.ic_receipt, size = 18.dp, tint = Sw.colors.accent300)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.first_expense),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.first_expense_sub),
                    fontSize = 12.sp,
                    color = Sw.colors.neutral500
                )
            }
            CheckCircle(on)
        }
        PrimaryButton(
            text = stringResource(
                if (on) R.string.create_and_add_expense else R.string.create_group
            ),
            enabled = state.canCreate,
            onClick = {
                val addExpense = state.addExpenseFirst
                viewModel.create()?.let { onCreated(it, addExpense) }
            }
        )
    }
}
