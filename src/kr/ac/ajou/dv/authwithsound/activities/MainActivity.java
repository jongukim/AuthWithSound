package kr.ac.ajou.dv.authwithsound.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import kr.ac.ajou.dv.authwithsound.R;

/*
MainActivity shows two buttons: prover / verifier

verifier: a server. This shows a QR code including AP's SSID, IP Address, and port #.
prover  : starts a camera. After taking a QR code, it tries to connect to the peer with the given IP Address and port #.
*/
public class MainActivity extends Activity {
    public static final String TAG = "AuthWithSound";
    public static final int PLAY_NO_SOUND = 1;
    public static final int PLAY_ONLY_VERIFIER = 2;
    public static final int PLAY_BOTH = 3;
    private RadioGroup radioButtonSoundByWho;
    private RadioGroup radioButtonSampleCount;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        TextView tv = (TextView) findViewById(R.id.main_text_info);
        tv.setText("Please choose your role:\nProver or Verifier");
        /*
        checking if WIFI is on
         */
        if (!wifiManager.isWifiEnabled()) {
            tv.setText("You should turn on the WiFi.");
            findViewById(R.id.main_button_prover).setEnabled(false);
            findViewById(R.id.main_button_verifier).setEnabled(false);
        }

        radioButtonSoundByWho = (RadioGroup) findViewById(R.id.radioButtonSoundByWho);
        radioButtonSampleCount = (RadioGroup) findViewById(R.id.radioButtonSampleCount);
    }

    @SuppressWarnings("unused")
    public void startProver(View view) {
        Intent intent = new Intent(this, ProverActivity.class);
        packIntentWithRadios(intent);
        startActivity(intent);
    }

    @SuppressWarnings("unused")
    public void startVerifier(View view) {
        Intent intent = new Intent(this, VerifierActivity.class);
        packIntentWithRadios(intent);
        startActivity(intent);
    }

    @SuppressWarnings("unused")
    public void startFftTest(View view) {
        Intent intent = new Intent(this, FftTestActivity.class);
        startActivity(intent);
    }

    private void packIntentWithRadios(Intent intent) {
        int playByWho, fftSize, sampleCount;
        switch (radioButtonSoundByWho.getCheckedRadioButtonId()) {
            case R.id.radioButtonNoSound:
                playByWho = PLAY_NO_SOUND;
                break;
            case R.id.radioButtonVerifier:
                playByWho = PLAY_ONLY_VERIFIER;
                break;
            case R.id.radioButtonBoth:
                playByWho = PLAY_BOTH;
                break;
            default:
                playByWho = PLAY_NO_SOUND;
        }
        intent.putExtra("PLAY", playByWho);

        switch (radioButtonSampleCount.getCheckedRadioButtonId()) {
            case R.id.radioButtonSampleCount15:
                sampleCount = 15;
                break;
            case R.id.radioButtonSampleCount25:
                sampleCount = 25;
                break;
            case R.id.radioButtonSampleCount50:
                sampleCount = 50;
                break;
            default:
                sampleCount = 25;
        }
        intent.putExtra("SAMPLECOUNT", sampleCount);
    }
}
