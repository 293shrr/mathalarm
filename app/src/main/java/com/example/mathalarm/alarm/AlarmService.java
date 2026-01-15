package com.example.mathalarm.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class AlarmService extends Service {

    public static final String ACTION_START = "com.example.mathalarm.action.START_ALARM";
    public static final String ACTION_STOP  = "com.example.mathalarm.action.STOP_ALARM";

    public static final String EXTRA_STOP_TOKEN = "extra_stop_token";
    private static final String STOP_TOKEN_VALUE = "SOLVED_OK_123";

    private static final String CHANNEL_ID = "alarm_channel";
    private static final int NOTIF_ID = 1001;

    private static boolean isRinging = false;
    private static volatile boolean stopByUser = false;

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable pulseRunnable = new Runnable() {
        @Override public void run() {
            try {
                if (isRinging && (player == null || !player.isPlaying())) {
                    requestAudioFocus();
                    startSound(true);
                }
            } catch (Exception ignored) {}
            handler.postDelayed(this, 30_000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            String token = intent != null ? intent.getStringExtra(EXTRA_STOP_TOKEN) : null;
            if (!STOP_TOKEN_VALUE.equals(token)) return START_STICKY;

            stopByUser = true;
            stopAlarm();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        stopByUser = false;

        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        if (!isRinging) {
            stopByUser = false;
            startAlarm();
        } else {

            try {
                if (player == null || !player.isPlaying()) {
                    requestAudioFocus();
                    startSound(true);
                }
            } catch (Exception ignored) {}
        }

        return START_STICKY;
    }

    private Notification buildNotification() {
        Intent fsIntent = new Intent(this, AlarmRingingActivity.class);
        fsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPi = PendingIntent.getActivity(
                this, 0, fsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent restart = new Intent(this, AlarmService.class);
        restart.setAction(ACTION_START);

        PendingIntent restartPi = PendingIntent.getService(
                this, 999, restart,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Математический будильник")
                .setContentText("Реши 3 примера, чтобы выключить")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setDeleteIntent(restartPi) // ✅ ВОТ ЭТО КЛЮЧЕВО
                .setContentIntent(fullScreenPi)
                .setFullScreenIntent(fullScreenPi, true)
                .addAction(0, "Открыть", fullScreenPi)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        Notification n = b.build();
        n.flags |= Notification.FLAG_ONGOING_EVENT;
        n.flags |= Notification.FLAG_NO_CLEAR;
        return n;
    }

    private void startAlarm() {
        isRinging = true;

        acquireWakeLock();
        requestAudioFocus();
        startSound(false);
        startVibration();

        handler.removeCallbacks(pulseRunnable);
        handler.postDelayed(pulseRunnable, 30_000);
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChange -> {
                        try {
                            if (!isRinging) return;

                            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                                if (player != null) player.setVolume(1f, 1f);
                                if (player != null && !player.isPlaying()) player.start();

                            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                                    || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                // ✅ лучше не “убивать”, просто пауза
                                if (player != null && player.isPlaying()) player.pause();

                            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                                if (player != null) player.setVolume(0.2f, 0.2f);
                            }
                        } catch (Exception ignored) {}
                    })
                    .build();
            audioManager.requestAudioFocus(focusRequest);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            }
        } catch (Exception ignored) {}
        focusRequest = null;
    }

    private void startSound(boolean forceRestart) {
        if (!isRinging) return;

        if (player != null) {
            if (!forceRestart) {
                try {
                    if (!player.isPlaying()) player.start();
                    return;
                } catch (Exception ignored) {}
            }
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }

        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

        player = new MediaPlayer();

        player.setOnErrorListener((mp, what, extra) -> {
            try {
                requestAudioFocus();
                startSound(true);
            } catch (Exception ignored) {}
            return true;
        });



        try {
            player.setDataSource(this, uri);

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            player.setAudioAttributes(attrs);

            player.setLooping(true);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);

            player.prepare();
            player.start();
            player.setVolume(1f, 1f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startVibration() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.VIBRATE)
                != PackageManager.PERMISSION_GRANTED) return;
        if (vibrator == null || !vibrator.hasVibrator()) return;

        long[] pattern = new long[]{0, 800, 400, 800, 400, 800};
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
    }

    private void stopAlarm() {
        handler.removeCallbacks(pulseRunnable);

        isRinging = false;

        releaseWakeLock();
        abandonAudioFocus();

        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        if (vibrator != null) {
            try { vibrator.cancel(); } catch (Exception ignored) {}
        }
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) return;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mathalarm:alarm");
            wakeLock.acquire(10 * 60 * 1000L);
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        wakeLock = null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Будильники",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Уведомления будильника");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (isRinging && !stopByUser) {
            Intent restart = new Intent(getApplicationContext(), AlarmService.class);
            restart.setAction(ACTION_START);
            ContextCompat.startForegroundService(getApplicationContext(), restart);
        }
        super.onTaskRemoved(rootIntent);
    }
}
