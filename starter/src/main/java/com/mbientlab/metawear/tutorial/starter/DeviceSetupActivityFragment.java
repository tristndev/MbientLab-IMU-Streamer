/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.mbientlab.metawear.tutorial.starter;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.data.EulerAngles;
import com.mbientlab.metawear.data.Quaternion;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.SensorFusionBosch;

import java.util.Locale;

/**
 * A placeholder fragment containing a simple view.
 */
public class DeviceSetupActivityFragment extends Fragment implements ServiceConnection {
    public interface FragmentSettings {
        BluetoothDevice getBtDevice();
    }

    private MetaWearBoard metawear = null;
    private boolean boardReady= false;
    private FragmentSettings settings;

    private SensorFusionBosch sensorFusion;
    protected Route streamRoute = null;
    private String sensorFusionMode = "euler";

    private TextView[] valTextViews;


    public DeviceSetupActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity owner= getActivity();
        if (!(owner instanceof FragmentSettings)) {
            throw new ClassCastException("Owning activity must implement the FragmentSettings interface");
        }

        settings= (FragmentSettings) owner;
        owner.getApplicationContext().bindService(new Intent(owner, BtleService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ///< Unbind the service when the activity is destroyed
        getActivity().getApplicationContext().unbindService(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);
        return inflater.inflate(R.layout.fragment_device_setup, container, false);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        metawear = ((BtleService.LocalBinder) service).getMetaWearBoard(settings.getBtDevice());
        try {
            boardReady= true;
            boardReady();
        } catch (UnsupportedModuleException e) {
            unsupportedModule();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        valTextViews = new TextView[] {(TextView) view.findViewById(R.id.tableVal1),
                (TextView) view.findViewById(R.id.tableVal2),
                (TextView) view.findViewById(R.id.tableVal3),
                (TextView) view.findViewById(R.id.tableVal4)};

        if (boardReady) {
            try {
                boardReady();
            } catch (UnsupportedModuleException e) {
                unsupportedModule();
            }
        }
        updateTableHeaders(view, sensorFusionMode);

        // >> LED Switch
        ((Switch) view.findViewById(R.id.led_ctrl)).setOnCheckedChangeListener((buttonView, isChecked) -> {
            Led led= metawear.getModule(Led.class);
            if (isChecked) {
                led.editPattern(Led.Color.BLUE, Led.PatternPreset.SOLID)
                        .repeatCount(Led.PATTERN_REPEAT_INDEFINITELY)
                        .commit();
                led.play();
            } else {
                led.stop(true);
            }
        });

        // >> Test Button
        ((Button) view.findViewById(R.id.button_change_vals)).setOnClickListener(x -> {
            Log.d("Tristan","Button pressed");
            TextView tv = (TextView)view.findViewById(R.id.tableVal1);
            tv.setText("Changed");
        });

        // >> Radio Buttons (Mode selection)
        RadioGroup rGroup = (RadioGroup) view.findViewById(R.id.fusionModeSelectionGroup);

        // TODO: Handle initial selection?
        rGroup.setOnCheckedChangeListener((group, checkedId) -> {
            Log.d("Radio group", "checked ID: "+checkedId);
            RadioButton checkedRadioButton = (RadioButton) group.findViewById(checkedId);

            if (checkedId == R.id.radioButton1) {
                Log.d("Radio group", "Selected button: Euler");
                sensorFusionMode = "euler";
            } else {
                // if (checkedId == R.id.radioButton2) {
                Log.d("Radio group", "Selected button: Quaternion");
                sensorFusionMode = "quat";
            }
            updateTableHeaders(view, sensorFusionMode);
        } );

        // >> Button (Sampling)
        ((Switch) view.findViewById(R.id.sensorFusionSwitch)).setOnCheckedChangeListener((compoundButton, b) -> {
            // SensorFusion Setup
            sensorFusion = metawear.getModule(SensorFusionBosch.class);
            if (b) {
                Log.d("Sampling Button", "Enabled");
                setupSensorFusion();
                enableDisableRadioButtons(view, false);
            } else {
                Log.d("Sampling Button", "Disabled");
                enableDisableRadioButtons(view, true);
                cleanSensorFusion();
                if (streamRoute != null) {
                    streamRoute.remove();
                    streamRoute = null;
                }
            }
        });
    }

    private void enableDisableRadioButtons(View view, boolean enableDisable) {
        RadioButton[] rbs = {(RadioButton) view.findViewById(R.id.radioButton1),
                (RadioButton) view.findViewById(R.id.radioButton2)};
        for (RadioButton rb : rbs) {
            rb.setEnabled(enableDisable);
        }
    }

    private void updateTableHeaders(View view, String mode) {
        TextView[] tableColNames =  {
                (TextView)view.findViewById(R.id.tableHeader1),
                (TextView)view.findViewById(R.id.tableHeader2),
                (TextView)view.findViewById(R.id.tableHeader3),
                (TextView)view.findViewById(R.id.tableHeader4)
        };

        String[] headerNames = new String[4];
        if (mode.equals("euler")) {
            headerNames = new String[] {"heading", "pitch", "roll", "yaw"};
        } else {
            headerNames = new String[] {"w", "x", "y", "z"};
        }

        for (int i = 0; i<tableColNames.length; i++) {
            tableColNames[i].setText(headerNames[i]);
        }
    }



    private void updateTableVals(Float[] vals) {
        for (int i = 0; i<valTextViews.length; i++) {
            valTextViews[i].setText(String.format("%.2f", vals[i]));
        }
    }

    protected void setupSensorFusion() {
        sensorFusion.configure()
                .mode(SensorFusionBosch.Mode.NDOF)
                .accRange(SensorFusionBosch.AccRange.AR_16G)
                .gyroRange(SensorFusionBosch.GyroRange.GR_2000DPS)
                .commit();

        if (sensorFusionMode.equals("euler")) {
            sensorFusion.eulerAngles().addRouteAsync(source -> source.stream((data, env) -> {
                final EulerAngles angles = data.value(EulerAngles.class);
                //Log.d("Sensor Fusion [Euler]", angles.toString());
                updateTableVals(new Float[] {angles.heading(), angles.pitch(), angles.roll(), angles.yaw()});
            })).continueWith(task -> {
                streamRoute = task.getResult();
                sensorFusion.eulerAngles().start();
                sensorFusion.start();

                return null;
            });
        } else if (sensorFusionMode.equals("quat")) {
            sensorFusion.quaternion().addRouteAsync(source -> source.stream((data, env) -> {
                final Quaternion quaternion = data.value(Quaternion.class);
                //Log.d("Sensor Fusion [Quat]", quaternion.toString());
                updateTableVals(new Float[] {quaternion.w(), quaternion.x(), quaternion.y(), quaternion.z()});
            })).continueWith(task -> {
                streamRoute = task.getResult();
                sensorFusion.quaternion().start();
                sensorFusion.start();

                return null;
            });
        }
    }

    protected void cleanSensorFusion() {
        sensorFusion.stop();
    }

    protected void boardReady() throws UnsupportedModuleException {
        sensorFusion = metawear.getModuleOrThrow(SensorFusionBosch.class);
    }

    /**
     * Called when the app has reconnected to the board
     */
    public void reconnected() { }

    private void unsupportedModule() {
        /**
         *
        new AlertDialog.Builder(getActivity()).setTitle(R.string.title_error)
                .setMessage(String.format("%s %s", getContext().getString(sensorResId), getActivity().getString(R.string.error_unsupported_module)))
                .setCancelable(false)
                .setPositiveButton(R.string.label_ok, (dialog, id) -> enableDisableViewGroup((ViewGroup) getView(), false))
                .create()
                .show();
         */
        Log.e("Unsupported Module", "Selected module is not supported by your board!");
    }
}
