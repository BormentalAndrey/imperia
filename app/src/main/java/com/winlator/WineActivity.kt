package com.winlator

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class WineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val exePath = intent.getStringExtra("executable") ?: "unknown"
        Toast.makeText(this, "Wine: $exePath", Toast.LENGTH_LONG).show()
        finish()
    }
}
