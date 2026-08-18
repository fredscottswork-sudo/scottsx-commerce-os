package com.scottsx.app.ui.screens

import androidx.compose.runtime.Composable

/** Seller messages inbox (mirrors the buyer inbox UI). */
@Composable
fun SellerMessagesScreen(onThreadClick: (String) -> Unit, onBack: () -> Unit = {}) {
    ConversationListScreen(title = "Seller Messages", onBack = onBack, onThreadClick = onThreadClick)
}
