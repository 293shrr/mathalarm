package com.example.mathalarm.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class AlarmScheduler {

    private static PendingIntent alarmPi(Context ctx, int alarmId) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.putExtra("alarm_id", alarmId);

        return PendingIntent.getBroadcast(
                ctx,
                alarmId,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static void schedule(Context ctx, int alarmId, long triggerAtMillis) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = alarmPi(ctx, alarmId);

        Intent show = new Intent(ctx, AlarmRingingActivity.class);
        show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent showPi = PendingIntent.getActivity(
                ctx,
                100000 + alarmId,
                show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerAtMillis, showPi);
            am.setAlarmClock(info, pi);
        } catch (SecurityException se) {
            se.printStackTrace();
        }
    }

    public static void cancel(Context ctx, int alarmId) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(alarmPi(ctx, alarmId));
    }
}