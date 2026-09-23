package com.moody.moodyvideoeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.moody.moodyvideoeditor.ui.theme.MoodyVideoEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoodyVideoEditorTheme {
                AppNavigation()
            }
        }
    }
}