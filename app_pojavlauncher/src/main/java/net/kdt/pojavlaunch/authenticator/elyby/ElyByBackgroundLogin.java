package net.kdt.pojavlaunch.authenticator.elyby;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.util.Log;

import androidx.annotation.Nullable;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.authenticator.listener.ErrorListener;
import net.kdt.pojavlaunch.authenticator.listener.ProgressListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Handles Ely.by OAuth sessions and refreshes without collecting Ely.by passwords. */
public final class ElyByBackgroundLogin {
    private static final String WORKER_URL = "https://auth.bchgabuttt.my.id";
    private static final String LEGACY_AUTH_URL = "https://authserver.ely.by";
    private static final int TIMEOUT_MS = 20000;

    private final MinecraftAccount existingAccount;
    private final String[] oauthData;
    private final boolean refreshMode;

    /** OAuth payload: access token, refresh token, username, UUID, expires_in seconds. */
    public ElyByBackgroundLogin(String[] oauthData) {
        if (oauthData == null || oauthData.length < 5) {
            throw new IllegalArgumentException("Data OAuth Ely.by tidak lengkap.");
        }
        this.oauthData = oauthData.clone();
        this.existingAccount = null;
        this.refreshMode = false;
    }

    /** Refresh an already-saved Ely.by account. */
    public ElyByBackgroundLogin(MinecraftAccount account) {
        if (account == null || !account.isElyBy) throw new IllegalArgumentException("Akun Ely.by tidak valid.");
        this.existingAccount = account;
        this.oauthData = null;
        this.refreshMode = true;
    }

    public void performLogin(@Nullable ProgressListener progressListener,
                             @Nullable DoneListener doneListener,
                             @Nullable ErrorListener errorListener) {
        sExecutorService.execute(() -> {
            try {
                notifyProgress(progressListener, 1);
                JSONObject response = refreshMode ? refresh() : createOAuthResponse(oauthData);
                notifyProgress(progressListener, 4);
                MinecraftAccount account = buildAccount(response);
                account.save();
                notifyProgress(progressListener, 5);
                if (doneListener != null) Tools.runOnUiThread(() -> doneListener.onLoginDone(account));
            } catch (Exception error) {
                Log.e("PerLauncherElyBy", "Ely.by OAuth authentication failed", error);
                if (errorListener != null) Tools.runOnUiThread(() -> errorListener.onLoginError(error));
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE_MICROSOFT);
            }
        });
    }

    private JSONObject createOAuthResponse(String[] values) throws JSONException, IOException {
        JSONObject response = new JSONObject();
        response.put("access_token", values[0]);
        response.put("refresh_token", values[1]);
        response.put("expires_in", parseExpires(values[4]));
        JSONObject user = new JSONObject();
        user.put("username", values[2]);
        user.put("uuid", values[3]);
        response.put("user", user);
        return response;
    }

    private JSONObject refresh() throws IOException, JSONException {
        String refreshToken = existingAccount.elyByRefreshToken;
        if (refreshToken == null || refreshToken.trim().isEmpty() || "0".equals(refreshToken)) {
            // Existing installations may still contain a pre-OAuth Yggdrasil token.
            return refreshLegacyToken();
        }
        JSONObject request = new JSONObject();
        request.put("refresh_token", refreshToken);
        JSONObject response = postJson(WORKER_URL + "/refresh", request);
        if (!response.has("refresh_token")) response.put("refresh_token", refreshToken);
        return response;
    }

    private JSONObject refreshLegacyToken() throws IOException, JSONException {
        JSONObject request = new JSONObject();
        request.put("accessToken", existingAccount.accessToken);
        request.put("clientToken", existingAccount.clientToken);
        request.put("requestUser", true);
        JSONObject legacy = postJson(LEGACY_AUTH_URL + "/auth/refresh", request);
        JSONObject selected = legacy.getJSONObject("selectedProfile");
        JSONObject user = new JSONObject();
        user.put("username", selected.getString("name"));
        user.put("uuid", normalizeUuid(selected.getString("id")));
        JSONObject normalized = new JSONObject();
        normalized.put("access_token", legacy.getString("accessToken"));
        normalized.put("refresh_token", "");
        normalized.put("expires_in", getJwtExpiresIn(legacy.getString("accessToken")));
        normalized.put("user", user);
        normalized.put("legacy_client_token", legacy.optString("clientToken", existingAccount.clientToken));
        return normalized;
    }

    private MinecraftAccount buildAccount(JSONObject response) throws JSONException, IOException {
        String accessToken = response.optString("access_token", "");
        JSONObject user = response.optJSONObject("user");
        if (accessToken.isEmpty() || user == null) throw new IOException("Respons Ely.by tidak berisi token atau profil.");
        String username = user.optString("username", "");
        String uuid = normalizeUuid(user.optString("uuid", ""));
        if (!username.matches("[A-Za-z0-9_]{3,16}")) throw new IOException("Username Ely.by tidak valid.");
        if (!uuid.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IOException("UUID profil Ely.by tidak valid.");
        }

        MinecraftAccount account = MinecraftAccount.load(username);
        if (account == null) account = new MinecraftAccount();
        if (account.isMicrosoft) {
            throw new IOException("Nama akun ini sudah tersimpan sebagai akun Microsoft. Ganti nama profil atau hapus duplikatnya.");
        }
        account.username = username;
        account.profileId = uuid;
        account.accessToken = accessToken;
        account.clientToken = response.optString("legacy_client_token",
                existingAccount != null ? existingAccount.clientToken : UUID.randomUUID().toString());
        account.isMicrosoft = false;
        account.isElyBy = true;
        account.msaRefreshToken = "0";
        account.xuid = null;
        account.elyByRefreshToken = response.optString("refresh_token", "");
        long expiresIn = response.optLong("expires_in", 86400L);
        account.expiresAt = System.currentTimeMillis() + Math.max(60L, expiresIn) * 1000L - 30000L;
        return account;
    }

    private static long parseExpires(String value) throws IOException {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 60L || parsed > 31536000L) throw new NumberFormatException("range");
            return parsed;
        } catch (NumberFormatException error) {
            throw new IOException("Masa berlaku token Ely.by tidak valid.", error);
        }
    }

    private static String normalizeUuid(String uuid) {
        String raw = uuid == null ? "" : uuid.replace("-", "");
        if (!raw.matches("(?i)[0-9a-f]{32}")) return uuid == null ? "" : uuid;
        return raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16)
                + "-" + raw.substring(16, 20) + "-" + raw.substring(20);
    }

    private static long getJwtExpiresIn(String token) {
        // Legacy Ely.by tokens are refreshed through their own refresh endpoint.
        return 3600L;
    }

    private static JSONObject postJson(String target, JSONObject payload) throws IOException, JSONException {
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "PerLauncher-Android");
        try {
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String text = stream == null ? "" : readFully(stream);
            if (status < 200 || status >= 300) {
                String message = "HTTP " + status;
                try {
                    JSONObject error = new JSONObject(text);
                    message = error.optString("error", error.optString("error_description", error.optString("message", message)));
                } catch (JSONException ignored) {
                    if (!text.isEmpty()) message = text;
                }
                throw new IOException("Ely.by: " + message);
            }
            return new JSONObject(text);
        } finally {
            connection.disconnect();
        }
    }

    private static String readFully(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void notifyProgress(@Nullable ProgressListener listener, int step) {
        if (listener != null) Tools.runOnUiThread(() -> listener.onLoginProgress(step));
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE_MICROSOFT, step * 20);
    }
}
