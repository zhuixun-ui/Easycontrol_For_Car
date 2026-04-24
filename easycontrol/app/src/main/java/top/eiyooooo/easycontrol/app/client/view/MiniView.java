package top.eiyooooo.easycontrol.app.client.view;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
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

    private static final String[] CAMERA_PACKAGES = {
            "com.android.camera"
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

        PublicTools.logToast("相机检测已启动");
        shouldMonitorCamera = true;
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
                if (currentPkg != null) {
                    Log.d("CameraCheck", "当前前台包名: " + currentPkg);
                    if (isCameraPackage(currentPkg)) {
                        PublicTools.logToast("检测到相机：" + currentPkg);
                        AppData.uiHandler.post(() -> {
                            if (shouldMonitorCamera) {
                                clientView.changeToFull();
                            }
                        });
                        return;
                    }
                } else {
                    Log.d("CameraCheck", "获取包名失败");
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
            PublicTools.logToast("命令返回空");
            return null;
        }
        // 按行查找 mCurrentFocus
        String[] lines = result.split("\n");
        for (String line : lines) {
            if (line.contains("mCurrentFocus")) {
                // 提取包名：找到 u0 后跟包名
                int idx = line.indexOf("u0");
                if (idx == -1) continue;
                // 从 u0 后面开始解析，跳过空格和大括号
                int start = idx + 2;
                while (start < line.length() && (line.charAt(start) == ' ' || line.charAt(start) == '{')) {
                    start++;
                }
                int end = line.indexOf("/", start);
                if (end == -1) continue;
                String pkg = line.substring(start, end);
                PublicTools.logToast("前台包名: " + pkg);
                return pkg;
            }
        }
        // 如果找不到 mCurrentFocus，再尝试找 mFocusedApp 或 mFocusedWindow
        for (String line : lines) {
            if (line.contains("mFocusedApp") || line.contains("mFocusedWindow")) {
                // 类似格式中提取包名
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("u0\\s+([\\w.]+)/");
                java.util.regex.Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String pkg = matcher.group(1);
                    PublicTools.logToast("前台包名(备选): " + pkg);
                    return pkg;
                }
            }
        }
        PublicTools.logToast("未找到焦点信息");
    } catch (Exception e) {
        e.printStackTrace();
        PublicTools.logToast("异常: " + e.getMessage());
    }
    return null;
}

    private boolean isCameraPackage(String pkg) {
        for (String cameraPkg : CAMERA_PACKAGES) {
            if (cameraPkg.equals(pkg)) return true;
        }
        return false;
    }
}
