package net.kdt.pojavlaunch.authenticator.elyby;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.util.Base64;
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

/** Ely.by's documented Yggdrasil-compatible login and token refresh flow. */
public final class ElyByBackgroundLogin {
    private static final String AUTH_URL = "https://authserver.ely.by";
    private static final int TIMEOUT_MS = 25000;

    private final boolean refreshMode;
    private final String username;
    private final String password;
    private final String totp;
    private final MinecraftAccount existingAccount;

    public ElyByBackgroundLogin(String username, String password, String totp) {
        this.refreshMode = false;
        this.username = username;
        this.password = password;
        this.totp = totp == null ? "" : totp.trim();
        this.existingAccount = null;
    }

    public ElyByBackgroundLogin(MinecraftAccount account) {
        this.refreshMode = true;
        this.existingAccount = account;
        this.username = null;
        this.password = null;
        this.totp = null;
    }

    public void performLogin(@Nullable ProgressListener progressListener,
                             @Nullable DoneListener doneListener,
                             @Nullable ErrorListener errorListener) {
        sExecutorService.execute(() -> {
            try {
                notifyProgress(progressListener, 1);
                JSONObject response = refreshMode ? refresh() : authenticate();
                notifyProgress(progressListener, 4);
                MinecraftAccount account = buildAccount(response);
                account.save();
                notifyProgress(progressListener, 5);
                if (doneListener != null) {
                    Tools.runOnUiThread(() -> doneListener.onLoginDone(account));
                }
            } catch (Exception error) {
                Log.e("PerLauncherElyBy", "Ely.by authentication failed", error);
                if (errorListener != null) {
                    Tools.runOnUiThread(() -> errorListener.onLoginError(error));
                }
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE_MICROSOFT);
            }
        });
    }

    private JSONObject authenticate() throws IOException, JSONException {
        JSONObject request = new JSONObject();
        request.put("username", username);
        request.put("password", totp.isEmpty() ? password : password + ":" + totp);
        request.put("clientToken", UUID.randomUUID().toString());
        request.put("requestUser", true);
        JSONObject agent = new JSONObject();
        agent.put("name", "Minecraft");
        agent.put("version", 1);
        request.put("agent", agent);
        return postJson("/auth/authenticate", request);
    }

    private JSONObject refresh() throws IOException, JSONException {
        JSONObject request = new JSONObject();
        request.put("accessToken", existingAccount.accessToken);
        request.put("clientToken", existingAccount.clientToken);
        request.put("requestUser", true);
        return postJson("/auth/refresh", request);
    }

    private MinecraftAccount buildAccount(JSONObject response) throws JSONException, IOException {
        JSONObject selected = response.getJSONObject("selectedProfile");
        String name = selected.getString("name");
        MinecraftAccount account = MinecraftAccount.load(name);
        if (account == null) account = new MinecraftAccount();
        if (account.isMicrosoft) {
            throw new IOException("Akun dengan nama yang sama sudah tersimpan sebagai akun Microsoft. Hapus akun duplikat tersebut lalu coba lagi.");
        }
        account.username = name;
        account.profileId = normalizeUuid(selected.getString("id"));
        account.accessToken = response.getString("accessToken");
        account.clientToken = response.optString("clientToken", account.clientToken);
        account.isMicrosoft = false;
        account.isElyBy = true;
        account.msaRefreshToken = "0";
        account.xuid = null;
        account.expiresAt = getJwtExpiresAt(account.accessToken);
        return account;
    }

    private static String normalizeUuid(String uuid) {
        String raw = uuid.replace("-", "");
        if (raw.length() != 32) return uuid;
        return raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16)
                + "-" + raw.substring(16, 20) + "-" + raw.substring(20);
    }

    private static long getJwtExpiresAt(String token) {
        try {
            String[] parts = token.split("\.");
            if (parts.length < 2) return 0L;
            byte[] bytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JSONObject claims = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            long exp = claims.optLong("exp", 0L);
            return exp > 0 ? exp * 1000L : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private JSONObject postJson(String endpoint, JSONObject payload) throws IOException, JSONException {
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(AUTH_URL + endpoint).openConnection();
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
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = stream == null ? "" : readFully(stream);
            if (status < 200 || status >= 300) {
                String message = connection.getResponseMessage();
                try {
                    JSONObject error = new JSONObject(responseText);
                    message = error.optString("errorMessage", error.optString("error", message));
                } catch (JSONException ignored) {
                    if (!responseText.isEmpty()) message = responseText;
                }
                if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("two factor")
                        && totp != null && totp.isEmpty()) {
                    message = "Akun Ely.by memakai verifikasi 2 langkah. Masukkan kode TOTP/Authenticator lalu coba lagi.";
                }
                throw new IOException("Ely.by (HTTP " + status + "): " + message);
            }
            return new JSONObject(responseText);
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
