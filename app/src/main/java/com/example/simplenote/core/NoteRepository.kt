package com.example.simplenote.core

import com.example.simplenote.core.AppDatabase
import com.example.simplenote.core.Note

class NoteRepository(private val db: AppDatabase) {
    suspend fun insert(note: Note): Long = db.noteDao().insert(note)
    suspend fun update(note: Note) = db.noteDao().update(note)
    suspend fun delete(note: Note) = db.noteDao().delete(note)
    suspend fun getNoteById(id: Long, username: String): Note? = db.noteDao().getNoteById(id, username)
    suspend fun getAllNotes(
        username: String,
        limit: Int,
        offset: Int
    ): List<Note> = db.noteDao().getAllNotes(username, limit, offset)

    // Pagination methods
    suspend fun getNotesWithPagination(username: String, limit: Int, offset: Int): List<Note> =
        db.noteDao().getNotesWithPagination(username, limit, offset)

    suspend fun getNotesCount(username: String): Int = db.noteDao().getNotesCount(username)

    // Search methods
    suspend fun searchNotes(username: String, query: String): List<Note> = db.noteDao().searchNotes(username, query)

    suspend fun searchNotesWithPagination(username: String, query: String, limit: Int, offset: Int): List<Note> =
        db.noteDao().searchNotesWithPagination(username, query, limit, offset)

    suspend fun getSearchNotesCount(username: String, query: String): Int = db.noteDao().getSearchNotesCount(username, query)

    suspend fun deleteAllNotes() = db.noteDao().deleteAllNotes()

    suspend fun getNoteByUserId(userId: Long) = db.noteDao().getNoteByUserId(userId)
}