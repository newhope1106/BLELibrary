package cn.appleye.ble;

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.UUID;

/**
 * @author liuliaopu
 * @date 2017-02-14
 *
 * 扫描蓝牙，连接蓝牙，蓝牙数据传输
 */

public class BLEManager {
    private static final String TAG = "BLEManager";

    /**
     * 日志显示开关，默认开启
     * */
    private static boolean DEBUG_ENABLE = true;

    /**
     * 单例模式，持有实例
     * */
    private static volatile BLEManager sInstance;

    /**
     * 扫描超时时间
     * */
    private static int TIME_OUT_SCAN = 1000*60;

    /**
     * 上下文
     * */
    private static volatile Context sContext;

    /**
     * 服务UUID
     * */
    private static String sServiceUUID;

    /**
     * 特征值UUID
     * */
    private static String sCharacteristicUUID;

    /**
     * 发送消息
     * */
    private static final int MESSAGE_SEND = 1000;

    /**
     * 重试连接
     * */
    private static final int MESSAGE_RETRY = 1001;

    /**
     * 连接错误消息
     * */
    private static final int MESSAGE_CONN_ERROR = 1002;

    /**
     * 重试延迟时间
     * */
    private static final int RETRY_TIME_DELAY = 3000;

    /**
     * 最大尝试次数
     * */
    private static final int MAX_TRY_TIMES = 4;

    /**
     * 当前尝试次数
     * */
    private int mCurrentTimes = 0;

    /**
     * 保存所有的gatt，避免异常情况下没有关闭
     * */
    private HashSet<BluetoothGatt> mBluetoothGattSet = new HashSet<>();

    /**
     * 蓝牙扫描器
     * */
    private BluetoothLeScanner mScanner;

    /**
     * 蓝牙连接回调
     * */
    private BluetoothGattCallback mBluetoothGattCallback;

    private BluetoothGatt mCurrentBluetoothGatt = null;

    /**
     * 蓝牙扫描结果回调
     * */
    private DeviceScanCallback mDeviceScanCallback;

    /**
     * 连接结果回调
     * */
    private ConnectCallback mConnectCallback;

    /**
     * 是否连接上设备
     * */
    private boolean mIsConnected = false;

    /**
     * 是否已经关闭
     * */
    private volatile boolean mIsShutdown = false;

    /**
     * 发送消息Handler，线程中处理发送，避免阻塞主线程
     * */
    private Handler mMessageHandler = null;

    /**
     * 当前全局数据
     * */
    private byte[] mGlobalResultBytes = null;

    /**
     * 线程，提供looper
     * */
    private HandlerThread mMessageThread;

