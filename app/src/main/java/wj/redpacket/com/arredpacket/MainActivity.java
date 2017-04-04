package wj.redpacket.com.arredpacket;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SurfaceHolder.Callback {

    private Button mHideRed;
    private Button mFindeRed;
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Bitmap mHideBitmap;
    private Bitmap mFindBitmap;
    private boolean mCurrentStateHide = false;
    private AtomicBoolean mFindRed = new AtomicBoolean(false);

    protected Handler mHandler = new Handler();

    private static final int REQUEST_CODE_CAMERA_AND_RECORD_AUDIO_PERMISSION = 1;
    private static final String[] REQUEST_PERMISSIONS = new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN ?
                    Manifest.permission.READ_EXTERNAL_STORAGE :
                    Manifest.permission.WRITE_EXTERNAL_STORAGE /* a place holder for API lower than 16 */,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHideRed = (Button)findViewById(R.id.hide_red);
        mFindeRed = (Button)findViewById(R.id.find_red);
        mSurfaceView = (SurfaceView)findViewById(R.id.surface_view);
        mHideRed.setOnClickListener(this);
        mFindeRed.setOnClickListener(this);
        mFindeRed.setEnabled(false);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        checkPermission();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = CameraUtil.open(this);
        CameraUtil.chooseBestPreviewSize(mCamera);
        CameraUtil.setCameraDisplayOrientation(this, mCamera);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (holder.getSurface() == null) {
            return;
        }
        startCamera();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHandler.removeCallbacksAndMessages(null);
        if (mCamera != null) {
            mCamera.release();
        }
    }

    private void checkPermission() {
        if (!isAllPermissionGranted(REQUEST_PERMISSIONS)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                ActivityCompat.requestPermissions(this, REQUEST_PERMISSIONS,
                        REQUEST_CODE_CAMERA_AND_RECORD_AUDIO_PERMISSION);
            } else {
                ActivityCompat.requestPermissions(this, REQUEST_PERMISSIONS,
                        REQUEST_CODE_CAMERA_AND_RECORD_AUDIO_PERMISSION);
            }
        }
    }

    private boolean isAllPermissionGranted(String[] permission) {
        if (permission != null) {
            boolean hasUnGranted = false;
            for (String s : permission) {
                if (ContextCompat.checkSelfPermission(this, s)
                        != PermissionChecker.PERMISSION_GRANTED) {
                    hasUnGranted = true;
                }
            }
            return !hasUnGranted;
        }

        return true;
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.hide_red:
                mCurrentStateHide = true;
                takeOneFrame();
                break;
            case R.id.find_red:
                mFindRed.set(false);
                mCurrentStateHide = false;
                mHideRed.setEnabled(false);
                mFindeRed.setEnabled(false);
                startCamera();
                mHandler.postDelayed(mSchedualTakeOneFrame, 1000);
                break;
        }
    }

    private void startCamera() {
        if (mCamera != null) {
            try {
                mCamera.stopPreview();
                mCamera.setPreviewDisplay(mSurfaceHolder);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mCamera.startPreview();
        }
    }

    private Runnable mSchedualTakeOneFrame=new Runnable() {
        @Override
        public void run() {
            takeOneFrame();
            mHandler.postDelayed(this, 100);
        }
    };

    private void takeOneFrame() {
        if (mCamera == null){
            return;
        }
        mCamera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(final byte[] data, final Camera camera) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Camera.Size size = camera.getParameters().getPreviewSize();
                        YuvImage image = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                        if (mCurrentStateHide) {
                            mHideBitmap = bitmap;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mFindeRed.setEnabled(true);
                                }
                            });
                            try {
                                mCamera.stopPreview();
                            } catch (Exception e) {
                                //// TODO: 17/4/4
                            }
                        } else {
                            mFindBitmap = bitmap;
                            float similar = BitmapCompare.similarity(mHideBitmap, mFindBitmap);
                            Log.e("MainActivity", "" + similar);
                            try {
                                if (similar > 85.555f) {
                                    if (mFindRed.get()) {
                                        return;
                                    }
                                    mFindRed.set(true);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this, getString(R.string.find_success), Toast.LENGTH_SHORT).show();
                                            mHandler.removeCallbacksAndMessages(null);
                                            mHideRed.setEnabled(true);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                //// TODO: 17/4/4
                            }
                        }
                    }
                }).start();
            }
        });
    }


}
