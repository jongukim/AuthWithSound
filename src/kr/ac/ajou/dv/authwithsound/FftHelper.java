package kr.ac.ajou.dv.authwithsound;

public class FftHelper {
    static {
        System.loadLibrary("analyzer");
    }

    public native static double[] fftw(double[] chunk, int size);

    public static double[] getAbs(double[] result) {
        double[] abs = new double[result.length / 2];
        for (int i = 0; i < result.length / 2; i++) {
            abs[i] = Math.sqrt(result[2 * i] * result[2 * i] + result[2 * i + 1] * result[2 * i + 1]);
        }
        return abs;
    }
}
