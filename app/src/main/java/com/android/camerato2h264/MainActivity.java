package com.android.camerato2h264;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.libyuv.util.YuvUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class MainActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceView surfaceview;

    private SurfaceHolder surfaceHolder;

    private Camera camera;

    private Camera.Parameters parameters;

    int width = 1280;
    int height = 720;

    int width2 = 1280;
    int height2 = 720;

    int dstWith = 640;
    int dstHeight = 480;

    int framerate = 30;

    int biterate = 8500 * 1000;

    private static int yuvqueuesize = 10;

    //待解码视频缓冲队列，静态成员！
    public static ArrayBlockingQueue<byte[]> YUVQueue1 = new ArrayBlockingQueue<byte[]>(yuvqueuesize);

    public static ArrayBlockingQueue<byte[]> YUVQueue2 = new ArrayBlockingQueue<byte[]>(yuvqueuesize);

    private AvcEncoder1 avcCodec1;

    private AvcEncoder2 avcCodec2;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    BitmapSurfaceView bitmapSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceview = (SurfaceView) findViewById(R.id.surfaceview);
        surfaceHolder = surfaceview.getHolder();
        surfaceHolder.addCallback(this);

        bitmapSurfaceView = findViewById(R.id.surfaceview_copy);
        dstWith = getResources().getDimensionPixelSize(R.dimen.video_width);
        dstHeight = getResources().getDimensionPixelSize(R.dimen.video_height);

        //onCreate: dstWith：640---dstHeight:480
        Log.e(TAG, "onCreate: dstWith：" + dstWith + "---dstHeight:" + dstHeight);

        // Example of a call to a native method
//        TextView tv = findViewById(R.id.sample_text);
//        tv.setText(stringFromJNI());
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        camera = getBackCamera();
        startcamera(camera);
        //创建AvEncoder对象:初始化编码的分辨率，测试先用
        avcCodec1 = new AvcEncoder1(dstHeight, dstWith, framerate, biterate, "test1.h264");
//        //启动编码线程
        avcCodec1.StartEncoderThread();

        avcCodec2 = new AvcEncoder2(width, height, framerate, biterate, "test2.h264");
        avcCodec2.StartEncoderThread();

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (null != camera) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;

            avcCodec1.StopThread();
            avcCodec2.StopThread();
        }
    }


    @Override
    public void onPreviewFrame(byte[] data, android.hardware.Camera camera) {

        Log.d(TAG, "onPreviewFrame: data:" + data.length);

        //将当前帧图像保存在队列中
//        putYUVData(data, data.length);
        putYUVData2(data, data.length);

        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        int width2 = previewSize.width;
        int height2 = previewSize.height;
        try {
            // NV21  转 I420
            byte[] i420Data = new byte[width2 * height2 * 3 / 2];
            YuvUtil.NV21ToI420(data, width2, height2, i420Data);

            // 镜像
//            byte[] i420MirrorData = new byte[width2 * height2 * 3 / 2];
//            YuvUtil.I420Mirror(i420Data, width2, height2, i420MirrorData);

            // 缩放，注意缩放后宽高会改变
            byte[] i420ScaleData = new byte[dstWith * dstHeight * 3 / 2];
//            YuvUtil.I420Scale(i420MirrorData, width2, height2, i420ScaleData, dstWith, dstHeight, 0);
            YuvUtil.I420Scale(i420Data, width2, height2, i420ScaleData, dstWith, dstHeight, 0);
            width2 = dstWith;
            height2 = dstHeight;

            // 旋转： 注意顺时针旋转 90 度后宽高对调了
//            byte[] i420RotateData = new byte[width2 * height2 * 3 / 2];
//            YuvUtil.I420Rotate(i420ScaleData, width2, height2, i420RotateData, 90);
//            int temp = width2;
//            width2 = height2;
//            height2 = temp;

            // I420 -> NV21
            byte[] newNV21Data = new byte[width2 * height2 * 3 / 2];
//            YuvUtil.I420ToNV21(i420RotateData, width2, height2, newNV21Data);
            YuvUtil.I420ToNV21(i420ScaleData, width2, height2, newNV21Data);

            Log.e(TAG, "onPreviewFrame newNV21Data: " + newNV21Data.length);
            putYUVData1(newNV21Data, newNV21Data.length);

            // 转Bitmap
            YuvImage yuvImage = new YuvImage(newNV21Data, ImageFormat.NV21, width2, height2, null);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width2, height2), 80, stream);
            Bitmap bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
            stream.close();

            Log.e(TAG, "onPreviewFrame stream.toByteArray(): " + stream.toByteArray().length);

            //宽: 640----高：480
            Log.e(TAG, "onPreviewFrame 宽: " + bitmap.getWidth() + "----高：" + bitmap.getHeight());

            if (bitmap != null) {
                bitmapSurfaceView.drawBitmap(bitmap);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void putYUVData1(byte[] buffer, int length) {
        if (YUVQueue1.size() >= 10) {
            YUVQueue1.poll();
        }
        YUVQueue1.add(buffer);
    }

    public void putYUVData2(byte[] buffer, int length) {
        if (YUVQueue2.size() >= 10) {
            YUVQueue2.poll();
        }
        YUVQueue2.add(buffer);
    }

    private static final String TAG = "MainActivity2";

    private void startcamera(Camera mCamera) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(this);
                mCamera.setDisplayOrientation(90);
                if (parameters == null) {
                    parameters = mCamera.getParameters();
                }
                //获取默认的camera配置
                parameters = mCamera.getParameters();

                List<Camera.Size> previewSizes = camera.getParameters().getSupportedPreviewSizes();
                //获取预览的分辨率:motic
                //*******previewSize.width = 176-----------------previewSize.height = 144
                //*******previewSize.width = 320-----------------previewSize.height = 240
                //*******previewSize.width = 352-----------------previewSize.height = 288
                //*******previewSize.width = 640-----------------previewSize.height = 480
                //*******previewSize.width = 720-----------------previewSize.height = 480
                //*******previewSize.width = 800-----------------previewSize.height = 600
                //*******previewSize.width = 1280-----------------previewSize.height = 720
                //*******previewSize.width = 1920-----------------previewSize.height = 1080
                //*******previewSize.width = 2688-----------------previewSize.height = 1520
                //*******previewSize.width = 960-----------------previewSize.height = 540
                for (int i = 0; i < previewSizes.size(); i++) {
                    Camera.Size pSize = previewSizes.get(i);
                    Log.i(TAG, "*******previewSize.width = " + pSize.width + "-----------------previewSize.height = " + pSize.height);
                }

                //设置预览格式
                parameters.setPreviewFormat(ImageFormat.NV21);
                //设置预览图像分辨率
                parameters.setPreviewSize(width, height);
                Camera.Size size = parameters.getPreviewSize();
                Log.e(TAG, "OpenCamera 当前预览的宽: " + size.width);
                Log.e(TAG, "OpenCamera  当前预览的高: " + size.height);
                //配置camera参数
                mCamera.setParameters(parameters);
                //将完全初始化的SurfaceHolder传入到setPreviewDisplay(SurfaceHolder)中
                //没有surface的话，相机不会开启preview预览
                mCamera.setPreviewDisplay(surfaceHolder);
                //调用startPreview()用以更新preview的surface，必须要在拍照之前start Preview
                mCamera.startPreview();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Camera getBackCamera() {
        Camera c = null;
        try {
            //获取Camera的实例
            c = Camera.open(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //获取Camera的实例失败时返回null
        return c;
    }


}
