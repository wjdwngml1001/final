package com.example.photoviewer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PhotoViewer";
    private static final int REQ_CAMERA = 1001;
    private static final int REQ_CAMERA_PERMISSION = 2001;

    TextView textView;
    RecyclerView recyclerView;

    String site_url = "https://wjdwngml1001.pythonanywhere.com";
    String token = "480f25f025436cb673a16ac443f3868a245e7c14";

    private Uri photoUri = null;

    private LoadPostsTask taskDownload;

    private final List<PostItem> postList = new ArrayList<>();
    private ImageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ImageAdapter(this, postList);
        recyclerView.setAdapter(adapter);
    }

    // ======= (1) 동기화 =======
    public void onClickDownload(View v) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }
        postList.clear();
        adapter.notifyDataSetChanged();

        // ✅ (선택) 서버가 ordering 필터를 지원하면 더 좋음 (지원 안 해도 아래에서 앱에서 정렬함)
        String firstUrl = site_url + "/api_root/Post/?ordering=-created_date";

        taskDownload = new LoadPostsTask();
        taskDownload.execute(firstUrl);
        Toast.makeText(this, "동기화 시작", Toast.LENGTH_SHORT).show();
    }

    private class LoadPostsTask extends AsyncTask<String, Void, List<PostItem>> {

        @Override
        protected List<PostItem> doInBackground(String... urls) {
            List<PostItem> resultList = new ArrayList<>();
            String nextUrl = urls[0];

            while (nextUrl != null && !isCancelled()) {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(nextUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("Authorization", "Token " + token);
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    int code = conn.getResponseCode();
                    InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

                    BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    if (code < 200 || code >= 300) {
                        Log.e(TAG, "GET error code=" + code + " body=" + sb);
                        break;
                    }

                    String raw = sb.toString().trim();

                    // ✅ DRF 페이지네이션: {"count":..,"next":..,"results":[...]}
                    // ✅ 또는 리스트: [{...},{...}]
                    JSONArray arr = null;

                    if (raw.startsWith("[")) {
                        // 리스트 형태
                        arr = new JSONArray(raw);
                        nextUrl = null; // 다음 페이지 없음
                    } else {
                        // 객체 형태(페이지네이션)
                        JSONObject json = new JSONObject(raw);

                        arr = json.optJSONArray("results");
                        // results가 없으면 예외(서버 응답 형식이 예상과 다름)
                        if (arr == null) {
                            Log.e(TAG, "Unexpected JSON format: " + raw);
                            break;
                        }

                        // next가 null이면 종료
                        if (json.isNull("next")) nextUrl = null;
                        else nextUrl = json.optString("next", null);
                    }

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);

                        PostItem item = new PostItem();
                        item.id=obj.optInt("id",0);
                        item.author = obj.optInt("author", 0);
                        item.title = obj.optString("title", "");
                        item.text = obj.opdtString("text", "");
                        item.createdDate = obj.optString("created_date", "");
                        item.publishedDate = obj.optString("published_date", "");
                        item.imageUrl = obj.optString("image", "");

                        // 혹시 image가 "/media/..." 같은 상대경로로 오면 보정
                        if (item.imageUrl != null && item.imageUrl.startsWith("/")) {
                            item.imageUrl = site_url + item.imageUrl;
                        }

                        resultList.add(item);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "LoadPostsTask error", e);
                    break;
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }

            // ✅ 최신 게시물이 맨 앞: createdDate 내림차순 정렬
            Collections.sort(resultList, new Comparator<PostItem>() {
                @Override
                public int compare(PostItem a, PostItem b) {
                    String da = (a.createdDate == null) ? "" : a.createdDate;
                    String db = (b.createdDate == null) ? "" : b.createdDate;
                    return db.compareTo(da); // 내림차순
                }
            });

            return resultList;
        }

        @Override
        protected void onPostExecute(List<PostItem> items) {
            postList.clear();
            postList.addAll(items);
            adapter.notifyDataSetChanged();

            if (items.isEmpty()) {
                Toast.makeText(MainActivity.this, "불러올 데이터가 없습니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "동기화 완료 (" + items.size() + "개)", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ======= (2) 업로드: 카메라 열기 =======
    public void onClickUpload(View v) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }
        openCamera();
    }

    private void openCamera() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            File imgFile = File.createTempFile("upload_", ".jpg", getCacheDir());
            photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imgFile
            );

            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQ_CAMERA);

        } catch (Exception e) {
            Toast.makeText(this, "카메라 실행 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "openCamera error", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CAMERA && resultCode == RESULT_OK) {
            if (photoUri != null) {
                Toast.makeText(this, "촬영 완료! 업로드 중...", Toast.LENGTH_SHORT).show();
                new UploadTask().execute(photoUri);
            } else {
                Toast.makeText(this, "사진 URI가 없습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ======= (3) 업로드(기존 구조 유지) =======
    private class UploadTask extends AsyncTask<Uri, Void, Integer> {

        @Override
        protected Integer doInBackground(Uri... uris) {
            Uri uri = uris[0];

            String boundary = "----PhotoViewerBoundary" + System.currentTimeMillis();
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            HttpURLConnection conn = null;

            try {
                URL url = new URL(site_url + "/api_root/Post/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);

                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("Accept", "application/json");

                OutputStream os = conn.getOutputStream();
                DataOutputStream dos = new DataOutputStream(os);

                // author=2
                writeFormField(dos, boundary, "author", "2");

                // title/text 기본값
                writeFormField(dos, boundary, "title", "mobile upload");
                writeFormField(dos, boundary, "text", "모바일에서 업로드된 이미지입니다.");

                // created_date는 서버에서 받아도 되지만(요청대로), 지금은 기존 코드 유지
                // 서버가 default를 갖고 있으면 이 필드 없이도 저장됨

                // 파일 파트
                String fileName = "upload_" + System.currentTimeMillis() + ".jpg";
                writeFileField(dos, boundary, "image", fileName, "image/jpeg", uri);

                // end boundary
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                dos.flush();
                dos.close();

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                Log.d(TAG, "[UPLOAD] code=" + code);
                Log.d(TAG, "[UPLOAD] body=" + sb);

                return code;

            } catch (Exception e) {
                Log.e(TAG, "UploadTask error", e);
                return -1;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Integer code) {
            if (code != null && code >= 200 && code < 300) {
                Toast.makeText(MainActivity.this, "업로드 성공! (code=" + code + ")", Toast.LENGTH_SHORT).show();
                // 업로드 후 자동 동기화(최신순)
                onClickDownload(null);
            } else {
                Toast.makeText(MainActivity.this, "업로드 실패! (code=" + code + ")", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void writeFormField(DataOutputStream dos, String boundary, String name, String value) throws Exception {
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        dos.writeBytes(twoHyphens + boundary + lineEnd);
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + lineEnd);
        dos.writeBytes("Content-Type: text/plain; charset=UTF-8" + lineEnd);
        dos.writeBytes(lineEnd);
        dos.write(value.getBytes(StandardCharsets.UTF_8));
        dos.writeBytes(lineEnd);
    }

    private void writeFileField(DataOutputStream dos, String boundary, String name, String fileName,
                                String mimeType, Uri fileUri) throws Exception {
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        dos.writeBytes(twoHyphens + boundary + lineEnd);
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"" + lineEnd);
        dos.writeBytes("Content-Type: " + mimeType + lineEnd);
        dos.writeBytes("Content-Transfer-Encoding: binary" + lineEnd);
        dos.writeBytes(lineEnd);

        InputStream inputStream = getContentResolver().openInputStream(fileUri);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            dos.write(buffer, 0, bytesRead);
        }
        inputStream.close();

        dos.writeBytes(lineEnd);
    }
}
