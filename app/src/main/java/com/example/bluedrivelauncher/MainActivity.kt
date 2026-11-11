package com.example.bluedrivelauncher

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity(), LocationListener {

    private enum class MediaSource { YANDEX_MUSIC, RADIO, LOCAL_DEVICE }
    private var currentSource = MediaSource.YANDEX_MUSIC

    private lateinit var tvSpeedValue: TextView
    private lateinit var tvTrackName: TextView
    private lateinit var tvArtistName: TextView
    private lateinit var btnSwitchSource: Button
    private lateinit var topVolumeText: TextView
    private lateinit var voiceAssistant: VoiceAssistantHelper

    private var mediaPlayer: MediaPlayer? = null
    private val localTracks = mutableListOf<Pair<String, Uri>>()
    private var currentTrackIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSpeedValue = findViewById(R.id.tvSpeedValue)
        tvTrackName = findViewById(R.id.tvTrackName)
        tvArtistName = findViewById(R.id.tvArtistName)
        btnSwitchSource = findViewById(R.id.btnSwitchSource)
        topVolumeText = findViewById(R.id.topVolumeText)

        findViewById<Button>(R.id.btnSetDefaultHome).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (e: Exception) {
                val selector = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(Intent.createChooser(selector, "Выберите Teyes CC3 Launcher на постоянку"))
            }
        }

        voiceAssistant = VoiceAssistantHelper(this) { action ->
            when (action) {
                "NAVI" -> launchSplitWindow("ru.yandex.yandexnavi")
                "PLAY", "PAUSE" -> controlMedia(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                "NEXT" -> controlMedia(KeyEvent.KEYCODE_MEDIA_NEXT)
                "RADIO" -> openRadioApp()
            }
            updateVolumeDisplay()
        }

        findViewById<ImageButton>(R.id.sideBtnVoice).setOnClickListener {
            voiceAssistant.activateAssistant()
        }
        findViewById<ImageButton>(R.id.sideBtnSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        findViewById<ImageButton>(R.id.sideBtnApps).setOnClickListener {
            showAllAppsDialog(null)
        }
        findViewById<ImageButton>(R.id.sideBtnNavi).setOnClickListener {
            launchAppFull("ru.yandex.yandexnavi")
        }

        btnSwitchSource.setOnClickListener {
            currentSource = when (currentSource) {
                MediaSource.YANDEX_MUSIC -> MediaSource.RADIO
                MediaSource.RADIO -> MediaSource.LOCAL_DEVICE
                MediaSource.LOCAL_DEVICE -> MediaSource.YANDEX_MUSIC
            }
            updateMusicCardUI()
        }

        findViewById<Button>(R.id.btnMusicPrev).setOnClickListener { controlMedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        findViewById<Button>(R.id.btnMusicPlay).setOnClickListener { controlMedia(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        findViewById<Button>(R.id.btnMusicNext).setOnClickListener { controlMedia(KeyEvent.KEYCODE_MEDIA_NEXT) }

        findViewById<Button>(R.id.btnOpenNaviWindow).setOnClickListener {
            launchSplitWindow("ru.yandex.yandexnavi")
        }
        findViewById<Button>(R.id.btnOpenNaviFull).setOnClickListener {
            launchAppFull("ru.yandex.yandexnavi")
        }
        findViewById<Button>(R.id.btnNaviClose).setOnClickListener {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(home)
        }

        setupAppSlot(findViewById(R.id.appSlot1), "slot1", "ru.yandex.music", "Я.Музыка")
        setupAppSlot(findViewById(R.id.appSlot2), "slot2", "com.android.musicfx", "Эквалайзер")
        setupAppSlot(findViewById(R.id.appSlot3), "slot3", "com.android.documentsui", "Файлы")
        setupAppSlot(findViewById(R.id.appSlot4), "slot4", "com.google.android.youtube", "YouTube")

        findViewById<Button>(R.id.appSlotAdd).setOnClickListener {
            showAllAppsDialog(null)
        }

        findViewById<Button>(R.id.btnRadioPrev).setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        findViewById<Button>(R.id.btnRadioNext).setOnClickListener { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }

        findViewById<Button>(R.id.btnRadioPrev).setOnLongClickListener {
            openRadioApp()
            true
        }
        findViewById<Button>(R.id.btnRadioNext).setOnLongClickListener {
            openRadioApp()
            true
        }

        updateVolumeDisplay()
        initPermissionsAndGps()
    }

    private fun updateMusicCardUI() {
        when (currentSource) {
            MediaSource.YANDEX_MUSIC -> {
                btnSwitchSource.text = "Я.Музыка ⇄"
                tvTrackName.text = "Яндекс Музыка"
                tvArtistName.text = MediaNotificationListener.currentTrackInfo.ifEmpty { "Управление потоком" }
                mediaPlayer?.pause()
            }
            MediaSource.RADIO -> {
                btnSwitchSource.text = "Радио ⇄"
                tvTrackName.text = "FM Радио"
                tvArtistName.text = "Нажмите Play для запуска тюнера"
                mediaPlayer?.pause()
            }
            MediaSource.LOCAL_DEVICE -> {
                btnSwitchSource.text = "Устройство ⇄"
                if (localTracks.isEmpty()) loadLocalAudio()
                if (localTracks.isNotEmpty()) {
                    tvTrackName.text = localTracks[currentTrackIndex].first
                    tvArtistName.text = "Память магнитолы / USB"
                } else {
                    tvTrackName.text = "Нет аудиофайлов"
                    tvArtistName.text = "Проверьте флешку или память"
                }
            }
        }
    }

    private fun controlMedia(keyCode: Int) {
        when (currentSource) {
            MediaSource.YANDEX_MUSIC -> sendMediaKey(keyCode)
            MediaSource.RADIO -> {
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) openRadioApp()
                else sendMediaKey(keyCode)
            }
            MediaSource.LOCAL_DEVICE -> {
                if (localTracks.isEmpty()) {
                    loadLocalAudio()
                    return
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (mediaPlayer == null) playLocalTrack(currentTrackIndex)
                        else if (mediaPlayer!!.isPlaying) mediaPlayer!!.pause()
                        else mediaPlayer!!.start()
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        currentTrackIndex = (currentTrackIndex + 1) % localTracks.size
                        playLocalTrack(currentTrackIndex)
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        currentTrackIndex = if (currentTrackIndex - 1 < 0) localTracks.size - 1 else currentTrackIndex - 1
                        playLocalTrack(currentTrackIndex)
                    }
                }
            }
        }
    }

    private fun playLocalTrack(index: Int) {
        mediaPlayer?.release()
        val track = localTracks[index]
        tvTrackName.text = track.first
        mediaPlayer = MediaPlayer.create(this, track.second).apply {
            setOnCompletionListener { controlMedia(KeyEvent.KEYCODE_MEDIA_NEXT) }
            start()
        }
    }

    private fun loadLocalAudio() {
        localTracks.clear()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            while (cursor.moveToNext()) {
                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol).toString())
                localTracks.add(Pair(cursor.getString(titleCol), uri))
            }
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun launchSplitWindow(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Приложение не установлено", Toast.LENGTH_SHORT).show()
        }
    }

