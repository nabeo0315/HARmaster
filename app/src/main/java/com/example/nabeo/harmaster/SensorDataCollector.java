package com.example.nabeo.harmaster;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import com.beardedhen.androidbootstrap.AwesomeTextView;
import com.beardedhen.androidbootstrap.BootstrapText;
import com.beardedhen.androidbootstrap.api.view.BootstrapTextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import umich.cse.yctung.androidlibsvm.LibSVM;

public class SensorDataCollector implements SensorEventListener {

    private SensorManager mSensorManager;
    private Context mContext;
    private AwesomeTextView mTextView;

    private float[] acc = new float[3];
    private float[] globalAccValues = new float[3];
    private float[] globalLinearAccValues = new float[3];
    private float pressure;
    private float[] gyro = new float[3];
    private float[] globalGyroValues = new float[3];
    private float[] Values = new float[13];
    private List<Double> Xw_list = new ArrayList<Double>();
    private List<Double> Yw_list = new ArrayList<Double>();
    private List<Double> Zw_list = new ArrayList<Double>();
    private List<Double> gps_list = new ArrayList<Double>();
    private List<Double> Pressure_list  = new ArrayList<Double>();
    private List<String> activities_array = new ArrayList<String>();
    private List<String> tmp_array = new ArrayList<String>();
    private float[] magnetic = new float[3];
    private float[] gravity = new float[3];
    private float[] linear_acc = new float[3];
    private int storeValuesCount = 0;
    private int preparationTime = 5000; //カウントダウン後の準備時間(ミリ秒)
    private int collectRawDataInterval = 20; //20msの間隔
    private int predictInterval = 1000; //???msの間隔
    private int decisionCount = 5;
    private int windowSize = 250; //5秒分のデータ
    private double[] param = new double[7];
    private boolean startTimerFlag = false;
    private boolean nowCollectingTimerFlag = false;
    private boolean isParam = false;
    private static boolean predictFlag = true;
    private Timer startTimer;
    private Timer collectRawDataTimer;
    private Timer predictTimer;
    private FileObserver outputObserver;
    private boolean fileObserverFlag = false;
    private Scaller scaller;

    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private double gpsSpeedinKilo = 0;
    private double gpsSpeedDifferential = 0;
    private double mLongitude = 0;
    private double mLatitude = 0;
    private String mGpsState = null;

    public final static String ROOT_DIR = Environment.getExternalStorageDirectory().toString();
    private final static String HARMASTER_PATH = ROOT_DIR +"/HARmaster";
    private final static String MODEL_PATH = HARMASTER_PATH + "/9state_model_windowSize_5s_optionB.model";//"/9state_model_windowSize_5s_scaled.txt.model";
    private final static String OUTPUT_PATH = HARMASTER_PATH + "/output.txt";
    private final static String SCALE_PATH = HARMASTER_PATH + "/scale_data.txt";
    private final static String NOW_INFO_PATH = HARMASTER_PATH + "/now_info";
//    private final static String TESTDATA_SCALED_PATH = HARMASTER_PATH + "/testdata_scaled.txt";
//    private final static String TESTDATA_PATH = HARMASTER_PATH + "testdata.txt";
    private String mFolderName;
    private String mState = "Stop";

