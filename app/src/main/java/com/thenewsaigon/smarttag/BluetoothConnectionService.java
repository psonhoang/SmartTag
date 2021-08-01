package com.thenewsaigon.smarttag;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.renderscript.ScriptGroup;
import android.support.annotation.RequiresApi;

import android.util.Log;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public class BluetoothConnectionService {
    private static final String TAG = "BluetoothConnectionServ";
    private static final UUID MY_UUID_INSECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int INTERVAL = 1500;

    private Handler mHandler;

    private BluetoothGatt mBluetoothGatt;

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "Connection Status: " + status);
            Log.d(TAG, "Expected status: " + BluetoothGatt.GATT_SUCCESS);
            if (newState == BluetoothGatt.GATT_SUCCESS) {
                boolean rssiFlag = mBluetoothGatt.readRemoteRssi();
                Log.d(TAG, "Flag: " + rssiFlag);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.d(TAG, "onReadRemote");
            final double distance = calculateDistance(rssi);
            Log.d(TAG, "Distance: " + distance);
            final MainActivity mainActivity = (MainActivity) mContext;
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.mDeviceDistance.setText(distance + "m");
                }
            });
        }
    };

//    private double round(double value, int places) {
//        if (places < 0) throw new IllegalArgumentException();
//
//        BigDecimal bd = new BigDecimal(value);
//        bd = bd.setScale(places, RoundingMode.HALF_UP);
//        return bd.doubleValue();
//    }

    private Runnable mStatusChecker = new Runnable() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void run() {
            try {
                Log.d(TAG, "StatusChecker: run() method");
                updateDistance(); //this function can change value of mInterval.
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                mHandler.postDelayed(mStatusChecker, INTERVAL);
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void updateDistance() {
        Log.d(TAG, "updateDistance");
        mBluetoothGatt = mmDevice.connectGatt(mContext, false, mGattCallback);
    }


    private void startRepeatingTask() {
        mStatusChecker.run();
    }

    void stopRepeatingTask() {
        mHandler.removeCallbacks(mStatusChecker);
    }

    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private BluetoothDevice mmDevice;
    private UUID deviceUUID;

    private ProgressDialog mProgressDialog;

    private final BluetoothAdapter mBluetoothAdapter;
    private Context mContext;

    public BluetoothConnectionService(Context context) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mContext = context;
        mHandler = new Handler();
        start();
    }

    // RSSI => Distance formula
    public double calculateDistance(double rssi) {
        int txPower = -59; //hard coded power value. Usually ranges between -59 to -65

        if (rssi == 0) {
            return -1.0;
        }

        double ratio = rssi*1.0/txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio,10);
        }
        else {
            double distance =  (0.89976)*Math.pow(ratio,7.7095) + 0.111;
            return distance;
        }
    }


    private class AcceptThread extends Thread {
        // the local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(mContext.getResources().getString(R.string.app_name),
                        MY_UUID_INSECURE);

                Log.d(TAG, "AcceptThread: Setting up server using " + MY_UUID_INSECURE);
            } catch(IOException ioe) {
                Log.e(TAG, "Socket's listen() method failed", ioe);
            }

            mmServerSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "run: AcceptThread running.");

            BluetoothSocket socket = null;

            try {
                // This is a blocking call and will only return a
                // successful connection or an exception
                Log.d(TAG, "run: RFCOM server socket start.......");

                socket = mmServerSocket.accept();

                Log.d(TAG, "run: RFCOM server socket accepted connection.");

            } catch(IOException ioe) {
                Log.e(TAG, "AcceptThread: IOException: " + ioe. getMessage());
            }

            if(socket != null) {
                connected(socket, mmDevice);
            }

            Log.i(TAG, "END mAcceptThread");
        }

        public void cancel() {
            Log.d(TAG, "cancel: Cancelling AcceptThread");
            try {
                mmServerSocket.close();
            } catch(IOException e) {
                Log.e(TAG, "error: Closing of socket failed: " + e.getMessage());
            }
        }
    }

    class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;

         public ConnectThread(BluetoothDevice device, UUID uuid) {
            Log.d(TAG, "Connect Thread: Started");
            mmDevice = device;
            deviceUUID = uuid;
         }

         public void run() {
             BluetoothSocket tmp = null;
             Log.i(TAG, "RUN mConnectThread");
             // Get a Bluetooth socket for the connection
             // with the given Bluetooth device
             try {
                 Log.d(TAG, "ConnectThread: Trying to create InsecureRfCommSocket using UUID: " + MY_UUID_INSECURE );
                 tmp = mmDevice.createRfcommSocketToServiceRecord(deviceUUID);
             } catch (IOException e) {
                 Log.e(TAG, "ConnectThread: Could not create InsecureRfComm socket: " + e.getMessage());
             }

             mmSocket = tmp;

             mBluetoothAdapter.cancelDiscovery();

             try {
                 // Make a connection to the BT socket
                 mmSocket.connect();

                 Log.d(TAG, "ConnectThread: Connected!");
             } catch (IOException e) {
                 try {
                     mmSocket.close();
                     Log.d(TAG, "run: Closed socket");
                 } catch (IOException e1) {
                     Log.e(TAG, "run: Unable to close socket: " + e1.getMessage());
                 }
                 Log.d(TAG, "run: ConnectThread: Could not connect to UUID: " + MY_UUID_INSECURE);
             }

             connected(mmSocket, mmDevice);
         }

         public void cancel() {
             try {
                 Log.d(TAG, "cancel: closing client socket");
                 mmSocket.close();
             } catch (IOException e) {
                 Log.e(TAG, "cancel: close() of socket failed: " + e.getMessage());
             }
         }
    }

    // Start AcceptThread (called by Activity in onResume)
    public synchronized  void start() {
        Log.d(TAG, "start");

        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }

    public void startClient(BluetoothDevice device, UUID uuid) {
        Log.d(TAG, "startClient: Started");

        mProgressDialog = ProgressDialog.show(mContext, "Connecting Bluetooth...", "Please wait", true);
        mConnectThread = new ConnectThread(device, uuid);
        mConnectThread.start();
    }

    // ConnectedThread
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "ConnectedThread: Starting");

            mmSocket = socket;

            // dismiss progress dialog, connection is established
            mProgressDialog.dismiss();
        }

        public void run() {
            // TODO: When connected read rssi value every 1s and update UI
            Log.d(TAG, "run() methond of ConnectedThread");
            startRepeatingTask();
        }
    }

    private void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected: Starting.");

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }
}
