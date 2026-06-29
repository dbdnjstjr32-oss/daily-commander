package com.dc.aurora

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dc.aurora.ui.AuroraScreen
import com.dc.aurora.ui.ChatViewModel
import com.dc.aurora.ui.theme.AuroraTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHARED_TEXT  = "shared_text"
        const val EXTRA_PREFILL_TEXT = "prefill_text"
    }

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            AuroraTheme {
                AuroraScreen(vm = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_PREFILL_TEXT)?.let {
            viewModel.prefill(it)
            return
        }
        intent?.getStringExtra(EXTRA_SHARED_TEXT)?.let {
            viewModel.prefill(it)
        }
    }
}
