package com.example.mathalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatDelegate;



import com.example.mathalarm.alarm.Alarm;
import com.example.mathalarm.alarm.AlarmAdapter;
import com.example.mathalarm.alarm.AlarmScheduler;
import com.example.mathalarm.alarm.AlarmStore;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Calendar;
import java.util.List;



public class MainActivity extends AppCompatActivity {

    private AlarmStore store;
    private List<Alarm> alarms;
    private AlarmAdapter adapter;

    private TextView tvEmpty;

    private final ActivityResultLauncher<String> notifPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        setContentView(R.layout.activity_main);

        askNotificationPermissionIfNeeded();

        store = new AlarmStore(this);
        alarms = store.load();

        tvEmpty = findViewById(R.id.tvEmpty);

        RecyclerView rv = findViewById(R.id.rvAlarms);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AlarmAdapter(alarms, (alarm, enabled) -> {
            int pos = alarms.indexOf(alarm);

            alarm.enabled = enabled;
            store.save(alarms);

            if (enabled) {
                if (!ensureExactAlarmPermission()) {
                    rollbackToggle(alarm, pos);
                    return;
                }

                if (!ensureFullScreenIntentPermission()) {
                    rollbackToggle(alarm, pos);
                    return;
                }

                long t = computeNextTriggerMillis(alarm.hour, alarm.minute);
                AlarmScheduler.schedule(this, alarm.id, t);
                Toast.makeText(this, "Будильник включён", Toast.LENGTH_SHORT).show();
            } else {
                AlarmScheduler.cancel(this, alarm.id);
                Toast.makeText(this, "Будильник выключен", Toast.LENGTH_SHORT).show();
            }

            updateEmpty();
        });

        rv.setAdapter(adapter);

        ItemTouchHelper ith = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                    @Override
                    public boolean onMove(RecyclerView recyclerView,
                                          RecyclerView.ViewHolder viewHolder,
                                          RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                        int pos = viewHolder.getAdapterPosition();
                        if (pos < 0 || pos >= alarms.size()) return;

                        Alarm removed = alarms.remove(pos);

                        if (removed.enabled) {
                            AlarmScheduler.cancel(MainActivity.this, removed.id);
                        }

                        store.save(alarms);
                        adapter.notifyItemRemoved(pos);
                        updateEmpty();

                        Snackbar.make(rv, "Будильник удалён", Snackbar.LENGTH_LONG)
                                .setAction("Отменить", v -> {
                                    alarms.add(pos, removed);
                                    store.save(alarms);
                                    adapter.notifyItemInserted(pos);
                                    updateEmpty();

                                    if (removed.enabled) {
                                        long t = computeNextTriggerMillis(removed.hour, removed.minute);
                                        AlarmScheduler.schedule(MainActivity.this, removed.id, t);
                                    }
                                })
                                .show();
                    }
                }
        );
        ith.attachToRecyclerView(rv);


        if (ensureExactAlarmPermission()) {
            for (Alarm a : alarms) {
                if (a.enabled) {
                    AlarmScheduler.schedule(this, a.id, computeNextTriggerMillis(a.hour, a.minute));
                }
            }
        }

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> showAddDialog());

        updateEmpty();
    }

    private void rollbackToggle(Alarm alarm, int pos) {
        alarm.enabled = false;
        store.save(alarms);

        if (pos >= 0) adapter.notifyItemChanged(pos);
        else adapter.notifyDataSetChanged();

        updateEmpty();
    }

    private void showAddDialog() {

        if (!ensureExactAlarmPermission()) return;

        if (!ensureFullScreenIntentPermission()) return;

        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        int m = now.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            Alarm a = new Alarm(store.nextId(), hourOfDay, minute, true);
            alarms.add(a);
            store.save(alarms);

            AlarmScheduler.schedule(this, a.id, computeNextTriggerMillis(a.hour, a.minute));
            adapter.notifyItemInserted(alarms.size() - 1);
            updateEmpty();

            Toast.makeText(this, "Будильник добавлен", Toast.LENGTH_SHORT).show();
        }, h, m, true).show();
    }

    private long computeNextTriggerMillis(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    private void updateEmpty() {
        if (alarms == null || alarms.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private boolean ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                Toast.makeText(this,
                        "Разреши точные будильники и попробуй ещё раз",
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;
    }

    private boolean ensureFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
                Toast.makeText(this,
                        "Разреши полноэкранный показ, иначе будильник будет только в уведомлениях",
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;
    }
}
