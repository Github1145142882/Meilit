package com.meilit.halo;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.Map;

public final class HaloNotificationListenerService extends NotificationListenerService {
    private static final Map<String, Integer> PACKAGE_COLORS = new HashMap<>();

    static {
        PACKAGE_COLORS.put("com.tencent.mm", 0x0000E676);
        PACKAGE_COLORS.put("com.tencent.mobileqq", 0x002964FF);
        PACKAGE_COLORS.put("org.telegram.messenger", 0x007DD3FC);
        PACKAGE_COLORS.put("com.discord", 0x00B388FF);
        PACKAGE_COLORS.put("com.android.dialer", 0x00FF1744);
        PACKAGE_COLORS.put("com.google.android.dialer", 0x00FF1744);
        PACKAGE_COLORS.put("com.android.mms", 0x00FFEA00);
        PACKAGE_COLORS.put("com.google.android.apps.messaging", 0x00FFEA00);
        PACKAGE_COLORS.put("com.android.calendar", 0x00FF9100);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.isOngoing()) {
            return;
        }
        Integer color = PACKAGE_COLORS.get(sbn.getPackageName());
        Notification notification = sbn.getNotification();
        String category = notification == null ? null : notification.category;
        if (color == null) {
            if (Notification.CATEGORY_CALL.equals(category)) {
                color = 0x00FF1744;
            } else if (Notification.CATEGORY_MESSAGE.equals(category)) {
                color = 0x0000E676;
            } else if (Notification.CATEGORY_ALARM.equals(category) || Notification.CATEGORY_REMINDER.equals(category)) {
                color = 0x00FFEA00;
            }
        }
        if (color != null) {
            HaloEngine.get(this).pulseNotification(color);
        }
    }
}
