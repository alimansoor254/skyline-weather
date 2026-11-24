package app.skyline.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.http.HttpResponseCache;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CityMap extends View {
    private static final ExecutorService TILES = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(48);
    private final Set<String> pending = new HashSet<>(), failed = new HashSet<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final double lat, lon;
    private final float tileSize;
    private int level = 5;
    private double centerX, centerY;
    private float previousX, previousY;
    private boolean dragging;

    CityMap(Context context, WeatherApi.Place place) {
        super(context);
        lat = Math.max(-85.0511, Math.min(85.0511, place.lat));
        lon = place.lon;
        tileSize = 210 * getResources().getDisplayMetrics().density;
        setContentDescription("Map centered on " + place.name);
        setBackgroundColor(0xffdeebe6);
        try {
            if (HttpResponseCache.getInstalled() == null)
                HttpResponseCache.install(new File(context.getCacheDir(), "map-http"), 16L * 1024L * 1024L);
        } catch (Exception ignored) {}
        recenter();
    }

    private double pinX() { return (lon + 180) / 360 * (1 << level); }

    private double pinY() {
        double sine = Math.sin(Math.toRadians(lat));
        return (0.5 - Math.log((1 + sine) / (1 - sine)) / (4 * Math.PI)) * (1 << level);
    }

    void recenter() {
        centerX = pinX();
        centerY = pinY();
        failed.clear();
        invalidate();
    }

    void zoom(int delta) {
        int next = Math.max(2, Math.min(14, level + delta));
        double scale = Math.pow(2, next - level);
        centerX *= scale;
        centerY *= scale;
        level = next;
        failed.clear();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        double left = centerX - getWidth() / (2 * tileSize);
        double top = centerY - getHeight() / (2 * tileSize);
        int world = 1 << level;
        int minX = (int) Math.floor(left), maxX = (int) Math.ceil(left + getWidth() / tileSize);
        int minY = Math.max(0, (int) Math.floor(top));
        int maxY = Math.min(world, (int) Math.ceil(top + getHeight() / tileSize));
        int visible = 0;
        boolean hasError = false;
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                String key = level + "/" + Math.floorMod(x, world) + "/" + y;
                Bitmap image = CACHE.get(key);
                if (image == null) {
                    hasError |= failed.contains(key);
                    if (!dragging) request(key);
                    continue;
                }
                visible++;
                float px = (float) ((x - left) * tileSize), py = (float) ((y - top) * tileSize);
                canvas.drawBitmap(image, null, new RectF(px, py, px + tileSize + 1, py + tileSize + 1), paint);
            }
        }
        double pin = pinX() + Math.rint((centerX - pinX()) / world) * world;
        float px = (float) ((pin - left) * tileSize), py = (float) ((pinY() - top) * tileSize);
        paint.setColor(0xff17362e);
        canvas.drawCircle(px, py, tileSize / 26, paint);
        paint.setColor(0xffffcf81);
        canvas.drawCircle(px, py, tileSize / 48, paint);
        if (visible == 0) {
            paint.setColor(0xff17362e);
            paint.setTextSize(tileSize / 12);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(hasError ? "Map unavailable · try Recenter" : "Loading map…",
                getWidth() / 2f, getHeight() * 0.82f, paint);
        }
    }

    private void request(String key) {
        if (failed.contains(key) || !pending.add(key)) return;
        TILES.execute(() -> {
            HttpURLConnection conn = null;
            Bitmap tile = null;
            try {
                conn = (HttpURLConnection) new URL("https://tile.openstreetmap.org/" + key + ".png").openConnection();
                conn.setRequestProperty("User-Agent", "SkylineWeather/1.0 (Android; app.skyline.weather)");
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(7000);
                try (InputStream stream = conn.getInputStream()) {
                    tile = BitmapFactory.decodeStream(stream);
                    if (tile != null) CACHE.put(key, tile);
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
            boolean ok = tile != null;
            post(() -> {
                pending.remove(key);
                if (!ok) failed.add(key);
                invalidate();
            });
        });
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                previousX = event.getX();
                previousY = event.getY();
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                int world = 1 << level;
                centerX = (centerX + (previousX - event.getX()) / tileSize) % world;
                centerY = Math.max(0, Math.min(world, centerY + (previousY - event.getY()) / tileSize));
                previousX = event.getX();
                previousY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
