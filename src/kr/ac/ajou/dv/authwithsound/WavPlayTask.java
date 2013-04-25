package kr.ac.ajou.dv.authwithsound;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

public class WavPlayTask extends Thread {
    public static final int PLAYING_TIME = 10000; // ms
    public static final int ROLE_VERIFIER = 1;
    public static final int ROLE_PROVER = 2;
    private Context ctx;
    private int which;

    public WavPlayTask(Context ctx, int role) {
        this.ctx = ctx;
        which = role;
        AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.8), 0);
    }

    @Override
    public void run() {
        MediaPlayer mp = MediaPlayer.create(ctx, (which == ROLE_VERIFIER) ? R.raw.gentleman : R.raw.gangnam);
        mp.setVolume(1.0f, 1.0f);

        int startPoint = (int) (Math.random() * (mp.getDuration() - PLAYING_TIME * 8)) + PLAYING_TIME * 4;
        mp.seekTo(startPoint);
        mp.start();
        try {
            Thread.sleep(PLAYING_TIME, 0);
        } catch (InterruptedException e) {
            Log.d(MainActivity.TAG, "Playing the music has been interrupted.");
        }
        mp.stop();
        mp.release();
    }
}
