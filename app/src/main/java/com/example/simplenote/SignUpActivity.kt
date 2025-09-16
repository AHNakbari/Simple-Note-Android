package com.example.simplenote.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.simplenote.BuildConfig
import com.example.simplenote.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import com.example.simplenote.core.util.showError

class SignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val firstNameInput = findViewById<EditText>(R.id.firstName)
        val lastNameInput = findViewById<EditText>(R.id.lastName)
        val usernameInput = findViewById<EditText>(R.id.username)
        val emailInput = findViewById<EditText>(R.id.emailAddress)
        val passwordInput = findViewById<EditText>(R.id.password)
        val retypePasswordInput = findViewById<EditText>(R.id.retypePassword)
        val sexGroup = findViewById<RadioGroup>(R.id.sexGroup)

        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginLink = findViewById<TextView>(R.id.loginLink)

        registerButton.isEnabled = false

        fun refreshEnable() = updateRegisterButtonState(
            firstNameInput.text.toString(),
            lastNameInput.text.toString(),
            usernameInput.text.toString(),
            emailInput.text.toString(),
            passwordInput.text.toString(),
            retypePasswordInput.text.toString(),
            sexGroup.checkedRadioButtonId != -1,
            registerButton
        )

        firstNameInput.doAfterTextChanged { refreshEnable() }
        lastNameInput.doAfterTextChanged { refreshEnable() }
        usernameInput.doAfterTextChanged { refreshEnable() }
        emailInput.doAfterTextChanged { refreshEnable() }
        passwordInput.doAfterTextChanged { refreshEnable() }
        retypePasswordInput.doAfterTextChanged { refreshEnable() }
        sexGroup.setOnCheckedChangeListener { _, _ -> refreshEnable() }

        registerButton.setOnClickListener {
            val firstName = firstNameInput.text.toString()
            val lastName = lastNameInput.text.toString()
            val username = usernameInput.text.toString()
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()
            val retypePassword = retypePasswordInput.text.toString()
            val sex = when (sexGroup.checkedRadioButtonId) {
                R.id.rbMan -> "man"
                R.id.rbWoman -> "woman"
                else -> ""
            }

            val client = OkHttpClient()
            val mediaType = "application/json".toMediaType()
            // Include "sex" in payload (backend may ignore it; safe to send)
            val body = """{  
                "password": "$password",
                "email": "$email",
                "username": "$username",
                "first_name": "$firstName",
                "last_name": "$lastName",
                "sex": "$sex"
            }""".trimIndent().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("${BuildConfig.BASE_URL}/api/auth/register/")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    showError(this@SignUpActivity, e.message ?: "Network error")
                    runOnUiThread { registerButton.isEnabled = true }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.code == 201) {
                        // Save chosen sex locally immediately (in case userinfo doesn't include it)
                        val masterKey = MasterKey.Builder(this@SignUpActivity)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .build()
                        val sharedPreferences = EncryptedSharedPreferences.create(
                            this@SignUpActivity,
                            "secure_prefs",
                            masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                        )
                        sharedPreferences.edit { putString("sex", sex) }

                        loginUser(username, password)
                    } else {
                        val errorBody = response.body?.string()
                        val errorMsg = try {
                            var ret = ""
                            val arr = JSONObject(errorBody).optJSONArray("errors")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    ret += "${arr.getJSONObject(i).optString("detail")}\n"
                                }
                                ret
                            } else errorBody ?: "Unknown error"
                        } catch (e: Exception) {
                            errorBody ?: "Unknown error"
                        }
                        showError(this@SignUpActivity, errorMsg)
                        runOnUiThread { registerButton.isEnabled = true }
                    }
                }
            })
        }

        loginLink.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun updateRegisterButtonState(
        firstName: String,
        lastName: String,
        username: String,
        email: String,
        password: String,
        retypePassword: String,
        sexSelected: Boolean,
        registerButton: Button
    ) {
        val emailPattern = Regex("^[\\w\\.-]+@[\\w\\.-]+\\.[a-zA-Z]{2,}$")
        registerButton.isEnabled =
            firstName.isNotEmpty() &&
            lastName.isNotEmpty() &&
            username.isNotEmpty() &&
            email.isNotEmpty() &&
            password.isNotEmpty() &&
            password == retypePassword &&
            emailPattern.matches(email) &&
            sexSelected
    }

    private fun loginUser(username: String, password: String) {
        val client = OkHttpClient()
        val mediaType = "application/json".toMediaType()
        val body = """{
            "username": "$username",
            "password": "$password"
        }""".trimIndent().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${BuildConfig.BASE_URL}/api/auth/token/")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HTTP", "Failed: ${e.message}")
                Log.e("HTTP ERROR", "Failed: ${Log.getStackTraceString(e)}")
                val intent = Intent(this@SignUpActivity, SignInActivity::class.java)
                startActivity(intent)
                finish()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code == 200) {
                    val jsonResponse = response.body!!.string()
                    val jsonObject = JSONObject(jsonResponse)
                    val accessToken = jsonObject.getString("access")
                    val refreshToken = jsonObject.getString("refresh")
                    val masterKey = MasterKey.Builder(this@SignUpActivity)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    val sharedPreferences = EncryptedSharedPreferences.create(
                        this@SignUpActivity,
                        "secure_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )

                    sharedPreferences.edit {
                        putString("access_token", accessToken)
                        putString("refresh_token", refreshToken)
                    }

                    val userinfoClient = OkHttpClient()
                    val request = Request.Builder()
                        .url("${BuildConfig.BASE_URL}/api/auth/userinfo/")
                        .addHeader("Accept", "application/json")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                    userinfoClient.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.e("HTTP", "Failed: ${e.message}")
                            Log.e("HTTP ERROR", "Failed: ${Log.getStackTraceString(e)}")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            if (response.code == 200) {
                                val jsonResponse = response.body!!.string()
                                val jo = JSONObject(jsonResponse)
                                sharedPreferences.edit {
                                    putString("username", jo.getString("username"))
                                    putString("email", jo.getString("email"))
                                    putInt("id", jo.getInt("id"))
                                    if (jo.has("sex") && !jo.isNull("sex")) {
                                        putString("sex", jo.getString("sex"))
                                    }
                                }
                            }
                        }
                    })

                    val intent = Intent(this@SignUpActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    val intent = Intent(this@SignUpActivity, SignInActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        })
    }
}
