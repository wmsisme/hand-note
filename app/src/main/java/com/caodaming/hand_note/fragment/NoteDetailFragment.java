package com.caodaming.hand_note.fragment;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.caodaming.hand_note.R;
import com.caodaming.hand_note.data.Note;
import com.caodaming.hand_note.viewmodel.NoteViewModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteDetailFragment extends Fragment {

    private static final String AUDIO_SEPARATOR = "||";

    private EditText etTitle;
    private EditText etLocation;
    private EditText etContent;
    private TextView tvTime;
    private Button btnSave;
    private Button btnDelete;
    private Button btnSelectImage;
    private ImageView ivImage;
    private LinearLayout layoutImageActions;
    private Button btnViewFullImage;
    private Button btnSaveImage;
    private Button btnDeleteImage;
    private LinearLayout layoutAudioList;

    private NoteViewModel viewModel;
    private long noteId = -1;
    private long userId;
    private Note currentNote;

    // 图片
    private String imageFilePath;
    // 多条录音路径
    private final List<String> audioFilePaths = new ArrayList<>();

    // 录音相关
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private Button btnRecordAudio;

    // 当前正在播放的录音索引，-1 表示没有
    private int playingIndex = -1;
    private MediaPlayer mediaPlayer;
    private Button playingBtn;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    String path = saveImageToInternal(uri);
                    if (path != null) {
                        imageFilePath = path;
                        showImage();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> requestAudioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startRecording();
                } else {
                    Toast.makeText(getContext(), R.string.record_permission_required, Toast.LENGTH_SHORT).show();
                }
            });

    public static NoteDetailFragment newInstance(long noteId, long userId) {
        NoteDetailFragment fragment = new NoteDetailFragment();
        Bundle args = new Bundle();
        args.putLong("note_id", noteId);
        args.putLong("user_id", userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            noteId = getArguments().getLong("note_id", -1);
            userId = getArguments().getLong("user_id", -1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_note_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etTitle = view.findViewById(R.id.et_title);
        etLocation = view.findViewById(R.id.et_location);
        etContent = view.findViewById(R.id.et_content);
        tvTime = view.findViewById(R.id.tv_time);
        btnSave = view.findViewById(R.id.btn_save);
        btnDelete = view.findViewById(R.id.btn_delete);
        btnSelectImage = view.findViewById(R.id.btn_select_image);
        ivImage = view.findViewById(R.id.iv_image);
        layoutImageActions = view.findViewById(R.id.layout_image_actions);
        btnViewFullImage = view.findViewById(R.id.btn_view_full_image);
        btnSaveImage = view.findViewById(R.id.btn_save_image);
        btnDeleteImage = view.findViewById(R.id.btn_delete_image);
        layoutAudioList = view.findViewById(R.id.layout_audio_list);

        NoteViewModel.Factory factory = new NoteViewModel.Factory(
                requireActivity().getApplication(), userId);
        viewModel = new ViewModelProvider(this, factory).get(NoteViewModel.class);

        if (noteId > 0) {
            btnDelete.setVisibility(View.VISIBLE);
            setTitle("查看笔记");
            viewModel.getNoteById(noteId).observe(getViewLifecycleOwner(), note -> {
                if (note != null) {
                    currentNote = note;
                    displayNote(note);
                }
            });
        } else {
            btnDelete.setVisibility(View.GONE);
            setTitle("新增笔记");
            tvTime.setText("记录时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        }

        btnSave.setOnClickListener(v -> saveNote());
        btnDelete.setOnClickListener(v -> showDeleteDialog());
        btnSelectImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // 图片操作按钮
        btnViewFullImage.setOnClickListener(v -> viewFullImage());
        btnSaveImage.setOnClickListener(v -> saveImageToGallery());
        btnDeleteImage.setOnClickListener(v -> deleteCurrentImage());
        ivImage.setOnClickListener(v -> viewFullImage());

        // 录音按钮
        btnRecordAudio = view.findViewById(R.id.btn_record_audio);
        btnRecordAudio.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                checkAudioPermissionAndRecord();
            }
        });
    }

    private void setTitle(String title) {
        if (getActivity() != null) {
            getActivity().setTitle(title);
        }
    }

    // ==================== 图片相关 ====================

    private void showImage() {
        if (imageFilePath == null) return;
        File imgFile = new File(imageFilePath);
        if (!imgFile.exists()) return;

        ivImage.setVisibility(View.VISIBLE);
        layoutImageActions.setVisibility(View.VISIBLE);

        // 使用 Glide 加载，自动支持 GIF 动画
        Glide.with(this)
                .load(imgFile)
                .centerCrop()
                .into(ivImage);
    }

    private void viewFullImage() {
        if (imageFilePath == null) return;
        File imgFile = new File(imageFilePath);
        if (!imgFile.exists()) {
            Toast.makeText(getContext(), "图片文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", imgFile);
        } else {
            uri = Uri.fromFile(imgFile);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法打开图片：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImageToGallery() {
        if (imageFilePath == null) return;
        File srcFile = new File(imageFilePath);
        if (!srcFile.exists()) {
            Toast.makeText(getContext(), "图片文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, srcFile.getName());
            values.put(MediaStore.Images.Media.MIME_TYPE, getMimeType(srcFile.getName()));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/随手笔记");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri destUri = requireContext().getContentResolver().insert(collection, values);
            if (destUri == null) {
                Toast.makeText(getContext(), R.string.image_save_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            try (InputStream in = new FileInputStream(srcFile);
                 OutputStream out = requireContext().getContentResolver().openOutputStream(destUri)) {
                if (out == null) {
                    Toast.makeText(getContext(), R.string.image_save_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                requireContext().getContentResolver().update(destUri, values, null, null);
            }

            Toast.makeText(getContext(), R.string.image_saved_to_gallery, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(getContext(), R.string.image_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteCurrentImage() {
        imageFilePath = null;
        ivImage.setVisibility(View.GONE);
        layoutImageActions.setVisibility(View.GONE);
        Glide.with(this).clear(ivImage);
        Toast.makeText(getContext(), R.string.image_deleted, Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private String saveImageToInternal(Uri uri) {
        try {
            // 获取文件扩展名，保留原始格式（包括 GIF）
            String extension = getExtensionFromUri(uri);
            if (extension.isEmpty()) extension = ".jpg";

            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(getContext(), R.string.image_save_failed, Toast.LENGTH_SHORT).show();
                return null;
            }

            File dir = new File(requireContext().getFilesDir(), "images");
            if (!dir.exists()) dir.mkdirs();

            String fileName = "IMG_" + System.currentTimeMillis() + extension;
            File file = new File(dir, fileName);

            // 直接复制原始字节，保留 GIF 等格式
            FileOutputStream fos = new FileOutputStream(file);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            fos.close();
            is.close();

            return file.getAbsolutePath();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.image_save_failed, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private String getExtensionFromUri(Uri uri) {
        String mime = requireContext().getContentResolver().getType(uri);
        if (mime != null) {
            switch (mime) {
                case "image/png": return ".png";
                case "image/gif": return ".gif";
                case "image/webp": return ".webp";
                case "image/bmp": return ".bmp";
                case "image/jpeg": return ".jpg";
            }
        }
        // 从文件名猜测
        String name = uri.getLastPathSegment();
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf("."));
        }
        return ".jpg";
    }

    private String getMimeType(String fileName) {
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".webp")) return "image/webp";
        if (fileName.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }

    // ==================== 录音相关 ====================

    private void checkAudioPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void startRecording() {
        File dir = new File(requireContext().getFilesDir(), "audios");
        if (!dir.exists()) dir.mkdirs();

        String fileName = "AUDIO_" + System.currentTimeMillis() + ".m4a";
        File audioFile = new File(dir, fileName);
        final String newPath = audioFile.getAbsolutePath();

        mediaRecorder = new MediaRecorder();
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(newPath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            btnRecordAudio.setText(R.string.stop_record);
            Toast.makeText(getContext(), R.string.recording, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(getContext(), "录音启动失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            releaseMediaRecorder();
        }
    }

    private void stopRecording() {
        try {
            mediaRecorder.stop();
        } catch (RuntimeException e) {
            // 录音时间过短可能会抛异常
        }

        String recordedPath = mediaRecorder != null ? findRecordedPath() : null;
        releaseMediaRecorder();

        isRecording = false;
        btnRecordAudio.setText(R.string.start_record);

        if (recordedPath != null && new File(recordedPath).exists()) {
            audioFilePaths.add(recordedPath);
            addAudioItemView(recordedPath, audioFilePaths.size() - 1);
            layoutAudioList.setVisibility(View.VISIBLE);
            Toast.makeText(getContext(), R.string.audio_saved, Toast.LENGTH_SHORT).show();
        }
    }

    private String findRecordedPath() {
        // 从 audios 目录找到最新的文件
        File dir = new File(requireContext().getFilesDir(), "audios");
        File[] files = dir.listFiles();
        if (files == null) return null;
        long latest = 0;
        File latestFile = null;
        for (File f : files) {
            if (f.lastModified() > latest) {
                latest = f.lastModified();
                latestFile = f;
            }
        }
        return latestFile != null ? latestFile.getAbsolutePath() : null;
    }

    private void addAudioItemView(final String path, final int index) {
        if (getContext() == null) return;

        // 创建录音项布局
        LinearLayout itemLayout = new LinearLayout(getContext());
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, 8, 0, 8);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        itemLayout.setLayoutParams(params);

        // 文件名文本
        TextView tvName = new TextView(getContext());
        tvName.setText("录音" + (index + 1) + "：" + new File(path).getName());
        tvName.setTextSize(13);
        tvName.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, null));
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvName.setLayoutParams(tvParams);

        // 播放按钮
        Button btnPlay = new Button(getContext(), null, 0,
                com.google.android.material.R.style.Widget_Material3_Button_TextButton);
        btnPlay.setText(R.string.play_audio);
        btnPlay.setTextSize(12);
        btnPlay.setTag(index);
        btnPlay.setOnClickListener(v -> {
            int idx = (int) v.getTag();
            togglePlayback(idx, (Button) v);
        });

        // 保存到本地按钮
        Button btnSaveLocal = new Button(getContext(), null, 0,
                com.google.android.material.R.style.Widget_Material3_Button_TextButton);
        btnSaveLocal.setText(R.string.save_audio_to_local);
        btnSaveLocal.setTextSize(11);
        btnSaveLocal.setTag(index);
        btnSaveLocal.setOnClickListener(v -> saveAudioToLocal((int) v.getTag()));

        // 删除按钮
        Button btnDel = new Button(getContext(), null, 0,
                com.google.android.material.R.style.Widget_Material3_Button_TextButton);
        btnDel.setText(R.string.delete_audio);
        btnDel.setTextSize(11);
        btnDel.setTag(index);
        btnDel.setOnClickListener(v -> deleteAudioItem((int) v.getTag()));

        itemLayout.addView(tvName);
        itemLayout.addView(btnPlay);
        itemLayout.addView(btnSaveLocal);
        itemLayout.addView(btnDel);

        layoutAudioList.addView(itemLayout);
    }

    private void deleteAudioItem(int index) {
        if (index < 0 || index >= audioFilePaths.size()) return;

        // 如果正在播放该录音，先停止
        if (index == playingIndex) {
            stopPlayback();
        }

        // 删除文件
        String path = audioFilePaths.get(index);
        File file = new File(path);
        if (file.exists()) file.delete();

        audioFilePaths.remove(index);
        rebuildAudioList();
        Toast.makeText(getContext(), R.string.audio_deleted, Toast.LENGTH_SHORT).show();
    }

    private void rebuildAudioList() {
        stopPlayback();
        // 移除播放按钮引用
        playingBtn = null;
        playingIndex = -1;

        layoutAudioList.removeAllViews();
        if (audioFilePaths.isEmpty()) {
            layoutAudioList.setVisibility(View.GONE);
            return;
        }
        for (int i = 0; i < audioFilePaths.size(); i++) {
            addAudioItemView(audioFilePaths.get(i), i);
        }
    }

    // ==================== 播放控制 ====================

    private void togglePlayback(int index, Button btn) {
        if (index == playingIndex) {
            // 停止当前播放
            stopPlayback();
            return;
        }

        // 先停止之前的播放
        if (playingIndex >= 0) {
            stopPlayback();
        }

        String path = audioFilePaths.get(index);
        if (path == null || !new File(path).exists()) {
            Toast.makeText(getContext(), "录音文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        startPlayback(index, btn);
    }

    private void startPlayback(int index, Button btn) {
        releaseMediaPlayer();

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioFilePaths.get(index));
            mediaPlayer.prepare();
            mediaPlayer.start();

            playingIndex = index;
            playingBtn = btn;
            btn.setText(R.string.stop_audio);

            mediaPlayer.setOnCompletionListener(mp -> {
                stopPlayback();
            });
        } catch (IOException e) {
            Toast.makeText(getContext(), "播放失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            releaseMediaPlayer();
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (RuntimeException ignored) {
            }
            mediaPlayer = null;
        }
        if (playingBtn != null && getContext() != null) {
            playingBtn.setText(R.string.play_audio);
            playingBtn = null;
        }
        playingIndex = -1;
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (RuntimeException ignored) {
            }
            mediaPlayer = null;
        }
        if (playingBtn != null && getContext() != null) {
            playingBtn.setText(R.string.play_audio);
            playingBtn = null;
        }
        playingIndex = -1;
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (RuntimeException ignored) {
            }
            mediaRecorder = null;
        }
    }

    // ==================== 保存音频到本地 ====================

    private void saveAudioToLocal(int index) {
        if (index < 0 || index >= audioFilePaths.size()) return;
        String srcPath = audioFilePaths.get(index);
        File srcFile = new File(srcPath);
        if (!srcFile.exists()) {
            Toast.makeText(getContext(), "录音文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, srcFile.getName());
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/随手笔记");
                values.put(MediaStore.Audio.Media.IS_PENDING, 1);
            }

            Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri destUri = requireContext().getContentResolver().insert(collection, values);
            if (destUri == null) {
                Toast.makeText(getContext(), "保存失败", Toast.LENGTH_SHORT).show();
                return;
            }

            try (InputStream in = new FileInputStream(srcFile);
                 OutputStream out = requireContext().getContentResolver().openOutputStream(destUri)) {
                if (out == null) {
                    Toast.makeText(getContext(), "保存失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Audio.Media.IS_PENDING, 0);
                requireContext().getContentResolver().update(destUri, values, null, null);
            }

            Toast.makeText(getContext(), R.string.audio_saved_to_local, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(getContext(), "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 笔记数据加载 ====================

    private void displayNote(Note note) {
        etTitle.setText(note.title);
        etContent.setText(note.content);
        etLocation.setText(note.location != null ? note.location : "");
        tvTime.setText("记录时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(note.createTime)));

        // 加载图片
        if (note.imagePath != null && !note.imagePath.isEmpty()) {
            imageFilePath = note.imagePath;
            File imgFile = new File(note.imagePath);
            if (imgFile.exists()) {
                showImage();
            }
        }

        // 加载多条录音
        audioFilePaths.clear();
        layoutAudioList.removeAllViews();
        if (note.audioPath != null && !note.audioPath.isEmpty()) {
            String[] paths = note.audioPath.split("\\|\\|");
            for (String p : paths) {
                p = p.trim();
                if (!p.isEmpty() && new File(p).exists()) {
                    audioFilePaths.add(p);
                }
            }
            if (!audioFilePaths.isEmpty()) {
                layoutAudioList.setVisibility(View.VISIBLE);
                for (int i = 0; i < audioFilePaths.size(); i++) {
                    addAudioItemView(audioFilePaths.get(i), i);
                }
            }
        }
    }

    // ==================== 保存笔记 ====================

    private void saveNote() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(getContext(), R.string.title_required, Toast.LENGTH_SHORT).show();
            return;
        }

        String content = etContent.getText().toString().trim();
        String location = etLocation.getText().toString().trim();

        if (currentNote == null) {
            currentNote = new Note();
            currentNote.createTime = System.currentTimeMillis();
            currentNote.userId = userId;
        }

        currentNote.title = title;
        currentNote.content = content;
        currentNote.location = location;

        // 图片路径
        currentNote.imagePath = imageFilePath;

        // 多条录音路径用分隔符拼接
        if (audioFilePaths.isEmpty()) {
            currentNote.audioPath = null;
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < audioFilePaths.size(); i++) {
                if (i > 0) sb.append(AUDIO_SEPARATOR);
                sb.append(audioFilePaths.get(i));
            }
            currentNote.audioPath = sb.toString();
        }

        if (noteId > 0) {
            viewModel.update(currentNote);
        } else {
            viewModel.insert(currentNote);
        }

        Toast.makeText(getContext(), R.string.save_success, Toast.LENGTH_SHORT).show();
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    // ==================== 删除笔记 ====================

    private void showDeleteDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_delete)
                .setMessage(R.string.confirm_delete_msg)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    if (currentNote != null) {
                        viewModel.delete(currentNote);
                        Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
                        if (getActivity() != null) {
                            getActivity().finish();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== 生命周期 ====================

    @Override
    public void onPause() {
        super.onPause();
        if (playingIndex >= 0) {
            stopPlayback();
        }
        if (isRecording) {
            stopRecording();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseMediaRecorder();
        releaseMediaPlayer();
    }
}
