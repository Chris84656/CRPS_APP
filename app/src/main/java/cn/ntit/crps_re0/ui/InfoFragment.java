package cn.ntit.crps_re0.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import cn.ntit.crps_re0.R;
import cn.ntit.crps_re0.model.DynamicData;
import cn.ntit.crps_re0.model.StaticData;
import cn.ntit.crps_re0.viewmodel.SharedViewModel;

public class InfoFragment extends Fragment {

    private SharedViewModel viewModel;
    private TextView tvNoInfo;
    private MaterialCardView cardInfo, cardStatus;
    private RecyclerView rvInfo, rvStatus;
    private TextView tvStatusTitle, tvStaticInterrupted;
    private InfoAdapter infoAdapter;
    private StatusAdapter statusAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        tvNoInfo = view.findViewById(R.id.tv_no_info);
        cardInfo = view.findViewById(R.id.card_info);
        cardStatus = view.findViewById(R.id.card_status);
        rvInfo = view.findViewById(R.id.rv_info);
        rvStatus = view.findViewById(R.id.rv_status);
        tvStatusTitle = view.findViewById(R.id.tv_status_title);
        tvStaticInterrupted = view.findViewById(R.id.tv_static_interrupted);

        rvInfo.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvStatus.setLayoutManager(new LinearLayoutManager(requireContext()));

        infoAdapter = new InfoAdapter();
        statusAdapter = new StatusAdapter();
        rvInfo.setAdapter(infoAdapter);
        rvStatus.setAdapter(statusAdapter);

        observeData();
    }

    private void observeData() {
        viewModel.getStaticData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            tvNoInfo.setVisibility(View.GONE);
            cardInfo.setVisibility(View.VISIBLE);
            infoAdapter.setData(data);
        });

        viewModel.getDynamicData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            tvStatusTitle.setVisibility(View.VISIBLE);
            cardStatus.setVisibility(View.VISIBLE);
            statusAdapter.setData(data.si, data.sn, data.st, data.sf);
        });

        viewModel.getStaticInterrupted().observe(getViewLifecycleOwner(), interrupted -> {
            if (interrupted == null) return;
            tvStaticInterrupted.setVisibility(interrupted ? View.VISIBLE : View.GONE);
        });

        viewModel.getConnectionState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            if (state != cn.ntit.crps_re0.ble.BleManager.STATE_CONNECTED) {
                tvNoInfo.setVisibility(View.VISIBLE);
                cardInfo.setVisibility(View.GONE);
                cardStatus.setVisibility(View.GONE);
                tvStatusTitle.setVisibility(View.GONE);
                // 断连时一并隐藏"静态信息中断"横幅，避免残留
                tvStaticInterrupted.setVisibility(View.GONE);
            }
        });
    }
}
