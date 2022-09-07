package com.hmd.vr_firefighter_app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.vr.sdk.base.GvrActivity
import com.google.vr.sdk.base.GvrView
import com.hdm.vr_firefighter_app.R
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usbcameracommon.UVCCameraHandler
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import kotlin.system.exitProcess


class MainActivity : GvrActivity(), GvrRenderer.GvrRendererEvents {

    private var cameraView: GvrView? = null
    private var gvrRenderer: GvrRenderer? = null

    private var cameraDevice: CameraDevice? = null
    private var cameraManager: CameraManager? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var previewSession: CameraCaptureSession? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var previewSize: Size? = Size(720, 720)
    private var mSurface: Surface? = null
    var mImageReader: ImageReader? = null
    private val mImageFormat = ImageFormat.YUV_420_888

    private val mUSBMonitor: USBMonitor? = null

    private val mHandlerFirst: UVCCameraHandler? = null

    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            //Log.d(TAG, "onDisconnected")
        }

        override fun onError(cameraDevice: CameraDevice, i: Int) {
            Log.e(TAG, "onError")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        //Checking the request code of our request
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            //If permission has not been granted
            if (!(grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                closeApp()
            }
            else {
                if(surfaceTexture != null) {
                    openCamera()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // if android version is 6 or higher
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )

        if (!OpenCVLoader.initDebug()) {
            // Opencv library was not found --> trying to initialize
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
        } else {
            // Opencv library found
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }

        MobileNetObjDetector.create(assets)

        ImageAnnotations.init(applicationContext)

        ObjectDetectionHandler.init()

        ObjectDetectionHandler.stopThread()

        ObjectDetectionHandler.startThread()

        setContentView(R.layout.activity_main)

        cameraView = findViewById(R.id.camera_view)
        gvrView = cameraView

        gvrRenderer = GvrRenderer(cameraView, this)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        counter = FPSCounter()
    }

    override fun onResume() {
        super.onResume()
        ObjectDetectionHandler.stopThread()
        ObjectDetectionHandler.startThread()
        cameraView!!.onResume()
    }

    override fun onPause() {
        super.onPause()
        ObjectDetectionHandler.stopThread()
        cameraView!!.onPause()
    }

    private fun openCamera() {
        try {
            //selecionando o camera id
            //TODO selecionar a câmera externa
            val cameraId = cameraManager!!.cameraIdList[0]
            cameraManager!!.openCamera(cameraId, stateCallback, cameraView!!.handler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
            e.printStackTrace()
        }

        mImageReader = ImageReader.newInstance(previewSize!!.width, previewSize!!.height,
                mImageFormat, /*maxImages*/ 2)
        //Log.d(TAG, previewSize!!.width.toString())
        //Log.d(TAG, previewSize!!.height.toString())
    }

    fun Bitmap.flip(): Bitmap {
        val matrix = Matrix().apply { postScale(-1f, 1f, width/2f, width/2f) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private val mOnImageAvailableListener =
        ImageReader.OnImageAvailableListener { reader ->
            val image: Image?
            val result: Bitmap
            try {
                image = reader.acquireLatestImage()
                if (image == null) {
                    return@OnImageAvailableListener
                }
                result = ImageProcessor.detectLane(image)
                counter!!.logFrame()
                gvrRenderer!!.bitmapImage = result.flip()

            } catch (e: java.lang.IllegalStateException) {
                return@OnImageAvailableListener
            }
            image.close()
        }

    private var counter: FPSCounter? = null

    class FPSCounter {
        var startTime = System.nanoTime()
        var frames = 0
        fun logFrame() {
            frames++
            if (System.nanoTime() - startTime >= 1000000000) {
                ////Log.d("CAMERA", "fps: $frames")
                frames = 0
                startTime = System.nanoTime()
            }
        }
    }

    protected fun startPreview() {
        val mPreviewRequestBuilder: CaptureRequest.Builder
        if (cameraDevice == null) {
            Log.e(TAG, "preview failed")
            return
        }

        mSurface = Surface(surfaceTexture)

        try {
            previewBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        previewBuilder!!.addTarget(mImageReader!!.surface)

        val outputSurfaces: MutableList<Surface> = ArrayList()
        outputSurfaces.add(mImageReader!!.surface)

        try {
            cameraDevice!!.createCaptureSession(outputSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        previewSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Log.e(TAG, "onConfigureFailed")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    protected fun updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "update Preview error, return")
        }

        previewBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        previewBuilder!!.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

        val thread = HandlerThread("CameraPreview")
        thread.start()
        val backgroundHandler = Handler(thread.looper)

        try {
            previewSession!!.setRepeatingRequest(previewBuilder!!.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        mImageReader?.setOnImageAvailableListener(mOnImageAvailableListener, backgroundHandler)

    }

    override fun onSurfaceTextureCreated(mSurfaceTexture: SurfaceTexture) {
        this.surfaceTexture = mSurfaceTexture
        openCamera()
    }

    private fun closeApp() {
        ObjectDetectionHandler.stopThread()
        finishAffinity()
        exitProcess(0)
    }

    companion object {
        private val TAG = "MainActivity"
        private const val CAMERA_PERMISSION_REQUEST = 1
    }
}


