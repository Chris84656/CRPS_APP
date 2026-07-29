package cn.ntit.crps_re0.viewmodel;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.ntit.crps_re0.ble.BleManager;
import cn.ntit.crps_re0.model.DynamicData;
import cn.ntit.crps_re0.model.StaticData;

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
    // #2.7: 源 List 改为 synchronizedList，提供单条操作原子性保证
    // 设计说明：当前所有访问均在主线程（addChartEntry 由 onDynamicData→mainHandler.post 触发，
    // getXxxEntries 由 DetailsFragment 的 observe 回调和 scrollRunnable 调用，均为 mainLooper）。
    // synchronizedList 是防御性措施，避免未来误用导致 ConcurrentModificationException。
    // 注意：复合操作（如遍历/LTTB）仍需调用方在外层 synchronized (list) { ... }，本类 addEntry 已示范。
    private static final int MAX_CACHE_SIZE = 7200;
    private final List<Entry> voutEntries = Collections.synchronizedList(new ArrayList<>());
    private final List<Entry> ioutEntries = Collections.synchronizedList(new ArrayList<>());
    private final List<Entry> poutEntries = Collections.synchronizedList(new ArrayList<>());
    private final List<Entry> effEntries = Collections.synchronizedList(new ArrayList<>());
    private final List<Entry> t1Entries = Collections.synchronizedList(new ArrayList<>());
    private final List<Entry> t2Entries = Collections.synchronizedList(new ArrayList<>());

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

    @MainThread
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
        addEntry(voutEntries, x, emaVout); // #29: 统一不做 round，与其他 5 路一致
        addEntry(ioutEntries, x, emaIout);
        addEntry(poutEntries, x, emaPout);
        addEntry(effEntries, x, emaEff);
        addEntry(t1Entries, x, emaT1);
        addEntry(t2Entries, x, emaT2);
    }

    // #2.7: add + while-remove 是复合操作，synchronizedList 单条原子但中间可能被插入，外层加锁保证一致
    private void addEntry(List<Entry> list, float x, float y) {
        synchronized (list) {
            list.add(new Entry(x, y));
            while (list.size() > MAX_CACHE_SIZE) {
                list.remove(0);
            }
        }
    }

    @MainThread
    public List<Entry> getVoutEntries() { return voutEntries; }
    @MainThread
    public List<Entry> getIoutEntries() { return ioutEntries; }
    @MainThread
    public List<Entry> getPoutEntries() { return poutEntries; }
    @MainThread
    public List<Entry> getEffEntries() { return effEntries; }
    @MainThread
    public List<Entry> getT1Entries() { return t1Entries; }
    @MainThread
    public List<Entry> getT2Entries() { return t2Entries; }

    @MainThread
    public void clearChartData() {
        // #2.7: 6 个 clear 分别原子，无需外层锁（synchronizedList 单条 clear 已线程安全）
        voutEntries.clear();
        ioutEntries.clear();
        poutEntries.clear();
        effEntries.clear();
        t1Entries.clear();
        t2Entries.clear();
        // #27: 设备切换时重置 EMA 初始化标志
        emaInit = false;
    }

    public void setChartPaused(boolean paused) {
        this.chartPaused = paused;
        // #28: 恢复时重置 EMA 状态，避免长时间暂停后首点用旧 EMA 产生异常值
        if (!paused) {
            emaInit = false;
        }
    }

    public boolean isChartPaused() {
        return chartPaused;
    }

    private final MutableLiveData<int[]> chartColors = new MutableLiveData<>();

    public LiveData<int[]> getChartColors() { return chartColors; }

    public void setChartColors(int[] colors) { chartColors.setValue(colors); }
}
