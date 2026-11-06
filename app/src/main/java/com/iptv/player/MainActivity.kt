package com.iptv.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Native Android/Android TV screen with playback provided by Media3/ExoPlayer.
 */
class MainActivity : Activity() {
    private data class Channel(
        val name: String,
        val url: String,
        val group: String,
        val logo: String
    )

    companion object {
        private const val REQUEST_M3U_FILE = 44
    }

    private val channels = mutableListOf<Channel>()
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var prefs: SharedPreferences
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private lateinit var playerContainer: FrameLayout
    private lateinit var homePanel: View
    private lateinit var channelsPanel: LinearLayout
    private lateinit var listsPanel: LinearLayout
    private lateinit var playerPanel: LinearLayout
    private lateinit var channelList: LinearLayout
    private lateinit var recentChannels: LinearLayout
    private lateinit var homeSummary: TextView
    private lateinit var listSummary: TextView
    private lateinit var panelTitle: TextView
    private lateinit var playingTitle: TextView
    private var currentSource = ""

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("iptv_native", MODE_PRIVATE)
        bindViews()
        bindActions()
        showTab("home")

        val saved = prefs.getString("playlist_url", "") ?: ""
        if (saved.isNotEmpty()) loadUrl(saved, save = false)
    }

    private fun bindViews() {
        homePanel = findViewById(R.id.homePanel)
        channelsPanel = findViewById(R.id.channelsPanel)
        listsPanel = findViewById(R.id.listsPanel)
        playerPanel = findViewById(R.id.playerPanel)
        channelList = findViewById(R.id.channelList)
        recentChannels = findViewById(R.id.recentChannels)
        homeSummary = findViewById(R.id.homeSummary)
        listSummary = findViewById(R.id.listSummary)
        panelTitle = findViewById(R.id.panelTitle)
        playingTitle = findViewById(R.id.playingTitle)
        playerContainer = findViewById(R.id.playerContainer)
    }

    private fun bindActions() {
        findViewById<View>(R.id.tabHome).setOnClickListener { showTab("home") }
        findViewById<View>(R.id.tabLive).setOnClickListener {
            renderChannels()
            showTab("channels")
        }
        findViewById<View>(R.id.tabGuide).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Guia de programação")
                .setMessage("A grade EPG XMLTV será adicionada na próxima etapa da migração nativa.")
                .setPositiveButton("OK", null)
                .show()
        }
        findViewById<View>(R.id.tabLists).setOnClickListener { showTab("lists") }
        findViewById<View>(R.id.addList).setOnClickListener { askPlaylistUrl() }
        findViewById<View>(R.id.addUrl).setOnClickListener { askPlaylistUrl() }
        findViewById<View>(R.id.addFile).setOnClickListener { pickPlaylistFile() }
        findViewById<View>(R.id.backToChannels).setOnClickListener {
            stopPlayback()
            renderChannels()
            showTab("channels")
        }
    }

    private fun showTab(tab: String) {
        homePanel.visibility = if (tab == "home") View.VISIBLE else View.GONE
        channelsPanel.visibility = if (tab == "channels") View.VISIBLE else View.GONE
        listsPanel.visibility = if (tab == "lists") View.VISIBLE else View.GONE
        playerPanel.visibility = if (tab == "player") View.VISIBLE else View.GONE

        if (tab == "home") {
            homeSummary.text = if (channels.isEmpty()) {
                "Nenhuma lista carregada"
            } else {
                "${channels.size} canais disponíveis"
            }
            renderRecent()
        }
        if (tab == "lists") updateListSummary()
    }

    private fun askPlaylistUrl() {
        val input = EditText(this).apply {
            isSingleLine = true
            hint = "https://servidor/lista.m3u"
            setText(if (currentSource.startsWith("http")) currentSource else "")
        }

        AlertDialog.Builder(this)
            .setTitle("Adicionar lista M3U")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Carregar") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) loadUrl(url, save = true)
            }
            .show()
    }

    private fun loadUrl(url: String, save: Boolean) {
        Toast.makeText(this, "Carregando lista…", Toast.LENGTH_SHORT).show()
        io.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "IPTVPlayer/1.0")
                connection.setRequestProperty("Accept-Encoding", "identity")

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw java.io.IOException("HTTP $responseCode")
                }

                connection.inputStream.use { stream ->
                    val parsed = parseM3u(stream)
                    runOnUiThread {
                        if (parsed.isEmpty()) {
                            Toast.makeText(this, "A lista não contém canais válidos.", Toast.LENGTH_LONG).show()
                            return@runOnUiThread
                        }
                        channels.clear()
                        channels.addAll(parsed)
                        currentSource = url
                        if (save) prefs.edit().putString("playlist_url", url).apply()
                        renderChannels()
                        updateListSummary()
                        showTab("home")
                        Toast.makeText(this, "${channels.size} canais carregados", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Falha ao carregar M3U: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parseM3u(stream: InputStream): List<Channel> {
        val result = mutableListOf<Channel>()
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            var name = "Canal"
            var group = ""
            var logo = ""

            reader.forEachLine { rawLine ->
                val line = rawLine.trim().removePrefix("\uFEFF")
                if (line.isEmpty()) return@forEachLine

                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val comma = line.lastIndexOf(',')
                        name = if (comma >= 0) line.substring(comma + 1).trim() else "Canal"
                        if (name.isEmpty()) name = "Canal"
                        group = attribute(line, "group-title")
                        logo = attribute(line, "tvg-logo")
                    }
                    !line.startsWith("#") -> {
                        val url = line.substringBefore('|').trim()
                        val lowerUrl = url.lowercase()
                        if (lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://") || lowerUrl.startsWith("rtsp://")) {
                            result.add(Channel(name, url, group, logo))
                        }
                        name = "Canal"
                        group = ""
                        logo = ""
                    }
                }
            }
        }
        return result
    }

    private fun attribute(line: String, key: String): String {
        val matcher = Pattern.compile("$key=[\"']([^\"']*)[\"']").matcher(line)
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }

    private fun pickPlaylistFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_M3U_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_M3U_FILE || resultCode != RESULT_OK || data?.data == null) return

        val uri = data.data ?: return
        io.execute {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val parsed = parseM3u(stream)
                    runOnUiThread {
                        if (parsed.isEmpty()) {
                            Toast.makeText(this, "Não encontrei canais válidos nesse arquivo.", Toast.LENGTH_LONG).show()
                            return@runOnUiThread
                        }
                        channels.clear()
                        channels.addAll(parsed)
                        currentSource = uri.toString()
                        prefs.edit().remove("playlist_url").apply()
                        renderChannels()
                        updateListSummary()
                        showTab("home")
                        Toast.makeText(this, "${channels.size} canais importados", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Não foi possível abrir o arquivo.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun renderChannels() {
        channelList.removeAllViews()
        panelTitle.text = "Ao vivo · ${channels.size} canais"
        channels.forEach { channel ->
            val button = Button(this).apply {
                isAllCaps = false
                text = channel.name + if (channel.group.isEmpty()) "" else "\n${channel.group}"
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(24, 12, 24, 12)
                setOnClickListener { play(channel) }
            }
            channelList.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun renderRecent() {
        recentChannels.removeAllViews()
        channels.take(8).forEach { channel ->
            val button = Button(this).apply {
                isAllCaps = false
                text = channel.name
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { play(channel) }
            }
            recentChannels.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun play(channel: Channel) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build().also { exoPlayer ->
                    playerView = PlayerView(this).apply {
                        useController = true
                        setControllerAutoShow(false)
                        setControllerShowTimeoutMs(8_000)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setPlayer(exoPlayer)
                    }
                    playerContainer.removeAllViews()
                    playerContainer.addView(playerView, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Erro ao reproduzir este canal.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    })
                }
            }

            playingTitle.text = channel.name
            player?.apply {
                setMediaItem(MediaItem.fromUri(channel.url))
                prepare()
                play()
            }
            showTab("player")
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível iniciar o canal.", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopPlayback() {
        player?.apply {
            stop()
            clearMediaItems()
        }
    }

    private fun updateListSummary() {
        listSummary.text = if (currentSource.isEmpty()) {
            "Nenhuma lista adicionada."
        } else {
            "Fonte atual: $currentSource\nCanais válidos: ${channels.size}"
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (playerPanel.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            ) {
                // OK on the TV remote explicitly opens the Media3 player controls.
                // Move focus into the visible controls so the D-pad can navigate them.
                playerView?.showController()
                playerView?.post { focusFirstPlayerControl(playerView) }
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                stopPlayback()
                renderChannels()
                showTab("channels")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun focusFirstPlayerControl(root: View?) {
        if (root !is ViewGroup) return
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child.visibility != View.VISIBLE) continue
            if (child.isFocusable && child.requestFocus()) return
            if (child is ViewGroup) {
                focusFirstPlayerControl(child)
                if (child.hasFocus()) return
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (player?.isPlaying == true) player?.pause()
    }

    override fun onDestroy() {
        io.shutdownNow()
        playerView?.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
