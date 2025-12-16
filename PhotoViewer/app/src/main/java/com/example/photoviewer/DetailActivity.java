package com.example.photoviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DetailActivity extends AppCompatActivity {

    private static final String TAG = "PhotoViewer";

    String site_url = "https://wjdwngml1001.pythonanywhere.com";
    String token = "480f25f025436cb673a16ac443f3868a245e7c14";

    private PostItem post;

    ImageView detailImage;
    TextView detailDate;
    EditText editTitle, editText;
    Button btnSave, btnDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        detailImage = findViewById(R.id.detailImage);
        detailDate  = findViewById(R.id.detailDate);
        editTitle   = findViewById(R.id.editTitle);
        editText    = findViewById(R.id.editText);
        btnSave     = findViewById(R.id.btnSave);
        btnDelete   = findViewById(R.id.btnDelete);

        Intent intent = getIntent();
        post = (PostItem) intent.getSerializableExtra("post");

        if (post == null) {
            Toast.makeText(this, "post 데이터가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 이미지 로드(Glide 쓰고 있다면 Glide로 교체 가능)
        // 여기선 기존 방식 유지: 간단히 URL만 로그. (네 프로젝트는 Glide 붙여서 쓰는 쪽이 좋아)
        // Glide 예시:
        // Glide.with(this).load(post.imageUrl).into(detailImage);

        detailDate.setText(post.getDisplayDate());
        editTitle.setText(post.title);
        editText.setText(post.text);

        btnSave.setOnClickListener(v -> {
            if (post.id <= 0) {
                Toast.makeText(this, "서버 응답에 id가 없어서 수정할 수 없습니다. (serializer에 id 추가 필요)", Toast.LENGTH_LONG).show();
                return;
            }
            String newTitle = editTitle.getText().toString().trim();
            String newText  = editText.getText().toString().trim();
            new PatchTask().execute(newTitle, newText);
        });

        btnDelete.setOnClickListener(v -> {
            if (post.id <= 0) {
                Toast.makeText(this, "서버 응답에 id가 없어서 삭제할 수 없습니다. (serializer에 id 추가 필요)", Toast.LENGTH_LONG).show();
                return;
            }
            new DeleteTask().execute();
        });
    }

    // ===== 수정(PATCH) =====
    private class PatchTask extends AsyncTask<String, Void, Integer> {
        String resp = "";

        @Override
        protected Integer doInBackground(String... args) {
            String newTitle = args[0];
            String newText  = args[1];

            HttpURLConnection conn = null;
            try {
                URL url = new URL(site_url + "/api_root/Post/" + post.id + "/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                // PATCH는 기기/버전에 따라 막히는 경우가 있어서 우회(hack) 적용
                // 1) 일단 PATCH 시도
                try {
                    conn.setRequestMethod("PATCH");
                } catch (Exception e) {
                    // 2) PATCH가 막히면 X-HTTP-Method-Override로 POST 우회 (서버가 허용해야 함)
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                }

                conn.setRequestProperty("Authorization", "Token " + token);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("title", newTitle);
                body.put("text", newText);

                byte[] out = body.toString().getBytes("UTF-8");
                OutputStream os = conn.getOutputStream();
                os.write(out);
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                Log.d(TAG, "[PATCH] code=" + code);
                return code;

            } catch (Exception e) {
                Log.e(TAG, "[PATCH] error", e);
                return -1;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Integer code) {
            if (code != null && code >= 200 && code < 300) {
                Toast.makeText(DetailActivity.this, "수정 완료", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(DetailActivity.this, "수정 실패(code=" + code + ")", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ===== 삭제(DELETE) =====
    private class DeleteTask extends AsyncTask<Void, Void, Integer> {

        @Override
        protected Integer doInBackground(Void... voids) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(site_url + "/api_root/Post/" + post.id + "/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("Authorization", "Token " + token);

                int code = conn.getResponseCode();
                Log.d(TAG, "[DELETE] code=" + code);
                return code;

            } catch (Exception e) {
                Log.e(TAG, "[DELETE] error", e);
                return -1;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Integer code) {
            if (code != null && (code == 204 || (code >= 200 && code < 300))) {
                Toast.makeText(DetailActivity.this, "삭제 완료", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(DetailActivity.this, "삭제 실패(code=" + code + ")", Toast.LENGTH_LONG).show();
            }
        }
    }
}
