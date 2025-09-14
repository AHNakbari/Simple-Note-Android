package com.example.simplenote

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.simplenote.SignUpActivity
import com.example.simplenote.core.util.showError
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class SignInActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val masterKey = MasterKey.Builder(this@SignInActivity)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            this@SignInActivity,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val token = sharedPreferences.getString("access_token", "")!!

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
            }

            override fun onResponse(call: Call, response: Response) {
                Log.e("HTTP", "${response.code}")
                if (response.code == 200) {
                    val intent = Intent(this@SignInActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        })

        val emailInput = findViewById<EditText>(R.id.emailEditText)
        val passwordInput = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerLink = findViewById<TextView>(R.id.registerLink)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()
        }

        loginButton.isEnabled = false

        emailInput.doAfterTextChanged {
            updateLoginButtonState(emailInput.text.toString(), passwordInput.text.toString(), loginButton)
        }

        passwordInput.doAfterTextChanged {
            updateLoginButtonState(emailInput.text.toString(), passwordInput.text.toString(), loginButton)
        }

        loginButton.setOnClickListener {
            loginButton.isEnabled = false
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()

            val client = OkHttpClient()
            val mediaType = "application/json".toMediaType()
            val body = """
                {
                    "username": "$email",
                    "password": "$password"
                }
            """.trimIndent().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("${BuildConfig.BASE_URL}/api/auth/token/")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    showError(this@SignInActivity, e.message ?: "Network error")
                    runOnUiThread {
                        loginButton.isEnabled = true
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        loginUser(response)
                    } else {
                        val errorBody = response.body?.string()
                        val errorMsg = try {
                            var ret = ""
                            for (i in 0 until JSONObject(errorBody).getJSONArray("errors").length()) {
                                ret += "${JSONObject(errorBody).getJSONArray("errors").getJSONObject(i).getString("detail")}\n"
                            }
                            ret
                        } catch (e: Exception) {
                            errorBody ?: "Unknown error"
                        }
                        showError(this@SignInActivity, errorMsg)
                        runOnUiThread {
                            loginButton.isEnabled = true
                        }
                    }
                }
            })
        }

        registerLink.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
            finish()
        }
    }

    private fun updateLoginButtonState(email: String, password: String, loginButton: Button) {
        loginButton.isEnabled = email.isNotEmpty() && password.isNotEmpty()
    }

    private fun loginUser(response: Response) {
        if (response.code == 200) {
            val jsonResponse = response.body!!.string()
            val jsonObject = JSONObject(jsonResponse)
            val accessToken = jsonObject.getString("access")
            val refreshToken = jsonObject.getString("refresh")
            val masterKey = MasterKey.Builder(this@SignInActivity)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                this@SignInActivity,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            sharedPreferences.edit {
                putString("access_token", accessToken)
                    .putString("refresh_token", refreshToken)
            }

            val client = OkHttpClient()
            val request = Request.Builder()
                .url("${BuildConfig.BASE_URL}/api/auth/userinfo/")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer ${accessToken}")
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("HTTP", "Failed: ${e.message}")
                    Log.e("HTTP ERROR", "Failed: ${Log.getStackTraceString(e)}")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.code == 200) {
                        val jsonResponse = response.body!!.string()
                        val jsonObject = JSONObject(jsonResponse)

                        sharedPreferences.edit {
                            putString("username", jsonObject.getString("username"))
                                .putString("email", jsonObject.getString("email"))
                                .putInt("id", jsonObject.getInt("id"))
                        }
                    }
                }
            })

            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            // todo: login error handling
        }
    }
}