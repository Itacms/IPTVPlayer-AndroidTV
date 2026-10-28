package com.iptv.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native Android/Android TV screen. The previous WebView UI is no longer the launch screen.
 * This migration intentionally does not show a PIP window on the Home tab.
 */
public class MainActivity extends Activity {
    private static final int REQUEST_M3U_FILE = 44;
    private final List<Channel> channels = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;
    private ExoPlayer player;
    private PlayerView playerView;
    private android.widget.FrameLayout playerContainer;
    private LinearLayout homePanel, channelsPanel, listsPanel, playerPanel, channelList, recentChannels;
    private TextView homeSummary, listSummary, panelTitle, playingTitle;
    private String currentSource = "";

    private static final class Channel {
        final String name, url, group, logo;
        Channel(String name, String url, String group, String logo) {
            this.name=name; this.url=url; this.group=group; this.logo=logo;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(com.iptv.player.R.layout.activity_main);
        prefs = getSharedPreferences("iptv_native", MODE_PRIVATE);
        bindViews();
        bindActions();
        showTab("home");
        String saved = prefs.getString("playlist_url", "");
        if (!saved.isEmpty()) loadUrl(saved, false);
    }

    private void bindViews() {
        homePanel=findViewById(R.id.homePanel); channelsPanel=findViewById(R.id.channelsPanel);
        listsPanel=findViewById(R.id.listsPanel); playerPanel=findViewById(R.id.playerPanel);
        channelList=findViewById(R.id.channelList); recentChannels=findViewById(R.id.recentChannels);
        homeSummary=findViewById(R.id.homeSummary); listSummary=findViewById(R.id.listSummary);
        panelTitle=findViewById(R.id.panelTitle); playingTitle=findViewById(R.id.playingTitle);
        playerContainer=findViewById(R.id.playerContainer);
    }

