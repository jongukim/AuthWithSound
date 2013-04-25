package kr.ac.ajou.dv.authwithsound;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class ProverActivity extends Activity {
    private TextView tv;
    private Context ctx;
    private String ipaddr;
    private String port;
    private Socket socket;
    private BufferedWriter bufWriter;
    private BufferedReader bufReader;

    public void onCreate(Bundle savedInstanceState) {
        /*
        Starts the Barcode Scanner app.
         */
        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
        startActivityForResult(intent, 0); // Prover should analyze the taken QR Code

        super.onCreate(savedInstanceState);
        setContentView(R.layout.prover);
        ctx = this;
        tv = (TextView) findViewById(R.id.text_right_params);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0) { // after Prover takes a QR Code
            if (resultCode == RESULT_OK) {
                String contents = data.getStringExtra("SCAN_RESULT");
                String format = data.getStringExtra("SCAN_RESULT_FORMAT");
                Log.d(MainActivity.TAG, "Return: " + contents + " (Format: " + format + ")");
                Map<String, String> map = QRCodeHandler.decode(contents);
                String ssid = map.get(QRCodeHandler.SSID);
                ipaddr = map.get(QRCodeHandler.IP);
                port = map.get(QRCodeHandler.PORT);

                tv.setText("SSID: " + ssid);
                tv.append("\nIP Address: " + ipaddr);
                tv.append("\nPort Number: " + port);

                try {
                    socket = new Socket(ipaddr, Integer.parseInt(port));
                    Log.d(MainActivity.TAG, "A socket is open.");
                    bufWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    bufReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    Log.d(MainActivity.TAG, "Each input/output stream is open.");
                    bufWriter.write("CAPTURE");
                    bufWriter.newLine();
                    bufWriter.flush();
                    Log.d(MainActivity.TAG, "Sent a READY message and waiting a response...");
                    bufReader.readLine();
                    Log.d(MainActivity.TAG, "Everything is OK. I start to record and play.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void startPlaynRec(View view) {
        new ProvingTask().execute();
    }

    private class ProvingTask extends AsyncTask<Void, String, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            boolean success = false;
            try {
                RecordingTask recordingTask = new RecordingTask();
                WavPlayTask wavPlayTask = new WavPlayTask(ctx, WavPlayTask.ROLE_PROVER);

                bufWriter.write("READY");
                bufWriter.newLine();
                bufWriter.flush();

                Log.d(MainActivity.TAG, "Sent a READY message and waiting a response...");
                bufReader.readLine();

                if (recordingTask.isReady()) {
                    // recording and playing at the same time
                    recordingTask.start();
                    wavPlayTask.start();
                    recordingTask.join();
                    wavPlayTask.join();

                    publishProgress("Analyzing the recordings.");
                    List<String> result = recordingTask.getResult();
                    StringBuilder sb = new StringBuilder();
                    for (String s : result) {
                        sb.append(s).append(","); // joins all results
                    }
                    publishProgress("Sending the analyzed data.");
                    socket = new Socket(ipaddr, Integer.parseInt(port));
                    bufWriter.write(sb.toString());
                    bufWriter.newLine();
                    bufWriter.flush();
                    bufWriter.close();
                    bufReader.close();
                    publishProgress("Sent.");
                    Log.d(MainActivity.TAG, "Sent all recording data.");
                    socket.close();
                    success = true;
                } else {
                    Log.e(MainActivity.TAG, "Failed to initialize the recording and playing.");
                }
            } catch (InterruptedException e) {
                Log.e(MainActivity.TAG, "Failed to wait for recording.");
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return success;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            if (values.length != 1) return;
            String out = values[0];
            tv.append("\n" + out);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                new AlertDialog.Builder(ProverActivity.this)
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