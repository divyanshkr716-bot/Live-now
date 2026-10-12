package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChannelStreamStatus
import com.example.model.MultiStreamStatus
import com.example.model.StreamChannel
import com.example.model.StreamConfig
import com.example.model.StreamPreset
import com.example.model.StreamStateEnum
import com.example.model.VideoMetadata
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.YouTubeRed
import com.example.ui.theme.YouTubeRedDark
import com.example.ui.theme.YouTubeRedGlow
import com.example.util.BatteryInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val channels by viewModel.channels.collectAsState()
    val defaultVideo by viewModel.defaultVideo.collectAsState()
    val channelVideos by viewModel.channelVideos.collectAsState()
    val multiStreamStatus by viewModel.multiStreamStatus.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()

    var showLogsDialog by remember { mutableStateOf(false) }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var showAmoledSaver by remember { mutableStateOf(false) }
    var channelToPickVideoFor by remember { mutableStateOf<String?>(null) }

    // Launcher for default video picker
    val defaultVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onDefaultVideoSelected(uri)
        }
    }

    // Launcher for per-channel custom video picker
    val channelCustomVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val channelId = channelToPickVideoFor
        if (uri != null && channelId != null) {
            viewModel.onChannelCustomVideoSelected(channelId, uri)
        }
        channelToPickVideoFor = null
    }

    // Request notification permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showAmoledSaver) {
        BackHandler { showAmoledSaver = false }
        AmoledSaverScreen(
            batteryInfo = batteryInfo,
            multiStreamStatus = multiStreamStatus,
            channels = channels,
            onDismiss = { showAmoledSaver = false }
        )
        return
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCanvas),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = YouTubeRed,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "YouTube Loop Live",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "MULTI",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // AMOLED Black Screen Mode Button
                    IconButton(
                        onClick = { showAmoledSaver = true },
                        modifier = Modifier.testTag("amoled_saver_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brightness2,
                            contentDescription = "AMOLED Saver",
                            tint = Color(0xFFAAAAAA)
                        )
                    }
                    // Real-time Logs Terminal Button
                    IconButton(
                        onClick = { showLogsDialog = true },
                        modifier = Modifier.testTag("logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Logs",
                            tint = Color(0xFFAAAAAA)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkCanvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Live Status Banner
            MultiStreamSummaryBanner(
                multiStatus = multiStreamStatus,
                batteryInfo = batteryInfo,
                onDisableBatteryOptimization = { viewModel.requestDisableBatteryOptimization() }
            )

            // Validation Error Banner
            if (validationError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF331414)),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(ErrorRed, ErrorRed)))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                        Text(
                            text = validationError ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFFFB4AB)),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearValidationError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.LightGray)
                        }
                    }
                }
            }

            // Global Shared Video Card
            DefaultVideoCard(
                video = defaultVideo,
                onSelectVideo = { defaultVideoPickerLauncher.launch("video/*") }
            )

            // Multi-Stream Action Bar (Start All / Stop All / Add Channel)
            MultiStreamActionBar(
                channels = channels,
                multiStatus = multiStreamStatus,
                onStartAll = { viewModel.startAllChannels() },
                onStopAll = { viewModel.stopAllChannels() },
                onAddChannel = { showAddChannelDialog = true }
            )

            // Channels List
            Text(
                text = "Live Stream Channels (${channels.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            channels.forEachIndexed { index, channel ->
                val chStatus = multiStreamStatus.getStatusForChannel(channel.id, channel.name)
                val customVideo = channelVideos[channel.id]
                val effectiveVideo = customVideo ?: defaultVideo
                val isKeyVisible = viewModel.isKeyVisibleForChannel(channel.id)

                ChannelCard(
                    channel = channel,
                    channelIndex = index + 1,
                    status = chStatus,
                    effectiveVideo = effectiveVideo,
                    isKeyVisible = isKeyVisible,
                    canDelete = channels.size > 1,
                    onStart = { viewModel.startSingleChannel(channel.id) },
                    onStop = { viewModel.stopSingleChannel(channel.id) },
                    onUpdateName = { viewModel.updateChannelName(channel.id, it) },
                    onUpdateKey = { viewModel.updateChannelStreamKey(channel.id, it) },
                    onUpdateServerUrl = { viewModel.updateChannelServerUrl(channel.id, it) },
                    onSelectPreset = { viewModel.selectPresetForChannel(channel.id, it) },
                    onToggleKeyVisibility = { viewModel.toggleKeyVisibilityForChannel(channel.id) },
                    onDuplicate = { viewModel.duplicateChannel(channel.id) },
                    onDelete = { viewModel.removeChannel(channel.id) },
                    onPickCustomVideo = {
                        channelToPickVideoFor = channel.id
                        channelCustomVideoPickerLauncher.launch("video/*")
                    },
                    onClearCustomVideo = { viewModel.clearChannelCustomVideo(channel.id) }
                )
            }

            // Android 9+ & Battery Optimization Info Card
            BatteryOptimizationCard(
                batteryInfo = batteryInfo,
                onRequestBatteryExemption = { viewModel.requestDisableBatteryOptimization() }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Add Channel Dialog
    if (showAddChannelDialog) {
        AddChannelDialog(
            defaultNumber = channels.size + 1,
            onDismiss = { showAddChannelDialog = false },
            onAdd = { name, serverUrl, streamKey, preset ->
                viewModel.addChannel(name, serverUrl, streamKey, preset)
                showAddChannelDialog = false
            }
        )
    }

    // Live Logs Dialog
    if (showLogsDialog) {
        LogsDialog(
            logs = multiStreamStatus.logs,
            onDismiss = { showLogsDialog = false }
        )
    }
}

