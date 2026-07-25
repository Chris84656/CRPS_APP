package cn.ntit.crps_app.ble;

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
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cn.ntit.crps_app.model.DynamicData;
import cn.ntit.crps_app.model.StaticData;

@SuppressLint("MissingPermission")
public class BleManager {

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
    private static final int MAX_CONNECT_RETRIES = 3;
    private static final int MAX_RECONNECT_ATTEMPTS = 1;

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
    private int connectRetryCount = 0;
    private int reconnectAttempts = 0;
    private String lastConnectedAddress = null;
    private boolean isScanning = false;
    private boolean userInitiatedDisconnect = false;

    // GATT 操作队列
    private final List<Runnable> gattQueue = new ArrayList<>();
    private boolean gattBusy = false;

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

        mainHandler.postDelayed(this::stopScan, SCAN_DURATION_MS);
    }

    public void stopScan() {
        if (!isScanning) return;
        isScanning = false;
        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception ignored) {}
        }
        mainHandler.removeCallbacksAndMessages(null);
        if (connectionState == STATE_SCANNING) {
            setConnectionState(STATE_DISCONNECTED);
        }
        if (callback != null) callback.onScanFinished();
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
        stopScan();
        disconnectGatt();
        userInitiatedDisconnect = false;
        connectRetryCount = 0;
        lastConnectedAddress = address;
        doConnect(address);
    }

    private void doConnect(String address) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        setConnectionState(STATE_CONNECTING);
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    public void disconnect() {
        userInitiatedDisconnect = true;
        disconnectGatt();
        setConnectionState(STATE_DISCONNECTED);
    }

    private void disconnectGatt() {
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        gattQueue.clear();
        gattBusy = false;
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
        enqueueGattOperation(() -> gatt.writeCharacteristic(ctrlChar));
    }

    // ==================== GATT 回调 ====================

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectAttempts = 0;
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
            g.discoverServices();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
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
            byte[] value = characteristic.getValue();
            if (value == null) return;
            String json = new String(value, StandardCharsets.UTF_8);
            parseAndDispatch(characteristic.getUuid(), json);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] value = characteristic.getValue();
                if (value != null) {
                    String json = new String(value, StandardCharsets.UTF_8);
                    parseAndDispatch(characteristic.getUuid(), json);
                }
            }
            processNextGattOperation();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
            processNextGattOperation();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
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
            enqueueGattOperation(() -> g.writeDescriptor(descriptor));
        }
    }

    private void parseAndDispatch(UUID uuid, String json) {
        try {
            if (CHAR_DATA_UUID.equals(uuid)) {
                DynamicData data = gson.fromJson(json, DynamicData.class);
                if (data != null && callback != null) {
                    mainHandler.post(() -> callback.onDynamicData(data));
                }
            } else if (CHAR_STATIC_UUID.equals(uuid)) {
                StaticData data = gson.fromJson(json, StaticData.class);
                if (data != null && callback != null) {
                    mainHandler.post(() -> callback.onStaticData(data));
                }
            }
        } catch (Exception e) {
            // JSON 解析失败，丢弃该包
        }
    }

    private void handleUnexpectedDisconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS && lastConnectedAddress != null) {
            reconnectAttempts++;
            setConnectionState(STATE_CONNECTING);
            mainHandler.postDelayed(() -> {
                if (!userInitiatedDisconnect) {
                    doConnect(lastConnectedAddress);
                    // 5 秒重连超时：未成功则强制断开
                    mainHandler.postDelayed(() -> {
                        if (connectionState == STATE_CONNECTING) {
                            disconnectGatt();
                            setConnectionState(STATE_DISCONNECTED);
                            if (callback != null) callback.onError("连接已断开");
                        }
                    }, 5000);
                }
            }, 1000);
        } else {
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
        gattQueue.add(operation);
        if (!gattBusy) {
            processNextGattOperation();
        }
    }

    private void processNextGattOperation() {
        if (gattQueue.isEmpty()) {
            gattBusy = false;
            return;
        }
        gattBusy = true;
        Runnable op = gattQueue.remove(0);
        mainHandler.post(op);
    }

    public String getLastConnectedAddress() {
        return lastConnectedAddress;
    }

    public void setLastConnectedAddress(String address) {
        this.lastConnectedAddress = address;
    }
}
