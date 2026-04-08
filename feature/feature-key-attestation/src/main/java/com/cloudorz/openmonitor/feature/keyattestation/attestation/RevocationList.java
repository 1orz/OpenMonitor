package com.cloudorz.openmonitor.feature.keyattestation.attestation;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.cloudorz.openmonitor.feature.keyattestation.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class RevocationList {
    private static final String TAG = "RevocationList";
    private static final String STATUS_URL = "https://android.googleapis.com/attestation/status";
    private static final String CACHE_FILE = "revocation_status.json";
    private static final long CACHE_VALIDITY_MS = 3 * 60 * 60 * 1000L; // 3 hours
    private static final int NETWORK_TIMEOUT_MS = 8000; // 8 seconds

    private static volatile JSONObject data;
    private static volatile long lastFetchTime;
    private static volatile String source = "";
    private static volatile boolean loading;
    private static volatile Context appContext;

    private final String status;
    private final String reason;

    public RevocationList(String status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public String status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return "status is " + status + ", reason is " + reason;
    }

    /**
     * Initialize the revocation list. Called from a background thread.
     * Always loads embedded resource first (guarantees data != null),
     * then tries cache or network for a fresher list.
     */
    public static void init(Context context) {
        appContext = context.getApplicationContext();

        // Always load embedded resource first — ensures data is never null
        // even when network/cache is unavailable.
        loadFromEmbeddedResource(appContext);

        // Try cache if it's fresh enough
        File cacheFile = new File(appContext.getFilesDir(), CACHE_FILE);
        if (cacheFile.exists()) {
            long cacheTime = cacheFile.lastModified();
            long cacheAge = System.currentTimeMillis() - cacheTime;
            if (cacheAge < CACHE_VALIDITY_MS) {
                try (var input = new FileInputStream(cacheFile)) {
                    data = parseStatus(readStream(input));
                    lastFetchTime = cacheTime;
                    source = "cache";
                    Log.i(TAG, "Loaded from cache, age: " + (cacheAge / 1000) + "s");
                    return;
                } catch (IOException e) {
                    Log.w(TAG, "Failed to read cache", e);
                }
            }
        }

        // Try to fetch from network (fresher data)
        fetchFromNetwork();
    }

    private static void loadFromEmbeddedResource(Context context) {
        try (var input = context.getResources().openRawResource(R.raw.status)) {
            data = parseStatus(readStream(input));
            source = "embedded";
            Log.i(TAG, "Loaded from embedded resource, entries: " + (data != null ? data.length() : 0));
        } catch (IOException e) {
            Log.w(TAG, "Failed to load embedded revocation list", e);
        }
    }

    public static boolean refresh() {
        return fetchFromNetwork();
    }

    private static boolean fetchFromNetwork() {
        if (appContext == null) return false;
        loading = true;
        try {
            URL url = new URL(STATUS_URL);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
            conn.setReadTimeout(NETWORK_TIMEOUT_MS);
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                Log.w(TAG, "HTTP " + responseCode);
                return false;
            }

            String responseBody;
            try (var input = conn.getInputStream()) {
                responseBody = readStream(input);
            }

            JSONObject entries = parseStatus(responseBody);

            // Save to cache file
            File cacheFile = new File(appContext.getFilesDir(), CACHE_FILE);
            try (var output = new FileOutputStream(cacheFile)) {
                output.write(responseBody.getBytes(StandardCharsets.UTF_8));
            }

            data = entries;
            lastFetchTime = System.currentTimeMillis();
            source = "network";
            Log.i(TAG, "Fetched from network successfully");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch revocation list", e);
            return false;
        } finally {
            loading = false;
        }
    }

    private static String readStream(InputStream input) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } else {
            var output = new ByteArrayOutputStream(8192);
            var buffer = new byte[8192];
            for (int length; (length = input.read(buffer)) != -1; ) {
                output.write(buffer, 0, length);
            }
            return output.toString();
        }
    }

    private static JSONObject parseStatus(String json) throws IOException {
        try {
            return new JSONObject(json).getJSONObject("entries");
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public static RevocationList get(BigInteger serialNumber) {
        JSONObject currentData = data;
        if (currentData == null) return null;

        String serialNumberString = serialNumber.toString(16).toLowerCase();
        JSONObject revocationStatus;
        try {
            revocationStatus = currentData.getJSONObject(serialNumberString);
        } catch (JSONException e) {
            return null;
        }
        try {
            var status = revocationStatus.getString("status");
            var reason = revocationStatus.optString("reason", "");
            return new RevocationList(status, reason);
        } catch (JSONException e) {
            return new RevocationList("", "");
        }
    }

    public static int getEntryCount() {
        JSONObject currentData = data;
        return currentData != null ? currentData.length() : 0;
    }

    public static long getLastFetchTime() {
        return lastFetchTime;
    }

    public static long getCacheExpiryTime() {
        return lastFetchTime > 0 ? lastFetchTime + CACHE_VALIDITY_MS : 0;
    }

    public static String getSource() {
        return source;
    }

    public static boolean isLoading() {
        return loading;
    }
}
