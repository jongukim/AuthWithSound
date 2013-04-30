package kr.ac.ajou.dv.authwithsound;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

class WavPlayTask extends Thread {
    public static final int ROLE_VERIFIER = 1;
    public static final int ROLE_PROVER = 2;
    private static final int MAX_PLAYING_TIME = 5000; // ms
    private final Context ctx;
    private final int which;

    public WavPlayTask(Context ctx, int role) {
        this.ctx = ctx;
        which = role;
        AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.5), 0);
    }

    @Override
    public void run() {
        MediaPlayer mp = MediaPlayer.create(ctx, (which == ROLE_VERIFIER) ? R.raw.gentleman : R.raw.gangnam);
        mp.setVolume(1.0f, 1.0f);

        int startPoint = (int) (Math.random() * (mp.getDuration() - MAX_PLAYING_TIME * 8)) + MAX_PLAYING_TIME * 4;
        mp.seekTo(startPoint);
        mp.start();
        try {
            Thread.sleep(MAX_PLAYING_TIME, 0);
        } catch (InterruptedException e) {
            Log.d(MainActivity.TAG, "Playing the music has been interrupted.");
        } finally {
            mp.stop();
        }
        mp.release();
    }
}
