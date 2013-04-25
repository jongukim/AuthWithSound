package kr.ac.ajou.dv.authwithsound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class RecordingTask extends Thread {
    public static final int FUZZ_FACTOR = 2;
    public static final int WINDOW_SIZE = 16;
    private static final int SAMPLE_RATE = 32000;
    private static final int BUFFER_SIZE = 4096;
    private static final int SAMPLE_SIZE = 4096 / (Short.SIZE / Byte.SIZE);
    private static final int SUB_CHUNK_SIZE = (SAMPLE_SIZE / WINDOW_SIZE);
    private static final short SOUND_LEVEL_THRESHOLD = 500;
    private short[] recorded;
    private AudioRecord audioRecord;
    private boolean isReady;

    public RecordingTask() {
        isReady = false;
        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        Log.i(MainActivity.TAG, "The minimum buffer size for recording: " + minBufferSize);
        if (BUFFER_SIZE < minBufferSize) {
            Log.e(MainActivity.TAG, "Required minimum buffer size is greater than 4096 bytes!!");
            return;
        }
        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE);
            if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
                Log.e(MainActivity.TAG, "Failed to initialize an AudioRecord instance!!");
                return;
            }
        } catch (IllegalArgumentException e) {
            Log.e(MainActivity.TAG, "Illegal arguments for initializing an AudioRecord instance!!");
            return;
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

    public List<String> getResult() {
        ArrayList<String> hints = new ArrayList<String>();
        if (recorded != null) {
            /*
            녹음한 데이터를 분석할 때는 정해진 크기의 chunk로 나누어 분석하는 것이 아니라
            시간 차이를 극복하기 위해서 chunk를 겹치도록 구성한다. window 개념의 도입.
            subchunk로 모두 나눈 후에,
            (1, 2, 3, 4)
            (2, 3, 4, 5)
            (3, 4, 5, 6) ...
            과 같은 식으로 묶어서 분석한다. subchunk가 왼쪽으로 한 칸 씩 움직이며 pop, push가 되므로 queue가 적당하다.
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
                int[] points = FftHelper.getIntensivePointsAfterFft(chunk);
                hints.add(fuzz(points));
                position++;
            }
            Log.i(MainActivity.TAG, "# of all sound chunks: " + position);
        }
        return hints;
    }

    public boolean isReady() {
        return isReady;
    }

    @Override
    public void run() {
        List<short[]> soundData = new ArrayList<short[]>();
        long startTime = System.currentTimeMillis();
        audioRecord.startRecording();

        while (System.currentTimeMillis() - startTime < WavPlayTask.PLAYING_TIME) { // 정해진 시간 동안만 녹음.
            short[] audio = new short[SAMPLE_SIZE];

            int read = audioRecord.read(audio, 0, SAMPLE_SIZE);
            if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) break;
            soundData.add(audio); // 녹음된 데이터를 ArrayList에 추가
        }
        audioRecord.stop();
        audioRecord.release(); // You MUST release audioRecord, esp. in Samsung Galaxy series.
        audioRecord = null;

        Log.i(MainActivity.TAG, "# of extracted hints: " + soundData.size());
        if (soundData.size() > 0) {
            recorded = new short[soundData.size() * SAMPLE_SIZE];
            int idx = 0;
            for (short[] data : soundData) {
                /*
                녹음된 사운드의 크기가 작을 경우 구분이 되지 않아서 두 개의 소리가 다름에도 같다고 인식할 수 있다는 가정에 따라,
                사운드의 크기를 검사하고 기준에 미치지 못할 경우에는 분석에 포함시키지 않는다.
                가깝거나 멀리 있을 때 큰 결과 차이가 없었기 때문에 이와 같은 코드를 추가해본다.
                 */
                short max = Short.MIN_VALUE;
                for (short s : data) {
                    short abs = (short) Math.abs(s);
                    if (abs > max) max = abs;
                }
                if (max > SOUND_LEVEL_THRESHOLD) {
                    System.arraycopy(data, 0, recorded, idx++ * SAMPLE_SIZE, SAMPLE_SIZE);
                }
            }
            Log.i(MainActivity.TAG, "# of meaningful hints: " + idx);
        } else {
            recorded = null;
        }
    }
}