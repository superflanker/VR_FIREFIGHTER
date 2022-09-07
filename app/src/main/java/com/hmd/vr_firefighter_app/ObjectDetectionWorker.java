package com.hmd.vr_firefighter_app;
// We need to use this Handler package
import android.os.Handler;

public class ObjectDetectionWorker implements Runnable {

    private Handler handler = null;

    public ObjectDetectionWorker(Handler handler) {
        this.handler = handler;
    }

    @Override
    public void run() {

        MobileNetObjDetector.detectObjects();

        this.handler.postDelayed(this, 250);

    }

}