    private Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_RETRY: {
                    final BluetoothDevice device = (BluetoothDevice) msg.obj;
                    if (device != null) {
                        realConnect(device);
                    }
                    break;
                }
                case MESSAGE_CONN_ERROR: {
                    ConnResult connResult = (ConnResult) msg.obj;
                    if (connResult != null) {
                        retryConnect(connResult.device, connResult.lostConnection, connResult.resultCode);
                    }
                    break;
                }
            }
        }
    };

    /**
     * 是否允许显示日志
     * @param enable true 显示 false 不显示
     * */
    public static void enableDebug(boolean enable) {
        DEBUG_ENABLE = enable;
    }

    /**
     * 初始化上下文、服务UUID、特征值UUID
     * @param context 上下文，因为可能涉及到跨Activity使用，使用全局的Application，可以在Application中初始化
     * @param serviceUUID 服务UUID
     * @param characteristicUUID 特征值UUID
     * */
    public static void install(Application context, String serviceUUID, String characteristicUUID) {
        if(sContext != null) {
            throw new IllegalArgumentException("you have initialized before");
        }

        if(context == null || TextUtils.isEmpty(serviceUUID) || TextUtils.isEmpty(characteristicUUID)) {
            throw new IllegalArgumentException("context is null or service UUID is empty or characteristic UUID is empty");
        }

        sContext = context;
        sServiceUUID = serviceUUID;
        sCharacteristicUUID = characteristicUUID;
    }

    /**
     * 采用饿汉模式创建实例
     * */
    public static BLEManager getInstance() {
        if(sContext == null) {
            throw new IllegalArgumentException("it has not been initialized, please call install first");
        }

        if(sInstance == null) {
            synchronized (BLEManager.class) {
                if(sInstance == null) {
                    sInstance = new BLEManager();
                }
            }
        }

        return sInstance;
    }

    private BLEManager() {
        perpareSomething();
    }

    /**
     * 做一些准备工作
     * */
    private void perpareSomething() {
        final BluetoothManager bluetoothManager = (BluetoothManager)sContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        /*初始化扫描器*/
        mScanner = new BluetoothLeScanner(new BluetoothAdapter.LeScanCallback(){

            @Override
            public void onLeScan(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
                if(mDeviceScanCallback != null) {
                    mDeviceScanCallback.onDeviceFound(bluetoothDevice, rssi, scanRecord);
                }
            }
        }, bluetoothAdapter);

        /*初始化连接回调*/
        mBluetoothGattCallback = new BluetoothGattCallback(){
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if(gatt != null) {
                    mBluetoothGattSet.add(gatt);
                    logd("mBluetoothGattSet= add gatt " + mBluetoothGattSet);
                }
                logd("onConnectionStateChange status = " + status + ", newState = " + newState);
                if(status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothGatt.STATE_CONNECTED) {//连接成功
                        gatt.discoverServices();
                        mCurrentBluetoothGatt = gatt;
                        logd("连接成功");
                    }
                } else {
                    logd("connect error, status = " + status + ", newState = " + newState);
                    if(!mIsShutdown) {//不是用户手动断开的，就继续重试
                        ConnResult connResult = new ConnResult();
                        connResult.device = gatt.getDevice();
                        connResult.lostConnection = true;
                        connResult.resultCode = status;

                        Message msg = Message.obtain();
                        msg.obj = connResult;
                        msg.what = MESSAGE_CONN_ERROR;

                        mMainHandler.sendMessage(msg);
                    }
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                if(gatt != null) {
                    mBluetoothGattSet.add(gatt);
                }
                logd("onServicesDiscovered");
                if(status == BluetoothGatt.GATT_SUCCESS) {
                    enableNotificationOfCharacteristic(gatt.getDevice(), gatt, true);
                    mIsConnected = true;
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if(status == BluetoothGatt.GATT_SUCCESS) {
                    logd("success : onCharacteristicWrite: "+ Arrays.toString(characteristic.getValue()));
                } else {
                    logd("failed : onCharacteristicWrite: "+ Arrays.toString(characteristic.getValue()));
                }
                super.onCharacteristicWrite(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                logd("onCharacteristicChanged: "+ Arrays.toString(characteristic.getValue()));
                byte[] value = characteristic.getValue();

                mGlobalResultBytes = BLEDataUtil.decode(value, mGlobalResultBytes);
                if(BLEDataUtil.isEnd(value)) {
                    String result = new String(mGlobalResultBytes);
                    logd("result : " + result);

                    if(mConnectCallback!=null){
                        mConnectCallback.onReceive(result);
                    }

                    mGlobalResultBytes = null;
                }

                super.onCharacteristicChanged(gatt, characteristic);
            }
        };
    }

    /**
     * 是否订阅个特征
     * @param enable true 订阅 false 取消订阅
     * */
    public void enableNotificationOfCharacteristic(BluetoothDevice device, BluetoothGatt bluetoothGatt, final boolean enable) {
        UUID serviceUUID = UUID.fromString(sServiceUUID);
        UUID charaUUID = UUID.fromString(sCharacteristicUUID);
        if(!bluetoothGatt.equals(null)){
            BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
            if(service != null){
                BluetoothGattCharacteristic chara= service.getCharacteristic(charaUUID);
                if(chara != null){
                    logd("[enableNotificationOfCharacteristic] first disable the notification");
                    if(bluetoothGatt.setCharacteristicNotification(chara, false)){
                        logd("[enableNotificationOfCharacteristic] disable the notification success");
                    }
                    boolean success = bluetoothGatt.setCharacteristicNotification(chara,enable);
                    logd("[enableNotificationOfCharacteristic] setCharactNotify: "+success);
                }
            }
        }
    }

    /**
     * 开启蓝牙，如果被关闭
     * */
    public void enableBluetoothIfDisabled(Activity activity) {
        if(activity == null) {
            return;
        }

        final BluetoothManager bluetoothManager = (BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if(bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            activity.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    /**
     * 设置连接回调
     * */
    public void setConnectCallback(ConnectCallback connectCallback) {
        mConnectCallback = connectCallback;
    }

    /**
     * 开始扫描设备，缺省的时间是{@link #TIME_OUT_SCAN}
     * */
    public void scanDevices(DeviceScanCallback callback) {
        scanDevices(TIME_OUT_SCAN, callback);
    }

    /**
     * 开始扫描设备
     * @param timeout 超时时间
     * */
    public void scanDevices(int timeout, DeviceScanCallback callback) {
        logd("[scanDevices] timeout = " + timeout);
        if(timeout <= 0) {
            return;
        }

        mDeviceScanCallback = callback;

        logd("[scanDevices] stop scan before starting scan");
        mScanner.stopScan();
        logd("[scanDevices] start scan now");
        mScanner.scanLeDevice(timeout, true, mDeviceScanCallback);
    }

    /**
     * 连接设备
     * */
    public void connect(final BluetoothDevice device) {
        if(device == null) {
            return;
        }

        logd("[connect] connect to device : " + device.getAddress());
        stopConnection();

        realConnect(device);
    }

    /**
     * 重试连接
     * */
    private void retryConnect(final BluetoothDevice device, boolean isLostConnection, int errorCode) {
        if(mIsShutdown) {//主动断开连接，不再重试
            return;
        }

        logd("[retryConnect] reconnect device : " + device.getAddress()
                + ", isLostConnection = " + isLostConnection + ", errorCode = " + errorCode);
        stopConnection();
        mIsConnected = false;

        if(++mCurrentTimes <= MAX_TRY_TIMES) {
            logd("[retryConnect] try time = " + mCurrentTimes + ", delay = " + RETRY_TIME_DELAY);
            Message msg = Message.obtain();
            msg.obj = device;
            msg.what = MESSAGE_RETRY;
            mMainHandler.removeMessages(MESSAGE_RETRY);
            mMainHandler.sendMessageDelayed(msg, RETRY_TIME_DELAY);
        } else {
            logd("connect failed with try out");
            if(mConnectCallback != null) {//连接超过最大次数，连接失败
                mConnectCallback.connectFailed();
            }
        }

    }

    /**
     * 真正的连接设备调用
     * */
    private void realConnect(final BluetoothDevice device) {
        logd("[realConnect]");
        device.connectGatt(sContext, false, mBluetoothGattCallback);
    }

    /**
     * 发送数据
     * @param data 数据
     * */
    public void sendData(final String data) {
        Message message = Message.obtain();
        message.what = MESSAGE_SEND;
        message.obj = data;

        if(mMessageHandler == null) {
            setupMessageHandler();
        }
    }

    /**
     * 初始化MessageHandler
     * */
    private void setupMessageHandler() {
        if(mMessageHandler == null) {
            if(mMessageThread == null) {
                mMessageThread = new HandlerThread("thread-send-message");
            }
            mMessageThread.start();
            mMessageHandler = new Handler(mMessageThread.getLooper()) {
                @Override
                public void handleMessage(Message message) {
                    if(mCurrentBluetoothGatt == null) {
                        return;
                    }

                    String msg = (String) message.obj;

                    if(!TextUtils.isEmpty(msg)) {
                        logd("send message : " + msg);
                        byte[][] packets = BLEDataUtil.encode(msg);
                        int tryCount  = 3;//最多重发次数
                        boolean sendSuccess = true;
                        while(--tryCount > 0) {
                            sendSuccess = true;
                            for(int i = 0; i < packets.length; i++) {
                                byte[] bytes = packets[i];
                                int onceTryCount = 3;//单次重发次数
                                boolean onceSendSuccess = true;
                                BluetoothGattService service = mCurrentBluetoothGatt.getService(UUID.fromString(sServiceUUID));
                                if(service != null) {
                                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(sCharacteristicUUID));
                                    characteristic.setValue(bytes);
                                    while(--onceTryCount > 0) {

                                        if(mIsShutdown) {
                                            break;
                                        }

                                        if(mCurrentBluetoothGatt.writeCharacteristic(characteristic)){
                                            logd("Write Success, DATA: " + Arrays.toString(characteristic.getValue()));
                                            onceSendSuccess = true;
                                            //避免数据发送太快丢失，需要分包延迟发送
                                            SystemClock.sleep(200);
                                            break;
                                        } else {
                                            logd("Write failed, DATA: " + Arrays.toString(characteristic.getValue()) + ", and left times = " + onceTryCount);
                                            onceSendSuccess = false;
                                            //避免数据发送太快丢失，需要分包延迟发送
                                            SystemClock.sleep(400);//失败的时候，把时间调大
                                        }

                                    }

                                }

                                if(!onceSendSuccess) {//一次发送，重试三次都未成功则，跳出重发这个数据
                                    sendSuccess = false;
                                    break;
                                }

                                //避免数据发送太快丢失，需要分包延迟发送
                                SystemClock.sleep(200);
                            }

                            if(sendSuccess) {
                                break;
                            } else {
                                logd("send msg failed, and try times = " + tryCount);
                            }

                            if(mIsShutdown) {
                                logd("give up for disconnected by user");
                                break;
                            }

                            //避免数据发送太快丢失，需要分包延迟发送
                            SystemClock.sleep(200);
                        }

                        if(!sendSuccess && !mIsShutdown) {
                            logd("send failed : " + msg);
                        }

                    }
                }
            };
        }
    }

    /**
     * 关闭所有连接
     * */
    public void closeConnection() {
        logd("[closeConnection]");

        stopConnection();
        mBluetoothGattCallback = null;
        mDeviceScanCallback = null;
    }

    /**
     * 停止所有连接
     * */
    private void stopConnection() {
        logd("[stopConnection]");

        //关闭所有的gatt
        synchronized (mBluetoothGattSet) {
            Iterator<BluetoothGatt> iterator = mBluetoothGattSet.iterator();
            while(iterator.hasNext()) {
                BluetoothGatt gatt = iterator.next();
                //try...catch放在while循环中，保证让所有的gatt都关闭，即使其中一个关闭异常
                try {
                    logd("close gatt : " + gatt);
                    gatt.disconnect();
                    Method e = BluetoothGatt.class.getMethod("refresh", new Class[0]);
                    if(e != null) {
                        boolean success = ((Boolean)e.invoke(gatt, new Object[0])).booleanValue();
                        logd("Refreshing result: " + success);
                    }

                    SystemClock.sleep(600);

                    gatt.close();
                }catch (Exception e) {
                    e.printStackTrace();
                    logd("close 失败 gatt:" + gatt);
                }
            }

            mBluetoothGattSet.clear();
        }
    }

    /**
     * 日志
     * @param message
     * */
    private static void logd(String message) {
        if(DEBUG_ENABLE) {
            Log.d(TAG, message);
        }
    }

    /**
     * 设备扫描回调
     * */
    public interface DeviceScanCallback extends BluetoothLeScanner.ScanTimeOut {
        void onDeviceFound(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord);
    }

    /**
     * 连接结果回调
     * */
    public interface ConnectCallback{
        void connectFailed();
        void onReceive(String data);
    }

    /**
     * 连接结果
     * */
    private static class ConnResult{
        public BluetoothDevice device;
        public boolean lostConnection;
        public int resultCode;
    }
}
