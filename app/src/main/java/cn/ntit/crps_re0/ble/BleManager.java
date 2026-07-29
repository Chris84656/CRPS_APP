package cn.ntit.crps_re0.ble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import cn.ntit.crps_re0.model.DynamicData;
import cn.ntit.crps_re0.model.StaticData;

@SuppressLint("MissingPermission")
public class BleManager {

    private static final String TAG = "BleManager";

    public interface BleCallback {
        void onScanResult(List<ScannedDevice> devices);
        void onScanFinished();
        void onConnectionStateChanged(int state);
        void onDynamicData(DynamicData data);
        void onStaticData(StaticData data);
        void onError(String message);
    }

    public static class ScannedDevice {
        public final String name;
        public final String address;
        public final int rssi;

        public ScannedDevice(String name, String address, int rssi) {
            this.name = name;
            this.address = address;
            this.rssi = rssi;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // 连接状态
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_SCANNING = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    // UUID
    private static final UUID SERVICE_UUID = UUID.fromString("00001815-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_DATA_UUID = UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_CTRL_UUID = UUID.fromString("00002a57-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_STATIC_UUID = UUID.fromString("00002a58-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final String DEVICE_NAME_PREFIX = "CRPS Monitor_";
    private static final int MTU_SIZE = 247;
    private static final long SCAN_DURATION_MS = 7000;
    private static final int MAX_RECONNECT_ATTEMPTS = 1;
    // #2.3: 单条 GATT 操作超时，避免 writeCharacteristic/writeDescriptor 返回 false 或回调丢失导致队列卡死
    private static final long GATT_OP_TIMEOUT_MS = 5000;
    // #2.6: 重连间隔与重连总超时
    private static final long RECONNECT_DELAY_MS = 2000;
    private static final long RECONNECT_TIMEOUT_MS = 5000;

    private static BleManager instance;

    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private Handler mainHandler;
    private Gson gson;
    private BleCallback callback;

    private int connectionState = STATE_DISCONNECTED;
    private List<ScannedDevice> scannedDevices = new ArrayList<>();
    private int reconnectAttempts = 0;
    private String lastConnectedAddress = null;
    private boolean isScanning = false;
    private boolean userInitiatedDisconnect = false;
    private int currentMtu = 23; // #34: 记录实际 MTU，默认 23

    // #5: JSON 拼包缓冲区（按 UUID 维护）
    // 用 StringBuffer 而非 StringBuilder：onCharacteristicChanged 在 binder 线程 append，
    // disconnectGatt 在主线程 setLength(0)，需要线程安全
    private final StringBuffer jsonBufferData = new StringBuffer();
    private final StringBuffer jsonBufferStatic = new StringBuffer();

    // #7: GATT 操作队列（线程安全）
    private final List<Runnable> gattQueue = Collections.synchronizedList(new ArrayList<>());
    private boolean gattBusy = false;
    // #2.3: 单条 op 超时 Runnable。
    // 设计说明：GATT 回调 API 不提供回调标识，无法 100% 过滤迟到回调。
    // 本方案三层兜底：① 超时清空整个队列 ② g==gatt 过滤旧 GATT 回调
    //              ③ 迟到回调触发 processNextGattOperation 时队列已空 → 直接 return
    private final Runnable gattOpTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (gattQueue) {
                if (gattBusy) {
                    Log.w(TAG, "GATT operation timeout, forcing next");
                    if (callback != null) callback.onError("GATT 操作超时");
                    gattBusy = false;
                    // 超时后清空队列：旧 op 已失效，其 pending op 也可能基于失效的 gatt 状态
                    gattQueue.clear();
                }
            }
        }
    };

    // #8: 扫描超时 Runnable 引用（用于精确移除）
    private final Runnable scanTimeoutRunnable = this::stopScan;

    // #2.6: 重连 Runnable 与重连总超时 Runnable，保存引用以便在新连接发起或连接成功时取消
    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!userInitiatedDisconnect && lastConnectedAddress != null) {
                doConnect(lastConnectedAddress);
                mainHandler.postDelayed(reconnectTimeoutRunnable, RECONNECT_TIMEOUT_MS);
            }
        }
    };
    private final Runnable reconnectTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (connectionState == STATE_CONNECTING) {
                Log.w(TAG, "Reconnect timeout, giving up");
                disconnectGatt();
                setConnectionState(STATE_DISCONNECTED);
                if (callback != null) callback.onError("连接已断开");
            }
        }
    };

    // #36: 蓝牙状态广播接收器
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_OFF) {
                if (connectionState == STATE_CONNECTED || connectionState == STATE_CONNECTING) {
                    userInitiatedDisconnect = true;
                    disconnectGatt();
                    setConnectionState(STATE_DISCONNECTED);
                    if (callback != null) callback.onError("蓝牙已关闭");
                }
            }
        }
    };
    private boolean bluetoothReceiverRegistered = false;

    private BleManager(Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
    }

    public static synchronized BleManager getInstance(Context context) {
        if (instance == null) {
            instance = new BleManager(context);
        }
        return instance;
    }

    public void setCallback(BleCallback callback) {
        this.callback = callback;
        // #36: 注册蓝牙状态广播；callback 为 null 时反注册避免接收器泄漏
        if (callback != null && !bluetoothReceiverRegistered) {
            context.registerReceiver(bluetoothStateReceiver,
                    new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            bluetoothReceiverRegistered = true;
        } else if (callback == null && bluetoothReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothStateReceiver);
            } catch (Exception e) {
                // 反注册失败忽略（接收器可能已被反注册）
            }
            bluetoothReceiverRegistered = false;
        }
    }

    public int getConnectionState() {
        return connectionState;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    // ==================== 扫描 ====================

    public void startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            if (callback != null) callback.onError("蓝牙未开启");
            return;
        }
        if (isScanning) return;

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) return;

        scannedDevices.clear();
        isScanning = true;
        setConnectionState(STATE_SCANNING);

        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceName(null)
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);

        scanner.startScan(filters, settings, scanCallback);

        // #8: 精确移除旧的超时，再注册新的
        mainHandler.removeCallbacks(scanTimeoutRunnable);
        mainHandler.postDelayed(scanTimeoutRunnable, SCAN_DURATION_MS);
    }

    // #41: 内部停止扫描，不设置连接状态（供 connect 调用）
    private void stopScanInternal() {
        if (!isScanning) return;
        isScanning = false;
        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception e) {
                // #43: 加日志
                Log.w(TAG, "Failed to stop scan", e);
            }
        }
        // #8: 精确移除扫描超时，不再用 removeCallbacksAndMessages(null)
        mainHandler.removeCallbacks(scanTimeoutRunnable);
        if (callback != null) callback.onScanFinished();
    }

    public void stopScan() {
        stopScanInternal();
        if (connectionState == STATE_SCANNING) {
            setConnectionState(STATE_DISCONNECTED);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name != null && name.startsWith(DEVICE_NAME_PREFIX)) {
                String address = device.getAddress();
                boolean exists = false;
                for (ScannedDevice d : scannedDevices) {
                    if (d.address.equals(address)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    scannedDevices.add(new ScannedDevice(name, address, result.getRssi()));
                    if (callback != null) callback.onScanResult(new ArrayList<>(scannedDevices));
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            isScanning = false;
            if (callback != null) callback.onError("扫描失败: " + errorCode);
        }
    };

    // ==================== 连接 ====================

    public void connect(String address) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            if (callback != null) callback.onError("蓝牙未开启");
            return;
        }
        // #40: 在 disconnectGatt 前设 userInitiatedDisconnect = true
        // 屏蔽 disconnectGatt 触发的延迟回调，避免触发 handleUnexpectedDisconnect
        userInitiatedDisconnect = true;
        // #41: 用 stopScanInternal 不设 DISCONNECTED，避免状态跳变
        stopScanInternal();
        disconnectGatt();
        lastConnectedAddress = address;
        reconnectAttempts = 0;
        doConnect(address);
    }

    private void doConnect(String address) {
        // #2.6: 新连接发起时取消挂起的重连/重连超时，避免误陷新连接
        mainHandler.removeCallbacks(reconnectRunnable);
        mainHandler.removeCallbacks(reconnectTimeoutRunnable);
        // #2: 先 close 旧 gatt，避免 GATT 133 泄漏
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        BluetoothDevice device;
        try {
            device = bluetoothAdapter.getRemoteDevice(address);
        } catch (IllegalArgumentException e) {
            // 地址格式非法
            if (callback != null) callback.onError("无效的设备地址: " + address);
            setConnectionState(STATE_DISCONNECTED);
            return;
        }
        setConnectionState(STATE_CONNECTING);
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        // #40: 新 GATT 创建后立即复位 userInitiatedDisconnect，否则新连接失败时
        // 不会触发 handleUnexpectedDisconnect，自动重连机制失效。
        // 旧 GATT 的延迟回调由 gattCallback 中各回调入口的 g != this.gatt 检查过滤。
        userInitiatedDisconnect = false;
    }

    public void disconnect() {
        userInitiatedDisconnect = true;
        // #2.6: 用户主动断开时取消挂起的重连 Runnable
        mainHandler.removeCallbacks(reconnectRunnable);
        mainHandler.removeCallbacks(reconnectTimeoutRunnable);
        disconnectGatt();
        setConnectionState(STATE_DISCONNECTED);
    }

    private void disconnectGatt() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        synchronized (gattQueue) {
            gattQueue.clear();
            gattBusy = false;
        }
        // #2.3: 清理挂起的 GATT op 超时
        mainHandler.removeCallbacks(gattOpTimeoutRunnable);
        // 清空拼包缓冲区
        jsonBufferData.setLength(0);
        jsonBufferStatic.setLength(0);
    }

    // ==================== 写入控制 ====================

    public void sendPowerCommand(boolean powerOn) {
        if (gatt == null) return;
        BluetoothGattService service = gatt.getService(SERVICE_UUID);
        if (service == null) return;
        BluetoothGattCharacteristic ctrlChar = service.getCharacteristic(CHAR_CTRL_UUID);
        if (ctrlChar == null) return;

        byte[] cmd = new byte[]{(byte) (powerOn ? 0x01 : 0x00)};
        ctrlChar.setValue(cmd);
        ctrlChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        enqueueGattOperation(() -> {
            // #2.3: 检查返回值，false 时不触发回调，必须主动推进队列避免卡死
            if (gatt != null) {
                if (!gatt.writeCharacteristic(ctrlChar)) {
                    Log.w(TAG, "writeCharacteristic returned false");
                    if (callback != null) callback.onError("写入命令失败");
                    processNextGattOperation();
                }
            } else {
                // gatt 已断开，丢弃本条 op
                processNextGattOperation();
            }
        });
    }

    // ==================== GATT 回调 ====================

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            // #40: 旧 GATT 的延迟回调直接丢弃，避免污染新连接状态
            if (g != gatt) return;
            // #32: 只检查 status=133 且 newState=CONNECTED 的特定组合
            if (status == 133 && newState == BluetoothProfile.STATE_CONNECTED) {
                mainHandler.post(() -> handleUnexpectedDisconnect());
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectAttempts = 0;
                // #40: 连接成功后清除 userInitiatedDisconnect 标志
                userInitiatedDisconnect = false;
                // #2.6: 连接成功，取消挂起的重连 Runnable
                mainHandler.removeCallbacks(reconnectRunnable);
                mainHandler.removeCallbacks(reconnectTimeoutRunnable);
                mainHandler.post(() -> {
                    setConnectionState(STATE_CONNECTED);
                    g.requestMtu(MTU_SIZE);
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mainHandler.post(() -> {
                    if (!userInitiatedDisconnect) {
                        handleUnexpectedDisconnect();
                    } else {
                        setConnectionState(STATE_DISCONNECTED);
                    }
                });
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            if (g != gatt) return;
            // #34: 记录实际 MTU
            currentMtu = (status == BluetoothGatt.GATT_SUCCESS) ? mtu : 23;
            Log.d(TAG, "MTU changed: " + currentMtu + " (status=" + status + ")");
            g.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (g != gatt) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = g.getService(SERVICE_UUID);
                if (service != null) {
                    enableNotification(g, service, CHAR_DATA_UUID);
                    enableNotification(g, service, CHAR_STATIC_UUID);
                }
            } else {
                if (callback != null) callback.onError("服务发现失败");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic characteristic) {
            if (g != gatt) return;
            byte[] value = characteristic.getValue();
            if (value == null) return;
            String chunk = new String(value, StandardCharsets.UTF_8);
            // #5: 拼包缓冲区，按 UUID 维护
            UUID uuid = characteristic.getUuid();
            StringBuffer buffer;
            if (CHAR_DATA_UUID.equals(uuid)) {
                buffer = jsonBufferData;
            } else if (CHAR_STATIC_UUID.equals(uuid)) {
                buffer = jsonBufferStatic;
            } else {
                return;
            }
            buffer.append(chunk);
            // 尝试解析完整 JSON，失败则等待下一包
            tryParseAndDispatch(uuid, buffer);
        }

        // #42: 删除 onCharacteristicRead 死代码

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
            if (g != gatt) return;
            // 注：迟到回调过滤依赖「超时清空队列 + g==gatt 检查 + gattBusy 队列空时直接 return」三层兜底
            // #33: 检查 status
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (callback != null) callback.onError("写入失败 status=" + status);
            }
            processNextGattOperation();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
            if (g != gatt) return;
            // 注：同 onCharacteristicWrite
            // #4: 检查 status，通知启用失败则断连报错
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (callback != null) callback.onError("通知启用失败");
                disconnectGatt();
                setConnectionState(STATE_DISCONNECTED);
                return;
            }
            processNextGattOperation();
        }
    };

    // ==================== 内部方法 ====================

    private void enableNotification(BluetoothGatt g, BluetoothGattService service, UUID charUuid) {
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
        if (characteristic == null) return;

        g.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            enqueueGattOperation(() -> {
                // #2.3: 检查返回值，false 时主动推进队列避免卡死
                if (gatt != null) {
                    if (!gatt.writeDescriptor(descriptor)) {
                        Log.w(TAG, "writeDescriptor returned false");
                        if (callback != null) callback.onError("通知启用失败");
                        disconnectGatt();
                        setConnectionState(STATE_DISCONNECTED);
                        // 不调用 processNextGattOperation，队列已在 disconnectGatt 中清空
                    }
                } else {
                    processNextGattOperation();
                }
            });
        }
    }

    // #5: 尝试解析完整 JSON
    private void tryParseAndDispatch(UUID uuid, StringBuffer buffer) {
        String json = buffer.toString().trim();
        if (json.isEmpty()) return;

        // 检测 JSON 是否完整：括号配对
        if (isJsonComplete(json)) {
            buffer.setLength(0);
            // #6: 移到主线程解析，避免占用 binder 线程
            String jsonStr = json;
            mainHandler.post(() -> parseAndDispatch(uuid, jsonStr));
        }
    }

    private boolean isJsonComplete(String json) {
        int braceCount = 0;
        boolean started = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') { braceCount++; started = true; }
            else if (c == '}') braceCount--;
            if (started && braceCount == 0) return true;
        }
        return false;
    }

    // #6: 在主线程解析（由 tryParseAndDispatch post 调用）
    private void parseAndDispatch(UUID uuid, String json) {
        try {
            if (CHAR_DATA_UUID.equals(uuid)) {
                DynamicData data = gson.fromJson(json, DynamicData.class);
                if (data != null && callback != null) {
                    callback.onDynamicData(data);
                }
            } else if (CHAR_STATIC_UUID.equals(uuid)) {
                StaticData data = gson.fromJson(json, StaticData.class);
                if (data != null && callback != null) {
                    callback.onStaticData(data);
                }
            }
        } catch (Exception e) {
            // #43: 加日志
            Log.w(TAG, "JSON parse failed: " + json, e);
        }
    }

    private void handleUnexpectedDisconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS && lastConnectedAddress != null) {
            reconnectAttempts++;
            setConnectionState(STATE_CONNECTING);
            // #2.6: 使用保存的 Runnable 引用，便于在 doConnect/disconnect/连接成功时取消
            // 先清理可能残留的旧 Runnable（避免叠加）
            mainHandler.removeCallbacks(reconnectRunnable);
            mainHandler.removeCallbacks(reconnectTimeoutRunnable);
            // #35: 重连间隔 2s
            mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        } else {
            // 重连次数耗尽：必须关闭 gatt，否则 BluetoothGatt 资源泄漏
            disconnectGatt();
            setConnectionState(STATE_DISCONNECTED);
            if (callback != null) callback.onError("连接已断开");
        }
    }

    private void setConnectionState(int state) {
        this.connectionState = state;
        if (callback != null) callback.onConnectionStateChanged(state);
    }

    // ==================== GATT 操作队列 ====================

    private void enqueueGattOperation(Runnable operation) {
        synchronized (gattQueue) {
            gattQueue.add(operation);
            if (!gattBusy) {
                processNextGattOperation();
            }
        }
    }

    private void processNextGattOperation() {
        synchronized (gattQueue) {
            // #2.3: 取消上一条 op 的超时（无论成功失败都会走到这里）
            mainHandler.removeCallbacks(gattOpTimeoutRunnable);
            if (gattQueue.isEmpty()) {
                gattBusy = false;
                return;
            }
            gattBusy = true;
            Runnable op = gattQueue.remove(0);
            mainHandler.post(op);
            // #2.3: 为本条 op 设置超时，回调丢失或 writeXxx 返回 false 但未触发推进时兜底
            // 注：超时由 gattOpTimeoutRunnable 清空整个队列处理；迟到的旧 op 回调触发本方法时
            // 队列已空 → 走 isEmpty 分支直接 return，不会污染下一条 op
            mainHandler.postDelayed(gattOpTimeoutRunnable, GATT_OP_TIMEOUT_MS);
        }
    }

    public String getLastConnectedAddress() {
        return lastConnectedAddress;
    }

    public void setLastConnectedAddress(String address) {
        this.lastConnectedAddress = address;
    }
}
