package cn.ntit.crps_re0.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import cn.ntit.crps_re0.R;
import cn.ntit.crps_re0.model.StaticData;

public class InfoAdapter extends RecyclerView.Adapter<InfoAdapter.ViewHolder> {

    private StaticData data;
    private static final String[][] ITEMS = {
            {"厂商", "Manufacturer"},
            {"型号", "Model Number"},
            {"额定最大功率", "Rated Power"},
            {"额定最大输出电流", "Rated Current"},
            {"PMBus 版本", "Protocol Version"},
            {"序列号", "Serial Number"},
            {"产地", "Location"},
            {"生产日期", "Manufacture Date"},
            {"固件版本", "Firmware Version"},
    };

    public void setData(StaticData data) {
        this.data = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_info, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.tvLabel.setText(ITEMS[position][0]);
        holder.tvSubLabel.setText(ITEMS[position][1]);
        if (data == null) {
            holder.tvValue.setText(R.string.unknown);
            return;
        }
        String value;
        switch (position) {
            case 0: value = data.getMidDisplay(); break;
            case 1: value = data.getMmDisplay(); break;
            case 2: value = data.getRpmaxDisplay() + " W"; break;
            case 3: value = data.getRimaxDisplay() + " A"; break;
            case 4: value = data.getPrevDisplay(); break;
            case 5: value = data.getMsnDisplay(); break;
            case 6: value = data.getMlocDisplay(); break;
            case 7: value = data.getMdateDisplay(); break;
            case 8: value = data.getMrevDisplay(); break;
            default: value = "未知";
        }
        holder.tvValue.setText(value);
    }

    @Override
    public int getItemCount() {
        return ITEMS.length;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvLabel, tvSubLabel, tvValue;

        ViewHolder(View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tv_item_label);
            tvSubLabel = itemView.findViewById(R.id.tv_item_sublabel);
            tvValue = itemView.findViewById(R.id.tv_item_value);
        }
    }
}
