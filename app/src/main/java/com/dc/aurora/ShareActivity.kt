package com.dc.aurora

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * 다른 앱(카카오톡, 문자, 메모 등)에서 텍스트를 공유할 때 진입점.
 * 받은 텍스트를 MainActivity로 전달하고 종료.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent
            ?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?: run { finish(); return }

        // MainActivity를 새로 시작하거나 기존 인스턴스에 shared text 전달
        val main = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SHARED_TEXT, sharedText)
        }
        startActivity(main)
        finish()
    }
}
