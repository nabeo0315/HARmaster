package com.example.nabeo.harmaster;

import android.Manifest;
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
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.beardedhen.androidbootstrap.AwesomeTextView;
import com.beardedhen.androidbootstrap.BootstrapButton;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Timestamp;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private Context mContext;
    private BootstrapButton mStartButton, mStopButton;
    private static SensorDataCollector mSensorDataCollector;
    private static AudioProc mAudioProc;
    private TextView mStatusTv;
    private AwesomeTextView mainTv;

    public final static String ROOT_DIR = Environment.getExternalStorageDirectory().toString();
    private final static String HARMASTER_PATH = ROOT_DIR +"/HARmaster";

    private LocationManager mLocationManager;
    private String mGpsState;
    private LocationListener mLocationListener;
    private static double gpsSpeedinKilo = 0;
    private double mLongitude = 0;
    private double mLatitude = 0;

    private static boolean recordFlag = false;
    private static String nowState = "", tempState = "", state = "", audioState;
    private static int count = 0, activityCount = 0, vehicleCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        mStatusTv = (TextView)findViewById(R.id.state_tv);
        mainTv = (AwesomeTextView)findViewById(R.id.predicted_activity);

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

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);

        mAudioProc = new AudioProc();

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

        final Handler handler = new Handler();
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                setActivity();
                if (!recordFlag) {
                    writeFile(timestamp.toString() + " " + state + " " + nowState + " " + gpsSpeedinKilo + " " + mLongitude + " " + mLatitude, "predicted_activity.txt");
                    writeFile(timestamp.toString() + " " + nowState, "corrected_result.txt");

                    if (tempState.equals(state)) count++;
                    else count = 0;
                    if (count > 8) {
                        if (!((nowState.equals("Bus") || nowState.equals("Bicycle")) && state.equals("Train")) && !((nowState.equals("Train") || nowState.equals("Bicycle")) && state.equals("Bus")) && !((nowState.equals("Train") || nowState.equals("Bus")) && state.equals("Bicycle"))) {
                            nowState = state;
                        }
                    }
                    tempState = state;
                } else if (recordFlag) {
                    audioState = mAudioProc.getState();

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mainTv.setText(audioState);
                        }
                    });

                    writeFile(timestamp.toString() + " " + audioState + "  (audio)" + " " + nowState + " " + gpsSpeedinKilo + " " + mLongitude + " " + mLatitude, "predicted_activity.txt");
                    writeFile(timestamp.toString() + " " + nowState, "corrected_result.txt");

                    if (tempState.equals(audioState)) count++;
                    else count = 0;
                    if (count > 8) {
                        if (!((nowState.equals("Bus") || nowState.equals("Bicycle")) && audioState.equals("Train")) && !((nowState.equals("Train") || nowState.equals("Bicycle")) && audioState.equals("Bus"))) {
                            nowState = audioState;
                        }
                    }
                    tempState = audioState;
                }
                //((WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).startScan();
            }
        }, 0, 1000);
    }

    private void stopPrediction(){
        mSensorDataCollector.stopSensor();
    }

    public static void setActivity(){
        state = mSensorDataCollector.getState();
        Log.d("vehiclecount", String.valueOf(vehicleCount));
        if(state.equals("Stop")){
            activityCount = 0;
        }
        if(gpsSpeedinKilo > 20){
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
