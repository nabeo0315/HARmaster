package com.example.nabeo.harmaster;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class StateCounter {

    private Map<String, Integer> mStateCountMap;//<state, counter>
    private Deque<String> mStateQueue;

    private static final String[] STATES = {"Stop", "Walking", "Running", "UpStairs", "DownStairs", "Up-Elevator", "Down-Elevator", "Up-Escalator", "Down-Escalator", "Bicycle", "Bus", "Train"};

    public StateCounter(){
        this.mStateCountMap = new HashMap<>();
        this.mStateQueue = new ArrayDeque<>();
    }

    public void initialize(){
        for(String state: STATES) mStateCountMap.put(state, 0);
    }

    public  void addCounter(String state){
        String removeState;
        if(mStateQueue.size() == 5){
            removeState = mStateQueue.poll();
            mStateCountMap.put(removeState, mStateCountMap.get(removeState) - 1);
        }
        mStateQueue.add(state);

        Log.v("statecounter:", state);
        mStateCountMap.put(state, mStateCountMap.get(state) + 1);
    }

    public String getMaxCountState(){
        if(mStateQueue.size() != 5) return "";

        String maxCountState = "";
        int maxCount = 0;
        for(Map.Entry<String, Integer> entry : mStateCountMap.entrySet()){
            if(entry.getValue() > maxCount){
                maxCount = entry.getValue();
                maxCountState = entry.getKey();
            }
        }
        Log.d("getMaxCountState:", maxCountState + ":" + String.valueOf(maxCount));

        return maxCountState;
    }
}
