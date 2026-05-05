package top.eiyooooo.easycontrol.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;

import java.util.UUID;

import android.view.animation.LinearInterpolator;

import top.eiyooooo.easycontrol.app.client.Client;
import top.eiyooooo.easycontrol.app.databinding.ActivityMainBinding;
import top.eiyooooo.easycontrol.app.databinding.ItemRequestPermissionBinding;
import top.eiyooooo.easycontrol.app.entity.AppData;
import top.eiyooooo.easycontrol.app.entity.Device;
import top.eiyooooo.easycontrol.app.helper.DeviceListAdapter;
import top.eiyooooo.easycontrol.app.helper.PublicTools;
import top.eiyooooo.easycontrol.app.helper.ConnectHelper;

import android.widget.Toast;
import android.os.Handler;



public class MainActivity extends Activity {
  // 设备列表
  private DeviceListAdapter deviceListAdapter;
  private ConnectHelper connectHelper;

  private static MainActivity instance;
  private android.os.Handler autoUsbHandler;
  private Runnable autoUsbRunnable;

  // 创建界面
  private ActivityMainBinding mainActivity;

  @SuppressLint("SourceLockedOrientationActivity")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AppData.init(this);
    
    instance = this;
    
    PublicTools.setStatusAndNavBar(this);
    PublicTools.setLocale(this);
    mainActivity = ActivityMainBinding.inflate(this.getLayoutInflater());
    setContentView(mainActivity.getRoot());
    // 检测权限
    if (AppData.setting.getAlwaysFullMode() || haveOverlayPermission()) startApp();
    else createAlert();
  }

 

  
  private void startApp() {
    // 设置设备列表适配器、广播接收器
    deviceListAdapter = new DeviceListAdapter(this, mainActivity.devicesList);
    mainActivity.devicesList.setAdapter(deviceListAdapter);
    AppData.myBroadcastReceiver.setDeviceListAdapter(deviceListAdapter);
    connectHelper = new ConnectHelper(this);
    AppData.myBroadcastReceiver.setConnectHelper(connectHelper);
    ConnectHelper.status = true;
    // 设置按钮监听
    setButtonListener();
    // 首次使用显示使用说明
    if (!AppData.setting.getShowUsage()) {
      AppData.setting.setShowUsage(true);
      AppData.uiHandler.postDelayed(() -> PublicTools.openWebViewActivity(this, "file:///android_asset/usage.html"), 1500);
    }
    if (!Client.allClient.isEmpty()) {
      for (Client client : Client.allClient) {
        if (client.clientView.viewMode == 3) {
          client.clientView.changeToFull();
          break;
        }
      }
    }
    startAutoUsbReset();
  }


  
  @Override
  protected void onDestroy() {
    stopAutoUsbReset();
    AppData.uiHandler.removeCallbacks(connectHelper.showStartDefaultUSB);
    AppData.myBroadcastReceiver.setDeviceListAdapter(null);
    AppData.myBroadcastReceiver.setConnectHelper(null);
    ConnectHelper.status = false;
    instance = null;
    super.onDestroy();

  }

  @Override
  protected void onPause() {
    AppData.uiHandler.removeCallbacks(connectHelper.showStartDefaultUSB);
    ConnectHelper.status = false;
    super.onPause();
  }

  @Override
  protected void onResume() {
    ConnectHelper.status = true;
    if (!AppData.setting.getAlwaysFullMode() && !haveOverlayPermission()) createAlert();
    else {
      if (!Client.allClient.isEmpty()) {
        for (Client client : Client.allClient) {
          if (client.clientView.viewMode == 3) {
            client.clientView.changeToFull();
            break;
          }
        }
      }
    }
    super.onResume();
  }

  // 检查权限
  private boolean haveOverlayPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return Settings.canDrawOverlays(this);
    else return PublicTools.checkOpNoThrow(this, "OP_SYSTEM_ALERT_WINDOW", 24);
  }

  // 创建无权限提示
  private void createAlert() {
    ItemRequestPermissionBinding requestPermissionView = ItemRequestPermissionBinding.inflate(LayoutInflater.from(this));
    requestPermissionView.buttonGoToSet.setOnClickListener(v -> startActivity(PublicTools.getOverlayPermissionIntent(this)));
    requestPermissionView.buttonAlwaysFullMode.setOnClickListener(v -> AppData.setting.setAlwaysFullMode(true));
    Dialog dialog = PublicTools.createDialog(this, false, requestPermissionView.getRoot());
    dialog.setOnCancelListener(dialog1 -> {
      if (!AppData.setting.getAlwaysFullMode() && !haveOverlayPermission()) dialog.show();
    });
    dialog.show();
    checkPermissionDelay(dialog);
  }

  // 定时检查
  private void checkPermissionDelay(Dialog dialog) {
    // 因为某些设备可能会无法进入设置或其他问题，导致不会有返回结果，为了减少不确定性，使用定时检测的方法
    AppData.uiHandler.postDelayed(() -> {
      if (AppData.setting.getAlwaysFullMode() || haveOverlayPermission()) {
        dialog.cancel();
        startApp();
      } else checkPermissionDelay(dialog);
    }, 1000);
  }

