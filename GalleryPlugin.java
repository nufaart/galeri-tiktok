package com.iqbal.galeri;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/** Membaca daftar foto & video dari galeri internal (hanya baca, tidak mengubah file). */
@CapacitorPlugin(
    name = "Gallery",
    permissions = {
        @Permission(alias = "media33", strings = { Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO }),
        @Permission(alias = "legacy", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
    }
)
public class GalleryPlugin extends Plugin {

    private String alias() {
        return Build.VERSION.SDK_INT >= 33 ? "media33" : "legacy";
    }

    @PluginMethod
    public void list(PluginCall call) {
        if (getPermissionState(alias()) != PermissionState.GRANTED) {
            requestPermissionForAlias(alias(), call, "permCallback");
            return;
        }
        doList(call);
    }

    @PermissionCallback
    private void permCallback(PluginCall call) {
        if (getPermissionState(alias()) == PermissionState.GRANTED) {
            doList(call);
        } else {
            call.reject("DENIED");
        }
    }

    private void doList(PluginCall call) {
        int limit = call.getInt("limit", 3000);
        JSArray out = new JSArray();
        String[] proj = {
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATA
        };
        String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?,?)";
        String[] args = {
            String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
            String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        };
        String sort = MediaStore.Files.FileColumns.DATE_ADDED + " DESC";
        try (Cursor c = getContext().getContentResolver().query(
            MediaStore.Files.getContentUri("external"), proj, sel, args, sort)) {
            if (c != null) {
                while (c.moveToNext() && out.length() < limit) {
                    String path = c.getString(4);
                    if (path == null) continue;
                    JSObject o = new JSObject();
                    o.put("id", c.getLong(0));
                    o.put("name", c.getString(1));
                    o.put("type", c.getString(2));
                    o.put("added", c.getLong(3) * 1000L);
                    o.put("path", path);
                    out.put(o);
                }
            }
        } catch (Exception e) {
            call.reject("ERR: " + e.getMessage());
            return;
        }
        JSObject res = new JSObject();
        res.put("items", out);
        call.resolve(res);
    }

    // ===================== UPDATE APLIKASI (dari GitHub Releases) =====================

    private static volatile String dlState = "idle"; // idle | downloading | ready | error
    private static volatile int dlPercent = 0;
    private static volatile String dlError = "";

    private File apkFile() {
        File dir = new File(getContext().getCacheDir(), "updates");
        dir.mkdirs();
        return new File(dir, "Galeri.apk");
    }

    /** Versi aplikasi yang sedang terpasang. */
    @PluginMethod
    public void getVersion(PluginCall call) {
        try {
            PackageInfo pi = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            JSObject o = new JSObject();
            o.put("code", code);
            o.put("name", pi.versionName == null ? "" : pi.versionName);
            call.resolve(o);
        } catch (Exception e) {
            call.reject("ERR: " + e.getMessage());
        }
    }

    /** Mulai mengunduh APK (berjalan di latar; pantau dengan status()). */
    @PluginMethod
    public void download(PluginCall call) {
        final String urlStr = call.getString("url");
        if (urlStr == null || !urlStr.startsWith("https://")) {
            call.reject("URL tidak valid");
            return;
        }
        if ("downloading".equals(dlState)) {
            call.resolve();
            return;
        }
        dlState = "downloading";
        dlPercent = 0;
        dlError = "";
        final File out = apkFile();
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(urlStr).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                int code = c.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);
                long total = c.getContentLengthLong();
                try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) {
                    byte[] buf = new byte[65536];
                    int n;
                    long got = 0;
                    while ((n = in.read(buf)) > 0) {
                        fo.write(buf, 0, n);
                        got += n;
                        if (total > 0) dlPercent = (int) (got * 100 / total);
                    }
                }
                dlPercent = 100;
                dlState = "ready";
            } catch (Exception e) {
                dlState = "error";
                dlError = String.valueOf(e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
        call.resolve();
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject o = new JSObject();
        o.put("state", dlState);
        o.put("percent", dlPercent);
        o.put("error", dlError);
        call.resolve(o);
    }

    /** Buka pemasangan Android untuk APK yang sudah diunduh. */
    @PluginMethod
    public void install(PluginCall call) {
        try {
            File f = apkFile();
            if (!f.exists()) {
                call.reject("APK belum diunduh");
                return;
            }
            if (Build.VERSION.SDK_INT >= 26 && !getContext().getPackageManager().canRequestPackageInstalls()) {
                Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getContext().getPackageName()));
                s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(s);
                call.reject("NEED_PERMISSION");
                return;
            }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("ERR: " + e.getMessage());
        }
    }

    /** Buka tautan https di browser (untuk login Google dan pemilih Google Foto). */
    @PluginMethod
    public void openUrl(PluginCall call) {
        String u = call.getString("url");
        if (u == null || !u.startsWith("https://")) {
            call.reject("URL tidak valid");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("ERR: " + e.getMessage());
        }
    }
}
