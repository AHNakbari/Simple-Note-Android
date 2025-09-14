package com.example.simplenote

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.simplenote.core.AppDatabase
import com.example.simplenote.core.NoteRepository
import com.example.simplenote.core.util.UserManager
import com.example.simplenote.databinding.FragmentFirstBinding
import com.example.simplenote.core.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class Feed : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    private val binding get() = _binding!!

    private lateinit var notes: List<Note>
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var repo: NoteRepository

    private val username by lazy { UserManager.getCurrentUsername(requireContext()) }

    private val pageSize = 8
    private var currentPage = 0
    private var isEndPage = false
    private var lastUpdate: Date? = null
    private val firstUpdate by lazy { UserManager.getCurrentIsUpdated(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        repo = NoteRepository(db)

        setupRecyclerView()
        setupSearchView()
        loadNotes(page = currentPage++)

        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        binding.fabAddNote.setOnClickListener {
            findNavController().navigate(R.id.action_firstFragment_to_noteEditorFragment)
        }
        binding.navSettings.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SettingsFragment)
        }

        lifecycleScope.launch(Dispatchers.Default) {
            updateLocalDatabase()
        }
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter { note ->
            updateLocalDatabase()
            val bundle = Bundle().apply { putInt("noteId", note.id.toInt()) }
            findNavController().navigate(R.id.action_firstFragment_to_noteEditorFragment, bundle)
        }
        binding.recyclerView.adapter = noteAdapter
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                synchronized("page_load") {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= pageSize
                    ) {
                        loadNotes(currentPage++)
                    }
                }
            }
        })

    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                noteAdapter.clear()
                val query = newText?.trim() ?: ""
                if (query.isEmpty()) {
                    currentPage = 0
                    loadNotes(currentPage++)
                } else {
                    lifecycleScope.launch(Dispatchers.Main) {
                        val list = repo.searchNotes(username = username, query = query)
                        noteAdapter.updateNotes(list)
                    }
                }
                return true
            }
        })
    }

    private fun loadNotes(page: Int) {
        if (isEndPage) return
        lifecycleScope.launch(Dispatchers.Default) {
            delay(300)

            notes = repo.getAllNotes(
                username = username,
                limit = pageSize,
                offset = (page * pageSize)
            ).sortedWith(
                compareByDescending<Note> { it.isPinned }
                    .thenByDescending { it.lastEdited }
            )
            isEndPage = notes.isEmpty()
            lifecycleScope.launch(Dispatchers.Main) {
                noteAdapter.updateNotes(notes)
                if (page == 0) setVisibilities(notes.isEmpty())
            }
        }
    }

    private fun setVisibilities(isDataEmpty: Boolean) {
        binding.recyclerView.isVisible = !isDataEmpty
        binding.searchCard.isVisible = !isDataEmpty
        binding.notesTitle.isVisible = !isDataEmpty
        binding.emptyIllustration.isVisible = isDataEmpty
        binding.emptyTitle.isVisible = isDataEmpty
        binding.emptySubtitle.isVisible = isDataEmpty
        binding.arrowToPlus.isVisible = isDataEmpty
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateLocalDatabase() {
        val lastUpdateTemp = Date(System.currentTimeMillis())
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
        var url = "${BuildConfig.BASE_URL}/api/notes/get_update"
        if (lastUpdate != null) {
            url += "?time=${formatDateToIso8601(lastUpdate!!)}"
            Log.e("TIME_NOW", formatDateToIso8601(lastUpdate!!))
        }
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HTTP", "Failed: ${e.message}")
                Log.e("HTTP ERROR", "Failed: ${Log.getStackTraceString(e)}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code == 200) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (firstUpdate) {
                            repo.deleteAllNotes()
                            UserManager.putCurrentIsUpdated(requireContext(), false)
                        }
                        val jsonArray = JSONArray(response.body!!.string())
                        for (i in 0 until jsonArray.length()) {
                            val jsonObject = jsonArray.getJSONObject(i)
                            val note = repo.getNoteByUserId(jsonObject.getLong("user_id"))
                            Log.e("FOUND", jsonObject.getLong("user_id").toString())

                            if (note != null) {
                                note!!.content = jsonObject.getString("description")
                                note!!.title = jsonObject.getString("title")
                                note!!.isPinned = jsonObject.getBoolean("is_pinned")
                                note!!.username = jsonObject.getString("creator_username")
                                repo.update(note)
                            } else {
                                repo.insert(Note(
                                    id=jsonObject.getLong("user_id"),
                                    title=jsonObject.getString("title"),
                                    content=jsonObject.getString("description"),
                                    isPinned=jsonObject.getBoolean("is_pinned"),
                                    username=jsonObject.getString("creator_username"),
                                    userId=jsonObject.getLong("user_id")
                                ))
                            }
                        }
                    }
                }
            }
        })
        lastUpdate = lastUpdateTemp
    }

    private fun formatDateToIso8601(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}