package com.pulseisland.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseisland.app.R
import com.pulseisland.app.data.IslandPreferences
import com.pulseisland.app.ui.theme.DarkBackground
import com.pulseisland.app.ui.theme.DarkBorder
import com.pulseisland.app.ui.theme.DarkSurface
import com.pulseisland.app.ui.theme.DarkSurfaceVariant
import com.pulseisland.app.ui.theme.ElectricCyan
import com.pulseisland.app.ui.theme.EmeraldGreen
import com.pulseisland.app.ui.theme.ErrorRed
import com.pulseisland.app.ui.theme.TextMuted
import com.pulseisland.app.ui.theme.TextPrimary
import com.pulseisland.app.ui.theme.TextSecondary
import com.pulseisland.app.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    hasOverlayPermission: Boolean,
    isIslandRunning: Boolean,
    islandConfig: IslandPreferences.IslandConfig,
    onOpenPermissionSettings: () -> Unit,
    onStartIsland: () -> Unit,
    onStopIsland: () -> Unit,
    onUpdateXOffset: (Int) -> Unit,
    onUpdateYOffset: (Int) -> Unit,
    onUpdateScale: (Float) -> Unit,
    onResetDefaults: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header Section
            HeaderSection()

            // Preview Pill Mockup with dynamic scale
            IslandPreviewCard(
                isIslandRunning = isIslandRunning,
                scale = islandConfig.scale
            )

            // Permission Card
            PermissionStatusCard(
                hasPermission = hasOverlayPermission,
                onOpenSettings = onOpenPermissionSettings
            )

            // Control Actions Card
            ControllerCard(
                hasPermission = hasOverlayPermission,
                isRunning = isIslandRunning,
                onStart = {
                    if (hasOverlayPermission) {
                        onStartIsland()
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Grant overlay permission first to display PulseIsland"
                            )
                        }
                    }
                },
                onStop = onStopIsland
            )

            // Size & Position Customization Card
            CustomizationCard(
                config = islandConfig,
                onUpdateXOffset = onUpdateXOffset,
                onUpdateYOffset = onUpdateYOffset,
                onUpdateScale = onUpdateScale,
                onResetDefaults = onResetDefaults
            )

            // Battery & Architecture Highlights
            BatteryEfficiencyCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ElectricCyan.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
                .border(BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "PulseIsland Logo",
                tint = Color.Unspecified,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "PulseIsland",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Lightweight Dynamic Island for Android",
            fontSize = 14.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun IslandPreviewCard(
    isIslandRunning: Boolean,
    scale: Float = 1.0f
) {
    var isExpanded by remember { mutableStateOf(false) }

    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 20.dp else 22.dp,
        label = "previewCornerRadius"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "INTERACTIVE OVERLAY PREVIEW",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Realistic Island Pill representation with interactive expand/collapse & live scale
            Box(
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color.Black)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), RoundedCornerShape(cornerRadius))
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = if (isExpanded) 18.dp else 20.dp, vertical = if (isExpanded) 12.dp else 9.dp)
            ) {
                if (!isExpanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (isIslandRunning) EmeraldGreen else Color.Gray)
                        )

                        Text(
                            text = "Pulse",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )

                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen)
                        )

                        Column {
                            Text(
                                text = "PulseIsland",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Active • Tap pill to collapse",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isIslandRunning) {
                    "Pill is currently floating on screen! (Tap preview above to test animation)"
                } else {
                    "Pill floats top-center when started. (Tap preview above to test animation)"
                },
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun PermissionStatusCard(
    hasPermission: Boolean,
    onOpenSettings: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = if (hasPermission) EmeraldGreen else WarningAmber,
        label = "statusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Overlay Permission",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (hasPermission) "Granted" else "Required",
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Android requires 'Display over other apps' permission so PulseIsland can float above running applications without interrupting them.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
            ) {
                Text(
                    text = if (hasPermission) "Manage System Permission" else "Grant Overlay Permission",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ControllerCard(
    hasPermission: Boolean,
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Island Controller",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) EmeraldGreen else TextMuted)
                    )
                    Text(
                        text = if (isRunning) "Active" else "Stopped",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isRunning) EmeraldGreen else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color.Black,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Text(
                        text = "Start Island",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onStop,
                    enabled = isRunning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSurfaceVariant,
                        contentColor = ErrorRed,
                        disabledContainerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                        disabledContentColor = TextMuted
                    ),
                    border = if (isRunning) BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)) else null
                ) {
                    Text(
                        text = "Stop Island",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomizationCard(
    config: IslandPreferences.IslandConfig,
    onUpdateXOffset: (Int) -> Unit,
    onUpdateYOffset: (Int) -> Unit,
    onUpdateScale: (Float) -> Unit,
    onResetDefaults: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Size & Position Customization",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Fixed permanently until you change it",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                OutlinedButton(
                    onClick = onResetDefaults,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = "Reset", fontSize = 12.sp)
                }
            }

            // Vertical Offset Slider (Y position)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Vertical Offset (Top Margin)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${config.yOffsetDp} dp",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }
                Text(
                    text = "Adjust distance from top edge / camera hole",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Slider(
                    value = config.yOffsetDp.toFloat(),
                    onValueChange = { onUpdateYOffset(it.toInt()) },
                    valueRange = 0f..90f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }

            // Horizontal Offset Slider (X position)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Horizontal Offset (Alignment)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = when {
                            config.xOffsetDp == 0 -> "Center"
                            config.xOffsetDp < 0 -> "${-config.xOffsetDp} dp Left"
                            else -> "${config.xOffsetDp} dp Right"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }
                Text(
                    text = "Adjust for corner/punch-hole cameras",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Slider(
                    value = config.xOffsetDp.toFloat(),
                    onValueChange = { onUpdateXOffset(it.toInt()) },
                    valueRange = -120f..120f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }

            // Size Scale Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Island Size Scale",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${(config.scale * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
                Slider(
                    value = config.scale,
                    onValueChange = { onUpdateScale(it) },
                    valueRange = 0.8f..1.3f,
                    colors = SliderDefaults.colors(
                        thumbColor = EmeraldGreen,
                        activeTrackColor = EmeraldGreen,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )
            }

            Text(
                text = "💡 Drag Support: You can also drag the floating island anywhere across your screen with your finger! Its new position is saved automatically.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun BatteryEfficiencyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Battery & Efficiency Architecture",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            EfficiencyItem(
                icon = "⚡",
                title = "Zero background polling",
                desc = "No timers, infinite loops, or persistent CPU wake locks."
            )

            EfficiencyItem(
                icon = "🎬",
                title = "Event-driven transitions",
                desc = "Animations run strictly during user tap transitions, idling at 0% CPU."
            )

            EfficiencyItem(
                icon = "🛡️",
                title = "Offline & Private",
                desc = "No internet permissions requested. Zero telemetry or data tracking."
            )
        }
    }
}

@Composable
private fun EfficiencyItem(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 16.sp)
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
