package com.example.splendit.ui.jackpot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.splendit.R
import com.example.splendit.ui.components.Avatar
import com.example.splendit.ui.components.FadeDivider
import com.example.splendit.ui.components.PrimaryButton
import com.example.splendit.ui.components.ScreenHeader
import com.example.splendit.ui.components.SpinPhase
import com.example.splendit.ui.components.SpinWheel
import com.example.splendit.ui.theme.Sw

@Composable
fun JackpotScreen(
    onBack: () -> Unit,
    viewModel: JackpotViewModel = viewModel(factory = JackpotViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Sw.colors.accent800, MaterialTheme.colorScheme.background)
                )
            )
    ) {
        ScreenHeader(
            title = stringResource(R.string.jackpot),
            subtitle = state.groupName,
            onBack = onBack,
            backIcon = R.drawable.ic_close
        )
        if (!state.found) return@Column
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 330.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
            ) {
                if (state.phase == JackpotPhase.IDLE) {
                    PotRing(state)
                } else {
                    SpinWheel(
                        ids = state.wheelIds,
                        labels = state.wheelLabels,
                        weights = state.wheelWeights,
                        rotation = state.rotation,
                        phase = when (state.phase) {
                            JackpotPhase.BUSY -> SpinPhase.BUSY
                            JackpotPhase.DONE -> SpinPhase.DONE
                            else -> SpinPhase.IDLE
                        },
                        winner = state.winner,
                        hubText = state.hubText,
                        size = 290.dp,
                        durationMillis = 5_200
                    )
                }
                if (state.status.isNotEmpty()) {
                    Text(
                        state.status,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                        color = Sw.colors.neutral300,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
            if (state.canTopUp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillOutline(
                        "Top up ${state.topUpLabel}",
                        viewModel::topUp,
                        Modifier.weight(1f),
                        accent = false
                    )
                    PillOutline(
                        stringResource(R.string.fill_pot_demo),
                        viewModel::fillDemo,
                        Modifier.weight(1f)
                    )
                }
            }
            if (state.canReady) {
                Button(
                    onClick = viewModel::imReady,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Sw.colors.accent400),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text(
                        stringResource(R.string.im_ready),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (state.phase == JackpotPhase.DONE) {
                WinnerCard(state)
                PrimaryButton(
                    stringResource(R.string.back_to_group),
                    onClick = {
                        viewModel.collect()
                        onBack()
                    }
                )
            }
            OddsCard(state.members)
        }
    }
}

@Composable
private fun PotRing(state: JackpotUiState) {
    val track = Sw.colors.accent800
    val fill = Sw.colors.accent400
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(
                fill,
                -90f,
                360f * state.progress,
                false,
                topLeft,
                arcSize,
                style = Stroke(stroke)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.kicker.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Sw.colors.accent300
            )
            Text(state.pool, fontSize = 36.sp, fontWeight = FontWeight.Medium)
            Text("of ${state.goal}", fontSize = 12.5.sp, color = Sw.colors.neutral500)
        }
    }
}

@Composable
private fun PillOutline(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    accent: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (accent) Sw.colors.accent else Sw.colors.divider),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = if (accent) Sw.colors.accent else MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.height(46.dp)
    ) { Text(text) }
}

@Composable
private fun WinnerCard(state: JackpotUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            state.winnerInitials,
            size = 52.dp,
            background = Sw.colors.accent400,
            contentColor = MaterialTheme.colorScheme.surface,
            fontSize = 16.sp
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(state.winnerLine, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Text(state.winnerSubtitle, fontSize = 13.sp, color = Sw.colors.neutral400)
        }
    }
}

@Composable
private fun OddsCard(members: List<JackpotMember>) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(Modifier.padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.odds),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.faster_payers),
                fontSize = 11.5.sp,
                color = Sw.colors.neutral500
            )
        }
        members.forEach { m ->
            FadeDivider()
            Row(
                Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Avatar(m.initials, size = 38.dp, fontSize = 12.sp)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(if (m.online) Sw.colors.accent400 else Sw.colors.neutral700)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(m.name, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { k ->
                            Text(
                                "★",
                                fontSize = 12.sp,
                                color = if (m.rating >=
                                    k - 0.25
                                ) {
                                    Sw.colors.accent400
                                } else {
                                    Sw.colors.neutral800
                                }
                            )
                        }
                        Text(
                            JackpotViewModel.ratingText(m.rating),
                            fontSize = 12.sp,
                            color = Sw.colors.neutral500,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(m.odds, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (m.readyLabel.isNotEmpty()) {
                        Text(
                            m.readyLabel,
                            fontSize = 11.5.sp,
                            color = if (m.ready) Sw.colors.accent300 else Sw.colors.neutral500
                        )
                    }
                }
            }
        }
    }
}
