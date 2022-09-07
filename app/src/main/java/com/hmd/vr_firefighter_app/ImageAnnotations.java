package com.hmd.vr_firefighter_app;

import static java.lang.String.*;

import android.annotation.SuppressLint;
import android.content.Context;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ImageAnnotations {

    private static final double THRESHOLD = 0.6;

    private static Mat priority_bitmap = null;

    private static Mat high_temperature_bitmap = null;

    private static void swapBRChannels(Mat pic){

        for(int i = 0; i < pic.rows(); i++){

            for(int j = 0; j < pic.cols(); j++){

                double[] pixel = new double[4];

                double rChannel = pic.get(i, j)[2];

                double bChannel = pic.get(i, j)[0];

                pixel[0] = rChannel;

                pixel[1] = pic.get(i, j)[1];

                pixel[2] = bChannel;

                pixel[3] = pic.get(i, j)[3];

                pic.put(i, j, pixel);

            }

        }

    }

    private static void overlayImage(Mat background,Mat foreground,Mat output, Point location){

        //background.copyTo(output);

        for(int y = (int) Math.max(location.y , 0); y < background.rows(); ++y){

            int fY = (int) (y - location.y);

            if(fY >= foreground.rows())
                break;

            for(int x = (int) Math.max(location.x, 0); x < background.cols(); ++x){
                int fX = (int) (x - location.x);
                if(fX >= foreground.cols()){
                    break;
                }

                double opacity;
                double[] finalPixelValue = new double[4];

                opacity = foreground.get(fY , fX)[3];

                finalPixelValue[0] = background.get(y, x)[0];
                finalPixelValue[1] = background.get(y, x)[1];
                finalPixelValue[2] = background.get(y, x)[2];
                finalPixelValue[3] = background.get(y, x)[3];

                if(opacity > 0){
                    float fOpacity = (float) (opacity / 255);
                    for(int c = 0;  c < output.channels(); ++c){
                        //double foregroundPx =  foreground.get(fY, fX)[c];
                        //double backgroundPx =  background.get(y, x)[c];

                        //finalPixelValue[c] = ((backgroundPx * ( 1.0 - fOpacity)) + (foregroundPx * fOpacity));

                        finalPixelValue[c] = foreground.get(fY,fX)[c];

                        //if(c==3){
                        //    finalPixelValue[c] = foreground.get(fY,fX)[3];
                        //}
                    }
                }

                output.put(y, x, finalPixelValue);

            }

        }

    }

    public static void init(final Context context) throws IOException {

        //Log.d(ImageAnnotations.class.getName(), "INIT IMAGE ANNOTATIONS");

        File priority_file = new File(context.getCacheDir() + "/priority_small.png" );

        //if (!priority_file.exists()) {
            try {

                InputStream is = context.getAssets().open("priority_small.png");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();

                FileOutputStream fos = new FileOutputStream(priority_file);

                fos.write(buffer);
                fos.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        //}

        File high_temperature_file = new File(context.getCacheDir() + "/high_temperature.png");
        //if (!high_temperature_file.exists()) {
            try {

                InputStream is = context.getAssets().open("high_temperature.png");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();

                FileOutputStream fos = new FileOutputStream(high_temperature_file);

                fos.write(buffer);
                fos.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        //}

        if (priority_file.exists()) {

            //Log.d(ImageAnnotations.class.getName(), "Priority File Exists");

            priority_bitmap = Imgcodecs.imread(priority_file.getAbsolutePath(), Imgcodecs.IMREAD_UNCHANGED);

            swapBRChannels(priority_bitmap);

        }

        if (high_temperature_file.exists()) {

            //Log.d(ImageAnnotations.class.getName(), "High Temperature File Exists");

            high_temperature_bitmap = Imgcodecs.imread(high_temperature_file.getAbsolutePath(), Imgcodecs.IMREAD_UNCHANGED);

            swapBRChannels(high_temperature_bitmap);

        }

    }

    public static void makeAnnotations(List<DetectionResult> detections, Mat frame){

        Collections.sort(detections);
        for(DetectionResult results: detections){
            if (results.getConfidence() > THRESHOLD) {
                if(!Objects.equals(results.getTitle(), "bad")){

                    //Log.d(ImageAnnotations.class.getName(), valueOf(results.getConfidence()));
                    int left = results.getLeft();
                    int top = results.getTop();
                    int right = results.getRight();
                    int bottom = results.getBottom();
                    String label = results.getTitle();

                    Imgproc.rectangle(frame,
                            new Point(left, top),
                            new Point(right, bottom),
                            new Scalar(0, 255, 0));

                    //image overlay = ID: 1

                    if(Objects.equals(label, "pessoa")){

                        overlayImage(frame, priority_bitmap, frame, new Point((int)(((float)left + (float)right - (float)priority_bitmap.width())/2.0),
                                (int)(((float)top + (float)bottom - (float)priority_bitmap.height())/2.0)));

                    }

                    int[] baseLine = new int[1];

                    Size labelSize = Imgproc.getTextSize(label, Imgproc.FONT_HERSHEY_SIMPLEX, 1, 2, baseLine);

                    // Draw background for label.

                    Imgproc.rectangle(frame,
                            new Point(left, top - labelSize.height),
                            new Point(left + labelSize.width,
                                    top + baseLine[0]),
                            new Scalar(255, 255, 255), Imgproc.FILLED);

                    // Write class name and confidence.

                    Imgproc.putText(frame,
                            label,
                            new Point(left, top),
                            Imgproc.FONT_HERSHEY_SIMPLEX,
                            1,
                            new Scalar(0, 0, 0));

                }

            }

        }

    }

    public static void makeTemperatureAnnotation(float temp, Mat frame){

        @SuppressLint("DefaultLocale") String label = MessageFormat.format("T: {0} oC", format("%.2f", temp));

        int[] baseLine = new int[1];

        Size labelSize = Imgproc.getTextSize(label,
                Imgproc.FONT_HERSHEY_SIMPLEX,
                1,
                2,
                baseLine);

        // Draw background for label.

        int x = 5;

        int y = 5;

        Imgproc.rectangle(frame,
                new Point(x, y),
                new Point(x + labelSize.width, y + 40),
                new Scalar(255, 255, 255), Imgproc.FILLED);

        // Write class name and confidence.

        Imgproc.putText(frame,
                label,
                new Point(x, y+30),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                1,
                new Scalar(0, 0, 0));

        // colocar medida de temperatura elevada aqui

        overlayImage(frame, high_temperature_bitmap, frame, new Point(10, 10));

    }

}
