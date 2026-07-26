package ch.cclerc.luxapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalBackStack = staticCompositionLocalOf<LuxBackStack> {
    error("No LuxBackStack provided")
}

val LocalSheetController = staticCompositionLocalOf<SheetController> {
    error("No SheetController provided")
}

val LocalCoverController = staticCompositionLocalOf<CoverController> {
    error("No CoverController provided")
}

@Composable
fun AppChrome(
    modifier: Modifier = Modifier,
    backStack: LuxBackStack = remember { LuxBackStack() },
    sheetController: SheetController = remember { SheetController() },
    coverController: CoverController = remember { CoverController() },
    detentSheet: (@Composable () -> Unit)? = null,
    destinationContent: @Composable (LuxDestination) -> Unit,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalBackStack provides backStack,
        LocalSheetController provides sheetController,
        LocalCoverController provides coverController
    ) {
        Box(modifier.fillMaxSize()) {
            LuxBackStackHost(
                stack = backStack,
                rootContent = content,
                destinationContent = destinationContent
            )
            detentSheet?.invoke()
            SheetHost(sheetController)
            FullScreenCoverHost(coverController)
        }
    }
}
