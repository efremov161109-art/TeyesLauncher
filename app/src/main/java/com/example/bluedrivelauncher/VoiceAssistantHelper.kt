package com.example.bluedrivelauncher

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.Toast
import java.util.Locale

class VoiceAssistantHelper(
    private val context: Context,
    private val onCommandExecuted: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var speechRecognizer: SpeechRecognizer? = null
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("ru", "RU")
            val result = tts?.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setPitch(1.35f)
                tts?.setSpeechRate(1.05f)

                val femaleVoice = tts?.voices?.firstOrNull { voice: Voice ->
                    voice.locale.language == "ru" &&
                        (voice.name.contains("female", ignoreCase = true) ||
                         voice.name.contains("ru-ru-x-ruf", ignoreCase = true) ||
                         voice.name.contains("ru-ru-x-dfc", ignoreCase = true))
                }
                if (femaleVoice != null) {
                    tts?.voice = femaleVoice
                }
                isReady = true
            }
        }
    }

    fun activateAssistant() {
        if (!isReady) {
            Toast.makeText(context, "Голосовой движок загружается...", Toast.LENGTH_SHORT).show()
            return
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "GREETING_ID") {
                    Handler(Looper.getMainLooper()).post {
                        startListening()
                    }
                }
            }
        })

        tts?.speak("Слушаю вас, мой господин", TextToSpeech.QUEUE_FLUSH, Bundle(), "GREETING_ID")
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Распознавание речи не поддерживается", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(context, "Говорите команду...", Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: return
                processVoiceCommand(command)
            }
        })

        speechRecognizer?.startListening(intent)
    }

    private fun processVoiceCommand(command: String) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        when {
            command.contains("навигатор") || command.contains("карту") || command.contains("поехали") -> {
                speakReply("Открываю навигатор")
                onCommandExecuted("NAVI")
            }
            command.contains("следующий") || command.contains("дальше") -> {
                speakReply("Переключаю")
                onCommandExecuted("NEXT")
            }
            command.contains("пауза") || command.contains("стоп") || command.contains("выключи музыку") -> {
                speakReply("Ставлю на паузу")
                onCommandExecuted("PAUSE")
            }
            command.contains("музык") || command.contains("играй") || command.contains("включи") -> {
                speakReply("Включаю музыку")
                onCommandExecuted("PLAY")
            }
            command.contains("радио") -> {
                speakReply("Включаю радио")
                onCommandExecuted("RADIO")
            }
            command.contains("громче") -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                speakReply("Сделала погромче")
            }
            command.contains("тише") -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                speakReply("Сделала потише")
            }
            else -> {
                speakReply("Команда не распознана, мой господин")
            }
        }
    }

    private fun speakReply(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "REPLY_ID")
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
