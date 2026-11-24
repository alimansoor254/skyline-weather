package app.skyline.weather;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class WeatherApi {
    static final class Place {
        final String name, region;
        final double lat, lon;
        Place(String name, String region, double lat, double lon) {
            this.name = name;
            this.region = region;
            this.lat = lat;
            this.lon = lon;
        }
        String key() { return lat + "," + lon; }
        JSONObject json() throws Exception {
            return new JSONObject().put("name", name).put("region", region)
                .put("lat", lat).put("lon", lon);
        }
        static Place from(JSONObject value) throws Exception {
            return new Place(value.getString("name"), value.getString("region"),
                value.getDouble("lat"), value.getDouble("lon"));
        }
    }

    static JSONObject forecast(Place place) throws Exception {
        return read("https://api.open-meteo.com/v1/forecast?latitude=" + place.lat
            + "&longitude=" + place.lon + "&timezone=auto&forecast_days=7"
            + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,weather_code,wind_speed_10m,surface_pressure,cloud_cover"
            + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day,visibility,dew_point_2m"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max");
    }

    static List<Place> search(String term) throws Exception {
        JSONArray results = read("https://geocoding-api.open-meteo.com/v1/search?count=10&language=en&name="
            + URLEncoder.encode(term, "UTF-8")).optJSONArray("results");
        List<Place> places = new ArrayList<>();
        if (results == null) return places;
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.getJSONObject(i);
            String area = item.optString("admin1"), country = item.optString("country");
            String region = area.isEmpty() || area.equals(item.optString("name"))
                ? country : area + (country.isEmpty() ? "" : ", " + country);
            places.add(new Place(item.getString("name"), region,
                item.getDouble("latitude"), item.getDouble("longitude")));
        }
        return places;
    }

    private static JSONObject read(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(12000);
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("Weather service unavailable");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = input.read(buffer)) != -1) bytes.write(buffer, 0, length);
                return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    static String condition(int code) {
        if (code < 0) return "Unavailable";
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77 || code == 85 || code == 86) return "Snow";
        if (code <= 82) return "Rain showers";
        return "Thunderstorms";
    }
}

