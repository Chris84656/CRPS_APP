package cn.ntit.crps_app.ui;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import cn.ntit.crps_app.R;
import cn.ntit.crps_app.ble.BleManager;
import cn.ntit.crps_app.model.DynamicData;
import cn.ntit.crps_app.util.NumberFormatter;
import cn.ntit.crps_app.viewmodel.SharedViewModel;

public class ControlFragment extends Fragment {

    private SharedViewModel viewModel;
    private BleManager bleManager;
    private Handler handler;

    private Spinner spinnerDevices;
    private ImageButton btnScan;
    private MaterialButton btnConnect;
    private TextView tvConnStatus;
    private MaterialSwitch switchPower;
    private TextView tvPowerStatus;
    private ImageView ivPowerIcon;
    private MaterialCardView bannerInterrupted;
    private View overlayDisconnected;
    private View layoutDataArea;

    private TextView tvVin, tvIin, tvPin, tvEff;
    private TextView tvVout, tvIout, tvPout;
    private TextView tvT1, tvT2, tvFan;

    private ArrayAdapter<BleManager.ScannedDevice> deviceAdapter;
    private List<BleManager.ScannedDevice> deviceList = new ArrayList<>();

    private boolean pendingPowerCommand = false;
    private int pendingPowerState = -1;
    private Handler powerTimeoutHandler;
    private Runnable powerTimeoutRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_control, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        bleManager = BleManager.getInstance(requireContext());
        handler = new Handler(Looper.getMainLooper());
        powerTimeoutHandler = new Handler(Looper.getMainLooper());

