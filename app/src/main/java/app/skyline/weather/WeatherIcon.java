package app.skyline.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

final class WeatherIcon extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int code;
    private final boolean day;

    WeatherIcon(Context context, int code, boolean day) {
        super(context);
        this.code = code;
        this.day = day;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void color(String hex) { paint.setColor(Color.parseColor(hex)); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        canvas.save();
        canvas.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
        canvas.scale(size / 100, size / 100);
        boolean clear = code <= 1 && code >= 0;
        if (clear || code == 2) {
            float x = clear ? 50 : 65, y = clear ? 50 : 35;
            color(day ? "#FFCC79" : "#BDC8FF");
            canvas.drawCircle(x, y, day ? 20 : 23, paint);
            if (day) {
                paint.setStrokeWidth(3);
                paint.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
                    canvas.drawLine(x + dx * 26, y + dy * 26, x + dx * 32, y + dy * 32, paint);
                }
            }
        }
        if (!clear) {
            color("#A2C6BA");
            canvas.drawRoundRect(11, 53, 87, 74, 12, 12, paint);
            color("#E8F4EB");
            canvas.drawCircle(30, 52, 15, paint);
            canvas.drawCircle(52, 46, 20, paint);
            canvas.drawCircle(72, 55, 13, paint);
            canvas.drawRoundRect(15, 54, 85, 69, 8, 8, paint);
        }
        if (code >= 45) {
            color(code >= 95 ? "#FFCC79" : "#70AFAB");
            paint.setStrokeWidth(4);
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (int x = 32; x <= 68; x += 18) {
                if (code == 45 || code == 48) canvas.drawLine(x - 5, 82, x + 6, 82, paint);
                else canvas.drawLine(x, 79, x - 5, 89, paint);
            }
        }
        canvas.restore();
    }
}
