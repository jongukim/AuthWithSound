package kr.ac.ajou.dv.authwithsound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

class RecordingTask extends Thread {
    private static final int FUZZ_FACTOR = 2;
    private static final int WINDOW_SIZE = 8;
    private static final int SAMPLE_RATE = 32000;
    private static final int SAMPLE_COUNT = 50;
    private static final int BUFFER_SIZE = 4096;
    private static final int SAMPLE_SIZE = 4096 / (Short.SIZE / Byte.SIZE);
    private static final int SUB_CHUNK_SIZE = (SAMPLE_SIZE / WINDOW_SIZE);
    private AudioRecord audioRecord;
    private boolean isReady;
    private short[] recorded;

    public RecordingTask() throws AudioException {
        isReady = false;
        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        Log.i(MainActivity.TAG, "The minimum buffer size for recording: " + minBufferSize);
        if (BUFFER_SIZE < minBufferSize) {
            throw new AudioException("Required minimum buffer size is greater than 4096 bytes!!");
        }
        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE);
            if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
                throw new AudioException("Failed to initialize an AudioRecord instance!!");
            }
        } catch (IllegalArgumentException e) {
            throw new AudioException("Illegal arguments for initializing an AudioRecord instance!!");
        }
        if (audioRecord.getState() != AudioRecord.STATE_UNINITIALIZED &&
                audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED)
            audioRecord.stop();
        isReady = true;
    }

    public static String fuzz(int[] points) {
        StringBuilder sb = new StringBuilder("H");
        for (int p : points) {
            sb.append(String.format("%04d", p - p % FUZZ_FACTOR));
        }
        return sb.toString();
    }

    public List<int[]> getResult() {
        if (recorded == null) return null;
        ArrayList<int[]> hints = new ArrayList<int[]>();
            /* We do not analyze each recorded unit (e.g. 4096 bytes) because we overcome the timing issue.
            As using the window scheme, after we got subchunks, bind them like
            (1, 2, 3, 4)
            (2, 3, 4, 5)
            (3, 4, 5, 6) ...
            Used data structure: Queue
             */
        ArrayBlockingQueue<short[]> queue = new ArrayBlockingQueue<short[]>(WINDOW_SIZE);

        int position = 0;
        while (recorded.length > (position + 1) * SUB_CHUNK_SIZE) {
            short[] subChunk = new short[SUB_CHUNK_SIZE];
            System.arraycopy(recorded, position * SUB_CHUNK_SIZE, subChunk, 0, SUB_CHUNK_SIZE);

            if (queue.offer(subChunk)) continue;
            queue.poll();
            queue.offer(subChunk);

            short[] chunk = new short[SAMPLE_SIZE];
            int offset = 0;
            for (short[] b : queue) {
                System.arraycopy(b, 0, chunk, offset, SUB_CHUNK_SIZE);
                offset += SUB_CHUNK_SIZE;
            }
            double[] sample = new double[SAMPLE_SIZE];
            for (int i = 0; i < SAMPLE_COUNT; i++) sample[i] = chunk[i];

            double[] analyzed = FftHelper.getAbs(FftHelper.fftw(sample, SAMPLE_COUNT));
            hints.add(FftHelper.getIntensivePoints(analyzed));
            position++;
        }
        return hints;
    }

    public boolean isReady() {
        return isReady;
    }

    @Override
    public void run() {
        ArrayList<short[]> soundData = new ArrayList<short[]>(SAMPLE_COUNT);
        audioRecord.startRecording();

        int ctRecordedSamples;
        for (ctRecordedSamples = 0; ctRecordedSamples < SAMPLE_COUNT; ctRecordedSamples++) {
            short[] audio = new short[SAMPLE_SIZE];

            int read = audioRecord.read(audio, 0, SAMPLE_SIZE); // Digitalize the sound
            if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) break;
            soundData.add(audio);
        }
        audioRecord.stop();
        audioRecord.release(); // You MUST release audioRecord, esp. in Samsung Galaxy series.
        audioRecord = null;

        if (ctRecordedSamples > 0) {
            recorded = new short[ctRecordedSamples * SAMPLE_SIZE];
            int idx = 0;
            for (short[] data : soundData) {
                System.arraycopy(data, 0, recorded, idx++ * SAMPLE_SIZE, SAMPLE_SIZE);
            }
        } else {
            recorded = null;
        }
    }
}