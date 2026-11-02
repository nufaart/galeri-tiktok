package com.iqbal.galeri;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(GalleryPlugin.class);
        super.onCreate(savedInstanceState);
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(this::sendInsets, 300);
        h.postDelayed(this::sendInsets, 1000);
        h.postDelayed(this::sendInsets, 2500);
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

    /** Aplikasi dibuka lagi: lanjutkan video yang aktif. */
    @Override
    public void onResume() {
        super.onResume();
        bg(false);
        sendInsets();
    }
}
