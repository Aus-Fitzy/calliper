package com.example.dean.calliper.app;


import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.MetaWearBleService;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.module.Gpio;
import com.mbientlab.metawear.module.Led;


/**
 * A simple {@link Fragment} subclass.
 */
public class MeasureFragment extends Fragment implements ServiceConnection {

    private static final String TAG = "MeasureFragment";
    private MeasureFragmentListener fragmentListener;
    private MetaWearBoard mwBoard;
    private Gpio gpioModule;
    private Led LEDModule;

    public MeasureFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Activity activity = (Activity) context;
        activity.getApplicationContext().bindService(new Intent(activity, MetaWearBleService.class), this, Context.BIND_AUTO_CREATE);
        fragmentListener = (MeasureFragmentListener) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_measure, container, false);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mwBoard = ((MetaWearBleService.LocalBinder) service).getMetaWearBoard(fragmentListener.getBtDevice());
        try {
            gpioModule = mwBoard.getModule(Gpio.class);
            LEDModule = mwBoard.getModule(Led.class);

            LEDModule.configureColorChannel(Led.ColorChannel.GREEN)
                    .setRiseTime((short) 500).setPulseDuration((short) 1000)
                    .setRepeatCount((byte) 1).setHighTime((short) 500)
                    .setHighIntensity((byte) 8).setLowIntensity((byte) 1)
                    .commit();
            LEDModule.play(true);

        } catch (UnsupportedModuleException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.i(TAG, "Service Disconnected");
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Button button = (Button) view.findViewById(R.id.button_measurement);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View innerView) {
                onMeasureClicked(view);
            }
        });
    }

    public void onMeasureClicked(View v) {

        final TextView tvADC = (TextView) v.findViewById(R.id.field_measurement_adc);
        final TextView tvMM = (TextView) v.findViewById(R.id.field_measurement_mm);

        gpioModule.routeData().fromAnalogIn(MeasureActivity.pinADC, Gpio.AnalogReadMode.ADC).stream("pin0_adc").commit().onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
            @Override
            public void success(RouteManager result) {
                result.subscribe("pin0_adc", new RouteManager.MessageHandler() {
                    @Override
                    public void process(Message message) {
                        //scale the reading to mm, 0-33mm range from cal0mm to cal33mm
                        //cal Zero approaches  1023, cal 33mm approaches 0.
                        int cal33 = fragmentListener.getCal33mm();
                        int cal0 = fragmentListener.getCal0mm();
                        Short signal = message.getData(Short.class);

                        float ratio = (cal0 - signal) / (float) (cal0 - cal33);

                        Log.i(TAG, String.format("Cal 0:\t%d\tcal 50:\t%d\tSignal:\t%d\tRatio:\t%f", cal0, cal33, signal, ratio));

                        float result = (ratio * 33);

                        tvADC.setText(String.format("%d ADC", signal));
                        tvMM.setText(String.format("%.1f mm", result));
                        Log.i(TAG, "ADC" + message.getData(Short.class));
                        fragmentListener.onMeasureSkin(result);
                    }
                });
            }
        });


        gpioModule.setDigitalOut(MeasureActivity.pinSource);
        LEDModule.play(true);

        gpioModule.readAnalogIn(MeasureActivity.pinADC, Gpio.AnalogReadMode.ADC);

        LEDModule.stop(false);
        gpioModule.clearDigitalOut(MeasureActivity.pinSource);
    }

    public interface MeasureFragmentListener {
        BluetoothDevice getBtDevice();

        int getCal33mm();

        int getCal0mm();

        void onMeasureSkin(float result);
    }
}
