#!/system/bin/sh
PKG="com.meilit.halo"
LISTENER="com.meilit.halo/com.meilit.halo.HaloNotificationListenerService"

sleep 20

pm grant "$PKG" android.permission.RECORD_AUDIO >/dev/null 2>&1
pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
pm grant "$PKG" android.permission.WAKE_LOCK >/dev/null 2>&1
pm grant "$PKG" android.permission.FOREGROUND_SERVICE >/dev/null 2>&1
pm grant "$PKG" android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK >/dev/null 2>&1

dumpsys deviceidle whitelist +"$PKG" >/dev/null 2>&1
cmd notification allow_listener "$LISTENER" >/dev/null 2>&1
CURRENT="$(settings get secure enabled_notification_listeners 2>/dev/null)"
case "$CURRENT" in
  *"$LISTENER"*) ;;
  ""|"null") settings put secure enabled_notification_listeners "$LISTENER" ;;
  *) settings put secure enabled_notification_listeners "$CURRENT:$LISTENER" ;;
esac

am start-foreground-service -n "$PKG/.HaloForegroundService" -a com.meilit.halo.action.START >/dev/null 2>&1
