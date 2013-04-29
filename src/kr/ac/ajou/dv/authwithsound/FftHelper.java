package kr.ac.ajou.dv.authwithsound;

public class FftHelper {
    static {
        System.loadLibrary("analyzer");
    }

    private static int findMaximumPoint(long[] fft, int start, int end) {
        int point = 0;
        long max = Long.MIN_VALUE;
        for (int i = start; i < end; i++)
            if (fft[i] > max) {
                max = fft[i];
                point = i;
            }
        return point;
    }

    public native static double[] fftw(double[] chunk, int size);

    public double[] getAbs(double [] result) {
        return null;
    }
}
