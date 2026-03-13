package com.example.inventorymanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GoogleOAuthRepository {
    private static final String PREFS_NAME = "inventory_manager_auth";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_TOKEN_TYPE = "token_type";

    private static final String ASSET_CLIENT_CONFIG = "google_oauth_client.json";
    private static final String ASSET_TOKEN_SEED = "google_token_seed.json";

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final long EXPIRY_SAFETY_WINDOW_MS = 60000L;

    private final Context appContext;
    private final SharedPreferences preferences;

    public GoogleOAuthRepository(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasStoredAuthorization() {
        if (!TextUtils.isEmpty(preferences.getString(KEY_REFRESH_TOKEN, ""))) {
            return true;
        }

        try {
            return !TextUtils.isEmpty(readAssetJson(ASSET_TOKEN_SEED).optString("refresh_token", "").trim());
        } catch (IOException ignored) {
            return false;
        }
    }

    public String getCachedAccessToken() {
        String accessToken = preferences.getString(KEY_ACCESS_TOKEN, "");
        long expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L);
        if (!TextUtils.isEmpty(accessToken) && expiresAt > System.currentTimeMillis() + EXPIRY_SAFETY_WINDOW_MS) {
            return accessToken;
        }
        return "";
    }

    public AuthSession ensureAccessToken() throws IOException {
        bootstrapFromAssetsIfNeeded();

        String cachedAccessToken = getCachedAccessToken();
        if (!TextUtils.isEmpty(cachedAccessToken)) {
            return new AuthSession(
                    cachedAccessToken,
                    preferences.getString(KEY_REFRESH_TOKEN, ""),
                    preferences.getLong(KEY_EXPIRES_AT, 0L)
            );
        }

        return refreshAccessToken();
    }

    public AuthSession refreshAccessToken() throws IOException {
        bootstrapFromAssetsIfNeeded();

        String refreshToken = preferences.getString(KEY_REFRESH_TOKEN, "").trim();
        if (TextUtils.isEmpty(refreshToken)) {
            throw new IOException("기존 승인 토큰을 찾지 못했습니다. PC 프로젝트 token 파일을 다시 가져와야 합니다.");
        }

        ClientConfig clientConfig = readClientConfig();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(clientConfig.tokenUri).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String requestBody = buildRefreshBody(clientConfig, refreshToken);
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readStream(stream);

            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException(mapTokenError(responseCode, body));
            }

            JSONObject response = new JSONObject(body);
            String accessToken = response.optString("access_token", "").trim();
            if (TextUtils.isEmpty(accessToken)) {
                throw new IOException("Google access token을 새로 만들지 못했습니다.");
            }

            String refreshedToken = response.optString("refresh_token", "").trim();
            if (!TextUtils.isEmpty(refreshedToken)) {
                refreshToken = refreshedToken;
            }

            long expiresInSeconds = Math.max(300L, response.optLong("expires_in", 3600L));
            long expiresAt = System.currentTimeMillis()
                    + (expiresInSeconds * 1000L)
                    - EXPIRY_SAFETY_WINDOW_MS;
            String tokenType = response.optString("token_type", "Bearer").trim();

            preferences.edit()
                    .putString(KEY_ACCESS_TOKEN, accessToken)
                    .putString(KEY_REFRESH_TOKEN, refreshToken)
                    .putLong(KEY_EXPIRES_AT, expiresAt)
                    .putString(KEY_TOKEN_TYPE, tokenType)
                    .apply();

            return new AuthSession(accessToken, refreshToken, expiresAt);
        } catch (JSONException exception) {
            throw new IOException("Google 토큰 응답을 해석하지 못했습니다.", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void clearAccessToken() {
        preferences.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_TOKEN_TYPE)
                .apply();
    }

    private void bootstrapFromAssetsIfNeeded() throws IOException {
        JSONObject seed = readAssetJson(ASSET_TOKEN_SEED);
        String seedRefreshToken = seed.optString("refresh_token", "").trim();
        String storedRefreshToken = preferences.getString(KEY_REFRESH_TOKEN, "").trim();

        if (TextUtils.isEmpty(seedRefreshToken)) {
            if (!TextUtils.isEmpty(storedRefreshToken)) {
                return;
            }
            throw new IOException("PC 앱의 저장된 Google refresh token을 찾지 못했습니다.");
        }

        if (seedRefreshToken.equals(storedRefreshToken)) {
            return;
        }

        preferences.edit()
                .putString(KEY_REFRESH_TOKEN, seedRefreshToken)
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_TOKEN_TYPE)
                .apply();
    }

    private ClientConfig readClientConfig() throws IOException {
        JSONObject seed = readAssetJson(ASSET_TOKEN_SEED);
        String seedClientId = seed.optString("client_id", "").trim();
        String seedClientSecret = seed.optString("client_secret", "").trim();
        String seedTokenUri = seed.optString("token_uri", "").trim();
        if (!TextUtils.isEmpty(seedClientId)
                && !TextUtils.isEmpty(seedClientSecret)
                && !TextUtils.isEmpty(seedTokenUri)) {
            return new ClientConfig(seedClientId, seedClientSecret, seedTokenUri);
        }

        JSONObject root = readAssetJson(ASSET_CLIENT_CONFIG);
        JSONObject installed = root.optJSONObject("installed");
        if (installed == null) {
            throw new IOException("앱에 포함된 Google OAuth 설정 형식이 올바르지 않습니다.");
        }

        String clientId = installed.optString("client_id", "").trim();
        String clientSecret = installed.optString("client_secret", "").trim();
        String tokenUri = installed.optString("token_uri", "https://oauth2.googleapis.com/token").trim();
        if (TextUtils.isEmpty(clientId) || TextUtils.isEmpty(clientSecret) || TextUtils.isEmpty(tokenUri)) {
            throw new IOException("앱에 포함된 Google OAuth 설정이 비어 있습니다.");
        }

        return new ClientConfig(clientId, clientSecret, tokenUri);
    }

    private JSONObject readAssetJson(String assetName) throws IOException {
        try (InputStream stream = appContext.getAssets().open(assetName)) {
            return new JSONObject(readStream(stream));
        } catch (JSONException exception) {
            throw new IOException("앱에 포함된 Google 설정 파일을 읽지 못했습니다.", exception);
        }
    }

    private String buildRefreshBody(ClientConfig clientConfig, String refreshToken) throws IOException {
        return "client_id=" + encode(clientConfig.clientId)
                + "&client_secret=" + encode(clientConfig.clientSecret)
                + "&refresh_token=" + encode(refreshToken)
                + "&grant_type=refresh_token";
    }

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
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

    private String mapTokenError(int responseCode, String body) {
        String detail = extractErrorMessage(body);
        if (responseCode == 400) {
            return "Google refresh token이 만료되었거나 앱 설정이 맞지 않습니다. " + detail;
        }
        if (responseCode == 401 || responseCode == 403) {
            return "Google 토큰 갱신 권한이 없습니다. " + detail;
        }
        return "Google 토큰 갱신에 실패했습니다. HTTP " + responseCode + ". " + detail;
    }

    private String extractErrorMessage(String body) {
        if (TextUtils.isEmpty(body)) {
            return "";
        }

        try {
            JSONObject root = new JSONObject(body);
            String error = root.optString("error", "").trim();
            String description = root.optString("error_description", "").trim();
            if (!TextUtils.isEmpty(description)) {
                return description;
            }
            if (!TextUtils.isEmpty(error)) {
                return error;
            }
        } catch (JSONException ignored) {
        }
        return body;
    }

    private static final class ClientConfig {
        private final String clientId;
        private final String clientSecret;
        private final String tokenUri;

        private ClientConfig(String clientId, String clientSecret, String tokenUri) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.tokenUri = tokenUri;
        }
    }

    public static final class AuthSession {
        private final String accessToken;
        private final String refreshToken;
        private final long expiresAtMillis;

        private AuthSession(String accessToken, String refreshToken, long expiresAtMillis) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtMillis = expiresAtMillis;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public long getExpiresAtMillis() {
            return expiresAtMillis;
        }
    }
}