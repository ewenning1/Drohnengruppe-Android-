package de.feuerwehr.drohnenflug;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL = "drohne_akku";

    @Override public void onReceive(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
            String flight = intent.getStringExtra("flightId");
            String title = intent.getStringExtra("drone");
            if (flight == null) flight = "0";
            if (title == null) title = "Drohne";
            Intent open = new Intent(context, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            open.putExtra("flightId", flight);
            PendingIntent launch = PendingIntent.getActivity(context, flight.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notice = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Akkuabfrage fällig")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(launch)
                .build();
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(flight.hashCode(), notice);
        } catch (Exception ignored) { }
    }
}
