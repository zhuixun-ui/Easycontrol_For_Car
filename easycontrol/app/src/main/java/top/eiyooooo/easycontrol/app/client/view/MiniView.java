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
        String result = clientView.getClient().adb.runAdbCmd("dumpsys window mCurrentFocus");
        if (result == null || result.isEmpty()) {
            PublicTools.logToast("命令返回空");
            return null;
        }
        
        // 将原始输出写入文件（方便查看）
        try {
            java.io.FileWriter fw = new java.io.FileWriter(AppData.main.getExternalFilesDir(null) + "/dump_focus.txt");
            fw.write(result);
            fw.close();
            PublicTools.logToast("已写入文件: dump_focus.txt");
        } catch (Exception e) {
            PublicTools.logToast("写文件失败: " + e.getMessage());
        }
        
        // 增强解析：尝试多种方式提取包名
        String pkg = null;
        // 方式1：查找 u0 和 / 之间的内容
        int idx = result.indexOf("u0");
        if (idx != -1) {
            int start = idx + 2;
            // 跳过空格或 {
            while (start < result.length() && (result.charAt(start) == ' ' || result.charAt(start) == '{')) start++;
            int end = result.indexOf("/", start);
            if (end != -1) {
                pkg = result.substring(start, end);
            }
        }
        // 方式2：如果上面没找到，尝试正则匹配 Window{... u0 xxx/...
        if (pkg == null) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("u0\\s+([\\w.]+)/");
            java.util.regex.Matcher matcher = pattern.matcher(result);
            if (matcher.find()) {
                pkg = matcher.group(1);
            }
        }
        
        if (pkg != null) {
            PublicTools.logToast("提取包名: " + pkg);
            return pkg;
        } else {
            PublicTools.logToast("解析失败, 原始内容已写入文件");
            return null;
        }
    } catch (Exception e) {
        e.printStackTrace();
        PublicTools.logToast("异常: " + e.getMessage());
        return null;
    }
}

    private boolean isCameraPackage(String pkg) {
        for (String cameraPkg : CAMERA_PACKAGES) {
            if (cameraPkg.equals(pkg)) return true;
        }
        return false;
    }
}
