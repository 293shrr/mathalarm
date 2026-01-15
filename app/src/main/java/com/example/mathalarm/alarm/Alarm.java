package com.example.mathalarm.alarm;

public class Alarm {
    public int id;
    public int hour;
    public int minute;
    public boolean enabled;

    public Alarm(int id, int hour, int minute, boolean enabled) {
        this.id = id;
        this.hour = hour;
        this.minute = minute;
        this.enabled = enabled;
    }
}
