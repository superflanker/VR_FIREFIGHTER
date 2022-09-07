package com.hmd.vr_firefighter_app;

import static org.opencv.imgproc.Imgproc.COLOR_YUV2GRAY_NV21;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.Image.Plane;

import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ImageProcessor {
    private static final String TAG = "ImageProcessor";

    private static List<DetectionResult> _detectionResults = new ArrayList<>();

    private static Mat _img = null;

    public synchronized static void setResults(List<DetectionResult> results){

        _detectionResults = results;

    }

    public synchronized static List<DetectionResult> getResults(){

        try {

            List<DetectionResult> results = new ArrayList<>(_detectionResults);

            return results;

        } catch(NullPointerException e) {

             //Log.d(ImageProcessor.class.getName(), "Detections Results are null. Returning an empty list to the caller");

        }

        return new ArrayList<>();

    }

    public synchronized static void setImage(Mat image){

        _img = image.clone();

    }

    public synchronized static Bitmap getImage(){

        Bitmap bmp = null;

        if(_img != null){

            int w = _img.width();

            Mat img = new Mat();

            Size size = new Size(MobileNetObjDetector.getInputSize(),
                    MobileNetObjDetector.getInputSize());

            Imgproc.resize(_img, img, size);

            bmp = Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);

            Utils.matToBitmap(img, bmp);

            MobileNetObjDetector.setScaleFactor((double)(w)/(double) (MobileNetObjDetector.getInputSize()));

        } else {

            //Log.d(ImageProcessor.class.getName(), "Image not yet set. Returning a null pointer to the caller ");

        }

        return bmp;

    }

    public static Mat imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * 3 / 2];// ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    // Advance buffer the remainder of the row stride, unless on the last row.
                    // Otherwise, this will throw an IllegalArgumentException because the buffer
                    // doesn't include the last padding.
                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {

                    // On the last row only read the width of the image minus the pixel stride
                    // plus one. Otherwise, this will throw a BufferUnderflowException because the
                    // buffer doesn't include the last padding.
                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        // Finally, create the Mat.
        Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        mat.put(0, 0, data);

        return mat;
    }

    public static Bitmap detectLane(Image src) {

        Mat mLines;
        if (src.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("src must have format YUV_420_888.");
        }

        Mat gray = new Mat();
        Mat grayRGBA = new Mat();
        mLines = imageToMat(src);
        Imgproc.cvtColor(mLines, gray, COLOR_YUV2GRAY_NV21);

        //ImageThermalMeasurement.getTemperatureMeasurement(gray);

        //Log.d(ImageProcessor.class.getName(), "Max temp: " + ImageThermalMeasurement.getTemperature());

        // Blur image
        Imgproc.blur(gray, gray, new Size(3, 3));

        // Apply canny edge algorithm
        // Imgproc.Canny(gray, gray, 70, 170);
        Imgproc.cvtColor(gray, gray, Imgproc.COLOR_GRAY2RGB);

        //crop image

        int width = gray.width();

        int height = gray.height();

        Rect crop = null;

        if(width > height){

            crop = new Rect((int)((width - height)/2), 0, height, height);

        } else {

            crop = new Rect(0, (height - width)/2, width, width);

        }

        gray = gray.submat(crop);

        //detecção de objetos

        setImage(gray);

        //annotations

        Imgproc.cvtColor(gray, grayRGBA, Imgproc.COLOR_RGB2RGBA);

        ImageAnnotations.makeAnnotations(getResults(), grayRGBA);

        //anotação de temperatura

        //ImageAnnotations.makeTemperatureAnnotation(ImageThermalMeasurement.getTemperature(), grayRGBA);

        Imgproc.cvtColor(grayRGBA, gray, Imgproc.COLOR_RGBA2RGB);

        Bitmap bmp = null;

        try {

            bmp = Bitmap.createBitmap(gray.cols(), gray.rows(), Bitmap.Config.ARGB_8888);

            Utils.matToBitmap(gray, bmp);

        } catch (CvException e) {

            //Log.d("Exception", e.getMessage());

        }

        return bmp;

    }
}
