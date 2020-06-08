package com.sziffer.millionvoices

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm
import com.sziffer.millionvoices.fft.FastFourierTransform
import com.tyorikan.voicerecordingvisualizer.RecordingSampler
import kotlinx.android.synthetic.main.activity_main.*
import java.io.OutputStreamWriter


class MainActivity : AppCompatActivity() {

    private var recording: Boolean = false
    private val sampleRate = 11025 // The sampling rate
    private val fftSize = 2048

    private var recordingSampler: RecordingSampler? = null
    private lateinit var record: AudioRecord
    private lateinit var audioBuffer: ShortArray

    private lateinit var pitchDetectionHandler: PitchDetectionHandler
    private lateinit var dispatcher: AudioDispatcher


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermission()

        recording = false

        startStopButton.setOnClickListener {
            handleVoiceRecorder()
        }

    }

    private fun handleVoiceRecorder() {
        if (!recording) {
            Log.i(LOG_TAG,"recording start")
            if (!checkPermission()) {
                requestPermission()
                return
            }
            recording = true
            recordingSampler = object : RecordingSampler() {}
            recordingSampler?.setSamplingInterval(100) // voice sampling interval
            recordingSampler?.link(visualizer) // link to visualizer
            recordingSampler?.startRecording()
            recordAudio()
            setButtonState()
        } else {
            Log.i(LOG_TAG,"recording stop")
            recordingSampler?.stopRecording()
            recording = false
            setButtonState()
        }
    }

    private fun setButtonState() {
        if (recording) {
            startStopButton.text = getString(R.string.stop)
        } else
            startStopButton.text = getString(R.string.start)
    }

    override fun onPause() {
        recording = false
        if (recordingSampler != null) {
            if (recordingSampler!!.isRecording)
                recordingSampler!!.stopRecording()
        }
        super.onPause()
    }

    override fun onStart() {
        setButtonState()
        super.onStart()
    }

    private fun recordAudio() {
        Thread(Runnable {

            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                bufferSize = sampleRate * 2
            }
            audioBuffer = ShortArray(bufferSize / 2)
            record = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            record.startRecording()

            val data: ArrayList<Short> = ArrayList()

            dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(
                22050,
                1024,
                0
            )
            pitchDetectionHandler = PitchDetectionHandler { p0, _ ->
                val pitchInHz = p0.pitch
                runOnUiThread {
                    processPitch(pitchInHz)
                }
            }
            val pitchProcessor: AudioProcessor =
                PitchProcessor(PitchEstimationAlgorithm.FFT_YIN, 22050F, 1024, pitchDetectionHandler)
            dispatcher.addAudioProcessor(pitchProcessor)



            val audioThread = Thread(dispatcher, "Audio Thread")
            audioThread.start()

            //for storing the whole sound
            val recordedData: ArrayList<Short> = ArrayList()

            Log.v(LOG_TAG, "Start recording")
            var shortsRead: Long = 0
            while (recording) {
                val numberOfShort = record.read(audioBuffer, 0, audioBuffer.size)

                shortsRead += numberOfShort.toLong()

                Log.i(LOG_TAG,audioBuffer.toString())

                for (item in audioBuffer) {
                    if (data.size == fftSize) {
                        doFtt(data)
                        data.clear()
                    } else {
                        data.add(item)
                        recordedData.add(item)
                    }
                }

            }
            record.stop()
            record.release()
            Log.i(LOG_TAG,data.toString())
            Log.v(
                LOG_TAG,
                String.format("Recording stopped. Samples read: %d", shortsRead)
            )
            audioThread.interrupt()

            //writeToFile(recordedData)
        }).start()
    }

    private fun doFtt(testArray: java.util.ArrayList<Short>) {


        if (testArray.size != fftSize)
            return

        val cmpArray = arrayOfNulls<FastFourierTransform.Complex>(fftSize)

        for (i in 0 until fftSize) {
            cmpArray[i] = FastFourierTransform.Complex(testArray[i].toDouble(),0.0)

        }


        FastFourierTransform.fft(cmpArray)

        //array for write to file
        val fftResult: ArrayList<Double> = ArrayList()
        var complexIndex = 0
        var complexMaxIndex = 0
        var complexMax = 0.0
        //getting the index of the highest value
        for (item in cmpArray) {
            if (item != null) {
                fftResult.add(item.getR())
                if (item.getR() > complexMax) {
                    complexMax = item.getR()
                    complexMaxIndex = complexIndex
                }
                complexIndex++
            }
        }
        //calculating frequency
        val complexFrequency: Int = complexMaxIndex * sampleRate / fftSize

        if (complexFrequency <= sampleRate.div(2)) {
            runOnUiThread {
                pitchInHzTextView.text = "$complexFrequency Hz"
            }
        }
        Log.i(LOG_TAG,"complexfreq from fft: $complexFrequency")


    }

    /*
    private fun writeResult(result: ArrayList<Double>) {

        for (item in result) {
            outputStreamWriter.write("${item.toInt()},")
            outputStreamWriter.flush()
        }

    }


    private fun writeToFile(testArray: java.util.ArrayList<Short>) {

        val outputStreamWriter =
            OutputStreamWriter(openFileOutput("sound.txt", Context.MODE_PRIVATE))
        for (item in testArray) {
            outputStreamWriter.write("$item,")
            outputStreamWriter.flush()
        }

        outputStreamWriter.close()
        Log.i(LOG_TAG,"WRITE OK")
    }
     */


    private fun processPitch(pitchInHz: Float) {
        tarsosTextView.text = "${pitchInHz.toInt()} Hz"
    }


    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_MICROPHONE
        )
    }

    companion object {
        private const val LOG_TAG = "MainActivity"
        private const val REQUEST_MICROPHONE = 123
    }
}