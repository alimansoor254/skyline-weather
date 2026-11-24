package app.skyline.weather;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int INK = 0xff17362e, MUTED = 0xff546b61, PAPER = 0xfff5f5ed;
    private static final int WHITE = 0xffffffff, LIGHT = 0xffc5e3d3, GOLD = 0xffffcf81;
    private static final WeatherApi.Place DEFAULT = new WeatherApi.Place("Paris", "France", 48.8566, 2.3522);
    private final ExecutorService network = Executors.newFixedThreadPool(3);
    private final List<WeatherApi.Place> saved = new ArrayList<>();
    private SharedPreferences prefs;
    private WeatherApi.Place place = DEFAULT;
    private JSONObject forecast;
    private LinearLayout root, content;
    private String tab = "Today", status = "";
    private boolean fahrenheit, loading, onboarding;
    private int forecastRequest, searchRequest;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("skyline", MODE_PRIVATE);
        fahrenheit = prefs.getBoolean("fahrenheit", false);
        try {
            place = WeatherApi.Place.from(new JSONObject(prefs.getString("place", DEFAULT.json().toString())));
            JSONArray rows = new JSONArray(prefs.getString("saved", "[]"));
            for (int i = 0; i < rows.length(); i++) saved.add(WeatherApi.Place.from(rows.getJSONObject(i)));
        } catch (Exception ignored) {}
        onboarding = !prefs.getBoolean("welcomed", false);
        if (onboarding) welcome();
        else load();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBaselineAligned(false);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("tnum");
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }

    private GradientDrawable fill(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }

    private void pad(View view, int x, int y) {
        view.setPadding(dp(x), dp(y), dp(x), dp(y));
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new Space(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private TextView action(String label, int background, int color, Runnable callback) {
        TextView view = text(label, 14, color, true);
        view.setGravity(Gravity.CENTER);
        pad(view, 12, 8);
        view.setMinimumHeight(dp(48));
        view.setBackground(fill(background, 16));
        view.setOnClickListener(v -> callback.run());
        view.setFocusable(true);
        return view;
    }

    private void base(boolean scroll) {
        root = column();
        root.setBackgroundColor(PAPER);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if (controller != null) controller.setSystemBarsAppearance(light, light);
        } else {
            getWindow().setStatusBarColor(PAPER);
            getWindow().setNavigationBarColor(PAPER);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        content = column();
        pad(content, 20, 14);
        if (scroll) {
            ScrollView viewport = new ScrollView(this);
            viewport.setFillViewport(true);
            viewport.addView(content);
            root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        } else root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.requestApplyInsets();
    }

    private void welcome() {
        base(true);
        content.addView(text("SKYLINE WEATHER", 14, MUTED, true));
        gap(content, 22);
        LinearLayout panel = column();
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setBackground(fill(INK, 32));
        pad(panel, 24, 28);
        panel.addView(new WeatherIcon(this, 2, true), new LinearLayout.LayoutParams(dp(160), dp(160)));
        TextView title = text("Weather for\nyour day", 41, WHITE, true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        gap(panel, 12);
        TextView subtitle = text("Check the forecast where you are and keep your favorite cities close.", 16, LIGHT, false);
        subtitle.setGravity(Gravity.CENTER);
        panel.addView(subtitle);
        content.addView(panel);
        gap(content, 20);
        content.addView(action("Continue", INK, WHITE, this::finishWelcome));
        gap(content, 8);
        content.addView(action("Set up a profile", 0xffe6eee4, INK, () -> profile(true)));
    }

    private void finishWelcome() {
        onboarding = false;
        prefs.edit().putBoolean("welcomed", true).apply();
        load();
    }

    private void profile(boolean firstRun) {
        EditText field = new EditText(this);
        field.setHint("Your name");
        field.setSingleLine();
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        field.setText(prefs.getString("profile", ""));
        LinearLayout body = column();
        pad(body, 24, 8);
        body.addView(text("Your profile is saved on this device.", 15, MUTED, false));
        body.addView(field);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Local profile").setView(body)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String name = field.getText().toString().trim();
            if (name.isEmpty()) {
                field.setError("Enter a name");
                return;
            }
            prefs.edit().putString("profile", name).apply();
            dialog.dismiss();
            if (firstRun) finishWelcome();
            else render();
        }));
        dialog.show();
    }

    private void render() {
        if (onboarding || isFinishing() || isDestroyed()) return;
        base(true);
        LinearLayout header = row();
        LinearLayout title = column();
        title.addView(text("SKYLINE WEATHER", 12, MUTED, true));
        title.addView(text(tab.equals("Today") ? place.name : tab, 30, INK, true));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        String shortcut = tab.equals("Places") ? "＋" : "⌕";
        TextView find = action(shortcut, 0xffe6eee4, INK, this::search);
        find.setContentDescription("Search for a city");
        header.addView(find, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(header);
        gap(content, 18);
        switch (tab) {
            case "Today": today(); break;
            case "Forecast": forecastPage(); break;
            case "Places": places(); break;
            case "Map": map(); break;
            default: settings();
        }
        LinearLayout nav = row();
        pad(nav, 8, 6);
        nav.setBackgroundColor(WHITE);
        for (String item : new String[]{"Today", "Forecast", "Places", "Map", "Settings"}) {
            TextView button = action(item, item.equals(tab) ? 0xffdcebe0 : WHITE, INK, () -> {
                tab = item;
                render();
            });
            button.setSelected(item.equals(tab));
            button.setTextSize(12);
            button.setPadding(dp(3), 0, dp(3), 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
            params.setMargins(dp(2), 0, dp(2), 0);
            nav.addView(button, params);
        }
        root.addView(nav);
    }

    private void load() {
        int token = ++forecastRequest;
        WeatherApi.Place requested = place;
        forecast = null;
        try {
            String value = prefs.getString("weather:" + requested.key(), "");
            if (!value.isEmpty()) forecast = new JSONObject(value);
        } catch (Exception ignored) {}
        loading = true;
        status = "";
        render();
        network.execute(() -> {
            try {
                JSONObject data = WeatherApi.forecast(requested);
                data.getJSONObject("current");
                data.getJSONObject("daily");
                data.getJSONObject("hourly");
                runOnUiThread(() -> {
                    if (token != forecastRequest || isDestroyed()) return;
                    forecast = data;
                    loading = false;
                    prefs.edit().putString("weather:" + requested.key(), data.toString()).apply();
                    if (tab.equals("Today") || tab.equals("Forecast")) render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (token != forecastRequest || isDestroyed()) return;
                    loading = false;
                    status = forecast == null ? "Weather is unavailable. Check your connection and try again."
                        : "Offline · showing the last forecast for this place.";
                    if (tab.equals("Today") || tab.equals("Forecast")) render();
                });
            }
        });
    }

    private void choose(WeatherApi.Place selected) {
        place = selected;
        try { prefs.edit().putString("place", place.json().toString()).apply(); }
        catch (Exception ignored) {}
        tab = "Today";
        load();
    }

    private JSONObject section(String name) {
        JSONObject value = forecast == null ? null : forecast.optJSONObject(name);
        return value == null ? new JSONObject() : value;
    }

    private Object at(JSONObject data, String key, int index) {
        JSONArray values = data.optJSONArray(key);
        return values == null || index < 0 ? null : values.opt(index);
    }

    private double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.NaN;
    }

    private String rounded(Object value, String suffix) {
        double n = number(value);
        return Double.isNaN(n) ? "—" : Math.round(n) + suffix;
    }

    private int weatherCode(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private String temp(Object value) {
        double n = number(value);
        if (Double.isNaN(n)) return "—";
        return Math.round(fahrenheit ? n * 1.8 + 32 : n) + "°";
    }

    private String clock(Object value) {
        if (!(value instanceof String)) return "—";
        try { return LocalDateTime.parse((String) value).format(DateTimeFormatter.ofPattern("HH:mm")); }
        catch (Exception ignored) { return "—"; }
    }

    private String day(Object value, int index) {
        if (index == 0) return "Today";
        if (!(value instanceof String)) return "—";
        try { return LocalDate.parse((String) value).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)); }
        catch (Exception ignored) { return "—"; }
    }

    private LinearLayout card(int background) {
        LinearLayout card = column();
        pad(card, 16, 14);
        card.setBackground(fill(background, 22));
        return card;
    }

    private void message() {
        if (loading || !status.isEmpty()) {
            content.addView(text(loading ? "Updating forecast…" : status, 14, MUTED, false));
            gap(content, 8);
            if (!loading) {
                content.addView(action("Retry", 0xffe6eee4, INK, this::load));
                gap(content, 10);
            }
        }
    }

    private void heading(String label) {
        gap(content, 21);
        content.addView(text(label, 21, INK, true));
        gap(content, 9);
    }

    private void today() {
        JSONObject current = section("current"), daily = section("daily"), hourly = section("hourly");
        int code = current.optInt("weather_code", -1);
        boolean isDay = current.optInt("is_day", 1) == 1;
        int sky = !isDay ? 0xff252e4b : code >= 95 ? 0xff3c3855 : code >= 45 ? 0xff294756 : INK;
        LinearLayout hero = card(sky);
        LinearLayout top = row();
        top.addView(text(place.region.isEmpty() ? "CURRENT WEATHER" : place.region.toUpperCase(Locale.ROOT),
            13, LIGHT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView refresh = action("↻", 0xff2e5447, WHITE, this::load);
        refresh.setContentDescription("Refresh forecast");
        top.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
        hero.addView(top);
        LinearLayout main = row();
        LinearLayout temperature = column();
        temperature.addView(text(temp(current.opt("temperature_2m")), 72, WHITE, true));
        temperature.addView(text(forecast == null ? "Current conditions" : WeatherApi.condition(code), 19, WHITE, true));
        main.addView(temperature, new LinearLayout.LayoutParams(0, -2, 1));
        main.addView(new WeatherIcon(this, code, isDay),
            new LinearLayout.LayoutParams(dp(125), dp(125)));
        hero.addView(main);
        gap(hero, 12);
        hero.addView(text("High " + temp(at(daily, "temperature_2m_max", 0)) + "  ·  Low "
            + temp(at(daily, "temperature_2m_min", 0)), 15, GOLD, true));
        gap(hero, 6);
        hero.addView(text("Feels like " + temp(current.opt("apparent_temperature")) + "  ·  °"
            + (fahrenheit ? "F" : "C"), 14, LIGHT, false));
        if (current.has("time")) {
            gap(hero, 8);
            hero.addView(text("Conditions as of " + clock(current.opt("time")) + " local time", 12, LIGHT, false));
        }
        content.addView(hero);
        gap(content, 10);
        message();
        if (forecast == null) return;
        heading("Next 24 hours");
        hourly(hourly, current);
        heading("Weather details");
        metricRow("Wind", fahrenheit ? rounded(number(current.opt("wind_speed_10m")) * 0.621371, " mph")
            : rounded(current.opt("wind_speed_10m"), " km/h"), "Humidity", rounded(current.opt("relative_humidity_2m"), "%"));
        metricRow("UV index", rounded(at(daily, "uv_index_max", 0), ""), "Pressure",
            rounded(current.opt("surface_pressure"), " hPa"));
        int hour = firstHour(hourly, current);
        metricRow("Rain chance", rounded(at(hourly, "precipitation_probability", hour), "%"),
            "Visibility", fahrenheit ? rounded(number(at(hourly, "visibility", hour)) / 1609.344, " mi")
                : rounded(number(at(hourly, "visibility", hour)) / 1000, " km"));
        metricRow("Sunrise", clock(at(daily, "sunrise", 0)), "Sunset", clock(at(daily, "sunset", 0)));
        metricRow("Cloud cover", rounded(current.opt("cloud_cover"), "%"), "Dew point", temp(at(hourly, "dew_point_2m", hour)));
        heading("This week");
        daily(daily, 3);
        content.addView(action("View 7-day forecast", 0xffe6eee4, INK, () -> { tab = "Forecast"; render(); }));
        footer();
    }

    private void metricRow(String left, String leftValue, String right, String rightValue) {
        LinearLayout line = row();
        for (String[] values : new String[][]{{left, leftValue}, {right, rightValue}}) {
            LinearLayout tile = card(WHITE);
            tile.addView(text(values[0], 14, MUTED, true));
            gap(tile, 7);
            tile.addView(text(values[1], 23, INK, true));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
            if (line.getChildCount() > 0) params.leftMargin = dp(8);
            line.addView(tile, params);
        }
        content.addView(line);
        gap(content, 8);
    }

    private int firstHour(JSONObject hourly, JSONObject current) {
        JSONArray times = hourly.optJSONArray("time");
        String now = current.optString("time");
        if (times == null || now.length() < 13) return 0;
        String nowHour = now.substring(0, 13);
        for (int i = 0; i < times.length(); i++) {
            if (times.optString(i).compareTo(nowHour) >= 0) return i;
        }
        return 0;
    }

    private void hourly(JSONObject hourly, JSONObject current) {
        JSONArray times = hourly.optJSONArray("time");
        if (times == null) return;
        int first = firstHour(hourly, current);
        HorizontalScrollView rail = new HorizontalScrollView(this);
        rail.setHorizontalScrollBarEnabled(false);
        LinearLayout items = row();
        for (int index = first; index < Math.min(first + 24, times.length()); index++) {
            LinearLayout tile = card(index == first ? 0xffdaeddf : WHITE);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.addView(text(index == first ? "Now" : clock(times.opt(index)), 13, MUTED, true));
            int code = weatherCode(at(hourly, "weather_code", index));
            tile.addView(new WeatherIcon(this, code, number(at(hourly, "is_day", index)) == 1),
                new LinearLayout.LayoutParams(dp(45), dp(45)));
            tile.addView(text(temp(at(hourly, "temperature_2m", index)), 20, INK, true));
            tile.addView(text(rounded(at(hourly, "precipitation_probability", index), "% rain"), 12, MUTED, false));
            tile.setContentDescription((index == first ? "Now" : clock(times.opt(index))) + ", "
                + WeatherApi.condition(code) + ", " + temp(at(hourly, "temperature_2m", index))
                + ", " + rounded(at(hourly, "precipitation_probability", index), "% chance of rain"));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(90), -2);
            params.rightMargin = dp(7);
            items.addView(tile, params);
        }
        rail.addView(items);
        content.addView(rail);
    }

    private void forecastPage() {
        content.addView(text(place.name + "  ·  7-day outlook", 16, MUTED, true));
        gap(content, 12);
        message();
        if (forecast == null) return;
        daily(section("daily"), 7);
        heading("Next 24 hours");
        hourly(section("hourly"), section("current"));
        footer();
    }

    private void daily(JSONObject daily, int count) {
        JSONArray times = daily.optJSONArray("time");
        if (times == null) return;
        for (int i = 0; i < Math.min(times.length(), count); i++) {
            final int index = i;
            LinearLayout line = row();
            pad(line, 12, 8);
            line.setBackground(fill(WHITE, 16));
            String date = day(times.opt(i), i);
            int code = weatherCode(at(daily, "weather_code", i));
            LinearLayout labels = column();
            labels.addView(text(date, 16, INK, true));
            labels.addView(text(WeatherApi.condition(code), 13, MUTED, false));
            line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(new WeatherIcon(this, code, true), new LinearLayout.LayoutParams(dp(45), dp(45)));
            TextView values = text(temp(at(daily, "temperature_2m_min", i)) + "  /  "
                + temp(at(daily, "temperature_2m_max", i)), 16, INK, true);
            values.setGravity(Gravity.END);
            line.addView(values, new LinearLayout.LayoutParams(dp(98), -2));
            line.setFocusable(true);
            line.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(date + " in " + place.name)
                .setMessage(WeatherApi.condition(code) + "\n\nRain chance  "
                    + rounded(at(daily, "precipitation_probability_max", index), "%")
                    + "\nUV index  " + rounded(at(daily, "uv_index_max", index), "")
                    + "\nSunrise  " + clock(at(daily, "sunrise", index))
                    + "\nSunset  " + clock(at(daily, "sunset", index)))
                .setPositiveButton("Close", null).show());
            content.addView(line);
            gap(content, 6);
        }
    }

    private void places() {
        content.addView(action("Use my location", INK, WHITE, this::locate));
        gap(content, 8);
        content.addView(action("Search for a city", 0xffe6eee4, INK, this::search));
        heading("Saved places");
        if (saved.isEmpty()) content.addView(text("Save a city from the search results or settings.", 15, MUTED, false));
        for (WeatherApi.Place item : saved) {
            LinearLayout line = row();
            line.setBackground(fill(WHITE, 16));
            TextView name = action(item.name + (item.region.isEmpty() ? "" : "\n" + item.region),
                WHITE, INK, () -> choose(item));
            name.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView remove = action("×", WHITE, MUTED, () -> {
                saved.remove(item);
                persistPlaces();
                render();
            });
            remove.setContentDescription("Remove " + item.name);
            line.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
            content.addView(line);
            gap(content, 7);
        }
    }

    private void savePlace(WeatherApi.Place item) {
        for (WeatherApi.Place existing : saved) {
            if (existing.key().equals(item.key())) return;
        }
        saved.add(item);
        persistPlaces();
    }

    private void persistPlaces() {
        JSONArray output = new JSONArray();
        for (WeatherApi.Place item : saved) {
            try { output.put(item.json()); }
            catch (Exception ignored) {}
        }
        prefs.edit().putString("saved", output.toString()).apply();
    }

    private void search() {
        LinearLayout body = column();
        pad(body, 20, 10);
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setHint("City or postal code");
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        body.addView(field);
        LinearLayout results = column();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(results);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, dp(265)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Find a city").setView(body)
            .setNegativeButton("Close", null).create();
        Runnable submit = () -> {
            String term = field.getText().toString().trim();
            if (term.length() < 2) {
                field.setError("Enter at least two characters");
                return;
            }
            int token = ++searchRequest;
            getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(field.getWindowToken(), 0);
            results.removeAllViews();
            results.addView(text("Searching…", 15, MUTED, false));
            network.execute(() -> {
                try {
                    List<WeatherApi.Place> found = WeatherApi.search(term);
                    runOnUiThread(() -> {
                        if (token != searchRequest || !dialog.isShowing() || isDestroyed()) return;
                        results.removeAllViews();
                        if (found.isEmpty()) results.addView(text("No cities found.", 15, MUTED, false));
                        for (WeatherApi.Place item : found) {
                            TextView option = action(item.name + (item.region.isEmpty() ? "" : "\n" + item.region),
                                WHITE, INK, () -> {
                                    dialog.dismiss();
                                    new AlertDialog.Builder(this).setTitle(item.name)
                                        .setMessage(item.region).setNegativeButton("View forecast", (d, which) -> choose(item))
                                        .setPositiveButton("Save and view", (d, which) -> {
                                            savePlace(item);
                                            choose(item);
                                        }).show();
                                });
                            option.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                            results.addView(option);
                        }
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (token != searchRequest || !dialog.isShowing() || isDestroyed()) return;
                        results.removeAllViews();
                        results.addView(text("City search is unavailable. Try again.", 15, MUTED, false));
                    });
                }
            });
        };
        body.addView(action("Search", INK, WHITE, submit), 1);
        field.setOnEditorActionListener((v, id, event) -> {
            if (id != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            submit.run();
            return true;
        });
        dialog.show();
    }

    private void locate() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
            return;
        }
        LocationManager manager = getSystemService(LocationManager.class);
        String provider = LocationManager.NETWORK_PROVIDER;
        try {
            if (manager == null || !manager.isProviderEnabled(provider)) {
                notifyLocationUnavailable();
                return;
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                manager.getCurrentLocation(provider, null, getMainExecutor(), this::showLocation);
            } else {
                showLocation(manager.getLastKnownLocation(provider));
            }
        } catch (RuntimeException error) {
            notifyLocationUnavailable();
        }
    }

    private void showLocation(Location point) {
        if (isDestroyed()) return;
        if (point == null) {
            notifyLocationUnavailable();
            return;
        }
        double lat = Math.round(point.getLatitude() * 100.0) / 100.0;
        double lon = Math.round(point.getLongitude() * 100.0) / 100.0;
        choose(new WeatherApi.Place("My location", "Approximate location", lat, lon));
    }

    private void notifyLocationUnavailable() {
        Toast.makeText(this, "Location unavailable. You can search for a city instead.", Toast.LENGTH_LONG).show();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 10 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) locate();
        else if (request == 10) notifyLocationUnavailable();
    }

    private void settings() {
        String name = prefs.getString("profile", "");
        content.addView(action(name.isEmpty() ? "Set up a local profile" : "Edit profile · " + name,
            WHITE, INK, () -> profile(false)));
        heading("Temperature unit");
        LinearLayout line = row();
        for (String unit : new String[]{"Celsius (°C)", "Fahrenheit (°F)"}) {
            boolean useF = unit.startsWith("F");
            TextView toggle = action(unit, fahrenheit == useF ? INK : WHITE, fahrenheit == useF ? WHITE : INK, () -> {
                fahrenheit = useF;
                prefs.edit().putBoolean("fahrenheit", fahrenheit).apply();
                render();
            });
            toggle.setSelected(fahrenheit == useF);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
            p.rightMargin = dp(4);
            line.addView(toggle, p);
        }
        content.addView(line);
        heading("Current place");
        content.addView(action("Save " + place.name, WHITE, INK, () -> {
            savePlace(place);
            Toast.makeText(this, "Saved to Places", Toast.LENGTH_SHORT).show();
        }));
        footer();
    }

    private void map() {
        content.addView(text(place.name + "  ·  Drag to explore", 15, MUTED, true));
        gap(content, 10);
        CityMap view = new CityMap(this, place);
        content.addView(view, new LinearLayout.LayoutParams(-1, dp(380)));
        gap(content, 8);
        LinearLayout controls = row();
        for (String label : new String[]{"Zoom out", "Recenter", "Zoom in"}) {
            TextView button = action(label, WHITE, INK, () -> {
                if (label.equals("Recenter")) view.recenter();
                else view.zoom(label.equals("Zoom in") ? 1 : -1);
            });
            controls.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        content.addView(controls);
        gap(content, 8);
        content.addView(action("© OpenStreetMap contributors", PAPER, MUTED,
            () -> openLink("https://www.openstreetmap.org/copyright")));
    }

    private void openLink(String url) {
        try {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException ignored) {}
    }

    private void footer() {
        gap(content, 12);
        content.addView(action("Weather data by Open-Meteo", PAPER, MUTED,
            () -> openLink("https://open-meteo.com/")));
    }

    @Override protected void onDestroy() {
        forecastRequest++;
        searchRequest++;
        network.shutdownNow();
        super.onDestroy();
    }
}



