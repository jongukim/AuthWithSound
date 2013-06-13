package kr.ac.ajou.dv.authwithsound;

import kr.ac.ajou.dv.authwithsound.activities.MainActivity;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class SoundAnalyzer {
    public static final String TAG = MainActivity.TAG.concat(SoundAnalyzer.class.getSimpleName());
    public static final int N_STARS = 150;
    public static final int FAN_OUT = 15;
    private static final int PATCH_SIZE = 3;
    private static final int TARGET_ZONE_X = 50;
    private static final int TARGET_ZONE_Y = 30;

    public static List<Coordinate> getConstellationMap(double[][] spectogram) {
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
                if (edges.size() >= N_STARS) {
                    if (edges.lastKey() > altitude) continue;
                    edges.remove(edges.lastKey());
                }
                edges.put(altitude, new Coordinate(x, y));
            }

        List<Coordinate> result = new Vector<Coordinate>();
        for (int i = 0; i < N_STARS; i++) {
            Map.Entry<Double, Coordinate> e = edges.pollFirstEntry();
            if (e == null) break;
            result.add(e.getValue());
        }
        return result;
    }

    public static List<Hash> analyze(List<Coordinate> stars) {
        Collections.sort(stars, new Comparator<Coordinate>() {
            @Override
            public int compare(Coordinate coord1, Coordinate coord2) {
                if (coord1.getX() > coord2.getX()) return 1;
                else if (coord1.getX() < coord2.getX()) return -1;
                return 0;
            }
        });

        // FAN_OUT 개수 만큼 hash를 얻어낸다.
        Vector<Hash> hashs = new Vector<Hash>();
        for (int i = 0; i < stars.size(); i++) {
            Coordinate anchor = stars.get(i);
            for (int j = 0, k = i + 1; j < FAN_OUT && k < stars.size(); k++) {
                Coordinate ts = stars.get(k);
                if (anchor.getX() < ts.getX() && ts.getX() < anchor.getX() + TARGET_ZONE_X &&
                        ts.getY() > anchor.getY() - TARGET_ZONE_Y && ts.getY() < anchor.getY() + TARGET_ZONE_Y) {
                    hashs.add(new Hash(anchor, ts));
                    j++;
                }
            }
        }
        return hashs;
    }

    public static String marshall(List<Hash> hashs) {
        StringBuilder sb = new StringBuilder();
        for (Hash h : hashs) {
            sb.append(h.getF1() + "," + h.getF2() + "," + h.getDt() + "," + h.getT1()).append("|");
        }
        return sb.toString();
    }

    public static List<Hash> unmarshall(String str) {
        Vector<Hash> hashs = new Vector<Hash>();
        for (String hash : str.split("[|]")) {
            String[] p = hash.split("[,]");
            int f1 = Integer.parseInt(p[0]);
            int f2 = Integer.parseInt(p[1]);
            int dt = Integer.parseInt(p[2]);
            int t1 = Integer.parseInt(p[3]);
            hashs.add(new Hash(f1, f2, dt, t1));
        }
        return hashs;
    }
}
