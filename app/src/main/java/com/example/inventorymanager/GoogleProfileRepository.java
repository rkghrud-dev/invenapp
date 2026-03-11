package com.example.inventorymanager;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GoogleProfileRepository {
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    public String fetchEmail(String accessToken) {
        if (TextUtils.isEmpty(accessToken)) {
            return "";
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(USERINFO_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readStream(stream);

            if (responseCode < 200 || responseCode >= 300 || TextUtils.isEmpty(body)) {
                return "";
            }

            JSONObject json = new JSONObject(body);
            return json.optString("email", "").trim();
        } catch (IOException | JSONException ignored) {
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
