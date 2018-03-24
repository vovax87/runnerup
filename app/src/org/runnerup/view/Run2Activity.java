/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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

package org.runnerup.view;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.util.Formatter;
import org.runnerup.util.HRZones;
import org.runnerup.util.TickListener;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Intensity;
import org.runnerup.workout.Scope;
import org.runnerup.workout.Step;
import org.runnerup.workout.Workout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@TargetApi(Build.VERSION_CODES.FROYO)
public class Run2Activity extends Activity implements TickListener {
    Workout workout = null;
    Tracker mTracker = null;
    final Handler handler = new Handler();

    Button stopButton = null;
    TextView activityTime = null;
    TextView activityDistance = null;
    TextView activityPace = null;


    Step currentStep = null;
    Formatter formatter = null;
    private TextView activityHr;

    class WorkoutRow {
        Step step = null;
        ContentValues lap = null;
        public int level;
    }

    boolean simpleWorkout;
    HRZones hrZones = null;

    /** Called when the activity is first created. */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.run2);
        formatter = new Formatter(this);
        hrZones = new HRZones(this);

        stopButton = (Button) findViewById(R.id.stop_button);
        stopButton.setOnClickListener(stopButtonClick);

        activityTime = (TextView) findViewById(R.id.activity_time);
        activityDistance = (TextView) findViewById(R.id.activity_distance);
        activityPace = (TextView) findViewById(R.id.activity_pace);
        activityHr = (TextView) findViewById(R.id.activity_hr);


        bindGpsTracker();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.e(getClass().getName(), "onConfigurationChange => do NOTHING!!");
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindGpsTracker();
        stopTimer();

    }

    void onGpsTrackerBound() {
        if (mTracker == null) {
            // should not happen
            return;
        }

        if (mTracker.getWorkout() == null) {
            // should not happen
            return;
        }

        workout = mTracker.getWorkout();


        startTimer();

        simpleWorkout = false;

    }



    Timer timer = null;

    void startTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Run2Activity.this.handler.post(new Runnable() {
                    public void run() {
                        Run2Activity.this.onTick();
                    }
                });
            }
        }, 0, 500);
    }

    void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    Location l = null;

    public void onTick() {
        if (workout != null) {
            workout.onTick();
            updateView();

            if (mTracker != null) {
                Location l2 = mTracker.getLastKnownLocation();
                if (l2 != null && !l2.equals(l)) {
                    l = l2;
                }
            }
        }
    }

    final OnClickListener stopButtonClick = new OnClickListener() {
        public void onClick(View v) {
            if (timer != null) {
                workout.onStop(workout);
                stopTimer(); // set timer=null;
                mTracker.stopForeground(true); // remove notification
                Intent intent = new Intent(Run2Activity.this, Detail2Activity.class);
                /**
                 * The same activity is used to show details and to save
                 * activity they show almost the same information
                 */
                intent.putExtra("mode", "save");
                intent.putExtra("ID", mTracker.getActivityId());
                Run2Activity.this.startActivityForResult(intent, workout.isPaused() ? 1 : 0);
            }
        }
    };

    @Override
    public void onBackPressed() {
        boolean ignore_back = true; // atleast magnus belives that this is
                                    // better...
        if (ignore_back == false) {
            stopButtonClick.onClick(stopButton);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (workout == null) {
            // "should not happen"
            finish();
            return;
        }
        if (resultCode == Activity.RESULT_OK) {
            /**
             * they saved
             */
            workout.onComplete(Scope.ACTIVITY, workout);
            workout.onSave();
            mTracker = null;
            finish();
            return;
        } else if (resultCode == Activity.RESULT_CANCELED) {
            /**
             * they discarded
             */
            workout.onComplete(Scope.ACTIVITY, workout);
            workout.onDiscard();
            mTracker = null;
            finish();
            return;
        } else if (resultCode == Activity.RESULT_FIRST_USER) {
            startTimer();
            if (requestCode == 0) {
                workout.onResume(workout);
            } else {
                // we were paused before stopButtonClick...don't resume
            }
        } else {
            if (BuildConfig.DEBUG) { throw new AssertionError(); }
        }
    }





    final OnClickListener newLapButtonClick = new OnClickListener() {
        public void onClick(View v) {
            workout.onNewLap();
        }
    };

    final OnClickListener nextStepButtonClick = new OnClickListener() {
        public void onClick(View v) {
            workout.onNextStep();
        }
    };

    private void updateView() {
        double ad = workout.getDistance(Scope.ACTIVITY);
        double at = workout.getTime(Scope.ACTIVITY);
        double ap = workout.getPace(Scope.ACTIVITY);
        activityTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_LONG, Math.round(at)));
        activityDistance.setText(formatter.formatDistance(Formatter.Format.TXT_SHORT, Math.round(ad)));
        activityPace.setText(formatter.formatPace(Formatter.Format.TXT_SHORT, ap));

        double ahr = workout.getHeartRate(Scope.ACTIVITY);
        double ld = workout.getDistance(Scope.LAP);
        double lt = workout.getTime(Scope.LAP);
        double lp = workout.getPace(Scope.LAP);

        double lhr = workout.getHeartRate(Scope.LAP);
        double id = workout.getDistance(Scope.STEP);
        double it = workout.getTime(Scope.STEP);
        double ip = workout.getPace(Scope.STEP);

        double ihr = workout.getHeartRate(Scope.STEP);
        if (mTracker.isComponentConnected(TrackerHRM.NAME)) {

            activityHr.setText(formatter.formatHeartRate(Formatter.Format.TXT_SHORT, ahr));
            activityHr.setVisibility(View.VISIBLE);

        } else {
            activityHr.setVisibility(View.GONE);

        }
        Step curr = workout.getCurrentStep();

    }

    private int getPosition(ArrayList<WorkoutRow> workoutRows,
            Step currentActivity) {
        for (int i = 0; i < workoutRows.size(); i++) {
            if (workoutRows.get(i).step == currentActivity)
                return i;
        }
        return 0;
    }

    private boolean mIsBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            if (mTracker == null) {
                mTracker = ((Tracker.LocalBinder) service).getService();
                // Tell the user about this for our demo.
                Run2Activity.this.onGpsTrackerBound();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mIsBound = false;
            mTracker = null;
        }
    };

    void bindGpsTracker() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        getApplicationContext().bindService(new Intent(this, Tracker.class),
                mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void unbindGpsTracker() {
        if (mIsBound) {
            // Detach our existing connection.
            getApplicationContext().unbindService(mConnection);
            mIsBound = false;
        }
    }

    class WorkoutAdapter extends BaseAdapter {

        ArrayList<WorkoutRow> rows = null;

        WorkoutAdapter(ArrayList<WorkoutRow> workoutRows) {
            this.rows = workoutRows;
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            WorkoutRow tmp = rows.get(position);
            if (tmp.step != null)
            {
                return getWorkoutRow(tmp.step, tmp.level, convertView, parent);
            }
            else
            {
                return getLapRow(tmp.lap, convertView, parent);
            }
        }

        private View getWorkoutRow(Step step, int level, View convertView,
                ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(Run2Activity.this);
            View view = inflater.inflate(R.layout.workout_row, parent, false);
            TextView intensity = (TextView) view.findViewById(R.id.step_intensity);
            TextView durationType = (TextView) view.findViewById(R.id.step_duration_type);
            TextView durationValue = (TextView) view.findViewById(R.id.step_duration_value);
            TextView targetPace = (TextView) view.findViewById(R.id.step_pace);
            intensity.setPadding(level * 10, 0, 0, 0);
            intensity.setText(getResources().getText(step.getIntensity().getTextId()));
            if (step.getDurationType() != null) {
                durationType.setText(getResources().getText(step.getDurationType().getTextId()));
                durationValue.setText(formatter.format(Formatter.Format.TXT_LONG, step.getDurationType(),
                        step.getDurationValue()));
            } else {
                durationType.setText("");
                durationValue.setText("");
            }
            if (currentStep == step) {
                view.setBackgroundResource(android.R.color.background_light);
            } else {
                view.setBackgroundResource(android.R.color.black);
            }

            if (step.getTargetType() == null) {
                targetPace.setText("");
            } else {
                double minValue = step.getTargetValue().minValue;
                double maxValue = step.getTargetValue().maxValue;
                if (minValue == maxValue) {
                    targetPace.setText(formatter.format(Formatter.Format.TXT_SHORT, step.getTargetType(),
                            minValue));
                } else {
                    targetPace.setText(formatter.format(Formatter.Format.TXT_SHORT, step.getTargetType(),
                            minValue) + "-" +
                            formatter.format(Formatter.Format.TXT_SHORT, step.getTargetType(), maxValue));
                }
            }

            if (step.getRepeatCount() > 0) {
                if (step.getCurrentRepeat() == step.getRepeatCount()) {
                    durationValue.setText(getString(R.string.Finished));
                } else {
                    durationValue.setText("" + (step.getCurrentRepeat() + 1) + "/"
                            + step.getRepeatCount());
                }
            }
            return view;
        }

        private View getLapRow(ContentValues tmp, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(Run2Activity.this);
            View view = inflater.inflate(R.layout.laplist_row, parent, false);
            return view;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
