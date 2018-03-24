/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.tracker.component;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import org.runnerup.hr.HRProvider;

import static android.content.Context.SENSOR_SERVICE;

/**
 * Created by jonas on 12/11/14.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class TrackerHRM extends DefaultTrackerComponent implements SensorEventListener {

    private final Handler handler = new Handler();
    private HRProvider hrProvider;

    public static final String NAME = "HRM";
    private boolean hrIsEnable = false;
    private int hrValue = 0;
    private SensorManager mSensorManager;
    private Sensor mHeartRateSensor;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ResultCode onConnecting(final Callback callback, final Context context) {
        final PackageManager PM= context.getPackageManager();
         hrIsEnable = PM.hasSystemFeature(PackageManager.FEATURE_SENSOR_HEART_RATE);
        Log.e("SENSOR_HEART_RATE",hrIsEnable+"");
        if (hrIsEnable){

            mSensorManager = ((SensorManager)context.getSystemService(SENSOR_SERVICE));
            mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);

            return ResultCode.RESULT_OK;

        }
        return ResultCode.RESULT_NOT_SUPPORTED;

    }

    @Override
    public boolean isConnected() {

        return hrIsEnable;
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {

        if (mSensorManager != null){
            mSensorManager.unregisterListener(this);
        }
        return ResultCode.RESULT_OK;
    }

    public int getHrValue() {
        return hrValue;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            hrValue = (int)sensorEvent.values[0];
//            Log.e("TYPE_HEART_RATE", sensorEvent.values[0]+"");

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
