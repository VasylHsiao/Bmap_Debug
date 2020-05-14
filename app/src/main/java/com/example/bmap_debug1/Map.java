package com.example.bmap_debug1;

import android.app.Application;
import android.app.Service;
import android.os.Vibrator;

import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.example.bmap_debug1.service.LocationService;

public class Map extends Application {

    public LocationService locationService;
    public Vibrator mVibrator;

    @Override
    public void onCreate() {
        super.onCreate();

        locationService = new LocationService(getApplicationContext());
        mVibrator =(Vibrator)getApplicationContext().getSystemService(Service.VIBRATOR_SERVICE);

        //在使用SDK各组件之前初始化context信息，传入ApplicationContext
        SDKInitializer.initialize(getApplicationContext());
        //包括BD09LL和GCJ02两种坐标，默认是BD09LL坐标。
        SDKInitializer.setCoordType(CoordType.BD09LL);
    }
}
