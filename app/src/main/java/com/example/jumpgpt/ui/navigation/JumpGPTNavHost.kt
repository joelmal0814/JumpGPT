package com.example.jumpgpt.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jumpgpt.ui.chat.ChatScreen
import com.example.jumpgpt.ui.chat.VoiceChatScreen
import com.example.jumpgpt.ui.chat.ChatViewModel

sealed class Screen(val route: String) {
    object Chat : Screen("chat") {
        const val ROUTE_WITH_ARGS = "chat?conversationId={conversationId}"
        
        fun createRoute(conversationId: String? = null): String {
            return if (conversationId != null) {
                "chat?conversationId=$conversationId"
            } else {
                "chat"
            }
        }
    }
    object VoiceChat : Screen("voice_chat/{conversationId}") {
        fun createRoute(conversationId: String) = "voice_chat/$conversationId"
    }
}

@Composable
fun JumpGPTNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Chat.route
) {
    val chatViewModel = hiltViewModel<ChatViewModel>()
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Base route without arguments
        composable(route = Screen.Chat.route) {
            ChatScreen(
                onVoiceChatClick = { conversationId -> 
                    if (conversationId.isNotEmpty()) {
                        navController.navigate(Screen.VoiceChat.createRoute(conversationId))
                    } else {
                        // Create a new conversation first
                        chatViewModel.createNewConversation { newId ->
                            navController.navigate(Screen.VoiceChat.createRoute(newId))
                        }
                    }
                },
                onHistoryClick = { /* No longer needed since we're using a drawer */ }
            )
        }
        
        // Route with optional conversationId
        composable(
            route = Screen.Chat.ROUTE_WITH_ARGS,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            ChatScreen(
                onVoiceChatClick = { conversationId -> 
                    if (conversationId.isNotEmpty()) {
                        navController.navigate(Screen.VoiceChat.createRoute(conversationId))
                    } else {
                        // Create a new conversation first
                        chatViewModel.createNewConversation { newId ->
                            navController.navigate(Screen.VoiceChat.createRoute(newId))
                        }
                    }
                },
                onHistoryClick = { /* No longer needed since we're using a drawer */ }
            )
        }
        
        composable(
            route = Screen.VoiceChat.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId")
                ?: throw IllegalStateException("Conversation ID is required")
            
            VoiceChatScreen(
                onClose = { 
                    // Navigate back to the chat screen with the conversation ID
                    navController.navigate(Screen.Chat.createRoute(conversationId)) {
                        popUpTo(Screen.Chat.route) {
                            inclusive = true
                        }
                    }
                },
                onMessageSent = { /* Messages are handled by the ViewModel */ },
                viewModel = hiltViewModel(),
                activeConversationId = conversationId
            )
        }
    }
} 