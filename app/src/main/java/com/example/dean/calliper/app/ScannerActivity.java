package com.example.dean.calliper.app;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;

import com.mbientlab.bletoolbox.scanner.BleScannerFragment;
import com.mbientlab.bletoolbox.scanner.BleScannerFragment.ScannerCommunicationBus;
import com.mbientlab.bletoolbox.scanner.BleScannerFragment.ScannerListener;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;

import java.util.UUID;

public class ScannerActivity extends AppCompatActivity implements ScannerCommunicationBus, ScannerListener, ServiceConnection {
    public static final int REQUEST_START_APP= 1;
    private final static UUID[] serviceUuids;

    static {
        serviceUuids= new UUID[] {
                MetaWearBoard.METAWEAR_SERVICE_UUID,
                MetaWearBoard.METABOOT_SERVICE_UUID
        };
    }

    private MetaWearBleService.LocalBinder serviceBinder;
    private MetaWearBoard mwBoard;
    private BleScannerFragment scannerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        getApplicationContext().bindService(new Intent(this, MetaWearBleService.class), this, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ///< Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_START_APP:
                scannerFragment.startBleScan();
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDeviceSelected(final BluetoothDevice btDevice) {
        mwBoard= serviceBinder.getMetaWearBoard(btDevice);

        final ProgressDialog connectDialog = new ProgressDialog(this);
        connectDialog.setTitle(getString(R.string.title_connecting));
        connectDialog.setMessage(getString(R.string.message_wait));
        connectDialog.setCancelable(false);
        connectDialog.setCanceledOnTouchOutside(false);
        connectDialog.setIndeterminate(true);
        connectDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(com.example.dean.calliper.app.R.string.label_abort), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mwBoard.disconnect();
            }
        });
        connectDialog.show();

        mwBoard.setConnectionStateHandler(new MetaWearBoard.ConnectionStateHandler() {
            @Override
            public void connected() {
                connectDialog.dismiss();
                
                Intent messureActivityIntent = new Intent(ScannerActivity.this, MeasureActivity.class);
                messureActivityIntent.putExtra(MeasureActivity.EXTRA_BT_DEVICE, btDevice);
                startActivityForResult(messureActivityIntent, REQUEST_START_APP);
            }

            @Override
            public void disconnected() {
                mwBoard.connect();
            }

            @Override
            public void failure(int status, Throwable error) {
                mwBoard.connect();
            }
        });
        mwBoard.connect();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        serviceBinder = (MetaWearBleService.LocalBinder) iBinder;
        serviceBinder.executeOnUiThread();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }

    @Override
    public UUID[] getFilterServiceUuids() {
        return serviceUuids;
    }

    @Override
    public long getScanDuration() {
        return 10000L;
    }

    @Override
    public void retrieveFragmentReference(BleScannerFragment scannerFragment) {
        this.scannerFragment= scannerFragment;
    }
}
