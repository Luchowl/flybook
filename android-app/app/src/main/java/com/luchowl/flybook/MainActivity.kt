package com.luchowl.flybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.luchowl.flybook.ui.FlybookTheme
import com.luchowl.flybook.ui.screens.DashboardScreen
import com.luchowl.flybook.ui.screens.FlightsScreen
import com.luchowl.flybook.ui.screens.MapScreen
import com.luchowl.flybook.ui.screens.SettingsScreen
import com.luchowl.flybook.ui.screens.StatsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            FlybookTheme {
                FlybookRoot()
            }
        }
    }
}

private data class Dest(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val destinations = listOf(
    Dest("dashboard", "Dashboard", Icons.Default.BarChart),
    Dest("flights", "My Flights", Icons.Default.List),
    Dest("map", "Map", Icons.Default.Map),
    Dest("stats", "Stats", Icons.Default.Leaderboard),
    Dest("settings", "Settings", Icons.Default.Settings),
)

private val autoHideBottomBarRoutes = setOf("flights", "stats")

@Composable
private fun CompactBottomBar(selectedRoute: String?, onSelect: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { dest ->
                val selected = selectedRoute == dest.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(dest.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        dest.icon,
                        contentDescription = dest.label,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        dest.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlybookRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val currentRoute = currentDestination?.route
    val autoHideBottomBar = currentRoute in autoHideBottomBarRoutes
    var bottomBarVisible by remember { mutableStateOf(true) }
    val scrollThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    val scrollConnection = remember(scrollThreshold, autoHideBottomBar) {
        object : NestedScrollConnection {
            private var accumulatedScroll = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!autoHideBottomBar) return Offset.Zero

                val delta = if (bottomBarVisible) consumed.y else consumed.y + available.y
                if (delta == 0f) return Offset.Zero

                if (accumulatedScroll != 0f && (delta > 0f) != (accumulatedScroll > 0f)) {
                    accumulatedScroll = 0f
                }
                accumulatedScroll += delta

                if (accumulatedScroll <= -scrollThreshold && bottomBarVisible) {
                    bottomBarVisible = false
                    accumulatedScroll = 0f
                } else if (accumulatedScroll >= scrollThreshold && !bottomBarVisible) {
                    bottomBarVisible = true
                    accumulatedScroll = 0f
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(currentRoute) {
        bottomBarVisible = true
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollConnection),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = bottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }) +
                    expandVertically(expandFrom = Alignment.Bottom),
                exit = slideOutVertically(targetOffsetY = { it }) +
                    shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                CompactBottomBar(
                    selectedRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable("dashboard") { DashboardScreen() }
            composable("flights") { FlightsScreen() }
            composable("map") { MapScreen() }
            composable("stats") { StatsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
