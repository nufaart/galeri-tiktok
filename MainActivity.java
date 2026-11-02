package com.iqbal.galeri;

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

public class MainActivity extends BridgeActivity {
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

    /** Bersihkan cache WebView agar penyimpanan tidak membengkak. Folder "updates" tidak disentuh. */
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
            File web = new File(getApplicationInfo().dataDir, "app_webview/Default");
            clearDir(new File(web, "Code Cache"));
            clearDir(new File(web, "GPUCache"));
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
    }
}
