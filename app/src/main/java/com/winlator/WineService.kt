package com.winlator

import android.app.Service
import android.content.Intent
import android.os.IBinder

class WineService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
