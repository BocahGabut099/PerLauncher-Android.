package net.kdt.pojavlaunch.fragments;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Ely.by OAuth login. Credentials are entered only on Ely.by's own page. */
public class ElyByLoginFragment extends Fragment {
    public static final String TAG = "ELYBY_LOGIN_FRAGMENT";

    private static final String WORKER_BASE = "https://auth.bchgabuttt.my.id";
    private WebView webView;
    private ProgressBar progressBar;
    private boolean loginCompleted;

    public ElyByLoginFragment() {
        super(R.layout.fragment_elyby_login);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        webView = view.findViewById(R.id.elyby_oauth_webview);
        progressBar = view.findViewById(R.id.elyby_oauth_progress);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Never bypass certificate validation for authentication pages.
                handler.cancel();
                showError("Sertifikat keamanan halaman Ely.by tidak valid.");
            }
        });

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            return;
        }
        startOAuth();
    }

    private void startOAuth() {
        try {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            StringBuilder state = new StringBuilder(64);
            for (byte value : random) state.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            webView.loadUrl(WORKER_BASE + "/authorize?state=" + Uri.encode(state.toString()));
        } catch (Exception error) {
            showError("Gagal memulai login Ely.by: " + error.getMessage());
        }
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return false;
        if ("perlauncher".equalsIgnoreCase(uri.getScheme())
                && "elyby".equalsIgnoreCase(uri.getHost())
                && "/callback".equals(uri.getPath())) {
            String ticket = uri.getQueryParameter("ticket");
            if (ticket == null || !ticket.matches("[a-fA-F0-9]{64}")) {
                showError("Tiket login Ely.by tidak valid.");
            } else if (!loginCompleted) {
                loginCompleted = true;
                fetchSession(ticket);
            }
            return true;
        }
        // Keep the official Ely.by authorization and account pages inside Android WebView.
        return false;
    }

    private void fetchSession(String ticket) {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        PojavApplication.sExecutorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(WORKER_BASE + "/session?ticket=" + Uri.encode(ticket));
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String body = stream == null ? "" : readFully(stream);
                if (status < 200 || status >= 300) {
                    JSONObject error = new JSONObject(body);
                    throw new IOException(error.optString("error", "Login Ely.by gagal (HTTP " + status + ")"));
                }
                JSONObject session = new JSONObject(body);
                JSONObject user = session.getJSONObject("user");
                String accessToken = session.getString("access_token");
                String refreshToken = session.optString("refresh_token", "");
                String username = user.getString("username");
                String uuid = user.getString("uuid");
                String expiresIn = String.valueOf(session.optLong("expires_in", 86400L));

                if (!username.matches("[A-Za-z0-9_]{3,16}")) {
                    throw new IOException("Username Ely.by tidak valid untuk profil Minecraft.");
                }
                if (!uuid.matches("(?i)[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}")) {
                    throw new IOException("UUID profil Ely.by tidak valid.");
                }

                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    // The account spinner owns persistence and account selection, just like Microsoft login.
                    ExtraCore.setValue(ExtraConstants.ELYBY_LOGIN_TODO,
                            new String[]{accessToken, refreshToken, username, uuid, expiresIn});
                    Tools.backToMainMenu(requireActivity());
                });
            } catch (Exception error) {
                loginCompleted = false;
                showError(error.getMessage() == null ? "Gagal mengambil sesi Ely.by." : error.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
                Tools.runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                });
            }
        });
    }

    private void showError(String message) {
        if (!isAdded()) return;
        Tools.runOnUiThread(() -> {
            if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        });
    }

    private static String readFully(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    public void goBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        progressBar = null;
        super.onDestroyView();
    }
}
