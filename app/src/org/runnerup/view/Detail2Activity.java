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
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import com.mapbox.mapboxsdk.maps.MapView;

import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.content.ActivityProvider;
import org.runnerup.db.ActivityCleaner;
import org.runnerup.db.DBHelper;
import org.runnerup.export.SyncManager;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.Synchronizer.Feature;
import org.runnerup.util.Bitfield;
import org.runnerup.util.Formatter;
import org.runnerup.util.GraphWrapper;
import org.runnerup.util.MapWrapper;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Intensity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import static org.runnerup.content.ActivityProvider.GPX_MIME;
import static org.runnerup.content.ActivityProvider.TCX_MIME;

@TargetApi(Build.VERSION_CODES.FROYO)
public class Detail2Activity extends AppCompatActivity implements Constants {

    private long mID = 0;
    private SQLiteDatabase mDB = null;
    private final HashSet<String> pendingSynchronizers = new HashSet<>();
    private final HashSet<String> alreadySynched = new HashSet<>();
    private final Map<String,String> synchedExternalId = new HashMap<>();

    private boolean lapHrPresent = false;
    private ContentValues[] laps = null;
    private final ArrayList<ContentValues> reports = new ArrayList<>();
    private final ArrayList<BaseAdapter> adapters = new ArrayList<>(2);

    private int mode; // 0 == save 1 == details
    private final static int MODE_SAVE = 0;
    private final static int MODE_DETAILS = 1;
    private boolean edit = false;
    private boolean uploading = false;

    private Button saveButton = null;
    private Button resumeButton = null;

    private TextView activityTime = null;
    private TextView activityPace = null;
    private TextView activityDistance = null;




    private SyncManager syncManager = null;
    private Formatter formatter = null;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapWrapper.start(this);
        setContentView(R.layout.detail2);
        WidgetUtil.addLegacyOverflowButton(getWindow());

        Intent intent = getIntent();
        mID = intent.getLongExtra("ID", -1);
        String mode = intent.getStringExtra("mode");

        mDB = DBHelper.getReadableDatabase(this);
        syncManager = new SyncManager(this);
        formatter = new Formatter(this);

        if (mode.contentEquals("save")) {
            this.mode = MODE_SAVE;
        } else if (mode.contentEquals("details")) {
            this.mode = MODE_DETAILS;
        } else {
            if (BuildConfig.DEBUG) {
                throw new AssertionError();
            }
        }

        saveButton = (Button) findViewById(R.id.save_button);
        resumeButton = (Button) findViewById(R.id.resume_button);
        activityTime = (TextView) findViewById(R.id.activity_time);
        activityDistance = (TextView) findViewById(R.id.activity_distance);
        activityPace = (TextView) findViewById(R.id.activity_pace);




        saveButton.setOnClickListener(saveButtonClick);
        if (this.mode == MODE_SAVE) {
            resumeButton.setOnClickListener(resumeButtonClick);
            setEdit(true);
        } else if (this.mode == MODE_DETAILS) {
            resumeButton.setVisibility(View.GONE);
            setEdit(false);
        }

