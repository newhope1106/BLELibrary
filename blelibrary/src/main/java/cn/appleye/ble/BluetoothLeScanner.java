package cn.appleye.ble;

import android.bluetooth.BluetoothAdapter;
import android.os.Handler;

/**
 * @author liuliaopu
 * @date 2017-02-14
 */
public class BluetoothLeScanner {
    private static final String TAG = "BluetoothLeScanner";

    private final Handler mHandler;
    private final BluetoothAdapter.LeScanCallback mLeScanCallback;

    private final BluetoothAdapter mBluetoothAdapter;

    private boolean mScanning;

    public BluetoothLeScanner(final BluetoothAdapter.LeScanCallback leScanCallback, BluetoothAdapter bluetoothAdapter) {
        mHandler = new Handler();
        mLeScanCallback = leScanCallback;
        mBluetoothAdapter = bluetoothAdapter;
    }

    public boolean isScanning() {
        return mScanning;
    }

    /**
     * @param duration 持续时间
     * @param enable true 开启扫描 false 结束扫描
     * */
    public void scanLeDevice(final int duration, final boolean enable) {
        scanLeDevice(duration, enable, null);
    }

    /**
     * @param duration 持续时间
     * @param enable true 开启扫描 false 结束扫描
     * */
    public void scanLeDevice(final int duration, final boolean enable, final ScanTimeOut scanTimeOut) {
        if (enable) {
            if (mScanning) {
                return;
            }
            if (duration > 0) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(mScanning && scanTimeOut != null) {
                            scanTimeOut.onScanTimeOut();
                        }
                        mScanning = false;
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    }
                }, duration);
            }
            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    /**
     * 结束扫描
     * */
    public void stopScan() {
        scanLeDevice(-1, false);
    }

    public interface ScanTimeOut{
        void onScanTimeOut();
    }
}
