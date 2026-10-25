package com.iptv.player;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.DocumentsContract;
import android.util.Base64;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Interface (HTML) dentro de um WebView transparente, por cima de um player nativo (ExoPlayer).
 * A página decide qual motor usar e controla o player pela ponte "Android".
 */
public class MainActivity extends Activity {
    private static final int REQ_PICK = 77;
    private static final int REQ_SAVE = 78;
    private static final int REQ_BACKGROUND = 79;
    private static final int REQ_LOGO_FOLDER = 80;

    private FrameLayout root;
    private FrameLayout videoHost;
    private FrameLayout multiHost;
    private TextureView texture;
    private final List<MultiTile> multiTiles = new ArrayList<>();
    private WebView web;
    private SharedPreferences prefs;

    private ExoPlayer player;
    private String curUrl = "";
    private boolean retriedHls = false;
    private boolean muted = true;
    private int fitMode = 0;      // 0 = ajustar, 1 = preencher, 2 = esticar
    private int vw = 0, vh = 0;   // tamanho do vídeo
    private float rl = 0f, rt = 0f, rr = 1f, rb = 1f; // área do vídeo (frações da tela)
    private String pendingName = "";
    private String pendingText = "";

    /** Ponte entre a página e o Android. */
    private class Bridge {
        // ---- User-Agent ----
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

