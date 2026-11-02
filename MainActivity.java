package com.iqbal.galeri;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;
import java.io.File;
import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
    /** Batas cache + data WebView. Jika terlewati, cache dibersihkan otomatis. */
    private static final long LIMIT_BYTES = 50L * 1024 * 1024;
    private static final String[] CACHE_DIRS = {
        "Cache", "Code Cache", "GPUCache", "DawnCache", "GrShaderCache", "ShaderCache",
        "Service Worker", "blob_storage", "component_crx_cache"
    };
    private final Handler guard = new Handler(Looper.getMainLooper());
    private final Runnable guardTask = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                try {
                    if (usedBytes() > LIMIT_BYTES) runOnUiThread(MainActivity.this::trimCaches);
                } catch (Exception ignored) {}
            }).start();
            guard.postDelayed(this, 60000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(GalleryPlugin.class);
        super.onCreate(savedInstanceState);
        // Bersihkan sisa file update (APK) dari pemasangan sebelumnya
        try {
            clearDir(new File(getCacheDir(), "updates"));
        } catch (Exception ignored) {}
        // Jangan menyimpan cache jaringan WebView (aplikasi hanya membaca file lokal)
        try {
            getBridge().getWebView().getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        } catch (Exception ignored) {}
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(this::sendInsets, 300);
        h.postDelayed(this::sendInsets, 1000);
        h.postDelayed(this::sendInsets, 2500);
        h.postDelayed(() -> handleAuth(getIntent()), 1500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleAuth(intent);
    }

    /** Terima token login Google dari halaman login (galerilive://auth?token=...). */
    private void handleAuth(Intent intent) {
        try {
            if (intent == null) return;
            Uri d = intent.getData();
            if (d == null || !"galerilive".equals(d.getScheme())) return;
            String t = d.getQueryParameter("token");
            String e = d.getQueryParameter("exp");
            if (t == null || t.isEmpty()) return;
            String js = "window.__gauth&&window.__gauth(" + JSONObject.quote(t) + ","
                + JSONObject.quote(e == null ? "3500" : e) + ")";
            getBridge().getWebView().evaluateJavascript(js, null);
        } catch (Exception ignored) {}
    }

    /** Hapus isi sebuah folder (folder itu sendiri dibiarkan). */
    private static void clearDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) clearDir(k);
            k.delete();
        }
    }

    private static long dirSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long t = 0;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File c : kids) {
                if ("updates".equals(c.getName())) continue;
                t += dirSize(c);
            }
        }
        return t;
    }

    /** Total cache + data WebView yang bisa dibersihkan (di luar aplikasi itu sendiri). */
    private long usedBytes() {
        return dirSize(getCacheDir())
            + dirSize(getCodeCacheDir())
            + dirSize(new File(getApplicationInfo().dataDir, "app_webview"));
    }

    /** Bersihkan cache WebView agar penyimpanan tidak membengkak. Folder "updates" dan data suka tidak disentuh. */
    private void trimCaches() {
        try {
            WebView wv = getBridge().getWebView();
            if (wv != null) wv.clearCache(true);
        } catch (Exception ignored) {}
        try {
            File[] kids = getCacheDir().listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if ("updates".equals(k.getName())) continue;
                    if (k.isDirectory()) clearDir(k);
                    k.delete();
                }
            }
            clearDir(getCodeCacheDir());
            File root = new File(getApplicationInfo().dataDir, "app_webview");
            File web = new File(root, "Default");
            for (String n : CACHE_DIRS) {
                clearDir(new File(web, n));
                clearDir(new File(root, n));
            }
        } catch (Exception ignored) {}
    }

    /** Kirim tinggi status bar & tombol navigasi ke halaman agar tampilan tidak tertimpa. */
    private void sendInsets() {
        try {
            WindowInsetsCompat ins = ViewCompat.getRootWindowInsets(getWindow().getDecorView());
            if (ins == null) return;
            Insets b = ins.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            float d = getResources().getDisplayMetrics().density;
            getBridge().getWebView().evaluateJavascript(
                "window.__insets&&window.__insets(" + (b.top / d) + "," + (b.bottom / d) + ")", null);
        } catch (Exception ignored) {}
    }

    private void bg(boolean on) {
        try {
            getBridge().getWebView().evaluateJavascript("window.__bg&&window.__bg(" + on + ")", null);
        } catch (Exception ignored) {}
    }

    /** Aplikasi ke background / layar mati: jeda video. */
    @Override
    public void onPause() {
        bg(true);
        guard.removeCallbacks(guardTask);
        super.onPause();
    }

    /** Aplikasi tidak terlihat lagi: bersihkan cache. */
    @Override
    public void onStop() {
        trimCaches();
        super.onStop();
    }

    /** Aplikasi dibuka lagi: lanjutkan video yang aktif. */
    @Override
    public void onResume() {
        super.onResume();
        bg(false);
        sendInsets();
        guard.removeCallbacks(guardTask);
        guard.postDelayed(guardTask, 20000);
    }
}
