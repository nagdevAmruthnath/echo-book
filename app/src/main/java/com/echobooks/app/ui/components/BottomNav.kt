package com.echobooks.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echobooks.app.ui.theme.Magenta
import com.echobooks.app.ui.theme.TextSecondary
import com.echobooks.app.ui.theme.Violet

enum class BottomNavTab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Rounded.Home),
    Create("create", "Create", Icons.Rounded.AutoAwesome),
    Library("library", "Library", Icons.Rounded.LibraryBooks),
    Settings("settings", "Settings", Icons.Rounded.Settings)
}

@Composable
fun GlassBottomBar(
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .shadow(24.dp, shape, spotColor = Violet.copy(alpha = 0.22f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            BottomNavTab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                val iconColor by animateColorAsState(
                    if (selected) Color.White else TextSecondary,
                    label = "navIcon"
                )
                val pillW by animateDpAsState(if (selected) 72.dp else 52.dp, label = "navPill")
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(tab.route) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .width(pillW)
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) Brush.horizontalGradient(listOf(Violet, Magenta))
                                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                        ,
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(tab.icon, contentDescription = tab.label, tint = iconColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = iconColor,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}