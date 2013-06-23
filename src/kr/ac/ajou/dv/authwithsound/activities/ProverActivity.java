package kr.ac.ajou.dv.authwithsound.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import kr.ac.ajou.dv.authwithsound.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
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
    private int play;
    private int fftSize;
    private int sampleCount;

    public void onCreate(Bundle savedInstanceState) {
        play = getIntent().getIntExtra("PLAY", MainActivity.PLAY_NO_SOUND);
        sampleCount = getIntent().getIntExtra("SAMPLECOUNT", 50);
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
                RecordingTask recordingTask = new RecordingTask(wavView, sampleCount);
                WavPlayTask wavPlayTask = new WavPlayTask(ctx, WavPlayTask.ROLE_PROVER);

                if (!recordingTask.isReady()) return false;

                sockOut.writeUTF("HELLO");
                sockOut.flush();

                sockIn.readUTF(); // read CHECK
                int nonce = sockIn.readInt();
                publishProgress("Nonce: " + nonce);

                // recording and playing at the same time
                recordingTask.start();
                if (play == MainActivity.PLAY_BOTH) wavPlayTask.start();
                recordingTask.join();
                if (play == MainActivity.PLAY_BOTH) wavPlayTask.interrupt();

                List<Hash> result = SoundAnalyzer.analyze(recordingTask.getResult());
                sockOut.writeUTF(String.format("%010d", nonce).concat(SoundAnalyzer.marshall(result)));
                sockOut.flush();
                publishProgress("Sent.");
                Log.d(TAG, "Sent all recording data.");
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
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
        }
    }
}