@Composable
fun MultiStreamSummaryBanner(
    multiStatus: MultiStreamStatus,
    batteryInfo: BatteryInfo,
    onDisableBatteryOptimization: () -> Unit
) {
    val liveCount = multiStatus.activeLiveCount
    val isAnyLive = liveCount > 0

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAnyLive) Color(0xFF1E281F) else DarkSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (isAnyLive) listOf(LiveGreen, Color(0xFF00B0FF))
                else listOf(DarkSurfaceBorder, DarkSurfaceBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAnyLive) LiveGreen.copy(alpha = alphaAnim)
                                else Color.Gray
                            )
                    )
                    Text(
                        text = if (isAnyLive) "🔴 $liveCount STREAM(S) LIVE" else "ALL STREAMS OFFLINE",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isAnyLive) LiveGreen else Color.LightGray
                        )
                    )
                }

                // Battery Chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DarkSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = null,
                            tint = if (batteryInfo.isCharging) LiveGreen else Color(0xFF81C784),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${batteryInfo.levelPercent}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "• Eco 🔋",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA))
                        )
                    }
                }
            }

            if (isAnyLive) {
                Divider(color = DarkSurfaceBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "TOTAL BITRATE",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF888888))
                        )
                        Text(
                            text = "${multiStatus.totalBitrateKbps} kbps",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "BACKGROUND SERVICE",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF888888))
                        )
                        Text(
                            text = "Screen can be turned OFF",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DefaultVideoCard(
    video: VideoMetadata?,
    onSelectVideo: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = YouTubeRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Default Looping Video",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                OutlinedButton(
                    onClick = onSelectVideo,
                    modifier = Modifier.testTag("select_default_video_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (video != null) "Change Video" else "Choose Video")
                }
            }

            if (video != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = video.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Resolution: ${video.formattedResolution} • Duration: ${video.formattedDuration} • Size: ${video.formattedSize}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFAAAAAA))
                        )
                    }
                }
            } else {
                Text(
                    text = "Tap 'Choose Video' to pick a video from your phone storage. It will loop continuously 24/7 across all channels.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF999999))
                )
            }
        }
    }
}