    SensorDataCollector(Context context){
        mContext = context;

        mSensorManager = (SensorManager)mContext.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        mTextView = (AwesomeTextView)((com.example.nabeo.harmaster.MainActivity)mContext).findViewById(R.id.predicted_activity);

        for (Sensor sensor : sensors) {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_GRAVITY) {
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_PRESSURE) {
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
            if (sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
        }

        mLocationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                //Log.d("Longitude", String.valueOf(location.getLongitude()));
                //Log.d("Latitude", String.valueOf(location.getLatitude()));
                //Log.d("Altitude", String.valueOf(location.getAltitude()));
                //Log.d("Speed", String.valueOf(location.getSpeed()));

                gpsSpeedDifferential = gpsSpeedinKilo - (location.getSpeed() * 3600/1000);
                gpsSpeedinKilo = location.getSpeed() * 3600 / 1000;
                gps_list.add(gpsSpeedinKilo);
                mLongitude = location.getLongitude();
                mLatitude = location.getLatitude();
                Log.d("gpsSpeedinKilo", String.valueOf(gpsSpeedinKilo));

//                mGpsSpeed_tv.setText(String.valueOf(gpsSpeedinKilo));
//                mGpsState_tv.setText(mGpsState);

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
    }

    public void startSensor() {
        final LibSVM libsvm = new LibSVM();

        createTemplateFile();
        createGpsValuesCSV();

        try {
            scaller = new Scaller().loadRange(new File(SCALE_PATH));
        } catch (IOException e) {
            e.printStackTrace();
        }

        startTimer = new Timer();
        startTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                startTimerFlag = true;
                //**** センサデータの収集開始 *****//
                collectRawDataTimer = new Timer();
                collectRawDataTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        storeValues(Values); //センサ値を記録
                        nowCollectingTimerFlag = true;
                        if (storeValuesCount > windowSize + 1) {
                            //Log.d("d", String.valueOf(storeValuesCount));
                            isParam = true;
                        }
                    }
                }, collectRawDataInterval, collectRawDataInterval); //一定間隔でデータを記録，特徴量生成
                //**** 移動状態推定の開始 *****//
                predictTimer = new Timer();
                predictTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (isParam) {
                            if (storeValuesCount > windowSize + 1) {
                                createParam(); //特徴量生成 時間窓5秒
                                if(predictFlag) libsvm.predict("-b 1 " + NOW_INFO_PATH + " " + MODEL_PATH + " " + OUTPUT_PATH);
                                if(getAve_AccXY()  > 4 && !predictFlag){
                                    mState = "Walking";
                                }
                                recordLogfile();
                            }
                            if (!fileObserverFlag) {
                                outputObserver.startWatching();
                                fileObserverFlag = true;
                            }
                            isParam = false;
                        }
                    }
                }, predictInterval, predictInterval); //一定間隔で一定間隔で推定
            }
        }, preparationTime); //準備時間

        final Handler handler = new Handler();

        outputObserver = new FileObserver(OUTPUT_PATH) {
            @Override
            public void onEvent(int event, String path) {
                String str = "";
                String[] array = {};
                if (event == FileObserver.CLOSE_WRITE) {
                    try {
                        BufferedReader bufferedReader = new BufferedReader(new FileReader(OUTPUT_PATH));
                        str = bufferedReader.readLine();
                        array = bufferedReader.readLine().split(" ");
                        str = array[0];
                        bufferedReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.d("str", String.valueOf(str));

                    if(getVar_AccZ() > 1 && getAve_AccZ() < 6 && gpsSpeedinKilo > 8) str = "10";
                    if (str != null) {
                        int label = Integer.parseInt(str);
                        switch (label) {
                            case 1:
                                mState = "Stop";
                                break;
                            case 2:
                                mState = "Walking";
                                break;
                            case 3:
                                mState = "Upstairs";
                                break;
                            case 4:
                                mState = "Downstairs";
                                break;
                            case 5:
                                mState = "Up-Elevator";
                                break;
                            case 6:
                                mState = "Down-Elevator";
                                break;
                            case 7:
                                mState = "Running";
                                break;
                            case 8:
                                mState = "Up-Escalator";
                                break;
                            case 9:
                                mState = "Down-Escalator";
                                break;
                            case 10:
                                mState = "Bicycle";
                                break;
                            default:
                                break;
                        }
                    }
                    writeFile(mState, "temp_state.txt", false);
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText(mState);
                    }
                });
            }
        };
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                acc = event.values.clone();
                globalAccValues = convertGlobalValues(acc);
                Values[0] = globalAccValues[0];
                Values[1] = globalAccValues[1];
                Values[2] = globalAccValues[2];
                break;

            case Sensor.TYPE_LINEAR_ACCELERATION:
                linear_acc = event.values.clone();
                globalLinearAccValues = convertGlobalValues(linear_acc);
                Values[3] = globalLinearAccValues[0];
                Values[4] = globalLinearAccValues[1];
                Values[5] = globalLinearAccValues[2];
                break;

            case Sensor.TYPE_GRAVITY:
                gravity = event.values.clone();
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                magnetic = event.values.clone();
                break;

            case Sensor.TYPE_PRESSURE:
                pressure = event.values[0];
                Values[6] = pressure;
                break;

            case Sensor.TYPE_GYROSCOPE:
                gyro = event.values.clone();
                globalGyroValues = convertGlobalValues(gyro);
                Values[7] = gyro[0];
                Values[8] = gyro[1];
                Values[9] = gyro[2];
                Values[10] = globalGyroValues[0];
                Values[11] = globalGyroValues[1];
                Values[12] = globalGyroValues[2];
                break;

            case Sensor.TYPE_STEP_DETECTOR:
                break;
        }
    }

    static void changeFlagToFalse(){
        predictFlag = false;
    }

    static void changeFlagToTrue(){
        predictFlag = true;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //CSVファイルのテンプレート（一行目の項目名）を作成
    private void createTemplateFile() {
        File file = new File(HARMASTER_PATH + "/" + "values.csv");
        String items = "TimeStamp," +
                "Acc_X," + "Acc_Y," + "Acc_Z," +
                "Acc_X_Glo," + "Acc_Y_Glo," + "Acc_Z_Glo," +
                "Pressure," +
                "Gyro_X," + "Gyro_Y," + "Gyro_Z," +
                "Gyro_X_Glo," + "Gyro+Y_Glo," + "Gyro_Z_Glo," + "GPS_Speed," + "GPS_State," + "State," + "Position";
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
            bufferedWriter.write(items);
            bufferedWriter.write("\n");
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void recordLogfile(){
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        File file = new File(HARMASTER_PATH + "/" + "log.csv");
        try{
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
            bufferedWriter.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //センサ値をを格納 (CSVファイルの2行目以降)
    private void storeValues(float[] values) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String file = HARMASTER_PATH + "/" + "values.csv";
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
            bufferedWriter.write(timestamp.toString() + ",");
            for (int i = 0; i < values.length; i++) {
                bufferedWriter.write(String.valueOf(values[i]) + ",");
            }
            bufferedWriter.write(gpsSpeedinKilo + "," + mGpsState);
            bufferedWriter.write("\n");
            bufferedWriter.close();

            Xw_list.add(storeValuesCount, Double.valueOf(Values[3]));
            Yw_list.add(storeValuesCount, Double.valueOf(Values[4]));
            Zw_list.add(storeValuesCount, Double.valueOf(Values[5]));
            Pressure_list.add(storeValuesCount, Double.valueOf(Values[6]));
            gps_list.add(-1.0);//あとで取り除く

            storeValuesCount++;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createGpsValuesCSV(){
        String firstLine = "TimeStamp,Latitude,Longitude,Speed,State,SpeedDifference";
        try{
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(HARMASTER_PATH + "/" + mFolderName + "/gps_result.txt", true), "UTF-8"));
            bufferedWriter.write(firstLine);
            bufferedWriter.write("\n");
            bufferedWriter.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void storeGpsValues(){
        try{
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(HARMASTER_PATH + "/" + mFolderName + "/gps_result.txt", true), "UTF-8"));
            bufferedWriter.write(new Timestamp(System.currentTimeMillis()).toString() + "," + mLatitude + "," + mLongitude + "," + gpsSpeedinKilo + "," + mGpsState + "," + gpsSpeedDifferential);
            bufferedWriter.write("\n");
            bufferedWriter.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    //特徴量を記録
    private void createParam() {
        StringBuilder sb = new StringBuilder();
        DecimalFormat paramf = new DecimalFormat("0.00000000");
        param = getParam();
        if (!Double.isNaN(param[0]) && !Double.isNaN(param[1]) && !Double.isNaN(param[2]) && !Double.isNaN(param[3]) && !Double.isNaN(param[4])) {
            //9 is parameter number of train.
            String str = "0 1:" + paramf.format(param[0]) + " 2:" + paramf.format(param[1]) + " 3:" + paramf.format(param[2]) + " 4:" + paramf.format(param[3]) + " 5:" + paramf.format(param[4]);
            Log.d("param_str", str);
            String str_scaled = scaller.calcScaleFromLine(str);
            Log.d("param_str_scaled", str_scaled);
            try {
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(NOW_INFO_PATH)), "UTF-8"));
                bufferedWriter.write(str_scaled);
                bufferedWriter.close();

                FileWriter testFile = new FileWriter(new File(HARMASTER_PATH + "/testdata.txt"), true);
                FileWriter testFileScaled = new FileWriter(new File(HARMASTER_PATH + "/testdata_scaled.txt"), true);
                testFile.write(str + "\n");
                testFileScaled.write(str_scaled + "\n");
                testFile.close();
                testFileScaled.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d("value", sb.toString());
    }

    //特徴量を生成
    private double[] getParam() {
//        double[] param = new double[5];
        param[0] = getAve_AccXY();
        param[1] = getAve_AccZ();
        param[2] = getVar_AccXY();
        param[3] = getVar_AccZ();
        param[4] = getDiff_Pressure();
        param[5] = getAve_Gps();
        param[6] = getVar_Gps();
        return param;
    }


    //センサ値を世界座標系に変換
    private float[] convertGlobalValues(float[] deviceValues) {
        float[] globalValues = new float[4];
        if (acc != null && gravity != null && magnetic != null) {
            float[] inR = new float[16];
            float[] outR = new float[16];
            SensorManager.getRotationMatrix(inR, null, gravity, magnetic);
            SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Y, outR);
            float[] temp = new float[4];
            float[] inv = new float[16];
            temp[0] = deviceValues[0];
            temp[1] = deviceValues[1];
            temp[2] = deviceValues[2];
            temp[3] = 0;
            android.opengl.Matrix.invertM(inv, 0, outR, 0);
            android.opengl.Matrix.multiplyMV(globalValues, 0, inv, 0, temp, 0);
        }
        return globalValues;
    }

    //XY軸合成加速度の平均
    private double getAve_AccXY() {
        double sum = 0;
        for (int i = 0; i < windowSize; i++) {
//            sum = sum + Math.sqrt((Xw[(storeValuesCount - windowSize + i)] * Xw[(storeValuesCount - windowSize + i)]) + (Yw[(storeValuesCount - windowSize + i)]) * Yw[(storeValuesCount - windowSize + i)]);
            sum = sum + Math.sqrt((Xw_list.get(storeValuesCount - windowSize + i) * Xw_list.get(storeValuesCount - windowSize + i)) + (Yw_list.get(storeValuesCount - windowSize + i) * Yw_list.get(storeValuesCount - windowSize + i)));
        }
        return sum / windowSize;
    }

    //Z軸軸加速度の平均
    private double getAve_AccZ() {
        double sum = 0;
        for (int i = 0; i < windowSize; i++) {
//            sum = sum + Zw[(storeValuesCount - windowSize + i)];
            sum = sum + Zw_list.get(storeValuesCount - windowSize + i);
        }
        return sum / windowSize;
    }

    //XY軸軸合成加速度の分散
    private double getVar_AccXY() {
        double ave = getAve_AccXY();
        double temp = 0;
        for (int i = 0; i < windowSize; i++) {
//            temp += (Math.sqrt((Xw[(storeValuesCount - windowSize + i)] * Xw[(storeValuesCount - windowSize + i)]) + (Yw[(storeValuesCount - windowSize + i)]) * Yw[(storeValuesCount - windowSize + i)]) - ave) * (Math.sqrt((Xw[(storeValuesCount - windowSize + i)] * Xw[(storeValuesCount - windowSize + i)]) + (Yw[(storeValuesCount - windowSize + i)]) * Yw[(storeValuesCount - windowSize + i)]) - ave);
            temp += (Math.sqrt((Xw_list.get(storeValuesCount - windowSize + i) * Xw_list.get(storeValuesCount - windowSize + i)) + (Yw_list.get(storeValuesCount - windowSize + i)) * Yw_list.get(storeValuesCount - windowSize + i)) - ave) * (Math.sqrt((Xw_list.get(storeValuesCount - windowSize + i) * Xw_list.get(storeValuesCount - windowSize + i)) + (Yw_list.get(storeValuesCount - windowSize + i)) * Yw_list.get(storeValuesCount - windowSize + i)) - ave);
        }
        return temp / windowSize;
    }

    //Z軸加速度の分散
    private double getVar_AccZ() {
        double ave = getAve_AccZ();
        double temp = 0;
        for (int i = 0; i < windowSize; i++) {
//            temp += ((Zw[storeValuesCount - windowSize + i] - ave) * (Zw[storeValuesCount - windowSize + i] - ave));
            temp += ((Zw_list.get(storeValuesCount - windowSize + i) - ave) * (Zw_list.get(storeValuesCount - windowSize + i) - ave));
        }
        return temp / windowSize;
    }

    private double getDiff_Pressure() {
//        return Pressure[storeValuesCount - windowSize - 1] - Pressure[storeValuesCount - 1];
        double diff;
        diff = Pressure_list.get(storeValuesCount - windowSize - 1) - Pressure_list.get(storeValuesCount - 1);
        return diff;
    }

    private double getAve_Gps(){
        double sum = 0;
        int count = 0;
        Log.d("listsize", String.valueOf(gps_list.size()));
        for (int i = 0; i < windowSize; i++) {
            if(gps_list.get(storeValuesCount - windowSize + i) == -1) continue;
            //Log.d("value", String.valueOf(gps_list.get(storeValuesCount - windowSize + i)));
            count++;
            sum = sum + gps_list.get(storeValuesCount - windowSize + i);
        }
        double ave = sum/count;
        if(Double.isNaN(ave)) return 0;
        return ave;
    }

    private double getVar_Gps(){
        double ave = getAve_Gps();
        double temp = 0;
        int count = 0;
        for (int i = 0; i < windowSize; i++) {
            if(gps_list.get(storeValuesCount - windowSize + i) == -1) continue;
            count++;
            temp += ((gps_list.get(storeValuesCount - windowSize + i) - ave) * (gps_list.get(storeValuesCount - windowSize + i) - ave));
        }
        double var = temp/count;
        if(Double.isNaN(var)) return 0;
        return temp / count;
    }

    public void stopSensor() {
        outputObserver.stopWatching();
        collectRawDataTimer.cancel();
        predictTimer.cancel();
        startTimer.cancel();
    }

    public boolean setFolderName(String folderName){
        mFolderName = folderName;
        File collectAppFile = new File(HARMASTER_PATH + "/" + mFolderName);
        if(!collectAppFile.exists()) {
            //Log.d("d" , "mkdir");
            collectAppFile.mkdir();
            return true;
        }else{
            return false;
        }
    }

    private int getClassLabel(String state){

        if(state.equals("Stop")) return 1;
        else if(state.equals("Walking")) return 2;
        else if(state.equals("UpStairs")) return 3;
        else if(state.equals("DownStairs")) return 4;
        else if(state.equals("Up-Elevator")) return 5;
        else if(state.equals("Down-Elevator")) return 6;
        else if(state.equals("Running")) return 7;
        else if(state.equals("Bicycle")) return 8;
        else if(state.equals("Train")) return 9;
        else if(state.equals("Bus")) return 10;

        return 0;
    }

    public String getState(){
        //Log.d("SensorDataCollector.getState:", mState);
        return this.mState;
    }

    private void writeFile(String str, String filename, boolean append){
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(HARMASTER_PATH + "/" + filename), append), "UTF-8"));
            bufferedWriter.write(str + "\n");
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
