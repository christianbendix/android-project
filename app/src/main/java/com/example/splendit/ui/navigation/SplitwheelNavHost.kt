package com.example.splendit.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.splendit.R
import com.example.splendit.ui.components.FadeDivider
import com.example.splendit.ui.components.SwIcon
import com.example.splendit.ui.create.CreateGroupScreen
import com.example.splendit.ui.group.GroupScreen
import com.example.splendit.ui.home.HomeScreen
import com.example.splendit.ui.jackpot.JackpotScreen
import com.example.splendit.ui.profile.ProfileScreen
import com.example.splendit.ui.theme.Sw

/** Routes are defined once, here, instead of raw strings scattered over the screens. */
object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val CREATE = "create"
    const val ARG_GROUP_ID = "groupId"
    const val ARG_OPEN_EXPENSE = "openExpense"
    const val GROUP = "group/{$ARG_GROUP_ID}?$ARG_OPEN_EXPENSE={$ARG_OPEN_EXPENSE}"
    const val JACKPOT = "jackpot/{$ARG_GROUP_ID}"

    fun group(id: String, openExpense: Boolean = false) = "group/$id?$ARG_OPEN_EXPENSE=$openExpense"
    fun jackpot(id: String) = "jackpot/$id"
}

@Composable
fun SplitwheelApp(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottomBar = route == Routes.HOME || route == Routes.PROFILE

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                BottomBar(
                    current = route,
                    onHome = { navController.navigateTab(Routes.HOME) },
                    onCreate = { navController.navigate(Routes.CREATE) },
                    onProfile = { navController.navigateTab(Routes.PROFILE) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenGroup = { navController.navigate(Routes.group(it)) },
                    onCreateGroup = { navController.navigate(Routes.CREATE) },
                    contentPadding = innerPadding
                )
            }
            composable(Routes.PROFILE) { ProfileScreen(contentPadding = innerPadding) }
            composable(Routes.CREATE) {
                CreateGroupScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { id, addExpense ->
                        // Replace the form with the new group so Back goes home.
                        navController.navigate(Routes.group(id, addExpense)) {
                            popUpTo(Routes.CREATE) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                Routes.GROUP,
                arguments = listOf(
                    navArgument(Routes.ARG_GROUP_ID) { type = NavType.StringType },
                    navArgument(Routes.ARG_OPEN_EXPENSE) {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) {
                GroupScreen(
                    onBack = { navController.popBackStack() },
                    onOpenJackpot = { navController.navigate(Routes.jackpot(it)) }
                )
            }
            composable(
                Routes.JACKPOT,
                arguments = listOf(navArgument(Routes.ARG_GROUP_ID) { type = NavType.StringType })
            ) {
                JackpotScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Bottom-bar tabs share one back stack entry each instead of piling up. */
private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(
    current: String?,
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onProfile: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        FadeDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 44.dp, end = 44.dp, top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem(
                R.drawable.ic_home,
                stringResource(R.string.home),
                current == Routes.HOME,
                onHome
            )
            Box(
                Modifier
                    .size(60.dp)
                    .shadow(6.dp, CircleShape, ambientColor = Sw.colors.accent400)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Sw.colors.accent600, Sw.colors.accent400, Sw.colors.accent300)
                        )
                    )
                    .clickable(onClick = onCreate),
                contentAlignment = Alignment.Center
            ) {
                SwIcon(
                    R.drawable.ic_plus,
                    size = 26.dp,
                    tint = MaterialTheme.colorScheme.surface,
                    contentDescription = stringResource(R.string.create_group)
                )
            }
            TabItem(
                R.drawable.ic_profile,
                stringResource(R.string.profile),
                current == Routes.PROFILE,
                onProfile
            )
        }
    }
}

@Composable
private fun TabItem(icon: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Sw.colors.accent400 else Sw.colors.neutral500
    Column(
        Modifier
            .width(56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        SwIcon(icon, size = 24.dp, tint = color)
        Text(label, fontSize = 10.5.sp, color = color)
    }
}
