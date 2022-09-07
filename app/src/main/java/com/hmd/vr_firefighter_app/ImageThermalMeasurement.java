package com.hmd.vr_firefighter_app;

import org.opencv.core.Mat;

public class ImageThermalMeasurement {

    private static float _maxTemp = 0;

    public static void getTemperatureMeasurement(Mat img){

        int totalBytes = (int)(img.total() * img.elemSize());
        byte[] buffer;
        buffer = new byte[totalBytes];
        float maxTemp;
        maxTemp = -1000;

        img.get(0, 0,buffer);

        for(int i =0; i < totalBytes; i++){

            float temp = (float) (9.2 + ((float)(buffer[i]) / 255) * 26.2);

            if(temp >= maxTemp){

                maxTemp = temp;

            }

        }

        _maxTemp = maxTemp;

    }

    public static float getTemperature(){

        return _maxTemp;

    }

}
