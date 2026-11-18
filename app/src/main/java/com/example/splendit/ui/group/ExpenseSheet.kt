package com.example.splendit.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splendit.R
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.SplitMode
import com.example.splendit.ui.components.Avatar
import com.example.splendit.ui.components.PrimaryButton
import com.example.splendit.ui.components.SegmentedPills
import com.example.splendit.ui.components.SwIcon
import com.example.splendit.ui.components.SwTextField
import com.example.splendit.ui.theme.Sw

/** "New expense" bottom sheet, drawn as an overlay to match the prototype exactly. */
@Composable
fun BoxScope.ExpenseSheet(sheet: ExpenseSheetUiState, viewModel: GroupViewModel) {
    val d = sheet.draft
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x591B1D2A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = viewModel::closeSheet
            )
    )
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(38.dp, 4.dp)
                .clip(CircleShape)
                .background(Sw.colors.neutral700)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.new_expense),
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = viewModel::closeSheet) {
                Text(stringResource(R.string.cancel), color = Sw.colors.neutral400)
            }
        }
        AmountField(sheet, viewModel::onAmountChange)
        Column {
            Label(stringResource(R.string.whats_it_for))
            SwTextField(
                value = d.title,
                onValueChange = viewModel::onTitleChange,
                placeholder = "Dinner at Gasoline Grill",
                background = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
            )
        }
        Participants(sheet, viewModel)
        SplitSection(sheet, viewModel)
        Methods(d.method, viewModel::onMethodChange)
        sheet.cashbackHint?.let {
            Text("★ $it", fontSize = 12.sp, color = Sw.colors.accent300)
        }
        PrimaryButton(sheet.continueLabel, viewModel::startGame, enabled = sheet.canContinue)
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.sp, color = Sw.colors.neutral400, modifier = modifier)
}

@Composable
private fun AmountField(sheet: ExpenseSheetUiState, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicTextField(
                value = sheet.draft.amount,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(Sw.colors.accent),
                textStyle = TextStyle(
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.width(180.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (sheet.draft.amount.isEmpty()) {
                            Text(
                                "0",
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Medium,
                                color = Sw.colors.neutral600
                            )
                        }
                        inner()
                    }
                }
            )
            Text(
                sheet.currencyLabel,
                fontSize = 22.sp,
                color = Sw.colors.neutral500,
                modifier = Modifier.width(120.dp).padding(bottom = 8.dp)
            )
        }
        Text(sheet.amountHint, fontSize = 12.sp, color = Sw.colors.neutral500)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Participants(sheet: ExpenseSheetUiState, viewModel: GroupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.whos_in), Modifier.weight(1f))
            TextButton(onClick = viewModel::toggleAllParticipants) {
                Text(sheet.allLabel, fontSize = 12.sp, color = Sw.colors.accent)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sheet.chips.forEach { chip ->
                val on = chip.selected
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(if (on) Sw.colors.accent800 else Color.Transparent)
                        .border(
                            1.dp,
                            if (on) Sw.colors.accent400 else Sw.colors.neutral700,
                            CircleShape
                        )
                        .clickable { viewModel.toggleParticipant(chip.id) }
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Avatar(
                        chip.initials,
                        size = 26.dp,
                        background = if (on) Sw.colors.accent400 else Sw.colors.neutral800,
                        contentColor = if (on) Sw.colors.surface else Sw.colors.neutral300,
                        fontSize = 10.sp
                    )
                    Text(
                        chip.name,
                        fontSize = 13.sp,
                        color = if (on) Sw.colors.accent200 else Sw.colors.neutral400
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitSection(sheet: ExpenseSheetUiState, viewModel: GroupViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.how_to_split), Modifier.weight(1f))
            SegmentedPills(
                options = listOf(
                    SplitMode.EQUAL to "Equal",
                    SplitMode.PERCENT to "%",
                    SplitMode.AMOUNT to sheet.currencyLabel
                ),
                selected = sheet.draft.split,
                onSelect = viewModel::onSplitModeChange,
                selectedBackground = MaterialTheme.colorScheme.surface,
                selectedContent = Sw.colors.accent300,
                trackColor = MaterialTheme.colorScheme.background
            )
        }
        if (sheet.showSplitRows) {
            val balancedColor =
                if (sheet.splitBalanced) Sw.colors.neutral500 else Sw.colors.accent300
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 14.dp, vertical = 2.dp)
            ) {
                sheet.splitRows.forEach { row -> SplitRowItem(row, sheet.unitLabel, viewModel) }
                Row(
                    Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        sheet.splitSummary,
                        fontSize = 12.5.sp,
                        color = balancedColor,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::resetSplit) {
                        Text(
                            stringResource(R.string.reset_to_equal),
                            fontSize = 12.sp,
                            color = Sw.colors.accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitRowItem(row: SplitRow, unit: String, viewModel: GroupViewModel) {
    Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Avatar(row.initials, size = 30.dp, fontSize = 10.5.sp)
            Column(Modifier.weight(1f)) {
                Text(row.name, fontSize = 13.5.sp)
                Text(
                    row.subtitle,
                    fontSize = 11.5.sp,
                    color = if (row.highlight) Sw.colors.accent300 else Sw.colors.neutral500
                )
            }
            if (row.canCover) {
                TextButton(onClick = { viewModel.coverFor(row.id) }) {
                    Text(stringResource(R.string.cover), fontSize = 12.sp, color = Sw.colors.accent)
                }
            }
            Row(
                Modifier
                    .height(34.dp)
                    .shadow(1.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BasicTextField(
                    value = row.inputText,
                    onValueChange = { viewModel.onShareTyped(row.id, it) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .width(54.dp)
                        .onFocusChanged { if (!it.isFocused) viewModel.onShareFieldBlur(row.id) }
                )
                Text(unit, fontSize = 12.sp, color = Sw.colors.neutral500)
            }
        }
        Slider(
            value = row.sliderValue.coerceIn(0f, row.sliderMax),
            onValueChange = { viewModel.onSlide(row.id, it) },
            valueRange = 0f..row.sliderMax,
            steps = row.sliderSteps,
            colors = SliderDefaults.colors(
                thumbColor = Sw.colors.accent,
                activeTrackColor = Sw.colors.accent500,
                inactiveTrackColor = Sw.colors.neutral700
            )
        )
    }
}

@Composable
private fun Methods(selected: PayMethod, onSelect: (PayMethod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(stringResource(R.string.who_pays))
        PayMethod.entries.forEach { method ->
            val on = method == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Sw.colors.accent900 else MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        if (on) Sw.colors.accent400 else Sw.colors.neutral800,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(method) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Sw.colors.accent800),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (method) {
                        PayMethod.WHEEL -> R.drawable.ic_wheel
                        PayMethod.CARD -> R.drawable.ic_card
                        PayMethod.EVEN -> R.drawable.ic_even
                    }
                    SwIcon(icon, size = 19.dp, tint = Sw.colors.accent300)
                }
                Column(Modifier.weight(1f)) {
                    Text(method.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(method.description, fontSize = 12.sp, color = Sw.colors.neutral500)
                }
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(
                            1.5.dp,
                            if (on) Sw.colors.accent400 else Sw.colors.neutral700,
                            CircleShape
                        )
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(if (on) Sw.colors.accent400 else Color.Transparent)
                )
            }
        }
    }
}
