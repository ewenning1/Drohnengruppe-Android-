package de.feuerwehr.drohnenflug;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity implements LocationListener {
    private static final int GPS_PERMISSION_CODE = 301;
    private static final int NOTICE_PERMISSION_CODE = 302;
    private static final int SAVE_FILE_CODE = 303;
    private WebView web;
    private LocationManager locationManager;
    private byte[] pendingFile;
    private int locationRequestToken = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        NotificationManager notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && notifications != null) {
            NotificationChannel channel = new NotificationChannel(ReminderReceiver.CHANNEL,
                "Drohnen-Akkuabfragen", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Freundliche Erinnerung zur manuellen Akkuabfrage");
            notifications.createNotificationChannel(channel);
        }
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDefaultTextEncodingName("UTF-8");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (request.isForMainFrame() && ("https".equals(scheme) || "http".equals(scheme))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                    return true;
                }
                return false;
            }
        });
        web.addJavascriptInterface(new DroneBridge(), "NativeBridge");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private void eval(String js) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); });
    }

    private void errorToJs(String error) {
        eval("window.nativeGpsError(" + JSONObject.quote(error) + ")");
    }

    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION }, GPS_PERMISSION_CODE);
            return;
        }
        try {
            final int requestToken = ++locationRequestToken;
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager == null) { errorToJs("Standortdienst nicht verfügbar"); return; }
            boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (!network && !gps) { errorToJs("Bitte Standort/GPS in den Android-Einstellungen aktivieren"); return; }
            Location recent = null;
            if (network) recent = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps) {
                Location known = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (recent == null || (known != null && known.getTime() > recent.getTime())) recent = known;
            }
            // A recent fix is useful while waiting for a more accurate GPS position.
            if (recent != null && System.currentTimeMillis() - recent.getTime() < 120000) {
                deliverLocation(recent);
                return;
            }
            if (network) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this,
                Looper.getMainLooper());
            if (gps && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this,
                    Looper.getMainLooper());
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (locationManager != null && requestToken == locationRequestToken) {
                    locationManager.removeUpdates(this);
                    locationManager = null;
                    errorToJs("GPS-Zeitüberschreitung – Standort kontrollieren und erneut versuchen");
                }
            }, 30000);
        } catch (SecurityException e) {
            errorToJs("Standortberechtigung fehlt");
        } catch (Exception e) {
            errorToJs("GPS-Fehler: " + e.getMessage());
        }
    }

    private void deliverLocation(Location location) {
        ++locationRequestToken;
        if (locationManager != null) { locationManager.removeUpdates(this); locationManager = null; }
        eval(String.format(Locale.US, "window.nativeGps(%.7f,%.7f)",
            location.getLatitude(), location.getLongitude()));
    }

    @Override public void onLocationChanged(Location location) { deliverLocation(location); }
    @Override public void onProviderDisabled(String provider) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == GPS_PERMISSION_CODE) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                requestLocation();
            else errorToJs("Standortfreigabe abgelehnt. Bitte App-Berechtigung für Standort erlauben.");
        }
    }

    private PendingIntent reminderIntent(String id, String title) {
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("flightId", id);
        intent.putExtra("drone", title);
        return PendingIntent.getBroadcast(this, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void schedule(String id, String title, long at) {
        try {
            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarm == null) return;
            PendingIntent request = reminderIntent(id, title);
            long when = Math.max(System.currentTimeMillis() + 1000, at);
            if (Build.VERSION.SDK_INT >= 31 && alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, request);
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, request);
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTICE_PERMISSION_CODE);
            }
        } catch (Exception e) {
            eval("window.nativeReminderError(" + JSONObject.quote(String.valueOf(e.getMessage())) + ")");
        }
    }

    private void cancel(String id) {
        try {
            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarm != null) alarm.cancel(reminderIntent(id, ""));
        } catch (Exception ignored) { }
    }

    private void selectSaveLocation(String name, String mime, byte[] contents) {
        pendingFile = contents;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        try { startActivityForResult(intent, SAVE_FILE_CODE); }
        catch (Exception ex) {
            Toast.makeText(this, "Speicherdialog nicht verfügbar", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_FILE_CODE || resultCode != RESULT_OK || data == null || data.getData() == null)
            return;
        Uri dest = data.getData();
        byte[] bytes = pendingFile;
        pendingFile = null;
        if (bytes == null) return;
        new Thread(() -> {
            try (OutputStream stream = getContentResolver().openOutputStream(dest)) {
                if (stream == null) throw new Exception("Ziel konnte nicht geöffnet werden");
                stream.write(bytes);
                stream.flush();
                eval("window.nativeFileSaved(true, 'Datei gespeichert')");
            } catch (Exception ex) {
                eval("window.nativeFileSaved(false," + JSONObject.quote(ex.getMessage()) + ")");
            }
        }, "drohne-export").start();
    }

    private void requestTemperature(double latitude, double longitude) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String query = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&current=temperature_2m",
                    latitude, longitude);
                connection = (HttpURLConnection) new URL(query).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "Drohnengruppe-Android/0.6");
                if (connection.getResponseCode() != 200) throw new Exception("Wetterdienst HTTP " + connection.getResponseCode());
                try (InputStream in = connection.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] chunk = new byte[4096];
                    int n;
                    while ((n = in.read(chunk)) != -1) out.write(chunk, 0, n);
                    JSONObject current = new JSONObject(out.toString(StandardCharsets.UTF_8.name())).getJSONObject("current");
                    double value = current.getDouble("temperature_2m");
                    eval(String.format(Locale.US, "window.nativeTemperature(%.1f)", value));
                }
            } catch (Exception e) {
                eval("window.nativeTemperatureError(" + JSONObject.quote(String.valueOf(e.getMessage())) + ")");
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "drohne-weather").start();
    }

    private final class DroneBridge {
        @JavascriptInterface public void getLocation() { runOnUiThread(() -> requestLocation()); }
        @JavascriptInterface public void getTemperature(double lat, double lon) {
            requestTemperature(lat, lon);
        }
        @JavascriptInterface public void saveFile(String name, String mime, String base64) {
            try {
                byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                runOnUiThread(() -> selectSaveLocation(name, mime, bytes));
            } catch (Exception e) {
                eval("window.nativeFileSaved(false," + JSONObject.quote(e.getMessage()) + ")");
            }
        }
        @JavascriptInterface public void scheduleReminder(String id, String title, long dueAtMillis) {
            runOnUiThread(() -> schedule(id, title, dueAtMillis));
        }
        @JavascriptInterface public void cancelReminder(String id) { runOnUiThread(() -> cancel(id)); }
    }

    @Override protected void onDestroy() {
        if (locationManager != null) locationManager.removeUpdates(this);
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
