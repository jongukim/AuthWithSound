package kr.ac.ajou.dv.authwithsound;

import android.util.Log;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class SoundAnalyzer {
    public static final String TAG = MainActivity.TAG.concat(SoundAnalyzer.class.getSimpleName());
    public static final int N_OF_EXTRACTED_TOP_POINTS = 150;
    public static final int CLUSTERING_SIZE = 5;
    private static final int PATCH_SIZE = 3;


    public static List<Coordinate> findCorner(double[][] spectogram) {
        int height = spectogram[0].length;

        // descending ordered sorted map: we want to collect high intentity points.
        ConcurrentSkipListMap<Double, Coordinate> edges = new ConcurrentSkipListMap<Double, Coordinate>(new Comparator<Double>() {
            @Override
            public int compare(Double d1, Double d2) {
                if (d1 < d2) return 1;
                else if (d1 == d2) return 0;
                else return -1;
            }
        });

        // calculate the intensity of all points
        int margin = PATCH_SIZE - 1;
        int matrixSizeX = spectogram.length - 2 * margin;
        int matrixSizeY = height - 2 * margin;
        double[][] iMatrix = new double[matrixSizeX][matrixSizeY];
        for (int x = margin; x < spectogram.length - margin; x++) {
            for (int y = margin; y < height - margin; y++) {
                // corner detection
                double intensity = 0;
                for (int px = x - margin / 2; px <= x + margin / 2; px++)
                    for (int py = y - margin / 2; py <= y + margin / 2; py++)
                        intensity += Utils.square(spectogram[px][py] - spectogram[px][py + 1]) +
                                Utils.square(spectogram[px][py] - spectogram[px][py - 1]) +
                                Utils.square(spectogram[px][py] - spectogram[px + 1][py]) +
                                Utils.square(spectogram[px][py] - spectogram[px - 1][py]);
                iMatrix[x - margin][y - margin] = Math.sqrt(intensity);
            }
        }

        // find peaks
        for (int x = margin; x < matrixSizeX - margin; x++)
            for (int y = margin; y < matrixSizeY - margin; y++) {
                double altitude = 0;
                for (int px = x - margin / 2; px <= x + margin / 2; px++)
                    for (int py = y - margin / 2; py <= y + margin / 2; py++)
                        altitude += iMatrix[x][y] - iMatrix[px][py];
                if (edges.size() >= N_OF_EXTRACTED_TOP_POINTS) {
                    if (edges.lastKey() > altitude) continue;
                    edges.remove(edges.lastKey());
                }
                edges.put(altitude, new Coordinate(x, y));
            }

        List<Coordinate> result = new Vector<Coordinate>();
        for (int i = 0; i < N_OF_EXTRACTED_TOP_POINTS; i++) {
            Map.Entry<Double, Coordinate> e = edges.pollFirstEntry();
            if (e == null) break;
            result.add(e.getValue());
        }
        return result;
    }


    public static List<Coordinate> analyze(List<Coordinate> recording) {
        Collections.sort(recording, new Comparator<Coordinate>() {
            @Override
            public int compare(Coordinate coord1, Coordinate coord2) {
                if (coord1.getX() > coord2.getX()) return 1;
                else if (coord1.getX() < coord2.getX()) return -1;
                return 0;
            }
        });

        StringBuilder peakPoints = new StringBuilder();
        Vector<Coordinate> peaks = new Vector<Coordinate>();
        for (int i = 0; i < recording.size(); i++) {
            int cont = 0;
            int px = recording.get(i).getX();
            int py = recording.get(i).getY();
            for (int j = i; j < recording.size() - 1; j++) {
                int xdiff = Math.abs(recording.get(j + 1).getX() - recording.get(j).getX());
                int ydiff = Math.abs(recording.get(j + 1).getY() - recording.get(j).getY());
                if (xdiff <= CLUSTERING_SIZE && ydiff <= CLUSTERING_SIZE) {
                    px += recording.get(j + 1).getX();
                    py += recording.get(j + 1).getY();
                    cont++;
                } else break;
            }
            if (cont == 0) continue;

            int x = px / (cont + 1);
            int y = py / (cont + 1);
            peaks.add(new Coordinate(x, y));
            peakPoints.append(x + "," + y + "|");
            i += cont;
        }
        Log.d(TAG, "Peak Points: " + peakPoints.toString());
        return peaks;
    }

    public static String peaksToString(List<Coordinate> peaks) {
        StringBuilder sb = new StringBuilder();
        for (Coordinate c : peaks) sb.append(c.getX()).append(",").append(c.getY()).append("|");
        return sb.toString();
    }

    public static List<Coordinate> stringToPeaks(String str) {
        Log.d(TAG, "stringToPeaks: " + str);
        Vector<Coordinate> rv = new Vector<Coordinate>();
        for (String xyStr : str.split("[|]")) {
            String[] xy = xyStr.split("[,]");
            int x = Integer.parseInt(xy[0]);
            int y = Integer.parseInt(xy[1]);
            rv.add(new Coordinate(x, y));
        }
        return rv;
    }
}
