package kr.ac.ajou.dv.authwithsound.activities;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import kr.ac.ajou.dv.authwithsound.FftHelper;
import kr.ac.ajou.dv.authwithsound.R;

public class FftTestActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fft_test);
        TextView tv = (TextView) findViewById(R.id.fft_test_log);
        double[] in = new double[8];
        in[0] = 0.0176;
        in[1] = -0.0620;
        in[2] = 0.2467;
        in[3] = 0.4599;
        in[4] = -0.0582;
        in[5] = 0.4694;
        in[6] = 0.0001;
        in[7] = -0.2873;
        // 8 inputs, 5 outpus
        double[] result = FftHelper.fftw(in, 8);
        for (double r : result)
            tv.append(r + "\n");

        tv.append("\n");
        double[] abses = FftHelper.getAbs(result);
        for(double a: abses) tv.append(a + "\n");
    }
}