package com.example.photoviewer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.File;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    TextView textView;
    private static final String TAG = "PhotoViewer";
    private static final int REQ_CAMERA = 1001;
    private static final int REQ_CAMERA_PERMISSION = 2001;

    String site_url="https://wjdwngml1001.pythonanywhere.com";
    String token="480f25f025436cb673a16ac443f3868a245e7c14";

    JSONObject post_json;
    String ImageUrl=null;
    Bitmap bmlmg=null;

    CloadImage taskDownload;
    private Uri photoUri=null;
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
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_SHORT).show();
    }

    public void onClickUpload(View v) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }
        openCamera();
    }
    
    private void openCamera() {
        try {
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            File imgFile = File.createTempFile("upload_", ".jpg", getCacheDir());

            photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imgFile
            );

            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri);
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
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                OutputStream os = conn.getOutputStream();
                DataOutputStream dos = new DataOutputStream(os);

                // title
                writeFormField(dos, boundary, "title", "mobile upload");
                // text
                writeFormField(dos, boundary, "text", "uploaded from android");

                // image 파일 파트
                String fileName = "upload.jpg";
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"" + fileName + "\"" + lineEnd);
                dos.writeBytes("Content-Type: image/jpeg" + lineEnd);
                dos.writeBytes(lineEnd);

                InputStream is = getContentResolver().openInputStream(uri);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                is.close();
                dos.writeBytes(lineEnd);

                // end boundary
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                dos.flush();
                dos.close();

                int code = conn.getResponseCode();
                Log.d(TAG, "Upload responseCode=" + code);

                // 에러 바디도 로그로 확인 가능
                InputStream respIs = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (respIs != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(respIs));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    Log.d(TAG, "Upload response body=" + sb.toString());
                }

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
                Toast.makeText(MainActivity.this, "업로드 성공!", Toast.LENGTH_SHORT).show();
                // 업로드 후 자동 동기화(선택)
                onClickDownload(null);
            } else {
                Toast.makeText(MainActivity.this, "업로드 실패! code=" + code, Toast.LENGTH_LONG).show();
            }
        }

        private void writeFormField(DataOutputStream dos, String boundary, String name, String value) throws IOException {
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(value + lineEnd);
        }
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