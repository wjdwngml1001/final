package com.example.photoviewer;

import java.io.Serializable;

public class PostItem implements Serializable {
    public int id;            // ✅ 서버에서 내려오는 pk (필수)
    public int author;
    public String title;
    public String text;
    public String createdDate;
    public String publishedDate;
    public String imageUrl;

    public PostItem() {}

    public String getDisplayDate() {
        // createdDate가 "2025-12-16T07:58:56.849683+09:00" 같은 형태라서 앞부분만 보여주기
        if (createdDate == null) return "";
        return createdDate.replace("T", " ").replace("+09:00", "");
    }
}
