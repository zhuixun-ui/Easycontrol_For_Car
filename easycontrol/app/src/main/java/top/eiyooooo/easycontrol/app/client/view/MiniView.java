package top.eiyooooo.easycontrol.app.client.view;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.concurrent.atomic.AtomicInteger;

import top.eiyooooo.easycontrol.app.entity.AppData;
import top.eiyooooo.easycontrol.app.helper.PublicTools;
import top.eiyooooo.easycontrol.app.R;
import top.eiyooooo.easycontrol.app.databinding.ModuleMiniViewBinding;

public class MiniView {

    private final ClientView clientView;
    private Thread cameraDetectionThread;
    private volatile boolean shouldMonitorCamera = false;

    // 相机包名列表，可按需增删
    private static final String[] CAMERA_PACKAGES = {
            "com.android.camera",
            "com.sec.android.app.camera",
            "com.huawei.camera",
            "com.xiaomi.camera",
            "com.oppo.camera",
            "com.vivo.camera",
            "com.oneplus.camera",
            "com.google.android.GoogleCamera"
    };

    private final ModuleMiniViewBinding miniView = ModuleMiniViewBinding.inflate(LayoutInflater.from(AppData.main));
    private final WindowManager.LayoutParams miniViewParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
    );

    private static int num = 0;

    public MiniView(ClientView clientView) {
        this.clientView = clientView;
        miniViewParams.gravity = Gravity.START | Gravity.TOP;
        miniViewParams.x = 0;
        int colorNum = num++ % 4;
        int barColor = R.color.bar1;
        if (colorNum == 1) barColor = R.color.bar2;
        else if (colorNum == 2) barColor = R.color.bar3;
        else if (colorNum == 3) barColor = R.color.bar4;
        miniView.bar.setBackgroundTintList(ColorStateList.valueOf(AppData.main.getResources().getColor(barColor)));
    }

    public void show(int mode) {
        miniViewParams.y = clientView.device.mini_y;
        setBarListener();
        clientView.viewAnim(miniView.getRoot(), true, PublicTools.dp2px(-40f), 0, (isStart -> {
            if (isStart) {
                miniView.getRoot().setVisibility(View.VISIBLE);
                AppData.windowManager.addView(miniView.getRoot(), miniViewParams);
            }
        }));

        // 使用原设置开关，现在改为相机自动恢复
        if (mode != 0 && AppData.setting.getMiniRecoverOnTimeout()) {
            shouldMonitorCamera = true;
            if (cameraDetectionThread != null) cameraDetectionThread.interrupt();
            cameraDetectionThread = new Thread(this::cameraDetectionLoop);
            cameraDetectionThread.start();
        }
    }

    public void hide() {
        try {
            shouldMonitorCamera = false;
            miniView.getRoot().setVisibility(View.GONE);
            AppData.windowManager.removeView(miniView.getRoot());
            clientView.updateDevice();
            if (cameraDetectionThread != null) cameraDetectionThread.interrupt();
        } catch (Exception ignored) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setBarListener() {
        AtomicInteger yy = new AtomicInteger();
        AtomicInteger oldYy = new AtomicInteger();
        miniView.getRoot().setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_OUTSIDE:
                    clientView.lastTouchIsInside = false;
                    break;
                case MotionEvent.ACTION_DOWN:
                    clientView.lastTouchIsInside = true;
                    yy.set((int) event.getRawY());
                    oldYy.set(miniViewParams.y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    clientView.lastTouchIsInside = true;
                    miniViewParams.y = oldYy.get() + (int) event.getRawY() - yy.get();
                    clientView.device.mini_y = miniViewParams.y;
                    AppData.windowManager.updateViewLayout(miniView.getRoot(), miniViewParams);
                    break;
                case MotionEvent.ACTION_UP:
                    clientView.lastTouchIsInside = true;
                    int flipY = (int) (yy.get() - event.getRawY());
                    if (flipY * flipY < 16) clientView.changeToSmall();
                    break;
            }
            return true;
        });
    }

    private void cameraDetectionLoop() {
        try {
            while (!Thread.interrupted() && shouldMonitorCamera) {
                Thread.sleep(1000);
                if (isCameraAppForeground()) {
                    AppData.uiHandler.post(() -> {
                        if (shouldMonitorCamera) {
                            clientView.changeToFull();
                        }
                    });
                    return;
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }
    }

    private boolean isCameraAppForeground() {
        try {
            String result = clientView.getClient().adb.runAdbCmd("shell dumpsys activity activities | grep -i mResumedActivity | tail -1");
            if (result == null || result.isEmpty()) return false;

            int start = result.indexOf("u0 ");
            if (start == -1) return false;
            start += 3;
            int end = result.indexOf("/", start);
            if (end == -1) return false;
            String currentPkg = result.substring(start, end);

            for (String cameraPkg : CAMERA_PACKAGES) {
                if (cameraPkg.equals(currentPkg)) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
