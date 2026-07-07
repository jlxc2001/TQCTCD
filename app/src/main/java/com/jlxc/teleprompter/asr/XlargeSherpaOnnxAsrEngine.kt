package com.jlxc.teleprompter.asr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlin.math.max

class XlargeSherpaOnnxAsrEngine(context: Context) : AsrEngine {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null

    override fun name(): String = "sherpa-onnx 中文 xlarge 离线 ASR"

    override fun isAvailable(): Boolean {
        return AsrModelInfo.hasBundledModel(appContext)
    }

    override fun start(listener: AsrEngine.Listener?) {
        if (running) return
        if (!isAvailable()) {
            emitError(listener, AsrModelInfo.statusText(appContext))
            return
        }
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            emitError(listener, "没有麦克风权限，无法启动本地语音识别")
            return
        }
        running = true
        worker = Thread({ runRecognizer(listener) }, "JLXC-Sherpa-ASR").apply { start() }
    }

    override fun stop() {
        running = false
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { worker?.interrupt() } catch (_: Exception) {}
        worker = null
    }

    @SuppressLint("MissingPermission")
    private fun runRecognizer(listener: AsrEngine.Listener?) {
        var recognizer: OnlineRecognizer? = null
        var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
        try {
            emitReady(listener, "${name()} · 正在加载模型")
            val modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = AsrModelInfo.ENCODER,
                    decoder = AsrModelInfo.DECODER,
                    joiner = AsrModelInfo.JOINER,
                ),
                tokens = AsrModelInfo.TOKENS,
                numThreads = max(2, Runtime.getRuntime().availableProcessors() / 2),
                provider = "cpu",
                modelType = "zipformer2"
            )
            val config = OnlineRecognizerConfig(
                modelConfig = modelConfig,
                enableEndpoint = true,
                decodingMethod = "greedy_search",
                maxActivePaths = 4,
            )
            recognizer = OnlineRecognizer(assetManager = appContext.assets, config = config)
            stream = recognizer.createStream()

            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = max(minBuffer, sampleRate)
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord = record
            record.startRecording()
            emitReady(listener, "${name()} · ${AsrModelInfo.MODEL_LABEL} · 正在听 · 支持回读上一段")

            val shorts = ShortArray(bufferSize / 2)
            var lastPartial = ""
            while (running) {
                val n = record.read(shorts, 0, shorts.size)
                if (n <= 0) continue
                val samples = FloatArray(n)
                for (i in 0 until n) samples[i] = shorts[i] / 32768.0f
                stream.acceptWaveform(samples, sampleRate)
                while (running && recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }
                val result = recognizer.getResult(stream)
                val text = result.text.trim()
                if (text.isNotEmpty() && text != lastPartial) {
                    lastPartial = text
                    emitPartial(listener, text)
                }
                if (recognizer.isEndpoint(stream)) {
                    if (text.isNotEmpty()) emitFinal(listener, text)
                    recognizer.reset(stream)
                    lastPartial = ""
                }
            }
        } catch (e: Throwable) {
            emitError(listener, "sherpa-onnx 启动失败：${e.javaClass.simpleName}: ${e.message ?: "未知错误"}")
        } finally {
            try { audioRecord?.stop() } catch (_: Exception) {}
            try { audioRecord?.release() } catch (_: Exception) {}
            audioRecord = null
            try { stream?.release() } catch (_: Exception) {}
            try { recognizer?.release() } catch (_: Exception) {}
            running = false
        }
    }

    private fun emitReady(listener: AsrEngine.Listener?, text: String) = main.post { listener?.onReady(text) }
    private fun emitError(listener: AsrEngine.Listener?, text: String) = main.post { listener?.onError(text) }
    private fun emitPartial(listener: AsrEngine.Listener?, text: String) = main.post { listener?.onPartialText(text) }
    private fun emitFinal(listener: AsrEngine.Listener?, text: String) = main.post { listener?.onFinalText(text) }
}
