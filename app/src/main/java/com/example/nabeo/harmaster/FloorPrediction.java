package com.example.nabeo.harmaster;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.List;

public class FloorPrediction implements SensorEventListener {

    private double mPressValue;
    private double mStartValue;

    private Context mContext;
    private SensorManager mSensorManager;

    private final double STANDARD = 0.2;

    public FloorPrediction(Context context){
        mContext = context;
        mSensorManager = (SensorManager)mContext.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_PRESSURE);
        Sensor s = sensors.get(0);
        mSensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()){
            case Sensor.TYPE_PRESSURE:
                mPressValue = event.values[0];
        }
    }

    public void setStartValue(){
        mStartValue = mPressValue;
    }

    public int predictFloor(){
        return Math.abs((int)((mPressValue - mStartValue) / STANDARD));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void unRegisterSensorListner(){
        mSensorManager.unregisterListener(this);
    }
}
