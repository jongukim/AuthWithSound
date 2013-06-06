package kr.ac.ajou.dv.authwithsound;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/*
MainActivity shows two buttons: prover / verifier

verifier: a server. This shows a QR code including AP's SSID, IP Address, and port #.
prover  : starts a camera. After taking a QR code, it tries to connect to the peer with the given IP Address and port #.
*/
public class MainActivity extends Activity {
    public static final String TAG = "AuthWithSound";

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
    }

    @SuppressWarnings("unused")
    public void startProver(View view) {
        Intent intent = new Intent(this, ProverActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings("unused")
    public void startMaliciousProver(View view) {
        Intent intent = new Intent(this, MaliciousProverActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings("unused")
    public void startVerifier(View view) {
        Intent intent = new Intent(this, VerifierActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings("unused")
    public void startMaliciousVerifier(View view) {
        Intent intent = new Intent(this, MaliciousVerifierActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings("unused")
    public void startFftTest(View view) {
        Intent intent = new Intent(this, FftTestActivity.class);
        startActivity(intent);
    }
}
