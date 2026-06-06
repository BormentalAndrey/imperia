package com.imperia.emulator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this).apply {
            text = "Imperia Emulator\n\nReady"
            textSize = 24f
            setPadding(64, 64, 64, 64)
        }
        
        setContentView(textView)
    }
}