        // ---- Player nativo ----
        @JavascriptInterface
        public void nPlay(final String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startNative(url);
                }
            });
        }

        @JavascriptInterface
        public void nStop() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopNative();
                }
            });
        }

        @JavascriptInterface
        public void nMute(final boolean m) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    muted = m;
                    if (player != null) player.setVolume(m ? 0f : 1f);
                }
            });
        }

        @JavascriptInterface
        public void nToggle() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (player == null) return;
                    if (player.getPlayWhenReady()) player.pause();
                    else player.play();
                }
            });
        }

        @JavascriptInterface
        public void nRect(final float l, final float t, final float r, final float b) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    rl = l;
                    rt = t;
                    rr = r;
                    rb = b;
                    applyRect();
                }
            });
        }

        @JavascriptInterface
        public void nMultiStart(final String payload) { runOnUiThread(new Runnable() { @Override public void run() { startMultiNative(payload); }}); }
        @JavascriptInterface
        public void nMultiRect(final int index, final float l, final float t, final float r, final float b) { runOnUiThread(new Runnable() { @Override public void run() { applyMultiRect(index,l,t,r,b); }}); }
        @JavascriptInterface
        public void nMultiAudio(final int index) { runOnUiThread(new Runnable() { @Override public void run() { setMultiAudio(index); }}); }
        @JavascriptInterface
        public void nMultiStop() { runOnUiThread(new Runnable() { @Override public void run() { stopMultiNative(); }}); }
        @JavascriptInterface
        public void nFit(final int mode) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fitMode = mode;
                    applyFit();
                }
            });
        }

        // ---- Tema (cor de fundo atrás da página transparente) ----
        @JavascriptInterface
        public void setBackground(final String color) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int c = Color.parseColor(color);
                        prefs.edit().putInt("bg", c).apply();
                        root.setBackgroundColor(c);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        // ---- Backup local ----
        @JavascriptInterface
        public void saveBackup(final String name, final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pendingName = name;
                    pendingText = text;
                    try {
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("application/json");
                        i.putExtra(Intent.EXTRA_TITLE, name);
                        startActivityForResult(i, REQ_SAVE);
                    } catch (Exception e) {
                        saveBackupFallback();
                    }
                }
            });
        }

        // ---- Imagem de fundo local ----
        @JavascriptInterface
        public void pickBackground() {
            runOnUiThread(new Runnable() { @Override public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("image/*");
                    startActivityForResult(i, REQ_BACKGROUND);
                } catch (Exception e) { js("background:error"); }
            }});
        }

        // ---- Pasta local de logos ----
        @JavascriptInterface
        public void pickLogoFolder() {
            runOnUiThread(new Runnable() { @Override public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(i, REQ_LOGO_FOLDER);
                } catch (Exception e) { js("logofolder:error"); }
            }});
        }

        @JavascriptInterface
        public String getLocalLogo(String channelSlug) {
            try {
                String tree = prefs.getString("logo_tree", "");
                if (tree.isEmpty() || channelSlug == null || channelSlug.isEmpty()) return "";
                Uri treeUri = Uri.parse(tree);
                String docId = DocumentsContract.getTreeDocumentId(treeUri);
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
                Cursor c = getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
                if (c == null) return "";
                Uri match = null;
                while (c.moveToNext()) {
                    String name = c.getString(1);
                    String mime = c.getString(2);
                    if (name == null || mime == null || !mime.startsWith("image/")) continue;
                    String base = name.replaceFirst("(?i)\\.(png|jpe?g|webp|gif|svg)$", "").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
                    if (base.equals(channelSlug.toLowerCase(java.util.Locale.ROOT))) {
                        match = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0)); break;
                    }
                }
                c.close();
                if (match == null) return "";
                InputStream in = getContentResolver().openInputStream(match);
                if (in == null) return "";
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n; int total = 0;
                while ((n = in.read(buf)) > 0) { total += n; if (total > 2 * 1024 * 1024) { in.close(); return ""; } out.write(buf, 0, n); }
                in.close();
                String type = getContentResolver().getType(match); if (type == null) type = "image/png";
                return "data:" + type + ";base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) { return ""; }
        }

        // ---- Listas locais ----
        @JavascriptInterface
        public void pickFile() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        startActivityForResult(i, REQ_PICK);
                    } catch (Exception e) {
                        js("nofilemanager");
                    }
                }
            });
        }

        @JavascriptInterface
        public void putCache(String id, String text) {
            try {
                FileOutputStream o = new FileOutputStream(cacheFile(id));
                o.write(text.getBytes("UTF-8"));
                o.close();
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public String getCache(String id) {
            try {
                File f = cacheFile(id);
                if (!f.exists()) return "";
                FileInputStream in = new FileInputStream(f);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                return out.toString("UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void delCache(String id) {
            try {
                cacheFile(id).delete();
            } catch (Exception ignored) {
            }
        }
    }

    private File cacheFile(String id) {
        return new File(getFilesDir(), "src_" + id.replaceAll("[^A-Za-z0-9_]", "") + ".m3u");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("iptv", MODE_PRIVATE);

        root = new FrameLayout(this);
        root.setBackgroundColor(prefs.getInt("bg", 0xFF0B0B0D));

        videoHost = new FrameLayout(this);
        videoHost.setVisibility(View.GONE);
        texture = new TextureView(this);
        videoHost.addView(texture, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        root.addView(videoHost, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        multiHost = new FrameLayout(this);
        multiHost.setVisibility(View.GONE);
        root.addView(multiHost, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                applyRect();
            }
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        // A página (arquivo local) pode baixar listas e canais de qualquer servidor, sem bloqueio de CORS.
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

    // ------------------------------------------------------------------ player nativo

    private void ensurePlayer() {
        if (player != null) return;
        DefaultRenderersFactory rf = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true);
        player = new ExoPlayer.Builder(this, rf).build();
        player.setVideoTextureView(texture);
        player.setVolume(muted ? 0f : 1f);
        player.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize size) {
                vw = Math.round(size.width * size.pixelWidthHeightRatio);
                vh = size.height;
                applyFit();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                js(isPlaying ? "playing" : "paused");
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) js("buffering");
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                boolean hasV = false, okV = false, hasA = false, okA = false;
                String vm = "", am = "";
                for (Tracks.Group g : tracks.getGroups()) {
                    if (g.getType() == C.TRACK_TYPE_VIDEO) {
                        hasV = true;
                        if (g.isSupported()) okV = true;
                        else if (g.length > 0 && vm.isEmpty()) {
                            String m = g.getTrackFormat(0).sampleMimeType;
                            vm = m == null ? "?" : m;
                        }
                    } else if (g.getType() == C.TRACK_TYPE_AUDIO) {
                        hasA = true;
                        if (g.isSupported()) okA = true;
                        else if (g.length > 0 && am.isEmpty()) {
                            String m = g.getTrackFormat(0).sampleMimeType;
                            am = m == null ? "?" : m;
                        }
                    }
                }
                if (hasV && !okV) js("novideo:" + vm);
                else if (hasA && !okA) js("noaudio:" + am);
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                // Streams HLS sem extensão ".m3u8": tenta de novo declarando o tipo.
                if (!retriedHls && e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                    retriedHls = true;
                    playUrl(curUrl, true);
                    return;
                }
                js("error:" + e.getErrorCodeName());
            }
        });
    }


    private static final class MultiTile {
        final FrameLayout host; final TextureView texture; final ExoPlayer player;
        MultiTile(FrameLayout h, TextureView t, ExoPlayer p) { host=h; texture=t; player=p; }
    }
    private void startMultiNative(String payload) {
        stopMultiNative();
        try {
            JSONArray arr=new JSONArray(payload);
            for(int i=0;i<Math.min(4,arr.length());i++) {
                JSONObject item=arr.getJSONObject(i); String url=item.optString("url","");
                if(url.isEmpty()) continue;
                FrameLayout th=new FrameLayout(this); TextureView tv=new TextureView(this);
                th.addView(tv,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER)); multiHost.addView(th);
                DefaultRenderersFactory rf=new DefaultRenderersFactory(this).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON).setEnableDecoderFallback(true);
                ExoPlayer p=new ExoPlayer.Builder(this,rf).build(); p.setVideoTextureView(tv); p.setVolume(i==0?1f:0f);
                String ua=prefs.getString("ua","");
                if(ua.isEmpty()) ua="IPTVPlayer/1.0 (Linux; Android "+Build.VERSION.RELEASE+") ExoPlayer";
                DefaultHttpDataSource.Factory f=new DefaultHttpDataSource.Factory().setUserAgent(ua).setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15000).setReadTimeoutMs(20000);
                MediaSource ms=new DefaultMediaSourceFactory(this).setDataSourceFactory(f).createMediaSource(new MediaItem.Builder().setUri(url).build());
                p.setMediaSource(ms); p.prepare(); p.setPlayWhenReady(true);
                multiTiles.add(new MultiTile(th,tv,p));
                applyMultiRect(multiTiles.size()-1,(float)item.optDouble("l",0),(float)item.optDouble("t",0),(float)item.optDouble("r",.5),(float)item.optDouble("b",.5));
            }
            if(player!=null){player.setVolume(0f);player.pause();}
            videoHost.setVisibility(View.GONE); multiHost.setVisibility(multiTiles.isEmpty()?View.GONE:View.VISIBLE);
        } catch(Exception e){stopMultiNative();js("multiview:error");}
    }
    private void applyMultiRect(int i,float l,float t,float r,float b) {
        if(i<0||i>=multiTiles.size())return; int w=root.getWidth(),h=root.getHeight(); if(w<=0||h<=0)return;
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Math.max(1,Math.round((r-l)*w)),Math.max(1,Math.round((b-t)*h)));
        lp.leftMargin=Math.round(l*w);lp.topMargin=Math.round(t*h);multiTiles.get(i).host.setLayoutParams(lp);
    }
    private void setMultiAudio(int index){if(index<0||index>=multiTiles.size())return;for(int i=0;i<multiTiles.size();i++)multiTiles.get(i).player.setVolume(i==index?1f:0f);}
    private void stopMultiNative(){for(MultiTile t:multiTiles){t.player.stop();t.player.release();multiHost.removeView(t.host);}multiTiles.clear();if(multiHost!=null)multiHost.setVisibility(View.GONE);}

    private void startNative(String url) {
        stopMultiNative();
        curUrl = url;
        retriedHls = false;
        ensurePlayer();
        videoHost.setVisibility(View.VISIBLE);
        playUrl(url, false);
    }

    private void playUrl(String url, boolean forceHls) {
        String ua = prefs.getString("ua", "");
        if (ua.isEmpty()) {
            ua = "IPTVPlayer/1.0 (Linux; Android " + Build.VERSION.RELEASE + ") ExoPlayer";
        }
        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);
        MediaItem.Builder mb = new MediaItem.Builder().setUri(url);
        if (forceHls) mb.setMimeType(MimeTypes.APPLICATION_M3U8);
        MediaSource ms = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(f)
                .createMediaSource(mb.build());
        player.setMediaSource(ms);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void stopNative() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        videoHost.setVisibility(View.GONE);
        vw = 0;
        vh = 0;
    }

    private void applyRect() {
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.max(1, Math.round((rr - rl) * w)), Math.max(1, Math.round((rb - rt) * h)));
        lp.leftMargin = Math.round(rl * w);
        lp.topMargin = Math.round(rt * h);
        videoHost.setLayoutParams(lp);
        applyFit();
    }

    private void applyFit() {
        int w = Math.round((rr - rl) * root.getWidth());
        int h = Math.round((rb - rt) * root.getHeight());
        if (w <= 0 || h <= 0) return;
        int tw = w, th = h;
        if (vw > 0 && vh > 0 && fitMode != 2) {
            float sw = (float) w / vw, sh = (float) h / vh;
            float sc = fitMode == 0 ? Math.min(sw, sh) : Math.max(sw, sh);
            tw = Math.round(vw * sc);
            th = Math.round(vh * sc);
        }
        texture.setLayoutParams(new FrameLayout.LayoutParams(tw, th, Gravity.CENTER));
    }

    /** Envia um evento para a página (window.onNative). */
    private void js(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript("window.onNative&&onNative(" + JSONObject.quote(msg) + ")", null);
            }
        });
    }

    /** Sem seletor de arquivos na TV: grava na pasta do app (Android/data/com.iptv.player/files). */
    private void saveBackupFallback() {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            File f = new File(dir, pendingName);
            FileOutputStream o = new FileOutputStream(f);
            o.write(pendingText.getBytes("UTF-8"));
            o.close();
            js("backup:path:" + f.getAbsolutePath());
        } catch (Exception e) {
            js("backup:error");
        }
    }

    // ------------------------------------------------------------------ lista local

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SAVE) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            try {
                java.io.OutputStream o = getContentResolver().openOutputStream(data.getData(), "wt");
                o.write(pendingText.getBytes("UTF-8"));
                o.close();
                js("backup:ok:" + pendingName);
            } catch (Exception e) {
                saveBackupFallback();
            }
            return;
        }
        if (requestCode == REQ_BACKGROUND) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            final Uri imageUri = data.getData();
            new Thread(new Runnable() { @Override public void run() {
                try {
                    InputStream in = getContentResolver().openInputStream(imageUri);
                    if (in == null) throw new Exception("empty image");
                    ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n; int total = 0;
                    while ((n = in.read(buf)) > 0) { total += n; if (total > 2 * 1024 * 1024) throw new Exception("image too large"); out.write(buf, 0, n); }
                    in.close(); String type = getContentResolver().getType(imageUri); if (type == null) type = "image/jpeg";
                    Cursor c = getContentResolver().query(imageUri, null, null, null, null); String name = "Imagem local";
                    if (c != null) { try { int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (ix >= 0 && c.moveToFirst()) name = c.getString(ix); } finally { c.close(); } }
                    final String fn = name, uriData = "data:" + type + ";base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                    runOnUiThread(new Runnable() { @Override public void run() { web.evaluateJavascript("window.onBackgroundPicked&&onBackgroundPicked(" + JSONObject.quote(fn) + "," + JSONObject.quote(uriData) + ")", null); }});
                } catch (Exception e) { js("background:error"); }
            }});
            return;
        }
        if (requestCode == REQ_LOGO_FOLDER) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri tree = data.getData();
            try { getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            prefs.edit().putString("logo_tree", tree.toString()).apply();
            final String u = tree.toString();
            runOnUiThread(new Runnable() { @Override public void run() { web.evaluateJavascript("window.onLogoFolderPicked&&onLogoFolderPicked(" + JSONObject.quote(u) + "," + JSONObject.quote("pasta local") + ")", null); }});
            return;
        }
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String name = "Lista local";
                    Cursor c = getContentResolver().query(uri, null, null, null, null);
                    if (c != null) {
                        try {
                            int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx);
                        } finally {
                            c.close();
                        }
                    }
                    InputStream in = getContentResolver().openInputStream(uri);
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        total += n;
                        if (total > 30000000L) break;
                    }
                    in.close();
                    final String fname = name;
                    final String text = out.toString("UTF-8");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            web.evaluateJavascript("window.onFilePicked&&onFilePicked("
                                    + JSONObject.quote(fname) + "," + JSONObject.quote(text) + ")", null);
                        }
                    });
                } catch (Exception e) {
                    js("filepick:error");
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------ ciclo de vida

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
        if (player != null) player.pause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        stopMultiNative();
        if (player != null) {
            player.release();
            player = null;
        }
        web.destroy();
        super.onDestroy();
    }
}
