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
        StorageVolume removableVolume = null;
        for (StorageVolume vol : volumes) {
            Log.i(TAG, "Found volume: " + vol.getDescription(this) + ", isRemovable: " + vol.isRemovable());
            if (vol.isRemovable()) {
                removableVolume = vol;
                break; // 最初に見つかったリムーバブルストレージを使用
            }
        }

        if (removableVolume != null) {
            Log.i(TAG, "Using removable volume: " + removableVolume.getDescription(this));
            Intent intent = removableVolume.createOpenDocumentTreeIntent();
            startActivityForResult(intent, REQUEST_CODE_OPEN_TREE);
        } else {
            Log.w(TAG, "Removable USB storage not found");
            android.widget.Toast.makeText(this, "Removable USB storage not found.", android.widget.Toast.LENGTH_LONG).show();
        }
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
                treeUri = data.getData();
                if (treeUri != null) {
                    // パーミッションを永続化
                    try {
                        getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                        Log.i(TAG, "Persisted URI permission for: " + treeUri.toString() + " with flags: " + takeFlags);
                        android.widget.Toast.makeText(this, "USB storage access granted.", android.widget.Toast.LENGTH_SHORT).show();
                    } catch (SecurityException e) {
                        Log.e(TAG, "Failed to take persistable URI permission for: " + treeUri.toString(), e);
                        android.widget.Toast.makeText(this, "Failed to grant access to USB storage.", android.widget.Toast.LENGTH_LONG).show();
                        treeUri = null; // アクセス権取得失敗
                    }
                } else {
                    Log.w(TAG, "Selected URI is null.");
                    android.widget.Toast.makeText(this, "Failed to get URI for selected storage.", android.widget.Toast.LENGTH_LONG).show();
                }
            } else {
                Log.w(TAG, "Intent data is null in onActivityResult.");
                android.widget.Toast.makeText(this, "Failed to get selected storage information.", android.widget.Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CODE_OPEN_TREE && resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "USB storage selection was cancelled by the user.");
            android.widget.Toast.makeText(this, "USB storage selection cancelled.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** 2. 選択されたツリーからファイルを読み込む */
    private void readSampleFile() {
        if (treeUri == null) {
            Log.w(TAG, "Tree URI is null, please select storage first");
            android.widget.Toast.makeText(this, "Please select USB storage first.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // DocumentFile にラップ
        DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri);
        if (tree == null) {
            Log.e(TAG, "Could not create DocumentFile from tree URI.");
            android.widget.Toast.makeText(this, "Error accessing selected storage.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        // 例: "sample.txt" を探す
        DocumentFile file = tree.findFile("sample.txt");
        if (file != null && file.canRead()) {
            try (InputStream in = getContentResolver().openInputStream(file.getUri())) {
                // TODO: Consider reading in a background thread for large files
                byte[] buf = new byte[4096];
                int len;
                StringBuilder sb = new StringBuilder();
                while ((len = in.read(buf)) > 0) {
                    sb.append(new String(buf, 0, len));
                }
                Log.d(TAG, "File content: " + sb.toString());
                android.widget.Toast.makeText(this, "Read from sample.txt:\n" + sb.substring(0, Math.min(sb.length(), 100)), android.widget.Toast.LENGTH_LONG).show(); // Display first 100 chars
            } catch (Exception e) {
                Log.e(TAG, "Read error from sample.txt", e);
                android.widget.Toast.makeText(this, "Error reading file 'sample.txt'.", android.widget.Toast.LENGTH_LONG).show();
            }
        } else {
            Log.w(TAG, "sample.txt not found or cannot be read in the selected storage.");
            android.widget.Toast.makeText(this, "'sample.txt' not found or cannot be read.", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** 3. 選択されたツリーへファイルを書き込む */
    private void writeSampleFile() {
        if (treeUri == null) {
            Log.w(TAG, "Tree URI is null, please select storage first");
            android.widget.Toast.makeText(this, "Please select USB storage first.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri);
        if (tree == null) {
            Log.e(TAG, "Could not create DocumentFile from tree URI.");
            android.widget.Toast.makeText(this, "Error accessing selected storage.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        // 例: 存在しなければ新規作成
        DocumentFile file = tree.findFile("output.txt");
        if (file == null) {
            file = tree.createFile("text/plain", "output.txt");
            if (file == null) {
                Log.e(TAG, "Could not create 'output.txt'. Check permissions and storage space.");
                android.widget.Toast.makeText(this, "Error creating 'output.txt'.", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (file.canWrite()) {
            // TODO: Consider writing in a background thread for large files or slow I/O
            try (OutputStream out = getContentResolver().openOutputStream(file.getUri())) {
                String content = "Hello, Automotive OS! Written at " + System.currentTimeMillis();
                out.write(content.getBytes());
                out.flush();
                Log.i(TAG, "Write success to output.txt");
                android.widget.Toast.makeText(this, "Successfully wrote to 'output.txt'.", android.widget.Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Write error to output.txt", e);
                android.widget.Toast.makeText(this, "Error writing to 'output.txt'.", android.widget.Toast.LENGTH_LONG).show();
            }
        } else {
            Log.w(TAG, "Cannot write to output.txt. It might be read-only or an issue with permissions.");
            android.widget.Toast.makeText(this, "Cannot write to 'output.txt'.", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
