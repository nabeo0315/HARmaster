package com.example.nabeo.harmaster;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.os.FileObserver;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.beardedhen.androidbootstrap.AwesomeTextView;
import com.beardedhen.androidbootstrap.BootstrapButton;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.Picasso;

import org.w3c.dom.Text;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private Context mContext;
    private BootstrapButton mStartButton, mStopButton;
    private static SensorDataCollector mSensorDataCollector;
    private static AudioProc mAudioProc;
    private TextView mStatusTv;
    private TextView mStationTv;
    private AwesomeTextView mainTv;

    public final static String ROOT_DIR = Environment.getExternalStorageDirectory().toString();
    private final static String HARMASTER_PATH = ROOT_DIR +"/HARmaster";
    private final static String OUTPUT_PATH = HARMASTER_PATH + "/output.txt";
    private final static String TEMPFILE_PATH = HARMASTER_PATH + "/temp_state.txt";

    private LocationManager mLocationManager;
    private String mGpsState;
    private LocationListener mLocationListener;
    private static double gpsSpeedinKilo = 0;
    private int gpsSpeedCounter = 0;
    private double gpsSpeedTemp = 0;
    private double mLongitude = 0;
    private double mLatitude = 0;
    private Timer timer;

    private static boolean recordFlag = false;
    private static boolean mInStation = false;
    private static String nowState = "", tempState = "Stop", state = "Stop", audioState, preState = "", markovState="Stop";
    private static String[] stateArray, correctedStateArray;
    private static int count = 0, activityCount = 0, vehicleCount = 0, wifiCount = 0, awayCount = 0, stateCount = 0;
    private static StateCounter mStateCounter;

    private RecyclerView mRecyclerView;
    private RecyclerAdapter mAdapter;
    private ArrayList<ActivityItem> mData;
    private Timestamp mPreTime;
    private FileObserver mOutputObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        mStatusTv = (TextView)findViewById(R.id.state_tv);
        mainTv = (AwesomeTextView)findViewById(R.id.predicted_activity);
        mStationTv = (TextView)findViewById(R.id.station_tv);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 999);
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 998);
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 997);
        }

        File folder = new File(HARMASTER_PATH);
        if (!folder.exists()) folder.mkdir();

        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                //Log.d("Longitude", String.valueOf(location.getLongitude()));
                //Log.d("Latitude", String.valueOf(location.getLatitude()));
                //Log.d("Altitude", String.valueOf(location.getAltitude()));
                //Log.d("Speed", String.valueOf(location.getSpeed()));

                gpsSpeedinKilo = location.getSpeed() * 3600 / 1000;
                mLongitude = location.getLongitude();
                mLatitude = location.getLatitude();
                Log.d("gpsSpeedinKilo", String.valueOf(gpsSpeedinKilo));
                mStatusTv.setText("gpsSpeed" + gpsSpeedinKilo);

                Bundle extra = location.getExtras();
                if (extra != null) {
                    int satellites = extra.getInt("satellites");
                    Log.d("satellites", String.valueOf(satellites));
                }
            }

            @Override
            public void onStatusChanged(String s, int status, Bundle bundle) {
                switch(status){
                    case LocationProvider.AVAILABLE:
                        Log.v("Status","AVAILABLE");
                        mGpsState = "available";
                        break;
                    case LocationProvider.OUT_OF_SERVICE:
                        Log.v("Status","OUT_OF_SERVICE");
                        mGpsState = "out_of_service";
                        break;
                    case  LocationProvider.TEMPORARILY_UNAVAILABLE:
                        Log.v("Status","TEMPORARILY_UNAVAILABLE");
                        mGpsState = "temporarily_unavailable";
                        break;
                }
            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

            }
        };

        Timer gpsTimer = new Timer();
        gpsTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(gpsSpeedTemp == gpsSpeedinKilo){
                    //initialize gpsSpeedinKilo
                    gpsSpeedinKilo = 0;
                }else{
                    gpsSpeedTemp = gpsSpeedinKilo;
                }
            }
        }, 0, 5000);

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);

        mAudioProc = new AudioProc();

        mRecyclerView = (RecyclerView) findViewById(R.id.pict_recycler);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mData = new ArrayList<>();
        mAdapter = new RecyclerAdapter(this, mData);
        mRecyclerView.setAdapter(mAdapter);
        //mRecyclerView.addItemDecoration(new CustomItemDecoration(1, mContext));

        mStartButton = (BootstrapButton)findViewById(R.id.start_button);
        mStopButton = (BootstrapButton)findViewById(R.id.stop_button);

        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStartButton.setEnabled(false);
                mStopButton.setEnabled(true);
                startPrediction();
                mStatusTv.setText("now predicting .........");
            }
        });

        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStartButton.setEnabled(true);
                mStopButton.setEnabled(false);
                stopPrediction();
                mStatusTv.setText("");
            }
        });
    }

    private void startPrediction(){
        mSensorDataCollector = new SensorDataCollector(this);
        mSensorDataCollector.startSensor();

        //final MarkovProc markovProc = new MarkovProc();
        final Viterbi viterbi = new Viterbi();
        stateArray = new String[30];
        correctedStateArray = new String[30];

        mStateCounter = new StateCounter();
        mStateCounter.initialize();

        final Handler handler = new Handler();
        mOutputObserver = new FileObserver(TEMPFILE_PATH) {
            @Override
            public void onEvent(int event, @Nullable String path) {
                Log.d("event", String.valueOf(event));
                if(event != FileObserver.CLOSE_WRITE) return;

                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                setActivity();
                String maxState;
                if (!recordFlag) {
                    mStateCounter.addCounter(state);
                    maxState = mStateCounter.getMaxCountState();

                    if(stateCount < 30){
                        stateArray[stateCount] = maxState;
                        correctedStateArray[stateCount] = nowState;
                        Log.d("stateArray[" + stateCount + "]", stateArray[stateCount]);
                        stateCount++;
                    }else if(stateCount >= 30){
                        writeFile(viterbi.viterbiAlgorithm(stateArray, correctedStateArray), "viterbi.csv");
                        Log.d("viterbi", viterbi.viterbiAlgorithm(stateArray, correctedStateArray));
                        stateCount = 0;
                    }

                    writeFile(timestamp.toString() + " " + state + " " + maxState + " " + nowState + " " + gpsSpeedinKilo + " " + mLongitude + " " + mLatitude, "predicted_activity.txt");
                    writeFile(timestamp.toString() + " " + nowState, "corrected_result.txt");
                    writeFile(timestamp.toString() + "," + state+ "," + maxState + "," + nowState, "compare.csv");

                    if (tempState.equals(state)) count++;
                    else count = 0;
                    if (count > 5) {
                        if (!((nowState.equals("Bus") || nowState.equals("Bicycle")) && maxState.equals("Train"))
                                && !((nowState.equals("Train") || nowState.equals("Bicycle")) && maxState.equals("Bus"))
                                && !((nowState.equals("Train") || nowState.equals("Bus")) && maxState.equals("Bicycle"))
                                && !((nowState.equals("Up-Elevator") || nowState.equals("Down-Elevator")) && maxState.equals("Bicycle"))){
                            nowState = maxState;
                        }
                    }
                    tempState = maxState;
                } else if (recordFlag) {
                    audioState = mAudioProc.getState();
                    mStateCounter.addCounter(audioState);
                    maxState = mStateCounter.getMaxCountState();
                    //markovState = markovProc.getState(audioProbs, markovState);

                    if(stateCount < 30){
                        stateArray[stateCount] = maxState;
                        correctedStateArray[stateCount] = nowState;
                        stateCount++;
                    }else if(stateCount >= 30){
                        writeFile(viterbi.viterbiAlgorithm(stateArray, correctedStateArray), "viterbi.csv");
                        stateCount = 0;
                    }

                    final String tvState = maxState;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mainTv.setText(tvState);
                        }
                    });

                    writeFile(timestamp.toString() + " " + audioState + "(audio)" + " " + maxState + " " + nowState + " " + gpsSpeedinKilo + " " + mLongitude + " " + mLatitude, "predicted_activity.txt");
                    writeFile(timestamp.toString() + " " + nowState, "corrected_result.txt");
                    writeFile(timestamp.toString() + "," + audioState + "," + maxState + "," + nowState, "compare.csv");

                    if (tempState.equals(maxState)) count++;
                    else count = 0;
                    if (count > 5) {
                        if (!((nowState.equals("Bus") || nowState.equals("Bicycle")) && maxState.equals("Train")) && !((nowState.equals("Train") || nowState.equals("Bicycle")) && maxState.equals("Bus"))) {
                            nowState = maxState;
                        }
                    }
                    tempState = maxState;
                }

                //set pictogram
                if(!preState.equals(nowState)){
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            preState = nowState;
                            mData.add(new ActivityItem(nowState));
                            mAdapter.notifyItemInserted(mData.size());
                            int size = mData.size();
                            if(size > 1){
                                Timestamp nowTime = new Timestamp(System.currentTimeMillis());
                                ActivityItem activityItem = new ActivityItem(mData.get(size-2).getState());
                                activityItem.setTime(nowTime.getTime() - mPreTime.getTime());

                                mData.set(size-2, activityItem);
                                //mData.get(size-1).setDistance();
                                mAdapter.notifyItemChanged(size-1, activityItem);
                                Log.v("change", String.valueOf(nowTime.getTime() - mPreTime.getTime()));
                            }
                            mPreTime = new Timestamp(System.currentTimeMillis());
                        }
                    });
                }
            }
        };
        mOutputObserver.startWatching();
