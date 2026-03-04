package com.cloudorz.monitor.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Bottom navigation bar displaying the four top-level tabs.
 *
 * @param currentRoute The route string of the currently selected tab.
 * @param onNavigate Callback invoked with the [Route] when a tab is tapped.
 * @param modifier Modifier applied to the [NavigationBar].
 */
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        Route.all.forEach { route ->
            NavigationBarItem(
                selected = currentRoute == route.route,
                onClick = { onNavigate(route) },
                icon = {
                    Icon(
                        imageVector = route.icon,
                        contentDescription = route.label,
                    )
                },
                label = { Text(text = route.label) },
                alwaysShowLabel = true,
            )
        }
    }
}
