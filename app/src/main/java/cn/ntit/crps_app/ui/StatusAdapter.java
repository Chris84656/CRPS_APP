package cn.ntit.crps_app.ui;

import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import cn.ntit.crps_app.R;
import cn.ntit.crps_app.util.NumberFormatter;

public class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.ViewHolder> {

    private int[] values = new int[4];
    private boolean[] hasData = new boolean[4];
    private boolean[] expanded = new boolean[4];

    private static final String[] NAMES = {
            "STATUS_IOUT", "STATUS_INPUT", "STATUS_TEMPERATURE", "STATUS_FANS_1_2"
    };

    public void setData(int si, int sn, int st, int sf) {
        values[0] = si; values[1] = sn; values[2] = st; values[3] = sf;
        hasData[0] = true; hasData[1] = true; hasData[2] = true; hasData[3] = true;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_status, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tvName.setText(NAMES[position]);

        int color;
        String stateText;
        if (!hasData[position]) {
            stateText = holder.itemView.getContext().getString(R.string.status_unknown);
            color = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_unknown);
        } else if (values[position] == 0x0000) {
            stateText = holder.itemView.getContext().getString(R.string.status_normal);
            color = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_normal);
        } else if (values[position] == 0x0001) {
            stateText = holder.itemView.getContext().getString(R.string.status_abnormal);
            color = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_abnormal);
        } else {
            stateText = holder.itemView.getContext().getString(R.string.status_unknown);
            color = ContextCompat.getColor(holder.itemView.getContext(), R.color.warning_banner_text);
        }

        holder.tvState.setText(stateText);
        holder.tvState.setTextColor(color);
        holder.tvHex.setText(NumberFormatter.hex(values[position]));
        holder.tvHex.setVisibility(expanded[position] ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            ViewGroup parent1 = (ViewGroup) holder.itemView;
            AutoTransition transition = new AutoTransition();
            transition.setDuration(200);
            TransitionManager.beginDelayedTransition(parent1, transition);
            expanded[position] = !expanded[position];
            holder.tvHex.setVisibility(expanded[position] ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public int getItemCount() {
        return 4;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvState, tvHex;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_status_name);
            tvState = itemView.findViewById(R.id.tv_status_state);
            tvHex = itemView.findViewById(R.id.tv_status_hex);
        }
    }
}
