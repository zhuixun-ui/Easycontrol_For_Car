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



public class MainActivity extends Activity {
  // 设备列表
  private DeviceListAdapter deviceListAdapter;
  private ConnectHelper connectHelper;



  // 创建界面
  private ActivityMainBinding mainActivity;

  @SuppressLint("SourceLockedOrientationActivity")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AppData.init(this);
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
        new Thread(() -> {
        while (Client.allClient.isEmpty()) {
            try {
                Thread.sleep(3000);
                runOnUiThread(() -> android.widget.Toast.makeText(this, "模拟USB拔插...", android.widget.Toast.LENGTH_SHORT).show());
                
                // 第一步：将设备设置为仅充电模式
                Runtime.getRuntime().exec(new String[]{"su", "-c", "setprop sys.usb.config none"});
                Thread.sleep(500);
                
                // 第二步：关键修改！先将USB配置设为MTP+ADB模式
                Runtime.getRuntime().exec(new String[]{"su", "-c", "setprop sys.usb.config mtp,adb"});
                Thread.sleep(1000); // 为ADB连接稳定预留时间
    
                // 第三步：强制重新启动ADB服务
                Runtime.getRuntime().exec(new String[]{"su", "-c", "stop adbd"});
                Thread.sleep(500);
                Runtime.getRuntime().exec(new String[]{"su", "-c", "start adbd"});
    
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        runOnUiThread(() -> android.widget.Toast.makeText(this, "设备已连接，停止模拟", android.widget.Toast.LENGTH_SHORT).show());
    }).start();
  }


  
  @Override
  protected void onDestroy() {
    AppData.uiHandler.removeCallbacks(connectHelper.showStartDefaultUSB);
    AppData.myBroadcastReceiver.setDeviceListAdapter(null);
    AppData.myBroadcastReceiver.setConnectHelper(null);
    ConnectHelper.status = false;
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
}
