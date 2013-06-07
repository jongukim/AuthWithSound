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
    private static final int VERIFIED_THRESHOLD = 35;
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
                CryptoUtils crypto = new CryptoUtils();

                if (!recordingTask.isReady() || !crypto.ready()) return false;

                sockIn.readUTF(); // HELLO
                long wholeStart = System.nanoTime();
                if (!rttCheck(crypto)) return false;

                // simultaneously record and play
                recordingTask.start();
                wavPlayTask.start();
                recordingTask.join();
                wavPlayTask.interrupt();

                long start = System.nanoTime();
                List<Coordinate> resultFromVerifier = SoundAnalyzer.analyze(recordingTask.getResult());
                double analysisTime = System.nanoTime() - start;
                publishProgress("TVAnalying time: " + String.format("%,.2f ms", analysisTime / NANO_TO_MILLISEC));

                int recvLen = sockIn.readInt();
                byte[] recvBuf = new byte[recvLen];
                sockIn.read(recvBuf);
                String recvStr = new String(crypto.doit(recvBuf));
                List<Coordinate> resultFromProver = SoundAnalyzer.stringToPeaks(recvStr);
                int matches = compare(resultFromVerifier, resultFromProver);
                publishProgress("TVMatches: " + matches);

                if (!rttCheck(crypto)) return false;

                if (matches > VERIFIED_THRESHOLD) publishProgress("SVBG");
                else publishProgress("SVBR");

                publishProgress("TVAuthentication time: " + String.format("%,.2f s", (System.nanoTime() - wholeStart) / NANO_TO_SEC));
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            Log.d(TAG, "Verififer's cycle is complete.");
            return true;
        }

        private Boolean rttCheck(CryptoUtils crypto) throws IOException {
            Random r = new Random();
            int sentNonce = Math.abs(r.nextInt());
            long start = System.nanoTime();
            sockOut.writeUTF("PING");
            sockOut.writeInt(sentNonce);
            sockOut.flush();
            int recvLen = sockIn.readInt(); // PONG
            byte[] recvCipherText = new byte[recvLen];
            sockIn.read(recvCipherText);
            byte[] recvPlainText = crypto.doit(recvCipherText);
            long ping = System.nanoTime() - start;
            String recvStr = new String(recvPlainText);
            int recvNonce;
            try {
                recvNonce = Integer.parseInt(recvStr);
                if (recvNonce != sentNonce) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                publishProgress("TVReceived nonce is invalid!");
                return false;
            }
            publishProgress("TVRTT: " + String.format("%,.2f ms", ping / NANO_TO_MILLISEC));
            publishProgress("TVNonces (sent / received): " + sentNonce + " / " + recvNonce);
            return true;
        }

        private int compare(List<Coordinate> verifier, List<Coordinate> prover) {
            StringBuilder sb = new StringBuilder();
            for (Coordinate c : verifier) sb.append(formatCoord(c));
            Log.d(TAG, "From the verifier: " + sb.toString());
            sb = new StringBuilder();
            for (Coordinate c : prover) sb.append(formatCoord(c));
            Log.d(TAG, "From the prover  : " + sb.toString());

            int points = Integer.MIN_VALUE;
            for (Coordinate v : verifier) {
                for (Coordinate p : prover) {
                    int xdiff = Math.abs(v.getX() - p.getX());
                    if (xdiff <= 15 && Math.abs(v.getY() - p.getY()) <= 5) { // calibration
                        int diff = v.getX() - p.getX();
                        Log.d(TAG, "Datum point: " + formatCoord(v) + formatCoord(p) + " (diff: " + diff + ")");
                        int subPoints = 0;
                        for (Coordinate cv : verifier) {
                            for (Coordinate cp : prover) {
                                if (Math.abs(cv.getX() - (cp.getX() + diff)) <= 3 && Math.abs(cv.getY() - cp.getY()) <= 5) {
                                    Log.d(TAG, "\tMatch point: " + formatCoord(cv) + formatCoord(cp));
                                    subPoints++;
                                }
                            }
                        }
                        if (subPoints > points) {
                            points = subPoints;
                        }
                    }
                }
            }
            Log.d(TAG, "The length of the longest path: " + points);

//            while (!vPoints.isEmpty() && !pPoints.isEmpty()) {
//                int pStart = -1;
//                int vStart = -1;
//                int maxLength = Integer.MIN_VALUE;
//                for (int pi = 0; pi < pPoints.size(); pi++) {
//                    for (int vi = 0; vi < vPoints.size(); vi++) {
//                        int matchLength = 0;
//                        for (int k = 0; pi + k < pPoints.size() && vi + k < vPoints.size(); k++) {
//                            if (isSimilar(pPoints.get(pi + k), vPoints.get(vi + k))) matchLength++;
//                            else break;
//                        }
//                        if (matchLength > maxLength) {
//                            pStart = pi;
//                            vStart = vi;
//                            maxLength = matchLength;
//                        }
//                    }
//                }
//                if (maxLength < CONTINUOUS_MATCH_THRESHOLD) break;
//                points += maxLength;
//                for (int i = 0; i < maxLength; i++) {
//                    pPoints.remove(pStart);
//                    vPoints.remove(vStart);
//                }
//                Log.d(TAG, "Match! The found length: " + maxLength);
//            }
            return points;
        }

        private String formatCoord(Coordinate c) {
            return "[" + String.format("%3d", c.getX()) + "," + String.format("%3d", c.getY()) + "]";
        }

//        private boolean isSimilar(int pPoint, int vPoint) {
//            if (Math.abs(pPoint - vPoint) <= SIMILAR_POINT) return true;
//            else return false;
//        }

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