@Composable
fun MultiStreamActionBar(
    channels: List<StreamChannel>,
    multiStatus: MultiStreamStatus,
    onStartAll: () -> Unit,
    onStopAll: () -> Unit,
    onAddChannel: () -> Unit
) {
    val isAnyStreaming = multiStatus.isAnyStreaming

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isAnyStreaming) {
            Button(
                onClick = onStartAll,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("start_all_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "START ALL (${channels.count { it.isEnabled }})",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        } else {
            Button(
                onClick = onStopAll,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("stop_all_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "STOP ALL",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        OutlinedButton(
            onClick = onAddChannel,
            modifier = Modifier
                .height(48.dp)
                .testTag("add_channel_button"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("+ Add Channel")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelCard(
    channel: StreamChannel,
    channelIndex: Int,
    status: ChannelStreamStatus,
    effectiveVideo: VideoMetadata?,
    isKeyVisible: Boolean,
    canDelete: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateKey: (String) -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    onSelectPreset: (StreamPreset) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onPickCustomVideo: () -> Unit,
    onClearCustomVideo: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var isEditingName by remember { mutableStateOf(false) }
    var tempName by remember(channel.name) { mutableStateOf(channel.name) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    val isLive = status.state == StreamStateEnum.LIVE
    val isStarting = status.state == StreamStateEnum.STARTING
    val isReconnecting = status.state == StreamStateEnum.RECONNECTING

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                when {
                    isLive -> listOf(LiveGreen, LiveGreen)
                    isStarting -> listOf(WarningAmber, WarningAmber)
                    isReconnecting -> listOf(Color(0xFF00B0FF), Color(0xFF00B0FF))
                    status.state == StreamStateEnum.ERROR -> listOf(ErrorRed, ErrorRed)
                    else -> listOf(DarkSurfaceBorder, DarkSurfaceBorder)
                }
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Channel Name and Live Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditingName) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleSmall.copy(color = Color.White)
                        )
                        IconButton(onClick = {
                            if (tempName.isNotBlank()) onUpdateName(tempName.trim())
                            isEditingName = false
                        }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Save", tint = LiveGreen)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isEditingName = true }
                    ) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1
                        )
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Name",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Channel Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (status.state) {
                        StreamStateEnum.LIVE -> LiveGreen.copy(alpha = 0.2f)
                        StreamStateEnum.STARTING -> WarningAmber.copy(alpha = 0.2f)
                        StreamStateEnum.RECONNECTING -> Color(0xFF00B0FF).copy(alpha = 0.2f)
                        StreamStateEnum.ERROR -> ErrorRed.copy(alpha = 0.2f)
                        else -> Color(0xFF333333)
                    }
                ) {
                    Text(
                        text = when (status.state) {
                            StreamStateEnum.LIVE -> "🔴 LIVE"
                            StreamStateEnum.STARTING -> "⏳ STARTING"
                            StreamStateEnum.RECONNECTING -> "🔄 RECONNECTING"
                            StreamStateEnum.ERROR -> "⚠️ ERROR"
                            else -> "OFFLINE"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = when (status.state) {
                                StreamStateEnum.LIVE -> LiveGreen
                                StreamStateEnum.STARTING -> WarningAmber
                                StreamStateEnum.RECONNECTING -> Color(0xFF00B0FF)
                                StreamStateEnum.ERROR -> ErrorRed
                                else -> Color.LightGray
                            }
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Actions: Duplicate / Delete
                IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
                if (canDelete && !status.isStreaming) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Live Telemetry Bar when Live
            if (isLive || isReconnecting) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DURATION", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontSize = 10.sp))
                            Text(status.formattedDuration, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BITRATE", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontSize = 10.sp))
                            Text("${status.currentBitrateKbps} kbps", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = LiveGreen))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LOOPS", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray, fontSize = 10.sp))
                            Text("#${status.loopCount}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        }
                    }
                }
            }

            // Stream Key Input
            OutlinedTextField(
                value = channel.streamKey,
                onValueChange = onUpdateKey,
                label = { Text("YouTube Stream Key") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stream_key_input_${channel.id}"),
                singleLine = true,
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = onToggleKeyVisibility) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility",
                                tint = Color.Gray
                            )
                        }
                        IconButton(onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrEmpty()) onUpdateKey(clip.trim())
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.Gray)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = YouTubeRed,
                    unfocusedBorderColor = DarkSurfaceBorder
                )
            )

            // Preset Quality Chips
            Text(
                text = "Encoding Preset (Android 9+ Optimized):",
                style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamPreset.values().forEach { preset ->
                    val isSelected = channel.activePreset == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPreset(preset) },
                        label = {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (preset == StreamPreset.ULTRA_BATTERY) Color(0xFF2E7D32) else YouTubeRed,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceElevated,
                            labelColor = Color.LightGray
                        )
                    )
                }
            }

            // Custom Video for this channel (optional)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (channel.customVideoName != null) "Custom Video: ${channel.customVideoName}"
                        else "Using Default Looping Video",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (channel.customVideoName != null) Color(0xFF81C784) else Color.LightGray
                        ),
                        maxLines = 1
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (channel.customVideoUri != null) {
                        TextButton(onClick = onClearCustomVideo) {
                            Text("Use Default", style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray))
                        }
                    }
                    TextButton(onClick = onPickCustomVideo) {
                        Text(if (channel.customVideoUri != null) "Change" else "+ Override Video", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Advanced RTMP Destination
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedSettings = !showAdvancedSettings },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showAdvancedSettings) "Hide Server URL" else "Server: ${channel.serverUrl.take(30)}...",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                )
                Text(
                    text = if (showAdvancedSettings) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                )
            }

            if (showAdvancedSettings) {
                OutlinedTextField(
                    value = channel.serverUrl,
                    onValueChange = onUpdateServerUrl,
                    label = { Text("RTMP Server Endpoint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = YouTubeRed,
                        unfocusedBorderColor = DarkSurfaceBorder
                    )
                )
            }

            // Start / Stop Action Button for this Channel
            if (!status.isStreaming) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("start_channel_${channel.id}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "START LIVE: ${channel.name.uppercase()}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("stop_channel_${channel.id}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STOP STREAM",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationCard(
    batteryInfo: BatteryInfo,
    onRequestBatteryExemption: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = LiveGreen,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Ultra Battery & 24/7 Background System",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Text(
                text = "• Hardware H.264 Encoder: Runs on dedicated silicon, keeping CPU idle\n" +
                        "• Auto-Software Fallback: Supports 2+ streams on Android 9+ smoothly\n" +
                        "• Native PowerLocks: Streams 24/7 with the screen turned completely OFF\n" +
                        "• AMOLED Black Screen Mode: Reduces display battery to 0% on OLED screens",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.LightGray,
                    lineHeight = 18.sp
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (batteryInfo.isIgnoringOptimizations) "✅ Battery Optimization: Exempt"
                    else "⚠️ Battery Optimization: Enabled",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (batteryInfo.isIgnoringOptimizations) LiveGreen else WarningAmber,
                        fontWeight = FontWeight.Bold
                    )
                )

                if (!batteryInfo.isIgnoringOptimizations) {
                    OutlinedButton(
                        onClick = onRequestBatteryExemption,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Exempt App", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun AddChannelDialog(
    defaultNumber: Int,
    onDismiss: () -> Unit,
    onAdd: (name: String, serverUrl: String, streamKey: String, preset: StreamPreset) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var name by remember { mutableStateOf("Channel #$defaultNumber") }
    var serverUrl by remember { mutableStateOf(StreamConfig.DEFAULT_SERVER_URL) }
    var streamKey by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf(StreamPreset.BALANCED_HD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Multi-Live Target",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Stream simultaneously to another YouTube channel, or another broadcast event on the same channel!",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = streamKey,
                    onValueChange = { streamKey = it },
                    label = { Text("Stream Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrEmpty()) streamKey = clip.trim()
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.Gray)
                        }
                    }
                )

                Text(
                    text = "Quality Preset:",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.Gray)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StreamPreset.values().forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { selectedPreset = preset },
                            label = { Text(preset.label.take(12), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name.trim(), serverUrl.trim(), streamKey.trim(), selectedPreset) },
                colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
            ) {
                Text("Add Channel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun LogsDialog(
    logs: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = LiveGreen)
                    Text(
                        text = "Real-Time Multi-Live Logs",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }
        },
        text = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No logs recorded yet.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { entry ->
                            val color = when {
                                entry.contains("LIVE", ignoreCase = true) -> LiveGreen
                                entry.contains("error", ignoreCase = true) || entry.contains("failed", ignoreCase = true) -> ErrorRed
                                entry.contains("reconnect", ignoreCase = true) -> WarningAmber
                                else -> Color(0xFFB0BEC5)
                            }
                            Text(
                                text = entry,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = color
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White)
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
fun AmoledSaverScreen(
    batteryInfo: BatteryInfo,
    multiStreamStatus: MultiStreamStatus,
    channels: List<StreamChannel>,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "faintPulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(animation = tween(1500), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(alphaAnim)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (multiStreamStatus.activeLiveCount > 0) LiveGreen else Color.Gray)
                )
                Text(
                    text = "${multiStreamStatus.activeLiveCount} STREAM(S) LIVE 24/7",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                )
            }

            Text(
                text = "AMOLED ECO MODE ACTIVE",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.DarkGray,
                    letterSpacing = 1.sp
                )
            )

            Text(
                text = "Display Power: ~0% • Battery: ${batteryInfo.levelPercent}%",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray)
            )

            multiStreamStatus.channelStatuses.values
                .filter { it.state == StreamStateEnum.LIVE }
                .forEach { ch ->
                    Text(
                        text = "${ch.channelName}: ${ch.formattedDuration} (${ch.currentBitrateKbps} kbps)",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray)
                    )
                }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "[ Tap anywhere to wake screen ]",
                style = MaterialTheme.typography.labelSmall.copy(color = Color.DarkGray)
            )
        }
    }
}