        bindViews(view);
        setupSpinner();
        setupButtons();
        observeData();
    }

    private void bindViews(View view) {
        spinnerDevices = view.findViewById(R.id.spinner_devices);
        btnScan = view.findViewById(R.id.btn_scan);
        btnConnect = view.findViewById(R.id.btn_connect);
        tvConnStatus = view.findViewById(R.id.tv_conn_status);
        switchPower = view.findViewById(R.id.switch_power);
        tvPowerStatus = view.findViewById(R.id.tv_power_status);
        ivPowerIcon = view.findViewById(R.id.iv_power_icon);
        bannerInterrupted = view.findViewById(R.id.banner_interrupted);
        overlayDisconnected = view.findViewById(R.id.overlay_disconnected);
        layoutDataArea = view.findViewById(R.id.layout_data_area);

        tvVin = view.findViewById(R.id.tv_vin);
        tvIin = view.findViewById(R.id.tv_iin);
        tvPin = view.findViewById(R.id.tv_pin);
        tvEff = view.findViewById(R.id.tv_eff);
        tvVout = view.findViewById(R.id.tv_vout);
        tvIout = view.findViewById(R.id.tv_iout);
        tvPout = view.findViewById(R.id.tv_pout);
        tvT1 = view.findViewById(R.id.tv_t1);
        tvT2 = view.findViewById(R.id.tv_t2);
        tvFan = view.findViewById(R.id.tv_fan);
    }

    private void setupSpinner() {
        deviceAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, deviceList);
        spinnerDevices.setAdapter(deviceAdapter);
    }

    private void setupButtons() {
        btnScan.setOnClickListener(v -> {
            bleManager.startScan();
            startScanAnimation();
        });

        btnConnect.setOnClickListener(v -> {
            int state = bleManager.getConnectionState();
            if (state == BleManager.STATE_CONNECTED) {
                bleManager.disconnect();
            } else if (state == BleManager.STATE_DISCONNECTED || state == BleManager.STATE_SCANNING) {
                BleManager.ScannedDevice selected = (BleManager.ScannedDevice) spinnerDevices.getSelectedItem();
                if (selected != null) {
                    bleManager.connect(selected.address);
                }
            }
        });

        switchPower.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            if (bleManager.getConnectionState() != BleManager.STATE_CONNECTED) {
                buttonView.setChecked(!isChecked);
                return;
            }
            pendingPowerCommand = true;
            pendingPowerState = isChecked ? 1 : 0;
            bleManager.sendPowerCommand(isChecked);
            startPowerTimeout();
        });
    }

    private void startPowerTimeout() {
        powerTimeoutHandler.removeCallbacks(powerTimeoutRunnable);
        powerTimeoutRunnable = () -> {
            if (pendingPowerCommand) {
                pendingPowerCommand = false;
                switchPower.setChecked(!switchPower.isChecked());
                Snackbar.make(requireView(), R.string.operation_timeout, Snackbar.LENGTH_LONG)
                        .setAction(R.string.retry, v -> {
                            boolean target = !switchPower.isChecked();
                            switchPower.setChecked(target);
                            pendingPowerCommand = true;
                            pendingPowerState = target ? 1 : 0;
                            bleManager.sendPowerCommand(target);
                            startPowerTimeout();
                        })
                        .show();
            }
        };
        powerTimeoutHandler.postDelayed(powerTimeoutRunnable, 5000);
    }

    private void observeData() {
        viewModel.getConnectionState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            switch (state) {
                case BleManager.STATE_DISCONNECTED:
                    tvConnStatus.setText(R.string.not_connected);
                    btnConnect.setText(R.string.connect);
                    btnConnect.setEnabled(true);
                    switchPower.setEnabled(false);
                    overlayDisconnected.setVisibility(View.VISIBLE);
                    setDataAreaBlur(true);
                    stopScanAnimation();
                    break;
                case BleManager.STATE_SCANNING:
                    tvConnStatus.setText(R.string.scanning);
                    btnConnect.setEnabled(false);
                    overlayDisconnected.setVisibility(View.VISIBLE);
                    setDataAreaBlur(true);
                    break;
                case BleManager.STATE_CONNECTING:
                    tvConnStatus.setText(R.string.connecting);
                    btnConnect.setText(R.string.connecting);
                    btnConnect.setEnabled(false);
                    overlayDisconnected.setVisibility(View.VISIBLE);
                    setDataAreaBlur(true);
                    stopScanAnimation();
                    break;
                case BleManager.STATE_CONNECTED:
                    tvConnStatus.setText(R.string.connected);
                    btnConnect.setText(R.string.disconnect);
                    btnConnect.setEnabled(true);
                    switchPower.setEnabled(true);
                    overlayDisconnected.setVisibility(View.GONE);
                    setDataAreaBlur(false);
                    stopScanAnimation();
                    break;
            }
        });

        viewModel.getScannedDevices().observe(getViewLifecycleOwner(), devices -> {
            if (devices == null) return;
            deviceList.clear();
            deviceList.addAll(devices);
            deviceAdapter.notifyDataSetChanged();
        });

        viewModel.getDynamicData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            updatePowerState(data);
            updateReadings(data);
        });

        viewModel.getDataInterrupted().observe(getViewLifecycleOwner(), interrupted -> {
            if (interrupted == null) return;
            bannerInterrupted.setVisibility(interrupted ? View.VISIBLE : View.GONE);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void setDataAreaBlur(boolean blur) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (blur) {
                layoutDataArea.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP));
            } else {
                layoutDataArea.setRenderEffect(null);
            }
        } else {
            layoutDataArea.setAlpha(blur ? 0.3f : 1f);
        }
    }

    private void updatePowerState(DynamicData data) {
        if (pendingPowerCommand && data.pwr == pendingPowerState) {
            pendingPowerCommand = false;
            powerTimeoutHandler.removeCallbacks(powerTimeoutRunnable);
        }
        if (!pendingPowerCommand) {
            boolean on = data.isPowerOn();
            switchPower.setChecked(on);
            tvPowerStatus.setText(on ? R.string.power_on : R.string.power_off);
            tvPowerStatus.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                    requireView(), on ? com.google.android.material.R.attr.colorTertiary : com.google.android.material.R.attr.colorError));
            ivPowerIcon.setBackgroundResource(on ? R.drawable.bg_circle_tertiary : R.drawable.bg_circle_error);
        }
    }

    private void updateReadings(DynamicData data) {
        tvVin.setText(NumberFormatter.voltage(data.vin));
        tvIin.setText(NumberFormatter.current(data.iin));
        tvPin.setText(NumberFormatter.power(data.getPin()));
        tvEff.setText(NumberFormatter.efficiency(data.eff));
        tvVout.setText(NumberFormatter.voltage(data.vout));
        tvIout.setText(NumberFormatter.current(data.iout));
        tvPout.setText(NumberFormatter.power(data.pout));
        tvT1.setText(NumberFormatter.temperature(data.t1));
        tvT2.setText(NumberFormatter.temperature(data.t2));
        tvFan.setText(NumberFormatter.fanSpeed(data.fan));
    }

    private void startScanAnimation() {
        RotateAnimation anim = new RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(800);
        anim.setRepeatCount(Animation.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        btnScan.startAnimation(anim);
    }

    private void stopScanAnimation() {
        btnScan.clearAnimation();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        powerTimeoutHandler.removeCallbacksAndMessages(null);
    }
}