    private void bindActions() {
        findViewById(R.id.tabHome).setOnClickListener(v -> showTab("home"));
        findViewById(R.id.tabLive).setOnClickListener(v -> { renderChannels(); showTab("channels"); });
        findViewById(R.id.tabGuide).setOnClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("Guia de programação")
                .setMessage("A grade EPG XMLTV será adicionada na próxima etapa da migração nativa.")
                .setPositiveButton("OK", null).show();
        });
        findViewById(R.id.tabLists).setOnClickListener(v -> showTab("lists"));
        findViewById(R.id.addList).setOnClickListener(v -> askPlaylistUrl());
        findViewById(R.id.addUrl).setOnClickListener(v -> askPlaylistUrl());
        findViewById(R.id.addFile).setOnClickListener(v -> pickPlaylistFile());
        findViewById(R.id.backToChannels).setOnClickListener(v -> { stopPlayback(); renderChannels(); showTab("channels"); });
    }

    private void showTab(String tab) {
        homePanel.setVisibility("home".equals(tab) ? View.VISIBLE : View.GONE);
        channelsPanel.setVisibility("channels".equals(tab) ? View.VISIBLE : View.GONE);
        listsPanel.setVisibility("lists".equals(tab) ? View.VISIBLE : View.GONE);
        playerPanel.setVisibility("player".equals(tab) ? View.VISIBLE : View.GONE);
        if ("home".equals(tab)) {
            // Deliberately no PIP/video overlay on Home. Playback is shown only in the player screen.
            homeSummary.setText(channels.isEmpty() ? "Nenhuma lista carregada" :
                channels.size() + " canais disponíveis");
            renderRecent();
        }
        if ("lists".equals(tab)) updateListSummary();
    }

    private void askPlaylistUrl() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("https://servidor/lista.m3u");
        input.setText(currentSource.startsWith("http") ? currentSource : "");
        new AlertDialog.Builder(this).setTitle("Adicionar lista M3U").setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Carregar", (d, w) -> {
                String url=input.getText().toString().trim();
                if (!url.isEmpty()) loadUrl(url, true);
            }).show();
    }

    private void loadUrl(String url, boolean save) {
        Toast.makeText(this, "Carregando lista…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(12000); connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "IPTVPlayer/1.0");
                try (InputStream stream=connection.getInputStream()) {
                    List<Channel> parsed=parseM3u(stream);
                    runOnUiThread(() -> {
                        if (parsed.isEmpty()) {
                            Toast.makeText(this, "A lista não contém canais válidos.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        channels.clear(); channels.addAll(parsed); currentSource=url;
                        if (save) prefs.edit().putString("playlist_url", url).apply();
                        renderChannels(); updateListSummary(); showTab("home");
                        Toast.makeText(this, channels.size()+" canais carregados", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Falha ao carregar M3U: "+e.getMessage(), Toast.LENGTH_LONG).show());
            } finally { if (connection!=null) connection.disconnect(); }
        });
    }

    private List<Channel> parseM3u(InputStream stream) throws Exception {
        List<Channel> out=new ArrayList<>();
        try (BufferedReader reader=new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line, name="Canal", group="", logo="", url="";
            while ((line=reader.readLine())!=null) {
                line=line.trim();
                if (line.startsWith("#EXTINF")) {
                    int comma=line.lastIndexOf(',');
                    name=comma>=0 ? line.substring(comma+1).trim() : "Canal";
                    group=attribute(line, "group-title"); logo=attribute(line, "tvg-logo");
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    url=line;
                    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtsp://")) {
                        out.add(new Channel(name,url,group,logo));
                    }
                    name="Canal"; group=""; logo=""; url="";
                }
            }
        }
        return out;
    }

    private String attribute(String line, String key) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile(key+"=[\"']([^\"']*)[\"']").matcher(line);
        return m.find() ? m.group(1) : "";
    }

    private void pickPlaylistFile() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        startActivityForResult(intent, REQUEST_M3U_FILE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode!=REQUEST_M3U_FILE || resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        io.execute(() -> {
            try (InputStream in=getContentResolver().openInputStream(uri)) {
                List<Channel> parsed=parseM3u(in);
                runOnUiThread(() -> {
                    if (parsed.isEmpty()) { Toast.makeText(this,"Não encontrei canais válidos nesse arquivo.",Toast.LENGTH_LONG).show(); return; }
                    channels.clear(); channels.addAll(parsed); currentSource=uri.toString();
                    prefs.edit().remove("playlist_url").apply();
                    renderChannels(); updateListSummary(); showTab("home");
                    Toast.makeText(this,channels.size()+" canais importados",Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,"Não foi possível abrir o arquivo.",Toast.LENGTH_LONG).show());
            }
        });
    }

    private void renderChannels() {
        channelList.removeAllViews();
        panelTitle.setText("Ao vivo · "+channels.size()+" canais");
        for (Channel c: channels) {
            Button b=new Button(this);
            b.setText(c.name + (c.group.isEmpty() ? "" : "\n"+c.group));
            b.setAllCaps(false); b.setTextColor(0xFFFFFFFF);
            b.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            b.setPadding(24,12,24,12);
            channelList.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            b.setOnClickListener(v -> play(c));
        }
    }

    private void renderRecent() {
        recentChannels.removeAllViews();
        int count=Math.min(8,channels.size());
        for (int i=0;i<count;i++) {
            Channel c=channels.get(i);
            Button b=new Button(this); b.setAllCaps(false); b.setText(c.name);
            b.setTextColor(0xFFFFFFFF);
            recentChannels.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            b.setOnClickListener(v -> play(c));
        }
    }

    private void play(Channel c) {
        try {
            if (player==null) {
                player=new ExoPlayer.Builder(this).build();
                playerView=new PlayerView(this);
                playerView.setUseController(true);
                playerView.setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
                playerContainer.removeAllViews();
                playerContainer.addView(playerView, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                playerView.setPlayer(player);
                player.addListener(new Player.Listener() {
                    @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,"Erro ao reproduzir este canal.",Toast.LENGTH_LONG).show());
                    }
                });
            }
            playingTitle.setText(c.name);
            player.setMediaItem(MediaItem.fromUri(c.url)); player.prepare(); player.play();
            showTab("player");
        } catch (Exception e) {
            Toast.makeText(this,"Não foi possível iniciar o canal.",Toast.LENGTH_LONG).show();
        }
    }

    private void stopPlayback() {
        if (player!=null) { player.stop(); player.clearMediaItems(); }
    }

    private void updateListSummary() {
        listSummary.setText(currentSource.isEmpty() ? "Nenhuma lista adicionada." :
            "Fonte atual: "+currentSource+"\nCanais válidos: "+channels.size());
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode==KeyEvent.KEYCODE_BACK && playerPanel.getVisibility()==View.VISIBLE) {
            stopPlayback(); renderChannels(); showTab("channels"); return true;
        }
        return super.onKeyDown(keyCode,event);
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        if (player!=null) { player.release(); player=null; }
        super.onDestroy();
    }
}