package com.example.splendit.ui.group

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splendit.R
import com.example.splendit.data.model.PayMethod
import com.example.splendit.ui.components.Avatar
import com.example.splendit.ui.components.PrimaryButton
import com.example.splendit.ui.components.ScreenHeader
import com.example.splendit.ui.components.SpinPhase
import com.example.splendit.ui.components.SpinWheel
import com.example.splendit.ui.theme.Sw

@Composable
fun GameOverlay(game: GameUiState, viewModel: GroupViewModel) {
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
            title = game.title,
            subtitle = game.subtitle,
            onBack = viewModel::closeGame,
            backIcon = R.drawable.ic_close
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically)
        ) {
            when (game.type) {
                PayMethod.WHEEL -> SpinWheel(
                    ids = game.participants,
                    labels = game.wheelLabels,
                    weights = game.participants.map { 1.0 },
                    rotation = game.rotation,
                    phase = when (game.phase) {
                        GamePhase.READY -> SpinPhase.IDLE
                        GamePhase.BUSY -> SpinPhase.BUSY
                        GamePhase.DONE -> SpinPhase.DONE
                    },
                    winner = game.winner,
                    hubText = if (game.phase == GamePhase.DONE) game.winnerInitials else "★"
                )

                PayMethod.CARD -> CardTable(game.cards)
                PayMethod.EVEN -> Unit
            }
            when (game.phase) {
                GamePhase.READY -> PlayButton(
                    stringResource(
                        if (game.type ==
                            PayMethod.WHEEL
                        ) {
                            R.string.spin
                        } else {
                            R.string.deal_cards
                        }
                    ),
                    viewModel::play
                )

                GamePhase.BUSY -> Text(
                    stringResource(
                        if (game.type ==
                            PayMethod.WHEEL
                        ) {
                            R.string.round_and_round
                        } else {
                            R.string.drawing
                        }
                    ),
                    fontSize = 14.sp,
                    letterSpacing = 0.8.sp,
                    color = Sw.colors.accent300,
                    modifier = Modifier.height(56.dp).padding(top = 18.dp)
                )

                GamePhase.DONE -> Unit
            }
        }
        if (game.phase == GamePhase.DONE) ResultCard(game, viewModel::postExpense)
    }
}

@Composable
private fun PlayButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Sw.colors.accent400),
        modifier = Modifier
            .height(56.dp)
            .widthIn(min = 210.dp)
            .shadow(12.dp, CircleShape, ambientColor = Sw.colors.accent400)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun CardTable(cards: List<CardUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.chunked(3).forEach { rowCards ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowCards.forEach { PlayingCardView(it) }
            }
        }
    }
}

@Composable
private fun PlayingCardView(card: CardUi) {
    val shown = card.card != null
    val surface = MaterialTheme.colorScheme.surface
    val bg by animateColorAsState(
        when {
            card.loser -> Sw.colors.accent400
            shown -> surface
            else -> Sw.colors.accent700
        },
        label = "card"
    )
    val fg = when {
        card.loser -> surface
        shown -> MaterialTheme.colorScheme.onSurface
        else -> Sw.colors.accent300
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            Modifier
                .size(92.dp, 128.dp)
                .shadow(if (card.loser) 12.dp else 1.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val corner = card.card?.let { "${it.rank}${it.suit}" }.orEmpty()
            Text(corner, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg)
            Text(
                card.card?.rank ?: "?",
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                corner,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                modifier = Modifier.align(Alignment.End).rotate(180f)
            )
        }
        Text(
            card.name,
            fontSize = 12.sp,
            color = if (card.loser) Sw.colors.accent300 else Sw.colors.neutral400
        )
    }
}

@Composable
private fun ResultCard(game: GameUiState, onPost: () -> Unit) {
    Column(
        Modifier
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 16.dp)
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                game.winnerInitials,
                size = 52.dp,
                background = Sw.colors.accent400,
                contentColor = MaterialTheme.colorScheme.surface,
                fontSize = 16.sp
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(game.winnerLine, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                Text(game.winnerSubtitle, fontSize = 13.sp, color = Sw.colors.neutral400)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            game.owes.forEach { o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(o.initials, size = 24.dp, fontSize = 9.5.sp)
                    Text(
                        o.line,
                        fontSize = 13.sp,
                        color = Sw.colors.neutral300,
                        modifier = Modifier.weight(1f).padding(start = 10.dp)
                    )
                    Text(o.amount, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        PrimaryButton(stringResource(R.string.post_and_send), onPost, height = 48.dp)
    }
}
