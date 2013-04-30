package kr.ac.ajou.dv.authwithsound;

class FftHelper {
    static {
        System.loadLibrary("analyzer");
    }

    public static int[] getIntensivePoints(double[] analyzed) {
        int[] intensivePoints = new int[4];
        int len = analyzed.length;
        intensivePoints[0] = findMaximumPoint(analyzed, 0, len / 4);
        intensivePoints[1] = findMaximumPoint(analyzed, len / 4 + 1, len / 2);
        intensivePoints[2] = findMaximumPoint(analyzed, len / 2 + 1, len * 3 / 4);
        intensivePoints[3] = findMaximumPoint(analyzed, len * 3 / 4 + 1, len);
        return intensivePoints;
    }

    private static int findMaximumPoint(double[] fft, int start, int end) {
        int point = 0;
        double max = Double.MIN_VALUE;
        for (int i = start; i < end; i++)
            if (fft[i] > max) {
                max = fft[i];
                point = i;
            }
        return point;
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
