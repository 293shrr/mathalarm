package com.example.mathalarm.alarm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mathalarm.R;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;
import java.util.Locale;

public class AlarmAdapter extends RecyclerView.Adapter<AlarmAdapter.VH> {

    public interface Listener {
        void onToggle(Alarm alarm, boolean enabled);
    }

    private final List<Alarm> items;
    private final Listener listener;

    public AlarmAdapter(List<Alarm> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alarm, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Alarm a = items.get(position);
        h.tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d", a.hour, a.minute));

        h.swEnabled.setOnCheckedChangeListener(null);
        h.swEnabled.setChecked(a.enabled);
        h.swEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onToggle(a, isChecked);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTime;
        SwitchMaterial swEnabled;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvTime);
            swEnabled = itemView.findViewById(R.id.swEnabled);
        }
    }
}
