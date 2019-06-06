package com.example.nabeo.harmaster;

import android.app.Application;
import com.beardedhen.androidbootstrap.TypefaceProvider;

public class MyBootstrap extends Application{
    @Override
    public void onCreate(){
        super.onCreate();
        TypefaceProvider.registerDefaultIconSets();
    }
}
