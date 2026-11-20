package com.kopijojo.kasir;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Menyimpan file unduhan dari web (laporan Excel/CSV, backup, template) ke folder
 * Download/KopiJojo. WebView Android tidak bisa mengunduh link "blob:" sendiri.
 * Dipanggil: Capacitor.nativePromise('KopiFile','save',{name, mime, data(base64), open?}).
 */
@CapacitorPlugin(name = "KopiFile")
public class KopiFilePlugin extends Plugin {

    @PluginMethod
    public void save(final PluginCall call) {
        final String name = safeName(call.getString("name", "file"));
        final String mime = call.getString("mime", "application/octet-stream");
        final String data = call.getString("data");
        final boolean open = Boolean.TRUE.equals(call.getBoolean("open", false));
        if (data == null) {
            call.reject("Data file kosong");
            return;
        }
        new Thread(() -> {
            try {
                byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                Uri uri;
                String where;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver cr = getContext().getContentResolver();
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KopiJojo");
                    uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("Tidak bisa membuat file");
                    try (OutputStream os = cr.openOutputStream(uri)) {
                        if (os == null) throw new Exception("Tidak bisa menulis file");
                        os.write(bytes);
                    }
                    where = "Download/KopiJojo/" + name;
                } else {
                    File dir = getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) dir = getContext().getFilesDir();
                    File f = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        fos.write(bytes);
                    }
                    uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
                    where = f.getAbsolutePath();
                }
                final Uri fUri = uri;
                final String fWhere = where;
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Tersimpan: " + fWhere, Toast.LENGTH_LONG).show();
                    if (open) openFile(fUri, mime);
                });
                JSObject r = new JSObject();
                r.put("path", where);
                r.put("uri", uri.toString());
                call.resolve(r);
            } catch (Exception e) {
                call.reject("Gagal menyimpan file: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void open(PluginCall call) {
        String u = call.getString("uri");
        if (u == null) {
            call.reject("uri kosong");
            return;
        }
        String mime = call.getString("mime", "*/*");
        getActivity().runOnUiThread(() -> openFile(Uri.parse(u), mime));
        call.resolve();
    }

    private void openFile(Uri uri, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(Intent.createChooser(i, "Buka dengan"));
        } catch (Exception ignored) { }
    }

    private static String safeName(String n) {
        String s = (n == null ? "file" : n).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "file" : s;
    }
}
