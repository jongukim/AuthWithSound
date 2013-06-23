package kr.ac.ajou.dv.authwithsound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import kr.ac.ajou.dv.authwithsound.activities.MainActivity;

import java.util.List;

public class RecordingTask extends Thread {
    public static final String TAG = MainActivity.TAG.concat(RecordingTask.class.getSimpleName());
    private static final int INITIAL_TRASH = 3;
    private static final int SAMPLE_RATE = 32000;
    private static final int BUFFER_SIZE = 4096;
    private static final int FFT_SIZE = 512;
    private static final int SAMPLE_SIZE = BUFFER_SIZE / (Short.SIZE / Byte.SIZE);
    private AudioRecord audioRecord;
    private boolean isReady;
    private short[] soundData;
    private WavDrawView wavView;
    private int sampleCount;

    public RecordingTask(WavDrawView wavDrawView, int sampleCount) throws AudioException {
        isReady = false;
        wavView = wavDrawView;
        this.sampleCount = sampleCount;
        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        Log.i(TAG, "The minimum buffer size for recording: " + minBufferSize);
        if (BUFFER_SIZE < minBufferSize) {
            throw new AudioException("Required minimum buffer size is greater than 4096 bytes!!");
        }
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);
            if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED)
                throw new AudioException("Failed to initialize an AudioRecord instance!!");
        } catch (IllegalArgumentException e) {
            throw new AudioException("Illegal arguments for initializing an AudioRecord instance!!");
        }
        if (audioRecord.getState() != AudioRecord.STATE_UNINITIALIZED && audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED)
            audioRecord.stop();
        isReady = true;
    }

    @Override
    public void run() {
        soundData = new short[sampleCount * SAMPLE_SIZE];
        audioRecord.startRecording();

        int position = 0;
        for (int nRecordedSamples = -INITIAL_TRASH; nRecordedSamples < sampleCount; nRecordedSamples++) {
            short[] audio = new short[SAMPLE_SIZE];
            int read = audioRecord.read(audio, 0, SAMPLE_SIZE); // Digitalize the sound
            if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) break;
            if (nRecordedSamples < 0) continue;
            System.arraycopy(audio, 0, soundData, position, read);
            position += read;
        }
        audioRecord.stop();
        audioRecord.release(); // You MUST release audioRecord, esp. in Samsung Galaxy series.
        audioRecord = null;
    }

    public List<Coordinate> getResult() {
        if (soundData == null) return null;

        Log.d(TAG, "View Width: " + wavView.getWidth() + ", View Height: " + wavView.getHeight());
        float x = wavView.getWidth() * 0.05f;
        float y = wavView.getHeight() / 2.0f;
        float newX, newY;
        for (short rec : soundData) {
            newX = x + ((float) wavView.getWidth() / soundData.length * 0.9f);
            newY = wavView.getHeight() / 2.0f - ((float) rec * wavView.getHeight() / Short.MAX_VALUE * 0.45f);
            wavView.addLine(x, y, newX, newY);
            x = newX;
            y = newY;
        }

        int nSubChunks = soundData.length / FFT_SIZE;
        double[][] spectogram = new double[nSubChunks][];

        for (int i = 0; i < nSubChunks; i++) {
            double[] subChunk = new double[FFT_SIZE];
            for (int j = 0; j < FFT_SIZE; j++) subChunk[j] = (double) soundData[i * FFT_SIZE + j];
            double[] analyzed = FftHelper.getAbs(FftHelper.fftw(subChunk, FFT_SIZE));
            spectogram[i] = analyzed;
        }
        return SoundAnalyzer.getConstellationMap(spectogram);
    }

    public boolean isReady() {
        return isReady;
    }
}
