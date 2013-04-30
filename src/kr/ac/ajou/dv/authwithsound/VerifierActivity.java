package kr.ac.ajou.dv.authwithsound;

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

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;

public class VerifierActivity extends Activity {
    private static final int MAX_TRY = 10;
    private static final int VERIFIED_THRESHOLD = 20;
    private ServerSocket mServerSocket;
    private int mPort = 51819;
    private TextView tv;
    private ScrollView sv;
    private Context ctx;
    private Socket mSocket;
    private BufferedReader bufReader;
    private BufferedWriter bufWriter;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.verifier);
        ctx = this.getApplicationContext();
        tv = (TextView) findViewById(R.id.verifier_log);
        sv = (ScrollView) findViewById(R.id.scrollView);

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

        Log.d(MainActivity.TAG, "Starts to wait for a socket from a prover.");

        try {
            mSocket = mServerSocket.accept();
            Log.d(MainActivity.TAG, "A socket is accepted.");
            bufReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            bufWriter = new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream()));
            Log.d(MainActivity.TAG, "Each input/output stream is open.");
            String recv = bufReader.readLine();
            Log.d(MainActivity.TAG, "Waiting for a READY message.");
            if (recv.startsWith("CAPTURE")) {
                Log.d(MainActivity.TAG, "Got a READY message.");
                bufWriter.write("ACK");
                bufWriter.newLine();
                bufWriter.flush();
                Log.d(MainActivity.TAG, "Sent an ACK.");
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
        super.onStop();
        try {
            mServerSocket.close();
        } catch (IOException e) {
            Log.d(MainActivity.TAG, "Failed to close mServerSocket.");
        }
    }

    private class VerifyingTask extends AsyncTask<Void, String, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            boolean success = false;
            try {
                RecordingTask recordingTask = new RecordingTask();
                WavPlayTask wavPlayTask = new WavPlayTask(ctx, WavPlayTask.ROLE_VERIFIER);

                String recv = bufReader.readLine();
                if (recv.startsWith("READY")) {
                    bufWriter.write("ACK");
                    bufWriter.newLine();
                    bufWriter.flush();
                }

                if (recordingTask.isReady()) {
                    // simultaneously record and play
                    long wholeStart = System.nanoTime();
                    recordingTask.start();
                    wavPlayTask.start();
                    recordingTask.join();
                    wavPlayTask.interrupt();

                    long start = System.nanoTime();
                    // get recording data from me (verifier)
                    List<int[]> result = recordingTask.getResult();
                    long time = System.nanoTime() - start;
                    publishProgress("TVAnalying time: " + String.format("%,d", time));

                    recv = bufReader.readLine();
                    bufReader.close();
                    bufWriter.close();

                    String[] fromProver = recv.split("|");
                    publishProgress("TV# of recorded: " + result.size());
                    publishProgress("TV# of received: " + fromProver.length);

                    int points = compare(result, fromProver);

                    publishProgress("TVPoints: " + points);
                    mSocket.close();

                    if (points > VERIFIED_THRESHOLD) {
                        publishProgress("SVBG");
                    } else {
                        publishProgress("SVBR");
                    }
                    success = true;
                    time = System.nanoTime() - wholeStart;
                    publishProgress("TVAuthentication time: " + String.format("%,d", time));
                } else {
                    publishProgress("TVFailed to initialize the recording and playing.");
                }
            } catch (InterruptedException e) {
                publishProgress("TVFailed to wait for recording.");
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AudioException e) {
                e.printStackTrace();
            }
            Log.d(MainActivity.TAG, "Verififer's cycle is complete.");
            return success;
        }

        private int compare(List<int[]> result, String[] fromProver) {
            Log.i(MainActivity.TAG, result.size() + ", " + fromProver.length);

            return 0;
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

