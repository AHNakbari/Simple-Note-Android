package com.example.simplenote.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import android.content.Intent
import androidx.activity.OnBackPressedCallback
import android.app.Dialog
import android.graphics.Color
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.simplenote.R
import com.example.simplenote.activity.SignInActivity
import com.example.simplenote.activity.MainActivity

class ControlPanelFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        val goHome = {
            val intent = Intent(requireContext(), MainActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }
        view.findViewById<View>(R.id.backButton).setOnClickListener { goHome() }
        view.findViewById<View>(R.id.backText).setOnClickListener { goHome() }
        view.findViewById<View>(R.id.changePasswordRow)?.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_changePasswordFragment)
        }
        view.findViewById<View>(R.id.logoutRow)?.setOnClickListener { showLogoutDialog() }

        val usernameTextView = view.findViewById<TextView>(R.id.userName)
        val emailTextView = view.findViewById<TextView>(R.id.userEmail)
        val profileImageView = view.findViewById<ImageView>(R.id.profileImage)

        val masterKey = MasterKey.Builder(requireContext())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val sharedPreferences = EncryptedSharedPreferences.create(
            requireContext(),
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        usernameTextView.text = sharedPreferences.getString("username", "example examplian")
        emailTextView.text = sharedPreferences.getString("email", "something@example.com")

        // Pick a random avatar based on stored sex.
        // Ensure drawable names are lowercase: x_1..x_5 and y_1..y_4 in res/drawable.
        val sex = sharedPreferences.getString("sex", "man") ?: "man"
        val resId = if (sex.equals("woman", true)) {
            val pool = intArrayOf(
                R.drawable.y_1, R.drawable.y_2, R.drawable.y_3, R.drawable.y_4
            )
            pool.random()
        } else {
            val pool = intArrayOf(
                R.drawable.x_1, R.drawable.x_2, R.drawable.x_3, R.drawable.x_4, R.drawable.x_5
            )
            pool.random()
        }
        profileImageView.setImageResource(resId)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { goHome() }
            }
        )
    }

    private fun showLogoutDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_logout)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog.setCancelable(true)

        dialog.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.btnYes).setOnClickListener {
            val masterKey = MasterKey.Builder(requireContext())
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val sharedPreferences = EncryptedSharedPreferences.create(
                requireContext(),
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            sharedPreferences.edit {
                putString("access_token", "")
                putString("refresh_token", "")
            }
            dialog.dismiss()
            val intent = Intent(requireContext(), SignInActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
}
