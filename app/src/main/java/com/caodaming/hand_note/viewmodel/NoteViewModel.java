package com.caodaming.hand_note.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.caodaming.hand_note.data.AppDatabase;
import com.caodaming.hand_note.data.Note;
import com.caodaming.hand_note.data.NoteDao;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoteViewModel extends AndroidViewModel {

    private final NoteDao noteDao;
    private final long userId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public NoteViewModel(@NonNull Application application, long userId) {
        super(application);
        this.userId = userId;
        noteDao = AppDatabase.getInstance(application).noteDao();
    }

    public LiveData<List<Note>> getAllNotes() {
        return noteDao.getAllNotes(userId);
    }

    public LiveData<Note> getNoteById(long id) {
        return noteDao.getNoteById(id, userId);
    }

    public void insert(Note note) {
        executor.execute(() -> noteDao.insert(note));
    }

    public void update(Note note) {
        executor.execute(() -> noteDao.update(note));
    }

    public void delete(Note note) {
        executor.execute(() -> noteDao.delete(note));
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final Application application;
        private final long userId;

        public Factory(Application application, long userId) {
            this.application = application;
            this.userId = userId;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new NoteViewModel(application, userId);
        }
    }
}