        fillHeaderData();
        requery();






    }

    private void setEdit(boolean value) {
        edit = value;
        saveButton.setEnabled(value);

    }






    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        DBHelper.closeDB(mDB);
        syncManager.close();
    }

    private void requery() {
        {
            /**
             * Laps
             */
            String[] from = new String[]{
                    "_id", DB.LAP.LAP, DB.LAP.INTENSITY,
                    DB.LAP.TIME, DB.LAP.DISTANCE, DB.LAP.PLANNED_TIME,
                    DB.LAP.PLANNED_DISTANCE, DB.LAP.PLANNED_PACE, DB.LAP.AVG_HR
            };

            Cursor c = mDB.query(DB.LAP.TABLE, from, DB.LAP.ACTIVITY + " == " + mID,
                    null, null, null, "_id", null);

            laps = DBHelper.toArray(c);

            for (ContentValues lap:laps){
                int hr = lap.containsKey(DB.LAP.AVG_HR) ? lap
                        .getAsInteger(DB.LAP.AVG_HR) : 0;
                Log.e("lap",lap.toString()+"vova test "+hr);

            }

            c.close();
            lapHrPresent = false;
            for (ContentValues v : laps) {
                if (v.containsKey(DB.LAP.AVG_HR) && v.getAsInteger(DB.LAP.AVG_HR) > 0) {
                    lapHrPresent = true;
                    break;
                }
            }

        }

        {
            /**
             * Accounts/reports
             */
            String sql = "SELECT DISTINCT "
                    + "  acc._id, "
                    + ("  acc." + DB.ACCOUNT.NAME + ", ")
                    + ("  acc." + DB.ACCOUNT.FLAGS + ", ")
                    + ("  acc." + DB.ACCOUNT.AUTH_CONFIG + ", ")
                    + ("  rep._id as repid, ")
                    + ("  rep." + DB.EXPORT.ACCOUNT + ", ")
                    + ("  rep." + DB.EXPORT.ACTIVITY + ", ")
                    + ("  rep." + DB.EXPORT.EXTERNAL_ID + ", ")
                    + ("  rep." + DB.EXPORT.STATUS)
                    + (" FROM " + DB.ACCOUNT.TABLE + " acc ")
                    + (" LEFT OUTER JOIN " + DB.EXPORT.TABLE + " rep ")
                    + (" ON ( acc._id = rep." + DB.EXPORT.ACCOUNT)
                    + ("     AND rep." + DB.EXPORT.ACTIVITY + " = "
                    + mID + " )")
                    //Note: Show all configured accounts (also those are not currently enabled)
                    //Uploaded but removed accounts are not displayed
                    + (" WHERE acc." + DB.ACCOUNT.AUTH_CONFIG + " is not null");

            Cursor c = mDB.rawQuery(sql, null);
            alreadySynched.clear();
            synchedExternalId.clear();
            pendingSynchronizers.clear();
            reports.clear();
            if (c.moveToFirst()) {
                do {
                    ContentValues tmp = DBHelper.get(c);
                    Synchronizer synchronizer = syncManager.add(tmp);
                    if (!synchronizer.checkSupport(Feature.UPLOAD)) {
                        continue;
                    }

                    reports.add(tmp);
                    if (tmp.containsKey("repid")) {
                        alreadySynched.add(tmp.getAsString(DB.ACCOUNT.NAME));
                        if (tmp.containsKey(DB.EXPORT.STATUS) && tmp.getAsInteger(DB.EXPORT.STATUS) == Synchronizer.ExternalIdStatus.getInt(Synchronizer.ExternalIdStatus.OK)) {
                            synchedExternalId.put(tmp.getAsString(DB.ACCOUNT.NAME), tmp.getAsString(DB.EXPORT.EXTERNAL_ID));
                        }
                    } else if (tmp.containsKey(DB.ACCOUNT.FLAGS)
                            && Bitfield.test(tmp.getAsLong(DB.ACCOUNT.FLAGS),
                            DB.ACCOUNT.FLAG_UPLOAD)) {
                        pendingSynchronizers.add(tmp.getAsString(DB.ACCOUNT.NAME));
                    }
                } while (c.moveToNext());
            }
            c.close();
        }



        for (BaseAdapter a : adapters) {
            a.notifyDataSetChanged();
        }
    }

    private void fillHeaderData() {
        // Fields from the database (projection)
        // Must include the _id column for the adapter to work
        String[] from = new String[]{
                DB.ACTIVITY.START_TIME,
                DB.ACTIVITY.DISTANCE, DB.ACTIVITY.TIME, DB.ACTIVITY.COMMENT,
                DB.ACTIVITY.SPORT
        };

        Cursor c = mDB.query(DB.ACTIVITY.TABLE, from, "_id == " + mID, null,
                null, null, null, null);
        c.moveToFirst();
        ContentValues tmp = DBHelper.get(c);
        c.close();

        if (tmp.containsKey(DB.ACTIVITY.START_TIME)) {
            long st = tmp.getAsLong(DB.ACTIVITY.START_TIME);
            setTitle("RunnerUp - " + formatter.formatDateTime(st));
        }
        float d = 0;
        if (tmp.containsKey(DB.ACTIVITY.DISTANCE)) {
            d = tmp.getAsFloat(DB.ACTIVITY.DISTANCE);
            activityDistance.setText(formatter.formatDistance(Formatter.Format.TXT_LONG, (long) d));
        } else {
            activityDistance.setText("");
        }

        float t = 0;
        if (tmp.containsKey(DB.ACTIVITY.TIME)) {
            t = tmp.getAsFloat(DB.ACTIVITY.TIME);
            activityTime.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, (long) t));
        } else {
            activityTime.setText("");
        }

        if (d != 0 && t != 0) {
            activityPace.setText(formatter.formatPace(Formatter.Format.TXT_LONG, t / d));
        } else {
            activityPace.setText("");
        }


    }

    private class ViewHolderLapList {
        private TextView tv0;
        private TextView tv1;
        private TextView tv2;
        private TextView tv3;
        private TextView tv4;
        private TextView tvHr;
    }

    private class LapListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return laps.length;
        }

        @Override
        public Object getItem(int position) {
            return laps[position];
        }

        @Override
        public long getItemId(int position) {
            return laps[position].getAsLong("_id");
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            ViewHolderLapList viewHolder;

            if (view == null) {
                viewHolder = new ViewHolderLapList();
                LayoutInflater inflater = LayoutInflater.from(Detail2Activity.this);
                view = inflater.inflate(R.layout.laplist_row, parent, false);


                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolderLapList) view.getTag();
            }
            int i = laps[position].getAsInteger(DB.LAP.INTENSITY);
            Intensity intensity = Intensity.values()[i];
            switch (intensity) {
                case ACTIVE:
                    viewHolder.tv0.setText("");
                    break;
                case COOLDOWN:
                case RESTING:
                case RECOVERY:
                case WARMUP:
                case REPEAT:
                    viewHolder.tv0.setText("(" + getResources().getString(intensity.getTextId()) + ")");
                default:
                    break;

            }
            viewHolder.tv1.setText(laps[position].getAsString("_id"));
            float d = laps[position].containsKey(DB.LAP.DISTANCE) ? laps[position]
                    .getAsFloat(DB.LAP.DISTANCE) : 0;
            viewHolder.tv2.setText(formatter.formatDistance(Formatter.Format.TXT_LONG, (long) d));
            long t = laps[position].containsKey(DB.LAP.TIME) ? laps[position]
                    .getAsLong(DB.LAP.TIME) : 0;
            viewHolder.tv3.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, t));
            if (t != 0 && d != 0) {
                viewHolder.tv4.setText(formatter.formatPace(Formatter.Format.TXT_LONG, t / d));
            } else {
                viewHolder.tv4.setText("");
            }
            int hr = laps[position].containsKey(DB.LAP.AVG_HR) ? laps[position]
                    .getAsInteger(DB.LAP.AVG_HR) : 0;
            if (hr > 0) {
                viewHolder.tvHr.setVisibility(View.VISIBLE);
                viewHolder.tvHr.setText(formatter.formatHeartRate(Formatter.Format.TXT_LONG, hr) + " bpm");
            } else if (lapHrPresent) {
                viewHolder.tvHr.setVisibility(View.INVISIBLE);
            } else {
                viewHolder.tvHr.setVisibility(View.GONE);
            }

            return view;
        }
    }



    private void saveActivity() {
        ContentValues tmp = new ContentValues();
        String whereArgs[] = {
                Long.toString(mID)
        };
//        mDB.update(DB.ACTIVITY.TABLE, tmp, "_id = ?", whereArgs);
    }

    private final OnLongClickListener clearUploadClick = new OnLongClickListener() {

        @Override
        public boolean onLongClick(View arg0) {
            final String name = (String) arg0.getTag();
            AlertDialog.Builder builder = new AlertDialog.Builder(Detail2Activity.this);
            builder.setTitle("Clear upload for " + name);
            builder.setMessage(getString(R.string.Are_you_sure));
            builder.setPositiveButton(getString(R.string.Yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            syncManager.clearUpload(name, mID);
                            requery();
                        }
                    });
            builder.setNegativeButton(getString(R.string.No),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                        }

                    });
            builder.show();
            return false;
        }

    };

    //Note: onClick set in reportlist_row.xml
    public void onClickAccountName(View arg0) {
        final String name = (String) arg0.getTag();
        if (synchedExternalId.containsKey(name)) {
            String url = syncManager.getSynchronizerByName(name).getActivityUrl(synchedExternalId.get(name));
            if (url != null) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            }
        }
    }

    private final OnClickListener saveButtonClick = new OnClickListener() {
        public void onClick(View v) {
            saveActivity();

            uploading = true;
            syncManager.startUploading(new SyncManager.Callback() {
                @Override
                public void run(String synchronizerName, Synchronizer.Status status) {
                    uploading = false;
                    Detail2Activity.this.setResult(RESULT_OK);
                    Detail2Activity.this.finish();
                }
            }, pendingSynchronizers, mID);
        }
    };

    private final OnClickListener discardButtonClick = new OnClickListener() {
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(Detail2Activity.this);
            builder.setTitle(getString(R.string.Discard_activity));
            builder.setMessage(getString(R.string.Are_you_sure));
            builder.setPositiveButton(getString(R.string.Yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            Detail2Activity.this.setResult(RESULT_CANCELED);
                            Detail2Activity.this.finish();
                        }
                    });
            builder.setNegativeButton(getString(R.string.No),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                        }

                    });
            builder.show();
        }
    };

    @Override
    public void onBackPressed() {
        if (uploading) {
            /**
             * Ignore while uploading
             */
            return;
        }
        if (mode == MODE_SAVE) {
            resumeButtonClick.onClick(resumeButton);
        } else {
            super.onBackPressed();
        }
    }

    private final OnClickListener resumeButtonClick = new OnClickListener() {
        public void onClick(View v) {
            Detail2Activity.this.setResult(RESULT_FIRST_USER);
            Detail2Activity.this.finish();
        }
    };

    private final OnClickListener uploadButtonClick = new OnClickListener() {
        public void onClick(View v) {
            uploading = true;
            syncManager.startUploading(new SyncManager.Callback() {
                @Override
                public void run(String synchronizerName, Synchronizer.Status status) {
                    uploading = false;
                    requery();
                }
            }, pendingSynchronizers, mID);
        }
    };

    private final OnCheckedChangeListener onSendChecked = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
            final String name = (String) arg0.getTag();
            if (alreadySynched.contains(name)) {
                // Only accept long clicks
                arg0.setChecked(true);
            } else {
                if (arg1) {
                    pendingSynchronizers.add((String) arg0.getTag());
                } else {
                    pendingSynchronizers.remove(arg0.getTag());
                }

            }
        }
    };

    private final OnClickListener deleteButtonClick = new OnClickListener() {
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    Detail2Activity.this);
            builder.setTitle(getString(R.string.Delete_activity));
            builder.setMessage(getString(R.string.Are_you_sure));
            builder.setPositiveButton(getString(R.string.Yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            DBHelper.deleteActivity(mDB, mID);
                            dialog.dismiss();
                            Detail2Activity.this.setResult(RESULT_OK);
                            Detail2Activity.this.finish();
                        }
                    });
            builder.setNegativeButton(getString(R.string.No),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing but close the dialog
                            dialog.dismiss();
                        }

                    });
            builder.show();
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SyncManager.CONFIGURE_REQUEST) {
            syncManager.onActivityResult(requestCode, resultCode, data);
        }
        requery();
    }

    private void shareActivity() {
        final int which[] = {
            1 //TODO preselect tcx - choice should be remembered
        };
        final CharSequence items[] = {
                "gpx", "tcx" /* "nike+xml" */
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.Share_activity));
        builder.setPositiveButton(getString(R.string.OK),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int w) {
                        if (which[0] == -1) {
                            dialog.dismiss();
                            return;
                        }

                        final Activity context = Detail2Activity.this;
                        final CharSequence fmt = items[which[0]];
                        final Intent intent = new Intent(Intent.ACTION_SEND);

                        if (fmt.equals("tcx")) {
                            intent.setType(TCX_MIME);
                        } else {
                            intent.setType(GPX_MIME);
                        }
                        //Use of content:// (or STREAM?) instead of file:// is not supported in ES and other apps
                        //Solid Explorer File Manager works though
                        Uri uri = Uri.parse("content://" + ActivityProvider.AUTHORITY + "/" + fmt
                                + "/" + mID
                                + "/" + String.format(Locale.getDefault(), "RunnerUp_%04d.%s", mID, fmt));
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        context.startActivity(Intent.createChooser(intent, getString(R.string.Share_activity)));
                    }
                });
        builder.setNegativeButton(getString(R.string.Cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing but close the dialog
                        dialog.dismiss();
                    }

                });
        builder.setSingleChoiceItems(items, which[0], new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int w) {
                which[0] = w;
            }
        });
        builder.show();
    }
}
