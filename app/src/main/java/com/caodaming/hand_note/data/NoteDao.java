package com.caodaming.hand_note.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NoteDao {
    @Query("SELECT * FROM notes WHERE userId = :userId ORDER BY createTime DESC")
    LiveData<List<Note>> getAllNotes(long userId);

    @Query("SELECT * FROM notes WHERE id = :id AND userId = :userId")
    LiveData<Note> getNoteById(long id, long userId);

    @Insert
    long insert(Note note);

    @Update
    void update(Note note);

    @Delete
    void delete(Note note);

    @Query("DELETE FROM notes WHERE id = :id")
    void deleteById(long id);
}
