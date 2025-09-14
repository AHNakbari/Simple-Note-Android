package com.example.simplenote.core

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes WHERE id = :id AND username = :username")
    suspend fun getNoteById(id: Long, username: String): Note?

    @Query("SELECT * FROM notes WHERE userId = :userId")
    suspend fun getNoteByUserId(userId: Long): Note?

    @Query("SELECT * FROM notes WHERE username = :username ORDER BY lastEdited DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllNotes(
        username: String,
        limit: Int,
        offset: Int
    ): List<Note>

    @Query("SELECT * FROM notes WHERE username = :username ORDER BY lastEdited DESC LIMIT :limit OFFSET :offset")
    suspend fun getNotesWithPagination(username: String, limit: Int, offset: Int): List<Note>

    @Query("SELECT * FROM notes WHERE username = :username AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY lastEdited DESC")
    suspend fun searchNotes(username: String, query: String): List<Note>

    @Query("SELECT * FROM notes WHERE username = :username AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY lastEdited DESC LIMIT :limit OFFSET :offset")
    suspend fun searchNotesWithPagination(username: String, query: String, limit: Int, offset: Int): List<Note>

    @Query("SELECT COUNT(*) FROM notes WHERE username = :username")
    suspend fun getNotesCount(username: String): Int

    @Query("SELECT COUNT(*) FROM notes WHERE username = :username AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')")
    suspend fun getSearchNotesCount(username: String, query: String): Int

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}