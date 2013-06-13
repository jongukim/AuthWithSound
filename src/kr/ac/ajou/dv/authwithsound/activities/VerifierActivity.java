package kr.ac.ajou.dv.authwithsound.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;
import kr.ac.ajou.dv.authwithsound.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class VerifierActivity extends Activity {
    public static final String TAG = MainActivity.TAG.concat(VerifierActivity.class.getSimpleName());
    private static final int MAX_TRY = 10;
    private static final int VERIFIED_THRESHOLD = 15;
    private static final double NANO_TO_MILLISEC = 1000.0 * 1000.0;
    private static final double NANO_TO_SEC = NANO_TO_MILLISEC * 1000.0;
    private ServerSocket mServerSocket;
    private int mPort = 51819;
    private TextView tv;
    private ScrollView sv;
    private Context ctx;
    private Socket mSocket;
    private ObjectInputStream sockIn;
    private ObjectOutputStream sockOut;
    private WavDrawView wavView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.verifier);
        ctx = this.getApplicationContext();
        wavView = (WavDrawView) findViewById(R.id.verifier_wavform);
        tv = (TextView) findViewById(R.id.verifier_log);
        sv = (ScrollView) findViewById(R.id.verifier_scrollview);

        WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        int tryCount = 0;
        while (true) {
            try {
                mServerSocket = new ServerSocket(mPort);
            } catch (IOException e) {
                mPort++;
                if (++tryCount > MAX_TRY) {
                    tv.append("\nFail to open a listening socket.");
                    return;
                }
                continue;
            }
            break;
        }
        Intent intent = new Intent("com.google.zxing.client.android.ENCODE");
        intent.putExtra("ENCODE_TYPE", "TEXT_TYPE");
        intent.putExtra("ENCODE_FORMAT", "QR_CODE");

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID();
        String ipAddr = Formatter.formatIpAddress(wifiInfo.getIpAddress());

        HashMap<String, String> map = new HashMap<String, String>();
        map.put(QRCodeHandler.SSID, ssid);
        map.put(QRCodeHandler.IP, ipAddr);
        map.put(QRCodeHandler.PORT, Integer.toString(mPort));

        intent.putExtra("ENCODE_DATA", QRCodeHandler.encode(map));
        startActivity(intent);

        Log.d(TAG, "Starts to wait for a socket from a prover.");

        try {
            mSocket = mServerSocket.accept();
            Log.d(TAG, "A socket is accepted.");
            sockIn = new ObjectInputStream(mSocket.getInputStream());
            sockOut = new ObjectOutputStream(mSocket.getOutputStream());
            Log.d(TAG, "Each input/output stream is open.");
            String recv = sockIn.readUTF();
            Log.d(TAG, "Waiting for a READY message.");
            if (recv.equals("CAPTURE")) {
                Log.d(TAG, "Got a READY message.");
                sockOut.writeUTF("ACK");
                sockOut.flush();
                Log.d(TAG, "Sent an ACK.");
                startActivity(new Intent(this, VerifierActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        tv.setBackgroundColor(Color.GRAY);
        sv.setBackgroundColor(Color.BLACK);
        tv.setText("Waiting for a prover...");
        new VerifyingTask().execute();
    }

    @Override
    protected void onDestroy() {
        try {
            sockIn.close();
            sockOut.close();
            mSocket.close();
            mServerSocket.close();
        } catch (IOException e) {
            Log.d(TAG, "Failed to close the socket.");
        }
        super.onDestroy();
    }

    private class VerifyingTask extends AsyncTask<Void, String, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                RecordingTask recordingTask = new RecordingTask(wavView);
                WavPlayTask wavPlayTask = new WavPlayTask(ctx, WavPlayTask.ROLE_VERIFIER);

                if (!recordingTask.isReady()) return false;

                long wholeStart = System.nanoTime();
                Random r = new Random();
                sockIn.readUTF(); // HELLO
                int sentNonce = Math.abs(r.nextInt());
                sockOut.writeUTF("CHECK");
                sockOut.writeInt(sentNonce);
                sockOut.flush();

                // simultaneously record and play
                recordingTask.start();
                wavPlayTask.start();
                recordingTask.join();
                wavPlayTask.interrupt();

                long start = System.nanoTime();
                List<Hash> resultFromVerifier = SoundAnalyzer.analyze(recordingTask.getResult());
                double analysisTime = System.nanoTime() - start;
                publishProgress("TVAnalying time: " + String.format("%,.2f ms", analysisTime / NANO_TO_MILLISEC));

                String recvStr = sockIn.readUTF();
                Log.d(TAG, "Received String (before unmarshall): " + recvStr);

                int recvNonce;
                try {
                    recvNonce = Integer.parseInt(recvStr.substring(0, 10));
                    if (recvNonce != sentNonce) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    publishProgress("TVReceived nonce is invalid!");
                    return false;
                }
                publishProgress("TVNonces (sent / received): " + sentNonce + " / " + recvNonce);

                List<Hash> resultFromProver = SoundAnalyzer.unmarshall(recvStr.substring(10));
                Log.d(TAG, "# of received hashs: " + resultFromProver.size() + ", # of recorded hashs: " + resultFromVerifier.size());
                int matches = compare(resultFromVerifier, resultFromProver);
                publishProgress("TVMatches: " + matches);

                if (matches >= VERIFIED_THRESHOLD) publishProgress("SVBG");
                else publishProgress("SVBR");

                publishProgress("TVAuthentication time: " + String.format("%,.2f s", (System.nanoTime() - wholeStart) / NANO_TO_SEC));
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            Log.d(TAG, "Verififer's cycle is complete.");
            return true;
        }

        private int compare(List<Hash> verifier, List<Hash> prover) {
            StringBuilder sb = new StringBuilder();
            for (Hash h : prover) {
                sb.append(formatHash(h)).append(" ");
            }
            Log.d(TAG, "From the prover  : " + sb.toString());

            sb = new StringBuilder();
            HashMap<String, Integer> db = new HashMap<String, Integer>(); // <Hash String, t1>
            for (Hash h : verifier) {
                db.put(h.toString(), h.getT1());
                sb.append(formatHash(h)).append(" ");
            }
            Log.d(TAG, "From the verifier: " + sb.toString());

            sb = new StringBuilder();
            HashMap<Integer, Integer> offsetCounts = new HashMap<Integer, Integer>();
            for (Hash p : prover) {
                if (db.containsKey(p.toString())) {
                    int offset = db.get(p.toString()) - p.getT1();
                    sb.append(offset + ", ");
                    if (offsetCounts.containsKey(offset)) {
                        offsetCounts.put(offset, offsetCounts.get(offset) + 1);
                    } else {
                        offsetCounts.put(offset, 1);
                    }
                }
            }
            Log.d(TAG, "Matches (offsets): " + sb.toString());
            int max = Integer.MIN_VALUE;
            for (int v : offsetCounts.values()) {
                if (v > max) max = v;
            }
            return max;
        }

        private String formatHash(Hash h) {
            return "[" +
                    String.format("%03d", h.getF1()) + ":" +
                    String.format("%03d", h.getF2()) + ":" +
                    String.format("%03d", h.getDt()) + ":" +
                    "]:" +
                    String.format("%03d", h.getT1()) + "]";
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            if (values.length != 1) return;
            String out = values[0];
            String rest = out.substring(2);
            if (out.startsWith("TV")) {
                tv.append("\n" + rest);
            } else if (out.startsWith("SV")) {
                if (rest.equals("BG")) {
                    sv.setBackgroundColor(Color.GREEN);
                } else {
                    sv.setBackgroundColor(Color.RED);
                }
                tv.setBackgroundColor(Color.GRAY);
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            try {
                sockIn.close();
                sockOut.close();
                mSocket.close();
            } catch (IOException e) {
            }
            wavView.invalidate();
            if (result) {
                new AlertDialog.Builder(VerifierActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Authentication")
                        .setMessage("Waiting for the next prover?")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                finish();
                                startActivity(getIntent());
                            }
                        })
                        .create()
                        .show();
            }
        }
    }
}

