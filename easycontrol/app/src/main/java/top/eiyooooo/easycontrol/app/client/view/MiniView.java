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
    private volatile boolean hasCameraTriggered = false; // 是否已经触发过相机自动全屏
    private int previousViewMode = -1; // 记录触发全屏前的模式

    // 相机包名列表
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

        // 强制启动相机检测
        PublicTools.logToast("相机检测已启动");
        shouldMonitorCamera = true;
        hasCameraTriggered = false;
        previousViewMode = -1;
        if (cameraDetectionThread != null) cameraDetectionThread.interrupt();
        cameraDetectionThread = new Thread(this::cameraDetectionLoop);
        cameraDetectionThread.start();
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
                String currentPkg = getForegroundPackage();
                boolean isCamera = isCameraPackage(currentPkg);

                if (isCamera && !hasCameraTriggered) {
                    // 相机出现且尚未触发：记录当前模式并切换到全屏
                    previousViewMode = clientView.viewMode;
                    PublicTools.logToast("检测到相机，切换到全屏 (之前模式=" + previousViewMode + ")");
                    AppData.uiHandler.post(() -> {
                        if (shouldMonitorCamera) {
                            clientView.changeToFull();
                            hasCameraTriggered = true;
                        }
                    });
                } else if (!isCamera && hasCameraTriggered) {
                    // 相机消失且之前触发过：恢复到之前的模式
                    PublicTools.logToast("相机已退出，恢复到模式 " + previousViewMode);
                    AppData.uiHandler.post(() -> {
                        if (shouldMonitorCamera) {
                            switch (previousViewMode) {
                                case 1:
                                    clientView.changeToMini(0); // 传入mode参数，一般用0
                                    break;
                                case 2:
                                    clientView.changeToSmall();
                                    break;
                                default:
                                    // 如果之前是全屏或未知，不做操作或恢复到小窗
                                    clientView.changeToSmall();
                                    break;
                            }
                            hasCameraTriggered = false;
                            previousViewMode = -1;
                        }
                    });
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getForegroundPackage() {
        try {
            // 执行完整 dumpsys window 命令（不用 grep）
            String result = clientView.getClient().adb.runAdbCmd("dumpsys window");
            if (result == null || result.isEmpty()) {
                return null;
            }
            // 按行查找 mCurrentFocus
            String[] lines = result.split("\n");
            for (String line : lines) {
                if (line.contains("mCurrentFocus")) {
                    // 提取包名：找到 u0 后跟包名
                    int idx = line.indexOf("u0");
                    if (idx == -1) continue;
                    int start = idx + 2;
                    while (start < line.length() && (line.charAt(start) == ' ' || line.charAt(start) == '{')) {
                        start++;
                    }
                    int end = line.indexOf("/", start);
                    if (end == -1) continue;
                    return line.substring(start, end);
                }
            }
            // 备选：查找 mFocusedApp
            for (String line : lines) {
                if (line.contains("mFocusedApp")) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("u0\\s+([\\w.]+)/");
                    java.util.regex.Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean isCameraPackage(String pkg) {
        if (pkg == null) return false;
        for (String cameraPkg : CAMERA_PACKAGES) {
            if (cameraPkg.equals(pkg)) return true;
        }
        return false;
    }
}
