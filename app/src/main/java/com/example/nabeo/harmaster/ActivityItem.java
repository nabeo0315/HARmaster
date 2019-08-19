package com.example.nabeo.harmaster;

public class ActivityItem {
    private String mState;
    private long mTime = 0;
    private int mDistance = 0;

    public ActivityItem(String state){
        mState = state;
    }

    public void setTime(long time){
        mTime = time;
    }

    public void setDistance(int distance){
        mDistance = distance;
    }

    public long getTime(){
        return this.mTime;
    }

    public int getDistance(){
        return this.mDistance;
    }

    public String getState(){
        return this.mState;
    }
}
