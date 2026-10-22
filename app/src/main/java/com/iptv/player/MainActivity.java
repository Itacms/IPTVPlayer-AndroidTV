package com.iptv.player;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private SharedPreferences prefs;

    /** Ponte entre a página e o Android: permite trocar o User-Agent usado nas requisições. */
    private class Bridge {
        @JavascriptInterface
        public String getUserAgent() {
            return prefs.getString("ua", "");
        }

        @JavascriptInterface
        public void setUserAgent(String value) {
            final String ua = value == null ? "" : value.trim();
            prefs.edit().putString("ua", ua).apply();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    web.getSettings().setUserAgentString(ua.isEmpty() ? null : ua);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("iptv", MODE_PRIVATE);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0B0D);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        // Permite que a página (arquivo local) baixe listas e canais de qualquer servidor, sem bloqueio de CORS.
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        String ua = prefs.getString("ua", "");
        if (!ua.isEmpty()) {
            s.setUserAgentString(ua);
        }
        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {
                // Remove o ícone branco de "play" que o WebView mostra enquanto o vídeo carrega.
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/index.html");
        web.requestFocus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Pergunta à página se ela trata o "Voltar"; se não, fecha o app.
        web.evaluateJavascript("(window.onBack ? onBack() : false)", value -> {
            if (!"true".equals(value)) {
                finish();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
