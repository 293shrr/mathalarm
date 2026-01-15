package com.example.mathalarm.alarm;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mathalarm.R;

import java.util.Random;

public class AlarmRingingActivity extends AppCompatActivity {

    private final Random rnd = new Random();
    private int a, b;
    private int solved = 0;
    private static final int NEED = 3;

    private TextView tvTitle;
    private TextView tvTask;
    private TextView tvProgress;
    private EditText etAnswer;
    private Button btnOk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        setContentView(R.layout.activity_alarm_ringing);

        tvTitle = findViewById(R.id.tvTitle);
        tvTask = findViewById(R.id.tvTask);
        tvProgress = findViewById(R.id.tvProgress);
        etAnswer = findViewById(R.id.etAnswer);
        btnOk = findViewById(R.id.btnOk);

        etAnswer.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

        nextTask();
        updateProgress();

        btnOk.setOnClickListener(v -> checkAnswer());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(AlarmRingingActivity.this, "Сначала реши примеры 🙂", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void nextTask() {
        a = rnd.nextInt(50) + 1;
        b = rnd.nextInt(50) + 1;
        tvTask.setText(a + " + " + b + " = ?");
        etAnswer.setText("");
        etAnswer.requestFocus();
    }

    private void updateProgress() {
        tvTitle.setText("Реши примеры, чтобы выключить");
        tvProgress.setText("Прогресс: " + solved + " / " + NEED);
    }

    private void checkAnswer() {
        String s = etAnswer.getText().toString().trim();
        if (s.isEmpty()) return;

        int ans;
        try {
            ans = Integer.parseInt(s);
        } catch (Exception e) {
            Toast.makeText(this, "Введи число", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ans == a + b) {
            solved++;
            updateProgress();

            if (solved >= NEED) {
                stopAlarmAndExit();
            } else {
                nextTask();
            }
        } else {
            Toast.makeText(this, "Неверно. Ещё раз!", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAlarmAndExit() {
        Intent stop = new Intent(this, AlarmService.class);
        stop.setAction(AlarmService.ACTION_STOP);
        stop.putExtra(AlarmService.EXTRA_STOP_TOKEN, "SOLVED_OK_123");

        ContextCompat.startForegroundService(this, stop);

        finish();
    }


}
