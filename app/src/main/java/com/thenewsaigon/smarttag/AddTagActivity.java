package com.thenewsaigon.smarttag;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class AddTagActivity extends AppCompatActivity {
    private static final String TAG = "AddTagActivity";
    public static final String MIME_TEXT_PLAIN = "text/plain";

    private TextView mTextView;
    private EditText mNameEditTxt;
    private Button mSaveDeviceBtn;

    private NfcAdapter mNfcAdapter;
    private BluetoothAdapter mBluetoothAdapter;

    private String mUID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_tag);

        mTextView = findViewById(R.id.textView);
        mNameEditTxt = findViewById(R.id.nameEditTxt);
        mSaveDeviceBtn = findViewById(R.id.saveDeviceBtn);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if(!mNfcAdapter.isEnabled()) {
            //NFC not enabled
            mTextView.setText("NFC is not enabled!");
        } else {
            mTextView.setText("NFC is ready!");
            handleIntent(getIntent());
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Broadcasts when bond state changes (eg: pairing)
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBondReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        setupForegroundDispatch(this, mNfcAdapter);
        if(mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mBluetoothAdapter.startDiscovery();
    }

    @Override
    protected void onPause() {
        stopForegroundDispatch(this, mNfcAdapter);

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();


    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        //Instead of calling new activity when tag attached to device
        handleIntent(intent);
    }

    // ******** Bluetooth ********
    public void discoverTag() {
        if(mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mBluetoothAdapter.startDiscovery();
        mTextView.setText("Discovering...");
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mFoundReceiver, filter);
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver mFoundReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive: ACTION_FOUND");
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //TODO: check if deviceName == UUID then connect
                Log.d(TAG, "Device: " + device.toString());
                String deviceName = device.getName();
                if(deviceName != null) {
                    Log.d(TAG, device.getName());
                    if (device.getName().equals(mUID)) {
                        mTextView.setText(deviceName + ", uid: " + mUID);

                        mBluetoothAdapter.cancelDiscovery();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            Log.d(TAG, "Trying to pair with " + deviceName);
                            device.createBond();
                        }
                    } else {
                        mTextView.setText("No tag found with NFC's UID!");
                    }
                    ;
                }
            }
        }
    };

    //BroadcastReceiver for BOND_STATE_CHANGED
    private final BroadcastReceiver mBondReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            String action = intent.getAction();

            if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                switch(device.getBondState()) {
                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "ACTION_BOND_STATE: BONDED");
                        mNameEditTxt.setVisibility(View.VISIBLE);
                        mSaveDeviceBtn.setVisibility(View.VISIBLE);
                        mSaveDeviceBtn.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent backIntent = new Intent(AddTagActivity.this, MainActivity.class);
                                backIntent.putExtra("savedDevice", device);
                                backIntent.putExtra("savedName", mNameEditTxt.getText().toString());
                                startActivity(backIntent);
                                finish();
                            }
                        });
                        break;

                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "ACTION_BOND_STATE: BONDING");
                        break;

                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "ACTION_BOND_STATE: NONE");
                        break;
                }
            }
        }
    };

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
    // ******** Bluetooth ********

    // ******** NFC ********
    private void setupForegroundDispatch(Activity activity, NfcAdapter nfcAdapter) {
        Intent intent = new Intent(activity, activity.getClass());
        //To avoid creating new instance of activty -> onNewIntent()
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);
        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        //Same filter as manifest
        filters[0] = new IntentFilter();
        filters[0].addAction(nfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            e.printStackTrace();
        }

        nfcAdapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    private void stopForegroundDispatch(Activity activity, NfcAdapter nfcAdapter) {
        nfcAdapter.disableForegroundDispatch(activity);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if(action != null) {
            if (action.equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
                String dataType = intent.getType();

                if (dataType.equals(MIME_TEXT_PLAIN)) {
                    Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                    new NdefReaderTask().execute(tag);
                } else {
                    mTextView.setText("Mismatched data type!");
                }
            }
        }
    }

    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {
        @Override
        protected String doInBackground(Tag... tags) {
            Tag tag = tags[0];
            Ndef ndef = Ndef.get(tag);

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            NdefRecord[] records = ndefMessage.getRecords();
            for(NdefRecord record: records) {
                if(record.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)) {
                    try {
                        return readText(record);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }

            return null;
        }

        private String readText(NdefRecord record) throws UnsupportedEncodingException {
            byte[] payload = record.getPayload();

            //text encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

            //language code
            int languageCodeLength = payload[0] & 0063;

            //Get Text
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }

        // Getting UUID from NFC tag
        @Override
        protected void onPostExecute(String text) {
            super.onPostExecute(text);

            if(text != null) {
                mTextView.setText("Content: " + text);
                mUID = text;
                checkBTPermissions();
            } else {
                mTextView.setText("Text is null!");
            }
        }
        //******** NFC ********
    }
}
