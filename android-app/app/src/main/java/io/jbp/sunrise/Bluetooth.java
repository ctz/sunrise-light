package io.jbp.sunrise;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Bluetooth
{
    private static final String TAG = "Bluetooth";

    public static final int EVENT_CONNECTED = 1;
    public static final int EVENT_DISCONNECTED = 2;
    public static final int EVENT_WRITE_START = 3;
    public static final int EVENT_WRITE_END = 3;

    private static final String DEVICENAME = "Sunrise";
    private static Bluetooth instance;

    private Context context;
    private BluetoothAdapter adapter;
    private BluetoothDevice device;
    private Handler handler;

    private Bluetooth(Context ctx)
    {
        context = ctx;
        adapter = BluetoothAdapter.getDefaultAdapter();
        device = null;
        handler = new Handler(Looper.getMainLooper());
    }

    private void findDevice()
    {
        device = null;
        adapter.startLeScan(scanCallback);
    }

    private BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.v(TAG, "scan result " + device.getName() + " addr " + device.getAddress());
            if (device.getName().equals(DEVICENAME))
            {
                foundDevice(device);
                adapter.stopLeScan(this);
            }
        }
    };

    private void foundDevice(BluetoothDevice device)
    {
        this.device = device;
        startSend(null);
    }


    private void startSend(final byte[] data)
    {
        Log.v(TAG, "startSend()");

        if (data != null)
            gattChannel.write(data);

        if (device == null)
        {
            findDevice();
            return;
        }

        if (gattChannel.isConnected())
            gattChannel.doWrite();
        else
            device.connectGatt(context, true, gattChannel);
    }

    private BluetoothGattChannel gattChannel = new BluetoothGattChannel();

    public void writeSerial(byte[] data)
    {
        startSend(data);
    }

    public static Bluetooth getInstance(Context ctx)
    {
        if (instance == null)
        {
            instance = new Bluetooth(ctx);
        }
        return instance;
    }
}

class BluetoothGattChannel extends BluetoothGattCallback
{
    private static final String TAG = "Bluetooth";

    boolean connected = false, haveServices = false;
    BluetoothGattCharacteristic writeChr = null;
    BluetoothGatt gatt = null;
    Handler handler = null;
    boolean outstanding = false;
    ConcurrentLinkedQueue<byte[]> outgoing = new ConcurrentLinkedQueue<byte[]>();

    BluetoothGattChannel()
    {
        handler = new Handler(Looper.getMainLooper());
    }

    private void broadcastConnected()
    {
        handler.obtainMessage(Bluetooth.EVENT_CONNECTED).sendToTarget();
    }

    private void broadcastDisconnected()
    {
        handler.obtainMessage(Bluetooth.EVENT_DISCONNECTED).sendToTarget();
    }

    private void broadcastWriteStart()
    {
        handler.obtainMessage(Bluetooth.EVENT_WRITE_START).sendToTarget();
    }

    private void broadcastWriteEnd()
    {
        handler.obtainMessage(Bluetooth.EVENT_WRITE_END).sendToTarget();
    }

    public void write(final byte[] data)
    {
        outgoing.add(data);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
    {
        Log.v(TAG, "onConnectionStateChange st=" + status + " state=" + newState);
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED)
        {
            connected = true;
            haveServices = false;
            broadcastConnected();
            gatt.discoverServices();
            return;
        }

        connected = false;
        broadcastDisconnected();
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status)
    {
        Log.v(TAG, "onServicesDiscovered st=" + status);
        if (status != BluetoothGatt.GATT_SUCCESS)
            return;

        haveServices = true;
        this.gatt = gatt;

        for (BluetoothGattService svc : gatt.getServices())
        {
            Log.v(TAG, "service " + svc.getUuid().toString());
            if (svc.getUuid().toString().startsWith("0000ffe0-"))
            {
                for (BluetoothGattCharacteristic chr : svc.getCharacteristics())
                {
                    Log.v(TAG, "  char " + chr.getUuid().toString());
                    if (chr.getUuid().toString().startsWith("0000ffe1-"))
                    {
                        writeChr = chr;
                    }
                }
            }
        }

        if (writeChr != null)
        {
            doWrite();
        } else {
            Log.v(TAG, "write characteristic not found");
            gatt.disconnect();
        }
    }

    boolean isConnected()
    {
        return connected && haveServices && writeChr != null;
    }

    void doWrite()
    {
        if (!connected || !haveServices || writeChr == null)
        {
            Log.v(TAG, "doWrite with disconnect");
            gatt.connect();
            return;
        }

        if (outgoing.isEmpty())
        {
            Log.v(TAG, "doWrite with empty queue");
            return;
        }

        if (outstanding)
        {
            Log.v(TAG, "doWrite outstanding");
            return;
        }

        broadcastWriteStart();
        Log.v(TAG, "sending write");
        writeChr.setValue(outgoing.peek());
        outstanding = true;
        gatt.writeCharacteristic(writeChr);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic ch, int status)
    {
        outgoing.poll();
        outstanding = false;
        if (outgoing.isEmpty())
            broadcastWriteEnd();
        else
            doWrite();

    }
};
