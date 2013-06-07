package kr.ac.ajou.dv.authwithsound;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;
import kr.ac.ajou.dv.authwithsound.activities.MainActivity;

public class WavPlayTask extends Thread {
    public static final String TAG = MainActivity.TAG.concat(WavPlayTask.class.getSimpleName());
    public static final int ROLE_VERIFIER = 1;
    public static final int ROLE_PROVER = 2;
    private static final int MAX_PLAYING_TIME = 5000; // ms
    private final Context ctx;
    private final int which;

    public WavPlayTask(Context ctx, int role) {
        this.ctx = ctx;
        which = role;
        AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * ((which == ROLE_VERIFIER) ? 0.65 : 0.7)), 0);
    }

    @Override
    public void run() {
        MediaPlayer mp = MediaPlayer.create(ctx, (which == ROLE_VERIFIER) ? R.raw.stutter : R.raw.billie_jean);
        mp.setVolume(1.0f, 1.0f);

        int startPoint = (which == ROLE_VERIFIER) ? mp.getDuration() / 2 : mp.getDuration() / 2;
        mp.seekTo(startPoint);
        mp.start();
        try {
            Thread.sleep(MAX_PLAYING_TIME, 0);
        } catch (InterruptedException e) {
            Log.d(TAG, "Playing the music has been interrupted.");
        } finally {
            mp.stop();
        }
        mp.release();
    }
}