//        timer = new Timer();
//        timer.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
//                setActivity();
//                if (!recordFlag) {
//                    if(stateCount < 30){
//                        stateArray[stateCount] = state;
//                        correctedStateArray[stateCount] = nowState;
//                        Log.d("stateArray[" + stateCount + "]", stateArray[stateCount]);
//                        stateCount++;
//                    }else if(stateCount >= 30){
//                        writeFile(viterbi.viterbiAlgorithm(stateArray, correctedStateArray), "viterbi.csv");
//                        Log.d("viterbi", viterbi.viterbiAlgorithm(stateArray, correctedStateArray));
//                        stateCount = 0;
//                    }
//                    //markovState = markovProc.getState(probs, markovState);
//                    writeFile(timestamp.toString() + " " + state + " " + nowState + " " + gpsSpeedinKilo + " " + mLongitude + " " + mLatitude, "predicted_activity.txt");
//                    writeFile(timestamp.toString() + " " + nowState, "corrected_result.txt");
//                    writeFile(timestamp.toString() + "," + state+ "," + nowState, "compare.csv");
//
//                    if (tempState.equals(state)) count++;
//                    else count = 0;
//                    if (count > 5) {
//                        if (!((nowState.equals("Bus") || nowState.equals("Bicycle")) && state.equals("Train"))
//                                && !((nowState.equals("Train") || nowState.equals("Bicycle")) && state.equals("Bus"))
//                                && !((nowState.equals("Train") || nowState.equals("Bus")) && state.equals("Bicycle"))
//                                && !((nowState.equals("Up-Elevator") || nowState.equals("Down-Elevator")) && state.equals("Bicycle"))){
//                            nowState = state;
//                        }
//                    }
//                    tempState = state;
//                } else if (recordFlag) {
//                    audioState = mAudioProc.getState();
//                    //markovState = markovProc.getState(audioProbs, markovState);
//
//                    if(stateCount < 30){
//                        stateArray[stateCount] = audioState;
//                        correctedStateArray[stateCount] = nowState;
//                        stateCount++;
//                    }else if(stateCount >= 30){
//                        writeFile(viterbi.viterbiAlgorithm(stateArray, correctedStateArray), "viterbi.csv");
//                        stateCount = 0;
//                    }
//
//                    handler.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            mainTv.setText(audioState);
//                        }
//                    });
//
//                    writeFile(timestamp.toString() + " " + audioState + "(audio)" + " " + nowState + " " + gpsSpeedinKilo + " " + mLongitude + " " + mLatitude, "predicted_activity.txt");
//                    writeFile(timestamp.toString() + " " + nowState, "corrected_result.txt");
//                    writeFile(timestamp.toString() + "," + audioState + "," + nowState, "compare.csv");
//
//                    if (tempState.equals(audioState)) count++;
//                    else count = 0;
//                    if (count > 5) {
//                        if (!((nowState.equals("Bus") || nowState.equals("Bicycle")) && audioState.equals("Train")) && !((nowState.equals("Train") || nowState.equals("Bicycle")) && audioState.equals("Bus"))) {
//                            nowState = audioState;
//                        }
//                    }
//                    tempState = audioState;
//                }
//
//                //set pictogram
//                if(!preState.equals(nowState)){
//                    handler.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            preState = nowState;
//                            mData.add(new ActivityItem(nowState));
//                            mAdapter.notifyItemInserted(mData.size());
//                            int size = mData.size();
//                            if(size > 1){
//                                Timestamp nowTime = new Timestamp(System.currentTimeMillis());
//                                ActivityItem activityItem = new ActivityItem(mData.get(size-2).getState());
//                                activityItem.setTime(nowTime.getTime() - mPreTime.getTime());
//
//                                mData.set(size-2, activityItem);
//                                //mData.get(size-1).setDistance();
//                                mAdapter.notifyItemChanged(size-1, activityItem);
//                                Log.v("change", String.valueOf(nowTime.getTime() - mPreTime.getTime()));
//                            }
//                            mPreTime = new Timestamp(System.currentTimeMillis());
//                        }
//                    });
//                }
//            }
//        }, 0, 1000);
//エラー発生のため、コメントアウト。フラグによる制御が失敗してAudioProcを同時に呼び出していることが原因。
//        mContext.registerReceiver(new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                List<ScanResult> resultList = ((WifiManager)mContext.getSystemService(Context.WIFI_SERVICE)).getScanResults();
//                int resultSize = resultList.size();
//                wifiCount = 0;
//                for(ScanResult scanResult:resultList){
//                    if(scanResult.SSID.equals("TOBU_Free_Wi-Fi") || scanResult.SSID.equals("Metro_Free_Wi-Fi")){
//                        Log.d("SSID", scanResult.SSID);
//                        mInStation = true;
//                        getBaseContext().sendBroadcast(new Intent().setAction("Station").putExtra("station", "in the station."));
//                        awayCount = 0;
//                        handler.post(new Runnable() {
//                            @Override
//                            public void run() {
//                                mStationTv.setText("in the station.....");
//                            }
//                        });
//                        break;
//                    }
//                    wifiCount++;
//                    if(wifiCount == resultSize && mInStation && !recordFlag && state.equals("Stop")) {
//                        awayCount++;
//                        if(awayCount > 3) {
//                            mInStation = false;
//                            mAudioProc.startRecording();
//                            recordFlag = true;
//                            getBaseContext().sendBroadcast(new Intent().setAction("Station").putExtra("station", "out of the station."));
//                        }
//                    }
//                }
//                ((WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).startScan();
//            }
//        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
//        ((WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).startScan();
    }

    private void stopPrediction(){
        mSensorDataCollector.stopSensor();
        mOutputObserver.stopWatching();
        //timer.cancel();
    }

    public static void setActivity(){
        state = mSensorDataCollector.getState();
        Log.d("activitycount", String.valueOf(activityCount));
        Log.d("state", state);
        if(state.equals("Stop")){
            activityCount = 0;
        }
        if(gpsSpeedinKilo > 8 && state.equals("Stop")){
            vehicleCount++;
            if(!recordFlag && vehicleCount > 4){
                recordFlag = true;
                Log.v("status", "startRecording");
                mAudioProc.startRecording();
                mSensorDataCollector.changeFlagToFalse();
                activityCount = 0;
            }
        }else if(state.equals("Walking") || state.equals("Running") || state.equals("Bicycle")) {
            activityCount++;
            if (recordFlag && activityCount > 4) {
                mAudioProc.stop();
                mSensorDataCollector.changeFlagToTrue();
                recordFlag = false;
                vehicleCount = 0;
            }
        }
    }

    private void writeFile(String str, String filename){
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(HARMASTER_PATH + "/" + filename), true), "UTF-8"));
            bufferedWriter.write(str + "\n");
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
