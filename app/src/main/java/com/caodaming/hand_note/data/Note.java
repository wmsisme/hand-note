package com.caodaming.hand_note.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Note {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long userId;
    public String title;
    public String content;
    public String location;
    public long createTime;
    public String imagePath;
    public String audioPath;
}
