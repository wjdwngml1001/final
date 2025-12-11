package com.example.photoviewer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    TextView textView;
    String site_url="https://wjdwngml1001.pythonanywhere.com";
    String token="480f25f025436cb673a16ac443f3868a245e7c14";

    JSONObject post_json;
    String ImageUrl=null;
    Bitmap bmlmg=null;

    CloadImage taskDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
    }

    public void onClickDownload(View v) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        taskDownload = new CloadImage();
        taskDownload.execute(site_url + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();
    }

    public void onClickUpload(View v) {
        Toast.makeText(getApplicationContext(), "Upload", Toast.LENGTH_LONG).show();
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
    @Override
    protected List<Bitmap> doInBackground(String... urls) {
        List<Bitmap> bitmapList = new ArrayList<>();
        HttpURLConnection conn = null;

        try {
            String apiUrl = urls[0];  // https://wjdwngml1001.pythonanywhere.com/api_root/Post/
            URL urlAPI = new URL(apiUrl);
            conn = (HttpURLConnection) urlAPI.openConnection();
            conn.setRequestProperty("Authorization", "Token " + token);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            android.util.Log.d("PhotoViewer", "LIST HTTP CODE = " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                is.close();

                String strJson = result.toString();
                android.util.Log.d("PhotoViewer", "JSON RAW = " + strJson);

                // ✅ 최상단은 JSONObject
                JSONObject root = new JSONObject(strJson);

                // ✅ 실제 포스트 리스트는 "results" 배열 안에 있음
                JSONArray aryJson = root.getJSONArray("results");

                for (int i = 0; i < aryJson.length(); i++) {
                    JSONObject post_json = aryJson.getJSONObject(i);
                    String imageUrl = post_json.getString("image");

                    if (imageUrl == null || imageUrl.isEmpty() || "null".equals(imageUrl)) {
                        continue;
                    }

                    // (pythonanywhere용 URL 보정 – 필요시)
                    if (imageUrl.startsWith("/")) {
                        imageUrl = site_url + imageUrl;
                    }

                    android.util.Log.d("PhotoViewer", "final imageUrl = " + imageUrl);

                    try {
                        URL myImageUrl = new URL(imageUrl);
                        HttpURLConnection imgConn = (HttpURLConnection) myImageUrl.openConnection();
                        imgConn.setConnectTimeout(5000);
                        imgConn.setReadTimeout(15000);
                        imgConn.setRequestMethod("GET");

                        int imgCode = imgConn.getResponseCode();
                        android.util.Log.d("PhotoViewer", "IMG HTTP CODE = " + imgCode + " for " + imageUrl);

                        if (imgCode == HttpURLConnection.HTTP_OK) {
                            InputStream imgStream = imgConn.getInputStream();
                            Bitmap imageBitmap = BitmapFactory.decodeStream(imgStream);
                            bitmapList.add(imageBitmap);
                            imgStream.close();
                        }
                    } catch (IOException e) {
                        android.util.Log.e("PhotoViewer", "Error loading image: " + imageUrl, e);
                    }
                }
            }
        } catch (IOException | JSONException e) {
            android.util.Log.e("PhotoViewer", "Exception in doInBackground", e);
        } finally {
            if (conn != null) conn.disconnect();
        }

        return bitmapList;
    }

    @Override
    protected void onPostExecute(List<Bitmap> images) {
        android.util.Log.d("PhotoViewer", "onPostExecute: images size = " + images.size());

        if (images.isEmpty()) {
            textView.setText("불러올 이미지가 없습니다.");
        } else {
            textView.setText("이미지 로드 성공!");
            RecyclerView recyclerView = findViewById(R.id.recyclerView);
            ImageAdapter adapter = new ImageAdapter(images);
            recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            recyclerView.setAdapter(adapter);
        }
    }
}


}