package com.pokke.music.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.window.DialogWindowProvider

/** Hosts the full player in the dock-aware sheet used by the original player motion system. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMotionSheet(
    dock: PlayerDock,
    motion: PlayerSheetMotion,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            // Allow hide so the sheet actually dismisses (was returning false → black frame)
            if (target == SheetValue.Hidden) {
                motion.close()
            }
            true
        },
    )
    val windowInfo = LocalWindowInfo.current
    val view = LocalView.current

    SideEffect {
        motion.sheet = sheetState
        motion.windowInfo = windowInfo
        dock.sheetTravel = windowInfo.containerSize.height.toFloat()
        dock.sheetOffset = { motion.position() }
    }
    val playerWindow = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(motion, playerWindow) {
        motion.attachWindow(playerWindow)
        onDispose { }
    }
    LaunchedEffect(Unit) { motion.open() }
    DisposableEffect(motion, dock) {
        onDispose {
            motion.onSheetGone()
            dock.sheetOffset = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = { motion.close() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .onGloballyPositioned { motion.laidOut = true }
                .offset { IntOffset(0, motion.contentOffsetPx()) },
        ) {
            content(motion::close)
        }
    }
}
