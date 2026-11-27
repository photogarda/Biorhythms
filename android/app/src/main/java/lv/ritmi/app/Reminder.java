package lv.ritmi.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Locale;

/** Daily morning summary (about 8:00) for the selected person. */
public class Reminder extends BroadcastReceiver {
    private static final String CHANNEL = "daily";

    private static PendingIntent pi(Context c) {
        Intent i = new Intent(c, Reminder.class).setAction("lv.ritmi.app.DAILY");
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedule(Context c) {
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, 8);
        t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.DAY_OF_MONTH, 1);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi(c));
    }

    static void cancel(Context c) {
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(pi(c));
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        try {
            String raw = c.getSharedPreferences("ritmi", Context.MODE_PRIVATE).getString("state", null);
            if (raw == null) return;
            JSONObject st = new JSONObject(raw);
            JSONObject s = st.optJSONObject("settings");
            if (s == null || !s.optBoolean("notify")) return;
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) { schedule(c); return; }
            notifyToday(c, st, s);
        } catch (Exception ignored) { }
    }

    private static void notifyToday(Context c, JSONObject st, JSONObject s) throws Exception {
        JSONArray people = st.optJSONArray("people");
        if (people == null || people.length() == 0) return;
        JSONObject p = people.getJSONObject(0);
        String activeId = st.optString("activeId", "");
        for (int i = 0; i < people.length(); i++) if (people.getJSONObject(i).optString("id").equals(activeId)) p = people.getJSONObject(i);

        String algo = s.optString("algo", "alt");
        double[] per = "precise".equals(algo) ? new double[]{23.688437, 28.426125, 33.163812, 37.9015} : new double[]{23, 28, 33, 38};
        double off = "alt".equals(algo) ? 0.5 : 0;
        long n = LocalDate.now().toEpochDay() - LocalDate.parse(p.getString("birth")).toEpochDay();
        double t = n + off;

        JSONObject tx = st.optJSONObject("texts");
        JSONArray names = tx != null ? tx.optJSONArray("r") : null;
        String critWord = tx != null ? tx.optString("crit", "Critical day") : "Critical day";
        StringBuilder body = new StringBuilder(), crit = new StringBuilder();
        double sum = 0;
        for (int j = 0; j < 4; j++) {
            double v = Math.sin(2 * Math.PI * t / per[j]), pct = (v + 1) * 50, half = per[j] / 2;
            sum += pct;
            long m = (long) Math.ceil((t - 0.5) / half - 1e-9);
            String name = names != null && names.length() > j ? names.getString(j) : new String[]{"Physical", "Emotional", "Intellectual", "Intuitive"}[j];
            if (body.length() > 0) body.append(" · ");
            body.append(name.substring(0, Math.min(4, name.length()))).append(' ').append(Math.round(pct)).append('%');
            if (m * half < t + 0.5 - 1e-9) crit.append(crit.length() > 0 ? ", " : "").append(name);
        }
        if (crit.length() > 0) body.append('\n').append(critWord).append(": ").append(crit);

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(p.optString("name") + ": " + String.format(Locale.ROOT, "%.0f%%", sum / 4))
                .setContentText(body.toString())
                .setStyle(new Notification.BigTextStyle().bigText(body.toString()))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(1, note);
    }
}
