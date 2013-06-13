package kr.ac.ajou.dv.authwithsound;

public class Hash {
    private int f1;
    private int f2;
    private int dt;
    private int t1;

    public Hash(Coordinate anchor, Coordinate target) {
        f1 = anchor.getY();
        f2 = target.getY();
        dt = target.getX() - anchor.getX();
        t1 = anchor.getX();
    }

    public Hash(int f1, int f2, int dt, int t1) {
        this.f1 = f1;
        this.f2 = f2;
        this.dt = dt;
        this.t1 = t1;
    }

    public int getF1() {
        return f1;
    }

    public int getF2() {
        return f2;
    }

    public int getDt() {
        return dt;
    }

    public int getT1() {
        return t1;
    }

    @Override
    public String toString() {
        return String.format("%04d%04d%04d", f1, f2, dt);
    }
}
