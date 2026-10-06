package com.caodaming.hand_note.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.caodaming.hand_note.NoteDetailActivity;
import com.caodaming.hand_note.R;
import com.caodaming.hand_note.adapter.NoteAdapter;
import com.caodaming.hand_note.data.Note;
import com.caodaming.hand_note.viewmodel.NoteViewModel;

public class NoteListFragment extends Fragment {

    private RecyclerView rvNotes;
    private View tvEmpty;
    private NoteAdapter adapter;
    private NoteViewModel viewModel;
    private long userId;

    public static NoteListFragment newInstance(long userId) {
        NoteListFragment fragment = new NoteListFragment();
        Bundle args = new Bundle();
        args.putLong("user_id", userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (getArguments() != null) {
            userId = getArguments().getLong("user_id", -1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvNotes = view.findViewById(R.id.rv_notes);
        tvEmpty = view.findViewById(R.id.tv_empty);
        View fabAdd = view.findViewById(R.id.fab_add);

        rvNotes.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new NoteAdapter();
        rvNotes.setAdapter(adapter);

        adapter.setOnItemClickListener(note -> openDetail(note.id));
        adapter.setOnItemLongClickListener(note -> showDeleteDialog(note));

        fabAdd.setOnClickListener(v -> openDetail(-1));

        NoteViewModel.Factory factory = new NoteViewModel.Factory(
                requireActivity().getApplication(), userId);
        viewModel = new ViewModelProvider(this, factory).get(NoteViewModel.class);

        viewModel.getAllNotes().observe(getViewLifecycleOwner(), notes -> {
            if (notes == null || notes.isEmpty()) {
                rvNotes.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
            } else {
                rvNotes.setVisibility(View.VISIBLE);
                tvEmpty.setVisibility(View.GONE);
                adapter.setNotes(notes);
            }
        });
    }

    private void openDetail(long noteId) {
        Intent intent = new Intent(getActivity(), NoteDetailActivity.class);
        intent.putExtra("note_id", noteId);
        intent.putExtra("user_id", userId);
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_note_list, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            openDetail(-1);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDeleteDialog(Note note) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_delete)
                .setMessage(R.string.confirm_delete_msg)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    viewModel.delete(note);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
