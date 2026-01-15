package com.example.mathalarm.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int alarmId = intent != null ? intent.getIntExtra("alarm_id", -1) : -1;

        Intent s = new Intent(context, AlarmService.class);
        s.setAction(AlarmService.ACTION_START);
        s.putExtra("alarm_id", alarmId);
        ContextCompat.startForegroundService(context, s);
    }
}
