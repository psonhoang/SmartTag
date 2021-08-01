package com.thenewsaigon.smarttag;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 72;
    private static final int REQUEST_DISCOVERABLE_BT = 702;
    private static final int DISCOVERABLE_DURATION = 300;

    private TextView mTxtView;
    public TextView mDeviceName;
    public TextView mDeviceDistance;

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothConnectionService mConnectionService;
    private static final UUID MY_UUID_INSECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothDevice mBluetoothDevice;
    private String mSavedDevName;

    private Set<BluetoothDevice> mPairedDevices;
    private List<String> mPairedDevNames;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTxtView = findViewById(R.id.txtView);
        mDeviceName = findViewById(R.id.deviceName);
        mDeviceDistance = findViewById(R.id.deviceDistance);

        Intent intent = getIntent();
        if(intent.getParcelableExtra("savedDevice") != null) {
            mBluetoothDevice = intent.getParcelableExtra("savedDevice");
            mSavedDevName = intent.getStringExtra("savedName");
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mPairedDevices = mBluetoothAdapter.getBondedDevices();
            mPairedDevNames = new ArrayList<>();
            for(BluetoothDevice device: mPairedDevices) {
                mPairedDevNames.add(device.getName());
            }
            enableBT();
        } else {
            mTxtView.setText("No saved device!");
            mFoundReceiver = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(mFoundReceiver != null) {
            unregisterReceiver(mFoundReceiver);
        }
        if(mConnectionService != null) {
            mConnectionService.stopRepeatingTask();
        }
    }


    //Bluetooth
    public void enableBT() {
        if(!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            makeDiscoverable();
        }
    }

    public void makeDiscoverable() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
        startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_BT);
    }

    public void discoverTag() {
        mBluetoothAdapter.startDiscovery();
        mTxtView.setText("Discovering...");
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mFoundReceiver, filter);
    }

    public void startBTConnection(BluetoothDevice device, UUID uuid) {
        Log.d(TAG, "Initializing BT Connection!");

        mConnectionService.startClient(device, uuid);
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private BroadcastReceiver mFoundReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Action: " + action);
            Log.d(TAG, "Expected Action: " + BluetoothDevice.ACTION_FOUND);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                Log.d(TAG, "onReceive: ACTION_FOUND");
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getName() != null) {
                    Log.d(TAG, device.getName());
                    if (device.getName().equals(mBluetoothDevice.getName())) {
//                    String deviceName = device.getName();
//                    mTxtView.setText(deviceName);
                        Log.d(TAG, "Found saved device!");
                        mTxtView.setText("My Devices");
                        mDeviceName.setVisibility(View.VISIBLE);
                        mDeviceName.setText(mSavedDevName);
                        mDeviceDistance.setVisibility(View.VISIBLE);
                        mDeviceDistance.setText("n/a");
                        mConnectionService = new BluetoothConnectionService(MainActivity.this);
                        startBTConnection(mBluetoothDevice, MY_UUID_INSECURE);
                    }
                } else {
                    mTxtView.setText("Saved device not found!");
                }
            }
        }
    };



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case REQUEST_ENABLE_BT:
                if(resultCode == RESULT_OK) {
                    mTxtView.setText("Bluetooth enabled!");
                    makeDiscoverable();
                } else {
                    finish();
                }
                break;

            case REQUEST_DISCOVERABLE_BT:
                if(resultCode == DISCOVERABLE_DURATION) {
                    mTxtView.setText("Your device is now discoverable!");
                    //Check BT permissions in manifest
                    checkBTPermissions();
                } else {
                    finish();
                }
        }
    }

    /**
     * This method is required for all devices running API23+
     * Android must programmatically check the permissions for bluetooth. Putting the proper permissions
     * in the manifest is not enough.
     *
     * NOTE: This will only execute on versions > LOLLIPOP because it is not needed otherwise.
     */
    private void checkBTPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {  // Only ask for these permissions on runtime when running Android 6.0 or higher
            switch (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION)) {
                case PackageManager.PERMISSION_DENIED:
                    finish();
                    break;
                case PackageManager.PERMISSION_GRANTED:
                    discoverTag();
                    break;
            }
        }
    }

    //ActionBar Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch(itemId) {
            case R.id.action_add:
                startActivity(new Intent(this, AddTagActivity.class));
                finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
