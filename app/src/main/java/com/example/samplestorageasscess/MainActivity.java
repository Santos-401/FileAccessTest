package com.example.samplestorageasscess;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
// import android.provider.DocumentsContract; // Unused import
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_OPEN_TREE = 1001;
    private Uri treeUri; // URI for the selected document tree (USB storage directory)
    private static final String TAG = "MainActivity"; // Logcat TAG

    private Button btnReadFile;
    private Button btnWriteFile;

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

        btnReadFile = findViewById(R.id.btn_read_file);
        btnReadFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readSampleFile();
            }
        });

        btnWriteFile = findViewById(R.id.btn_write_file);
        btnWriteFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                writeSampleFile();
            }
        });

        // Initially disable read/write buttons until a USB storage is selected
        btnReadFile.setEnabled(false);
        btnWriteFile.setEnabled(false);
    }

    /**
     * Initiates the process for the user to select a directory on a removable USB storage device.
     * It iterates through available storage volumes, looking for a removable one,
     * and then launches an intent to let the user pick a directory tree.
     */
    private void openUsbStorage() {
        StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> volumes = sm.getStorageVolumes();
        StorageVolume removableVolume = null;
        for (StorageVolume vol : volumes) {
            Log.i(TAG, "Found volume: " + vol.getDescription(this) + ", isRemovable: " + vol.isRemovable());
            if (vol.isRemovable()) {
                removableVolume = vol;
                break; // Use the first removable volume found
            }
        }

        if (removableVolume != null) {
            Log.i(TAG, "Using removable volume: " + removableVolume.getDescription(this));
            Intent intent = removableVolume.createOpenDocumentTreeIntent();
            startActivityForResult(intent, REQUEST_CODE_OPEN_TREE);
        } else {
            Log.w(TAG, "Removable USB storage not found");
            Toast.makeText(this, "Removable USB storage not found.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Handles the result from activities started for a result, specifically the document tree selection.
     * If successful, it obtains a persistent URI permission for the selected tree.
     *
     * @param requestCode The integer request code originally supplied to startActivityForResult().
     * @param resultCode  The integer result code returned by the child activity.
     * @param data        An Intent, which can return result data to the caller.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_TREE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                // Define flags for persistent read/write URI permission
                final int takeFlags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                treeUri = data.getData();
                if (treeUri != null) {
                    try {
                        // Persist the URI permission
                        getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                        Log.i(TAG, "Persisted URI permission for: " + treeUri.toString() + " with flags: " + takeFlags);
                        Toast.makeText(this, "USB storage access granted.", Toast.LENGTH_SHORT).show();
                        btnReadFile.setEnabled(true); // Enable file operation buttons
                        btnWriteFile.setEnabled(true);
                    } catch (SecurityException e) {
                        Log.e(TAG, "Failed to take persistable URI permission for: " + treeUri.toString(), e);
                        Toast.makeText(this, "Failed to grant access to USB storage.", Toast.LENGTH_LONG).show();
                        treeUri = null; // Failed to get permission
                        btnReadFile.setEnabled(false);
                        btnWriteFile.setEnabled(false);
                    }
                } else {
                    Log.w(TAG, "Selected URI is null.");
                    Toast.makeText(this, "Failed to get URI for selected storage.", Toast.LENGTH_LONG).show();
                    btnReadFile.setEnabled(false);
                    btnWriteFile.setEnabled(false);
                }
            } else {
                Log.w(TAG, "Intent data is null in onActivityResult.");
                Toast.makeText(this, "Failed to get selected storage information.", Toast.LENGTH_LONG).show();
                btnReadFile.setEnabled(false);
                btnWriteFile.setEnabled(false);
            }
        } else if (requestCode == REQUEST_CODE_OPEN_TREE && resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "USB storage selection was cancelled by the user.");
            Toast.makeText(this, "USB storage selection cancelled.", Toast.LENGTH_SHORT).show();
            btnReadFile.setEnabled(false);
            btnWriteFile.setEnabled(false);
        }
    }

    /**
     * Reads a sample file named "sample.txt" from the selected USB storage directory.
     * Displays the content (or part of it) in a Toast message and logs it.
     */
    private void readSampleFile() {
        if (treeUri == null) {
            Log.w(TAG, "Tree URI is null, please select storage first");
            Toast.makeText(this, "Please select USB storage first.", Toast.LENGTH_SHORT).show();
            return;
        }
        DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri);
        if (tree == null) {
            Log.e(TAG, "Could not create DocumentFile from tree URI. Check if the URI is still valid.");
            Toast.makeText(this, "Error accessing selected storage. Please re-select.", Toast.LENGTH_LONG).show();
            return;
        }

        DocumentFile file = tree.findFile("sample.txt");
        if (file != null && file.canRead()) {
            try (InputStream in = getContentResolver().openInputStream(file.getUri())) {
                // TODO: Consider reading in a background thread for large files to avoid ANR.
                byte[] buf = new byte[4096];
                int len;
                StringBuilder sb = new StringBuilder();
                while ((len = in.read(buf)) > 0) {
                    sb.append(new String(buf, 0, len));
                }
                Log.d(TAG, "File content of sample.txt: " + sb.toString());
                // Display first 100 characters in Toast for brevity
                String toastMsg = "Read from sample.txt:\n" + (sb.length() > 100 ? sb.substring(0, 100) + "..." : sb.toString());
                Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Read error from sample.txt", e);
                Toast.makeText(this, "Error reading file 'sample.txt'.", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.w(TAG, "sample.txt not found or cannot be read in the selected storage.");
            Toast.makeText(this, "'sample.txt' not found or cannot be read.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Writes a sample string to a file named "output.txt" in the selected USB storage directory.
     * If the file doesn't exist, it attempts to create it.
     * Displays a success or failure message in a Toast.
     */
    private void writeSampleFile() {
        if (treeUri == null) {
            Log.w(TAG, "Tree URI is null, please select storage first");
            Toast.makeText(this, "Please select USB storage first.", Toast.LENGTH_SHORT).show();
            return;
        }
        DocumentFile tree = DocumentFile.fromTreeUri(this, treeUri);
        if (tree == null) {
            Log.e(TAG, "Could not create DocumentFile from tree URI. Check if the URI is still valid.");
            Toast.makeText(this, "Error accessing selected storage. Please re-select.", Toast.LENGTH_LONG).show();
            return;
        }

        DocumentFile file = tree.findFile("output.txt");
        if (file == null) { // If file doesn't exist, create it
            file = tree.createFile("text/plain", "output.txt");
            if (file == null) {
                Log.e(TAG, "Could not create 'output.txt'. Check permissions and storage space.");
                Toast.makeText(this, "Error creating 'output.txt'.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (file.canWrite()) {
            // TODO: Consider writing in a background thread for large files or slow I/O to avoid ANR.
            try (OutputStream out = getContentResolver().openOutputStream(file.getUri())) {
                String content = "Hello, Automotive OS! Written at " + System.currentTimeMillis();
                out.write(content.getBytes());
                out.flush(); // Ensure all data is written to the disk
                Log.i(TAG, "Write success to output.txt");
                Toast.makeText(this, "Successfully wrote to 'output.txt'.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Write error to output.txt", e);
                Toast.makeText(this, "Error writing to 'output.txt'.", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.w(TAG, "Cannot write to output.txt. It might be read-only, an issue with permissions, or the file creation failed silently.");
            Toast.makeText(this, "Cannot write to 'output.txt'.", Toast.LENGTH_LONG).show();
        }
    }
}
