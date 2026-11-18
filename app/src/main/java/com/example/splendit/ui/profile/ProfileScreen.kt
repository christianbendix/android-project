package com.example.splendit.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.splendit.R
import com.example.splendit.ui.components.Avatar
import com.example.splendit.ui.components.FadeDivider
import com.example.splendit.ui.components.SwIcon
import com.example.splendit.ui.theme.Sw

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(state, contentPadding)
}

@Composable
fun ProfileContent(state: ProfileUiState, contentPadding: PaddingValues) {
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
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.profile), style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(96.dp)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(listOf(Sw.colors.accent600, Sw.colors.accent400))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.me.initials,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.surface
                    )
                }
                Text(
                    state.me.fullName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(state.me.phone, fontSize = 13.sp, color = Sw.colors.neutral500)
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                Stat(
                    state.groupCount.toString(),
                    stringResource(R.string.groups),
                    Modifier.weight(1f)
                )
                Stat(
                    state.friends.size.toString(),
                    stringResource(R.string.friends),
                    Modifier.weight(1f)
                )
                Stat("★ ${state.rating}", stringResource(R.string.pay_rating), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {},
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Sw.colors.divider),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) { Text(stringResource(R.string.share_profile)) }
                OutlinedButton(
                    onClick = {},
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Sw.colors.accent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Sw.colors.accent),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) { Text(stringResource(R.string.add_friend)) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.friends), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.friends, key = { it.id }) { p ->
                        Column(
                            Modifier.width(56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Avatar(
                                p.initials,
                                size = 52.dp,
                                background = MaterialTheme.colorScheme.surface,
                                contentColor = Sw.colors.neutral300,
                                fontSize = 14.sp,
                                modifier = Modifier.shadow(1.dp, CircleShape)
                            )
                            Text(p.shortName, fontSize = 11.5.sp, color = Sw.colors.neutral400)
                        }
                    }
                }
            }
        }
        item {
            Column(
                Modifier
                    .shadow(1.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                state.settings.forEachIndexed { i, (label, value) ->
                    if (i > 0) FadeDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(value, fontSize = 13.sp, color = Sw.colors.neutral500)
                        SwIcon(
                            R.drawable.ic_chevron_right,
                            size = 14.dp,
                            tint = Sw.colors.neutral500
                        )
                    }
                }
            }
        }
        item {
            TextButton(onClick = {}) {
                Text(stringResource(R.string.log_out), color = Sw.colors.neutral400)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(label, fontSize = 12.sp, color = Sw.colors.neutral500)
    }
}
