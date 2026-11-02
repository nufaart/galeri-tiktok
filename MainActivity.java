package com.iqbal.galeri;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(GalleryPlugin.class);
        super.onCreate(savedInstanceState);
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
    }
}
