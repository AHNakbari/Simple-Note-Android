package com.example.simplenote.core.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast

fun showError(context: Context, message: String, button: Button? = null) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    button?.isEnabled = true
} 