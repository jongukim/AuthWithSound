package kr.ac.ajou.dv.authwithsound;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

public class WavDrawView extends View {
    private Paint paint;
    private ArrayList<float[]> lines;

    public WavDrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.BLUE);
        lines = new ArrayList<float[]>();
    }

    public void addLine(float x1, float y1, float x2, float y2) {
        float[] line = new float[4];
        line[0] = x1;
        line[1] = y1;
        line[2] = x2;
        line[3] = y2;
        lines.add(line);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (float[] line : lines)
            canvas.drawLine(line[0], line[1], line[2], line[3], paint);
    }
}
