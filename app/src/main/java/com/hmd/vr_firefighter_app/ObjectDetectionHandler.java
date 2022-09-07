package com.hmd.vr_firefighter_app;
import android.os.Handler;
import android.os.Looper;

public class ObjectDetectionHandler {
    private static Handler handler = null;
    private static ObjectDetectionWorker objectDetectionWorker = null;
    public static void init(){
        handler = new Handler(Looper.getMainLooper());
        objectDetectionWorker = new ObjectDetectionWorker(handler);
    }

    public static void startThread(){
        handler.postDelayed(objectDetectionWorker, 500);
    }

    public static void stopThread(){

        handler.removeCallbacks(objectDetectionWorker);

    }

}
