package com.kopijojo.kasir;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Update APK (OTA) dari GitHub Releases.
 * Setiap build di GitHub Actions membuat release "v1.0.<nomor build>" berisi kopi-jojo-pos.apk.
 * Saat aplikasi dibuka (dan setiap 3 jam sesudahnya) aplikasi membandingkan nomor build itu
 * dengan versionCode APK yang terpasang; kalau lebih baru, muncul tawaran update,
 * APK diunduh lalu dialog pemasangan Android dibuka.
 */
public class AppUpdater {
    private static final String APK_FILE = "kopi-jojo-pos-update.apk";
    private static final long AUTO_INTERVAL_MS = 3L * 60L * 60L * 1000L;
    private static AppUpdater instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<Activity> activityRef = new WeakReference<>(null);
    private long lastCheck = 0;
    private boolean checking = false;
    private long downloadId = -1;
    private File pendingInstall = null;
    private boolean receiverRegistered = false;

    public static synchronized AppUpdater get() {
        if (instance == null) instance = new AppUpdater();
        return instance;
    }

    public static String repo(Context ctx) {
        try {
            String r = ctx.getString(R.string.update_repo);
            if (r == null || r.trim().isEmpty() || r.contains("GANTI")) return null;
            return r.trim();
        } catch (Exception e) {
            return null;
        }
    }

    public static long currentVersionCode(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return PackageInfoCompat.getLongVersionCode(pi);
        } catch (Exception e) {
            return 0;
        }
    }

    public static String currentVersionName(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    public void onResume(Activity a) {
        activityRef = new WeakReference<>(a);
        if (pendingInstall != null && canInstall(a)) {
            File f = pendingInstall;
            pendingInstall = null;
            install(a, f);
            return;
        }
        if (System.currentTimeMillis() - lastCheck > AUTO_INTERVAL_MS) check(a, false);
    }

    /** manual = true: tampilkan pesan juga kalau sudah versi terbaru / gagal. */
    public void check(Activity a, final boolean manual) {
        activityRef = new WeakReference<>(a);
        final Context app = a.getApplicationContext();
        final String repo = repo(app);
        if (repo == null) {
            if (manual) toast(app, "Repo update belum diatur pada build ini.");
            return;
        }
        if (checking || downloadId != -1) {
            if (manual) toast(app, "Sedang memeriksa / mengunduh update…");
            return;
        }
        checking = true;
        lastCheck = System.currentTimeMillis();
        new Thread(() -> {
            try {
                JSONObject rel = fetchJson("https://api.github.com/repos/" + repo + "/releases/latest");
                String tag = rel.optString("tag_name", "");
                long remote = parseBuild(tag);
                long local = currentVersionCode(app);
                String apkUrl = null;
                JSONArray assets = rel.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject as = assets.getJSONObject(i);
                        if (as.optString("name", "").toLowerCase().endsWith(".apk")) {
                            apkUrl = as.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                final String url = apkUrl;
                final String notes = rel.optString("body", "");
                final String name = tag.startsWith("v") ? tag.substring(1) : tag;
                if (remote > local && url != null) {
                    main.post(() -> offer(name, notes, url));
                } else if (manual) {
                    toast(app, "Aplikasi sudah versi terbaru (" + currentVersionName(app) + ").");
                }
            } catch (Exception e) {
                if (manual) toast(app, "Gagal memeriksa update: " + e.getMessage());
            } finally {
                checking = false;
            }
        }).start();
    }

    private static long parseBuild(String tag) {
        Matcher m = Pattern.compile("(\\d+)\\s*$").matcher(tag == null ? "" : tag);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    private static JSONObject fetchJson(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "KopiJojoPOS-Updater");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        } finally {
            c.disconnect();
        }
        return new JSONObject(sb.toString());
    }

    private void offer(String version, String notes, String url) {
        Activity a = activityRef.get();
        if (a == null || a.isFinishing()) return;
        String msg = "Versi baru " + version + " tersedia (terpasang: " + currentVersionName(a) + ").";
        if (notes != null && !notes.trim().isEmpty()) {
            String n = notes.trim();
            if (n.length() > 500) n = n.substring(0, 500) + "…";
            msg += "\n\n" + n;
        }
        new AlertDialog.Builder(a)
            .setTitle("Update Aplikasi")
            .setMessage(msg)
            .setPositiveButton("Update Sekarang", (d, w) -> download(a, url))
            .setNegativeButton("Nanti", null)
            .show();
    }

    private void download(Activity a, String url) {
        final Context app = a.getApplicationContext();
        try {
            File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = app.getFilesDir();
            final File out = new File(dir, APK_FILE);
            if (out.exists()) out.delete();
            DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("Update Kopi Jojo POS");
            req.setDescription("Mengunduh versi terbaru…");
            req.setMimeType("application/vnd.android.package-archive");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, APK_FILE);
            registerReceiver(app, out);
            downloadId = dm.enqueue(req);
            toast(app, "Mengunduh update… lihat progres di notifikasi.");
        } catch (Exception e) {
            downloadId = -1;
            // Cadangan: buka link unduhan di browser.
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
                toast(app, "Gagal mengunduh update: " + e.getMessage());
            }
        }
    }

    private void registerReceiver(final Context app, final File out) {
        if (receiverRegistered) return;
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) return;
                downloadId = -1;
                boolean ok = false;
                DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
                try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
                    if (c != null && c.moveToFirst()) {
                        int idx = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        ok = idx >= 0 && c.getInt(idx) == DownloadManager.STATUS_SUCCESSFUL;
                    }
                } catch (Exception ignored) { }
                if (!ok || !out.exists()) {
                    toast(app, "Unduhan update gagal. Coba lagi nanti.");
                    return;
                }
                Activity a = activityRef.get();
                if (a != null && !a.isFinishing()) install(a, out);
                else pendingInstall = out;
            }
        };
        ContextCompat.registerReceiver(app, r, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED);
        receiverRegistered = true;
    }

    private static boolean canInstall(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return ctx.getPackageManager().canRequestPackageInstalls();
    }

    private void install(final Activity a, final File f) {
        if (!canInstall(a)) {
            pendingInstall = f;
            new AlertDialog.Builder(a)
                .setTitle("Izinkan Pemasangan")
                .setMessage("Supaya update bisa dipasang, aktifkan \"Izinkan dari sumber ini\" untuk Kopi Jojo POS, lalu kembali ke aplikasi.")
                .setPositiveButton("Buka Pengaturan", (d, w) -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()));
                        a.startActivity(i);
                    } catch (Exception e) {
                        toast(a, "Buka Pengaturan > Aplikasi > Kopi Jojo POS > Instal aplikasi tidak dikenal.");
                    }
                })
                .setNegativeButton("Nanti", null)
                .show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            toast(a, "Gagal membuka pemasang: " + e.getMessage());
        }
    }

    private void toast(final Context ctx, final String msg) {
        main.post(() -> Toast.makeText(ctx.getApplicationContext(), msg, Toast.LENGTH_LONG).show());
    }
}
