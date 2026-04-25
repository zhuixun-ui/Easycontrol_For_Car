package top.eiyooooo.easycontrol.app.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import top.eiyooooo.easycontrol.app.helper.PublicTools;

public class UsbPermissionAutoClickService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            // 检测到系统 USB 权限请求对话框
            if (packageName.equals("com.android.systemui") || packageName.equals("android")) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    tryAutoClickButton(root, "允许");
                    tryAutoClickButton(root, "确定");
                    tryAutoClickButton(root, "OK");
                    root.recycle();
                }
            }
        }
    }

    private void tryAutoClickButton(AccessibilityNodeInfo node, String text) {
        if (node == null) return;
        // 如果当前节点是按钮且文本匹配
        if (node.isClickable() && node.getText() != null && node.getText().toString().contains(text)) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            PublicTools.logToast("自动点击USB授权: " + text);
            return;
        }
        // 递归查找子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                tryAutoClickButton(child, text);
                child.recycle();
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // 设置监听的事件类型
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
    }
}
