package wj.redpacket.com.arredpacket;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.Surface;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Created by jiangwei on 17/4/4.
 */

public class CameraUtil {
    private static final Comparator<Camera.Size> SIZE_DESCENDING_COMPARATOR = new Comparator<Camera.Size>() {
        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            return rhs.width * rhs.height - lhs.width * lhs.height;
        }
    };
    private static final double STANDARD_MEDIA_RATIO = 16.0f / 9.0f;


    public static Camera open(Context context) {
        Camera camera;
        PackageManager packageManager = context.getPackageManager();
        if (packageManager != null && packageManager.checkPermission(Manifest.permission.CAMERA, context.getPackageName()) ==
                PackageManager.PERMISSION_GRANTED) {
            try {
                camera = Camera.open();
                if (camera != null) {
                    camera.getParameters(); // for some ROMs, or 360 permission manager, an empty operation to check if camera is released
                }
                return camera;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    public static void chooseBestPreviewSize(Camera camera) {
        double delta = Double.MAX_VALUE;
        Camera.Size suggestedSize = null;
        ArrayList<Camera.Size> sizes = new ArrayList<>(camera.getParameters().getSupportedPreviewSizes());
        if (sizes.size() > 0) {
            Collections.sort(sizes, SIZE_DESCENDING_COMPARATOR);
            for (Camera.Size size : sizes) {
                double d = Math.abs((double) size.width / (double) size.height - STANDARD_MEDIA_RATIO);
                if (d < delta) {
                    delta = d;
                    suggestedSize = size;
                }
            }
        }

        if (suggestedSize != null) {
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(suggestedSize.width, suggestedSize.height);
            parameters.setPictureFormat(ImageFormat.JPEG);
            try {
                camera.setParameters(parameters);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void setCameraDisplayOrientation(Context context, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result = (info.orientation - degrees + 360) % 360;
        camera.setDisplayOrientation(result);
    }
}
