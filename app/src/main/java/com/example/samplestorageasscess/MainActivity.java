package com.example.samplestorageasscess;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import android.content.Intent;              // ← 追加

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_OPEN_TREE = 1001;
    private Uri treeUri;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSelectUsb = findViewById(R.id.btn_select_usb);
        btnSelectUsb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUsbStorage();
            }
        });

        Button btnReadFile = findViewById(R.id.btn_read_file);
        btnReadFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readSampleFile();
            }
        });

        Button btnWriteFile = findViewById(R.id.btn_write_file);
        btnWriteFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeSampleFile();
            }
        });
    }

    /** 1. USB ストレージをユーザーに選択させる */
    private void openUsbStorage() {
        StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> volumes = sm.getStorageVolumes();
        for (StorageVolume vol : volumes) {
            Log.i("MainActivity", "Found volume: " + vol.getDescription(this));
            Intent intent = vol.createOpenDocumentTreeIntent();
            startActivityForResult(intent, REQUEST_CODE_OPEN_TREE);

            return;
//            if (vol.isRemovable() && !vol.isPrimary()) {
//                Intent intent = vol.createOpenDocumentTreeIntent();
//                startActivityForResult(intent, REQUEST_CODE_OPEN_TREE);
//                return;
//            }
        }
        Log.w("MainActivity", "Removable volume not found");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_TREE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                treeUri = data.getData();
                // 永続的パーミッション取得用フラグを定義
                final int takeFlags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                // パーミッションを永続化
                getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                Log.i(TAG, "Persisted URI permission with flags: " + takeFlags);
            }
        }
    }

    /** 2. 選択されたツリーからファイルを読み込む */
    private void readSampleFile() {
        if (treeUri == null) {
            Log.w("MainActivity", "Tree URI is null, please select storage first");
            return;
        }
        // DocumentFile にラップ
        DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri);
        // 例: "sample.txt" を探す
        DocumentFile file = tree.findFile("sample.txt");
        if (file != null && file.canRead()) {
            try (InputStream in = getContentResolver().openInputStream(file.getUri())) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    // 読み込んだデータを処理
                    Log.d("MainActivity", new String(buf, 0, len));
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Read error", e);
            }
        } else {
            Log.w("MainActivity", "sample.txt not found or cannot read");
        }
    }

    /** 3. 選択されたツリーへファイルを書き込む */
    private void writeSampleFile() {
        if (treeUri == null) {
            Log.w("MainActivity", "Tree URI is null, please select storage first");
            return;
        }
        DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri);
        // 例: 存在しなければ新規作成
        DocumentFile file = tree.findFile("output.txt");
        if (file == null) {
            file = tree.createFile("text/plain", "output.txt");
        }
        if (file != null && file.canWrite()) {
            try (OutputStream out = getContentResolver().openOutputStream(file.getUri())) {
                String content = "Hello, Automotive OS!";
                out.write(content.getBytes());
                out.flush();
                Log.i("MainActivity", "Write success");
            } catch (Exception e) {
                Log.e("MainActivity", "Write error", e);
            }
        } else {
            Log.w("MainActivity", "Cannot write to output.txt");
        }
    }
}
