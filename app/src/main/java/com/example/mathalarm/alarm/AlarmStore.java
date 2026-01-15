package com.example.mathalarm.alarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AlarmStore {

    private static final String PREFS = "alarms_prefs";
    private static final String KEY_LIST = "alarms_list";
    private static final String KEY_NEXT_ID = "alarms_next_id";

    private final SharedPreferences sp;

    public AlarmStore(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int nextId() {
        int id = sp.getInt(KEY_NEXT_ID, 1);
        sp.edit().putInt(KEY_NEXT_ID, id + 1).apply();
        return id;
    }

    public List<Alarm> load() {
        String raw = sp.getString(KEY_LIST, "[]");
        ArrayList<Alarm> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.getInt("id");
                int hour = o.getInt("hour");
                int minute = o.getInt("minute");
                boolean enabled = o.getBoolean("enabled");
                out.add(new Alarm(id, hour, minute, enabled));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void save(List<Alarm> alarms) {
        JSONArray arr = new JSONArray();
        try {
            for (Alarm a : alarms) {
                JSONObject o = new JSONObject();
                o.put("id", a.id);
                o.put("hour", a.hour);
                o.put("minute", a.minute);
                o.put("enabled", a.enabled);
                arr.put(o);
            }
        } catch (Exception ignored) {}

        sp.edit().putString(KEY_LIST, arr.toString()).apply();
    }
}
