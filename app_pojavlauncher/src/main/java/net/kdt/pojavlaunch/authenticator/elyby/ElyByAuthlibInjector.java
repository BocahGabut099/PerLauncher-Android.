package net.kdt.pojavlaunch.authenticator.elyby;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Downloads the official unmodified authlib-injector artifact and verifies its SHA-256. */
public final class ElyByAuthlibInjector {
    private static final String FILE_NAME = "authlib-injector-1.2.8.jar";
    private static final String SHA256 = "9c7f4343e6c82034958ffb48c14a2cb0c85928be7283103ce17da00c6d5a7b10";
    private static final String[] DOWNLOAD_URLS = {
            "https://authlib-injector.yushi.moe/artifact/56/authlib-injector-1.2.8.jar",
            "https://bmclapi2.bangbang93.com/mirrors/authlib-injector/artifact/56/authlib-injector-1.2.8.jar"
    };

    private ElyByAuthlibInjector() {}

    public static File ensureInstalled(Context context) throws IOException {
        File directory = new File(context.getFilesDir(), "components");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Tidak bisa membuat folder komponen PerLauncher.");
        }
        File destination = new File(directory, FILE_NAME);
        if (destination.isFile() && hasValidHash(destination)) return destination;

        IOException lastError = null;
        for (String address : DOWNLOAD_URLS) {
            File temporary = new File(directory, FILE_NAME + ".download");
            if (temporary.exists()) temporary.delete();
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(45000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "PerLauncher-Android");
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Server Authlib Injector merespons HTTP " + status);
                }
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.getFD().sync();
                }
                if (!hasValidHash(temporary)) {
                    throw new IOException("Checksum Authlib Injector tidak cocok; file unduhan ditolak.");
                }
                if (destination.exists() && !destination.delete()) {
                    throw new IOException("Tidak bisa mengganti Authlib Injector lama.");
                }
                if (!temporary.renameTo(destination)) {
                    throw new IOException("Tidak bisa menyimpan Authlib Injector.");
                }
                return destination;
            } catch (IOException error) {
                lastError = error;
                if (temporary.exists()) temporary.delete();
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw new IOException("Gagal mengunduh Authlib Injector dari server utama maupun mirror.", lastError);
    }

    private static boolean hasValidHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return SHA256.equalsIgnoreCase(hex.toString());
        } catch (IOException | NoSuchAlgorithmException error) {
            return false;
        }
    }
}
