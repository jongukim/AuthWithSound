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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class ProverActivity extends Activity {
    public static final String TAG = MainActivity.TAG.concat(ProverActivity.class.getSimpleName());
    private TextView tv;
    private Context ctx;
    private String ipaddr;
    private String port;
    private Socket socket;
    private ObjectOutputStream sockOut;
    private ObjectInputStream sockIn;
    private WavDrawView wavView;

    public void onCreate(Bundle savedInstanceState) {
        /*
        Starts the Barcode Scanner app.
         */
        Intent intent = new Intent("com.google.zxing.client.android.SCAN");
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
        startActivityForResult(intent, 0); // Prover should analyze the taken QR Code

        super.onCreate(savedInstanceState);
        setContentView(R.layout.prover);
        tv = (TextView) findViewById(R.id.prover_log);
        wavView = (WavDrawView) findViewById(R.id.prover_wavform);
        ctx = this;
    }

    @Override
    protected void onDestroy() {
        try {
            sockIn.close();
            sockOut.close();
            socket.close();
        } catch (IOException e) {
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0) { // after Prover takes a QR Code
            if (resultCode == RESULT_OK) {
                String contents = data.getStringExtra("SCAN_RESULT");
                String format = data.getStringExtra("SCAN_RESULT_FORMAT");
                Log.d(TAG, "Return: " + contents + " (Format: " + format + ")");
                Map<String, String> map = QRCodeHandler.decode(contents);
                String ssid = map.get(QRCodeHandler.SSID);
                ipaddr = map.get(QRCodeHandler.IP);
                port = map.get(QRCodeHandler.PORT);

                tv.setText("SSID: " + ssid);
                tv.append("\nIP Address: " + ipaddr);
                tv.append("\nPort Number: " + port);

                try {
                    socket = new Socket(ipaddr, Integer.parseInt(port));
                    Log.d(TAG, "A socket is open.");
                    sockOut = new ObjectOutputStream(socket.getOutputStream());
                    sockIn = new ObjectInputStream(socket.getInputStream());
                    Log.d(TAG, "Each input/output stream is open.");
                    sockOut.writeUTF("CAPTURE");
                    sockOut.flush();
                    Log.d(TAG, "Sent a READY message and waiting a response...");
                    sockIn.readUTF();
                    Log.d(TAG, "Everything is OK. I start to record and play.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @SuppressWarnings("unused")
    public void startPlaynRec(View view) {
        new ProvingTask().execute();
    }

    private class ProvingTask extends AsyncTask<Void, String, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                RecordingTask recordingTask = new RecordingTask(wavView);
                WavPlayTask wavPlayTask = new WavPlayTask(ctx, WavPlayTask.ROLE_PROVER);
                CryptoUtils crypto = new CryptoUtils();

                if (!recordingTask.isReady() || !crypto.ready()) return false;

                sockOut.writeUTF("HELLO");
                sockOut.flush();

                responsePing(crypto);

                // recording and playing at the same time
                recordingTask.start();
                wavPlayTask.start();
                recordingTask.join();
                wavPlayTask.interrupt();

                List<Coordinate> result =  SoundAnalyzer.analyze(recordingTask.getResult());

                byte[] cipherText = crypto.doit(SoundAnalyzer.peaksToString(result).getBytes());
                sockOut.writeInt(cipherText.length);
                sockOut.write(cipherText);
                sockOut.flush();
                publishProgress("Sent.");
                Log.d(TAG, "Sent all recording data.");

                responsePing(crypto);
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to wait for recording.");
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AudioException e) {
                e.printStackTrace();
            }
            return true;
        }

        private void responsePing(CryptoUtils crypto) throws IOException {
            sockIn.readUTF(); // read PING
            int nonce = sockIn.readInt();
            byte[] cipher = crypto.doit(String.valueOf(nonce).getBytes());
            sockOut.writeInt(cipher.length);
            sockOut.write(cipher);
            sockOut.flush();
            publishProgress("Nonce: " + nonce);
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
            wavView.invalidate();
            try {
                sockOut.close();
                sockIn.close();
                socket.close();
            } catch (IOException e) {
            }
            if (result) {
                new AlertDialog.Builder(ProverActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Authentication")
                        .setMessage("Next prover?")
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