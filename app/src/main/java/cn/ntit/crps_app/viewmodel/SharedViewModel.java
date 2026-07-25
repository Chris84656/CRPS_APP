package cn.ntit.crps_app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;

import cn.ntit.crps_app.ble.BleManager;
import cn.ntit.crps_app.model.DynamicData;
import cn.ntit.crps_app.model.StaticData;

public class SharedViewModel extends AndroidViewModel {

    // BLE 连接状态
    private final MutableLiveData<Integer> connectionState = new MutableLiveData<>(BleManager.STATE_DISCONNECTED);
    // 扫描到的设备列表
    private final MutableLiveData<List<BleManager.ScannedDevice>> scannedDevices = new MutableLiveData<>(new ArrayList<>());
    // 动态数据
    private final MutableLiveData<DynamicData> dynamicData = new MutableLiveData<>();
    // 静态信息
    private final MutableLiveData<StaticData> staticData = new MutableLiveData<>();
    // 错误消息
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    // 数据中断标志
    private final MutableLiveData<Boolean> dataInterrupted = new MutableLiveData<>(false);
    // 静态数据中断标志
    private final MutableLiveData<Boolean> staticInterrupted = new MutableLiveData<>(false);

    // 曲线图数据缓存（30 分钟，250ms 一个点，最多 7200 点）
    private static final int MAX_CACHE_SIZE = 7200;
    private final List<Entry> voutEntries = new ArrayList<>();
    private final List<Entry> ioutEntries = new ArrayList<>();
    private final List<Entry> poutEntries = new ArrayList<>();
    private final List<Entry> effEntries = new ArrayList<>();
    private final List<Entry> t1Entries = new ArrayList<>();
    private final List<Entry> t2Entries = new ArrayList<>();

    // 图表是否暂停缓存（切后台时）
    private boolean chartPaused = false;

    public SharedViewModel(@NonNull Application application) {
        super(application);
    }

    // ==================== LiveData getters ====================

    public LiveData<Integer> getConnectionState() { return connectionState; }
    public MutableLiveData<Integer> getConnectionStateMutable() { return connectionState; }

    public LiveData<List<BleManager.ScannedDevice>> getScannedDevices() { return scannedDevices; }
    public MutableLiveData<List<BleManager.ScannedDevice>> getScannedDevicesMutable() { return scannedDevices; }

    public LiveData<DynamicData> getDynamicData() { return dynamicData; }
    public MutableLiveData<DynamicData> getDynamicDataMutable() { return dynamicData; }

    public LiveData<StaticData> getStaticData() { return staticData; }
    public MutableLiveData<StaticData> getStaticDataMutable() { return staticData; }

    public LiveData<String> getErrorMessage() { return errorMessage; }
    public MutableLiveData<String> getErrorMessageMutable() { return errorMessage; }

    public LiveData<Boolean> getDataInterrupted() { return dataInterrupted; }
    public MutableLiveData<Boolean> getDataInterruptedMutable() { return dataInterrupted; }

    public LiveData<Boolean> getStaticInterrupted() { return staticInterrupted; }
    public MutableLiveData<Boolean> getStaticInterruptedMutable() { return staticInterrupted; }

    // ==================== 图表数据 ====================

    // EMA 滤波
    private static final float EMA_ALPHA = 0.3f;
    private float emaVout, emaIout, emaPout, emaEff, emaT1, emaT2;
    private boolean emaInit = false;

    public void addChartEntry(float x, DynamicData data) {
        if (chartPaused) return;
        if (!emaInit) {
            emaVout = data.vout; emaIout = data.iout; emaPout = data.pout;
            emaEff = data.eff; emaT1 = data.t1; emaT2 = data.t2;
            emaInit = true;
        } else {
            emaVout = EMA_ALPHA * data.vout + (1 - EMA_ALPHA) * emaVout;
            emaIout = EMA_ALPHA * data.iout + (1 - EMA_ALPHA) * emaIout;
            emaPout = EMA_ALPHA * data.pout + (1 - EMA_ALPHA) * emaPout;
            emaEff  = EMA_ALPHA * data.eff  + (1 - EMA_ALPHA) * emaEff;
            emaT1   = EMA_ALPHA * data.t1   + (1 - EMA_ALPHA) * emaT1;
            emaT2   = EMA_ALPHA * data.t2   + (1 - EMA_ALPHA) * emaT2;
        }
        addEntry(voutEntries, x, Math.round(emaVout * 10f) / 10f);
        addEntry(ioutEntries, x, emaIout);
        addEntry(poutEntries, x, emaPout);
        addEntry(effEntries, x, emaEff);
        addEntry(t1Entries, x, emaT1);
        addEntry(t2Entries, x, emaT2);
    }

    private void addEntry(List<Entry> list, float x, float y) {
        list.add(new Entry(x, y));
        while (list.size() > MAX_CACHE_SIZE) {
            list.remove(0);
        }
    }

    public List<Entry> getVoutEntries() { return voutEntries; }
    public List<Entry> getIoutEntries() { return ioutEntries; }
    public List<Entry> getPoutEntries() { return poutEntries; }
    public List<Entry> getEffEntries() { return effEntries; }
    public List<Entry> getT1Entries() { return t1Entries; }
    public List<Entry> getT2Entries() { return t2Entries; }

    public void clearChartData() {
        voutEntries.clear();
        ioutEntries.clear();
        poutEntries.clear();
        effEntries.clear();
        t1Entries.clear();
        t2Entries.clear();
    }

    public void setChartPaused(boolean paused) {
        this.chartPaused = paused;
    }

    public boolean isChartPaused() {
        return chartPaused;
    }
}
