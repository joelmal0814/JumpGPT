package com.example.jumpgpt.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.jumpgpt.ui.chat.ChatScreen
import com.example.jumpgpt.ui.chat.VoiceChatScreen
import com.example.jumpgpt.ui.history.HistoryScreen

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object History : Screen("history")
    object VoiceChat : Screen("voice_chat")
}

@Composable
fun JumpGPTNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Chat.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onVoiceChatClick = { navController.navigate(Screen.VoiceChat.route) },
                onHistoryClick = { navController.navigate(Screen.History.route) }
            )
        }
        
        composable(Screen.History.route) {
            HistoryScreen(
                onBackClick = { navController.navigateUp() },
                onConversationClick = { conversationId: String ->
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.VoiceChat.route) {
            VoiceChatScreen(
                onClose = { navController.navigateUp() },
                onSendVoiceMessage = { text -> 
                    navController.navigateUp()
                }
            )
        }
    }
} 