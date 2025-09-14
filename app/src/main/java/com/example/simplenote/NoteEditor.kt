package com.example.simplenote

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.simplenote.core.AppDatabase
import com.example.simplenote.core.NoteRepository
import com.example.simplenote.core.util.UserManager
import com.example.simplenote.databinding.FragmentNoteEditorBinding
import com.example.simplenote.core.Note
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class NoteEditor : Fragment() {
    private var _binding: FragmentNoteEditorBinding? = null
    private val binding get() = _binding!!

    private var lastEdited: Date = Date()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private var currentNote: Note? = null
    private var noteId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentNoteEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        // Get note ID from arguments if editing existing note
        noteId = arguments?.getInt("noteId", -1) ?: -1
        if (noteId != -1) {
            loadExistingNote()
        }

        val goHome = {
            saveNoteToDb()
            val intent = Intent(requireContext(), MainActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }
        binding.backButton.setOnClickListener { goHome() }
        binding.backText.setOnClickListener { goHome() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goHome()
                }
            })
        binding.btnDeleteBottom.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                lastEdited = Date()
                updateLastEdited()
            }

            override fun afterTextChanged(s: Editable?) {}
        }
        binding.editTitle.addTextChangedListener(watcher)
        binding.editContent.addTextChangedListener(watcher)

        updateLastEdited()
    }

    private fun loadExistingNote() {
        val db = AppDatabase.getDatabase(requireContext())
        val repo = NoteRepository(db)
        CoroutineScope(Dispatchers.IO).launch {
            val username = UserManager.getCurrentUsername(requireContext())
            val note = repo.getNoteById(noteId.toLong(), username)
            note?.let {
                currentNote = it
                CoroutineScope(Dispatchers.Main).launch {
                    binding.editTitle.setText(it.title)
                    binding.editContent.setText(it.content)
                    lastEdited = Date(it.lastEdited)
                    updateLastEdited()
                }
            }
        }
    }

    private fun updateLastEdited() {
        binding.lastEditedText.text = "Last edited on ${dateFormat.format(lastEdited)}"
    }

    private fun  saveNoteToDb() {
        val title = binding.editTitle.text.toString().trim()
        val content = binding.editContent.text.toString().trim()
        if (title.isNotBlank() || content.isNotBlank()) {
            val username = UserManager.getCurrentUsername(requireContext())
            val db = AppDatabase.getDatabase(requireContext())
            val repo = NoteRepository(db)


            CoroutineScope(Dispatchers.IO).launch {
                val createNote = currentNote == null
                val userId = if (!createNote) {
                    // Update existing note
                    currentNote!!.title = title
                    currentNote!!.content = content
                    currentNote!!.lastEdited = System.currentTimeMillis()
                    currentNote!!.isSynced = false
                    repo.update(currentNote!!)
                    currentNote!!.userId
                } else {
                    // Create new note
                    val note = Note(
                        title = title,
                        content = content,
                        lastEdited = System.currentTimeMillis(),
                        username = username
                    )
                    val generatedId = repo.insert(note)
                    val noteCopy = note.copy(id=generatedId, userId=generatedId)
                    repo.update(noteCopy)
                    generatedId
                }
                CoroutineScope(Dispatchers.Default).launch {
                    updateServer(title, content, userId, createNote)
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        setFragmentResultListener(
            requestKey = ConfirmDeleteSheet.DELETE_RESULT,
            listener = { _, _ ->
                deleteNote()
            }
        )
        findNavController().navigate(
            R.id.noteEditorFragment_delete_dialog
        )
    }

    private fun deleteNote() {
        currentNote?.let { note ->
            val db = AppDatabase.getDatabase(requireContext())
            val repo = NoteRepository(db)
            CoroutineScope(Dispatchers.IO).launch {
                repo.delete(note)
                updateServer("", "", note.userId, false, true)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(requireContext(), "Note deleted", Toast.LENGTH_SHORT).show()
                    val intent = Intent(requireContext(), MainActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateServer(title: String, content: String, user_id: Long, createNote: Boolean, deleteNote: Boolean = false) {
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
        val token = sharedPreferences.getString("access_token", "")!!
        val client = OkHttpClient()
        val mediaType = "application/json".toMediaType()
        val body = """
                {
                    "description": "$content",
                    "title": "$title",
                    "user_id": $user_id
                }
            """.trimIndent().toRequestBody(mediaType)
        var request = Request.Builder()
        request = if (createNote) request.url("${BuildConfig.BASE_URL}/api/notes/").post(body)
        else if (deleteNote) request.url("${BuildConfig.BASE_URL}/api/notes/${user_id}/").delete()
        else request.url("${BuildConfig.BASE_URL}/api/notes/${user_id}/").put(body)

        val finalRequest = request.addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer ${token}")
            .build()
        client.newCall(finalRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
            }

            override fun onResponse(call: Call, response: Response) {
            }
        })
    }
} 