public static void onDeviceConnected() {
    if (instance != null) {
        instance.stopAutoUsbReset();
    }
}
  

  // 设置按钮监听
  private void setButtonListener() {
    mainActivity.buttonRefresh.setOnClickListener(v -> {
      mainActivity.buttonRefresh.setClickable(false);
      deviceListAdapter.update();

      ObjectAnimator rotation = ObjectAnimator.ofFloat(mainActivity.buttonRefresh, "rotation", 0f, 360f);
      rotation.setDuration(800);
      rotation.setInterpolator(new LinearInterpolator());
      rotation.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          super.onAnimationEnd(animation);
          if (deviceListAdapter.checkConnectionExecutor != null) rotation.start();
          else mainActivity.buttonRefresh.setClickable(true);
        }
      });
      rotation.start();
    });
    mainActivity.buttonPair.setOnClickListener(v -> startActivity(new Intent(this, PairActivity.class)));
    mainActivity.buttonAdd.setOnClickListener(v -> PublicTools.createAddDeviceView(this, Device.getDefaultDevice(UUID.randomUUID().toString(), Device.TYPE_NORMAL), deviceListAdapter).show());
    mainActivity.buttonSet.setOnClickListener(v -> startActivity(new Intent(this, SetActivity.class)));
  }

    private void startAutoUsbReset() {
        // 已有设备连接则不再启动
        if (!Client.allClient.isEmpty()) return;
    
        autoUsbHandler = new android.os.Handler();
        autoUsbRunnable = new Runnable() {
            @Override
            public void run() {
                // 执行前再次检查是否有设备连接
                if (!Client.allClient.isEmpty()) {
                    stopAutoUsbReset();
                    return;
                }
    
                new Thread(() -> {
                    try {
                        // 需要 root 权限，执行 svc usb disable / enable
                        Runtime.getRuntime().exec(new String[]{"su", "-c", "svc usb disable"}).waitFor();
                        Thread.sleep(300);
                        Runtime.getRuntime().exec(new String[]{"su", "-c", "svc usb enable"}).waitFor();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "模拟USB拔插", Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "USB模拟失败，请检查root权限", Toast.LENGTH_LONG).show());
                        stopAutoUsbReset();
                    }
                }).start();
    
                // 3 秒后再次执行
                autoUsbHandler.postDelayed(this, 3000);
            }
        };
        autoUsbHandler.post(autoUsbRunnable);
    }
    
    private void stopAutoUsbReset() {
        if (autoUsbHandler != null && autoUsbRunnable != null) {
            autoUsbHandler.removeCallbacks(autoUsbRunnable);
            autoUsbHandler = null;
            autoUsbRunnable = null;
        }
    }
      
}
