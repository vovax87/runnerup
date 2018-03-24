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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.SensorEventListener;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.runnerup.R;
import org.runnerup.hr.HRDeviceRef;
import org.runnerup.hr.HRManager;
import org.runnerup.hr.HRProvider;

/**
 * Created by jonas on 12/11/14.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class TrackerHRM2 extends DefaultTrackerComponent {

    private final Handler handler = new Handler();
    private HRProvider hrProvider;
    private SensorEventListener hrEventListener;

    public static final String NAME = "HRM";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ResultCode onConnecting(final Callback callback, final Context context) {
        Resources res = context.getResources();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final PackageManager PM= context.getPackageManager();
        boolean hr = PM.hasSystemFeature(PackageManager.FEATURE_SENSOR_HEART_RATE);
        Log.e("SENSOR_HEART_RATE",hr+"");
        if (hr){
            return ResultCode.RESULT_NOT_SUPPORTED;

        }
//       hrEventListener
        hrProvider = HRManager.getHRProvider(context, PackageManager.FEATURE_SENSOR_HEART_RATE);
        if (hrProvider != null) {
            hrProvider.open(handler, new HRProvider.HRClient() {
                @Override
                public void onOpenResult(boolean ok) {


                    /* return RESULT_OK and connect in background */
                    // TODO: make it possible to make HRM mandatory i.e don't connect in background
                    callback.run(TrackerHRM2.this, ResultCode.RESULT_OK);

                    hrProvider.connect(HRDeviceRef.create(PackageManager.FEATURE_SENSOR_HEART_RATE, PM.getNameForUid(0), ""));
                }

                @Override
                public void onScanResult(HRDeviceRef device) {
                }

                @Override
                public void onConnectResult(boolean connectOK) {

                }

                @Override
                public void onDisconnectResult(boolean disconnectOK) {
                }

                @Override
                public void onCloseResult(boolean closeOK) {
                }

                @Override
                public void log(HRProvider src, String msg) {
                }
            });
        }
        return ResultCode.RESULT_PENDING;
    }

    @Override
    public boolean isConnected() {
        if (hrProvider == null)
            return false;
        return hrProvider.isConnected();
    }

    @Override
    public ResultCode onEnd(Callback callback, Context context) {
        if (hrProvider != null) {
            hrProvider.disconnect();
            hrProvider.close();
            hrProvider = null;
        }
        return ResultCode.RESULT_OK;
    }

    public HRProvider getHrProvider() {
        return hrProvider;
    }
}
