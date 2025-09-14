package com.example.simplenote

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.simplenote.MainActivity
import com.example.simplenote.SignInActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val getStartedButton = findViewById<Button>(R.id.getStartedButton)

        val masterKey = MasterKey.Builder(this@LauncherActivity)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            this@LauncherActivity,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val token = sharedPreferences.getString("access_token", "")!!
        sharedPreferences.edit {
            putBoolean("isUpdated", true)
        }

        if (token.isEmpty()) {
            getStartedButton.setOnClickListener {
                startActivity(Intent(this@LauncherActivity, SignInActivity::class.java))
                finish()
            }
            return
        }

        val client = OkHttpClient()
        val mediaType = "application/json".toMediaType()
        val body = """{
            "token": "$token"
        }""".trimIndent().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}/api/auth/token/verify/")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HTTP", "Failed: ${e.message}")
                Log.e("HTTP ERROR", "Failed: ${Log.getStackTraceString(e)}")
                getStartedButton.setOnClickListener {
                    startActivity(Intent(this@LauncherActivity, SignInActivity::class.java))
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                Log.e("HTTP", "${response.code}")
                if (response.code == 200) {
                    getStartedButton.setOnClickListener {
                        startActivity(Intent(this@LauncherActivity, MainActivity::class.java))
                        finish()
                    }
                } else {
                    getStartedButton.setOnClickListener {
                        startActivity(Intent(this@LauncherActivity, SignInActivity::class.java))
                        finish()
                    }
                }
            }
        })
    }
}