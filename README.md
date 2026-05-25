# Meilit Halo AW20072 Controller

这是给魅族 AW20072 灵动光环节点使用的原生 Android 控制 App。核心逻辑直接写入 `/sys/class/leds/aw20072_led/*`，并提供普通 APK 与 Magisk priv-app 模块两种交付形式。

## 功能

- `effect` 0..16、`light`、`all_light`、`alone_light` 全量保留
- 高级节点：`reg`、`hwen`、`imax`、`i2c_log`、`rgbcolor`、`allrgbcolor`、`rgbbrightness`、`allrgbbrightness`
- 前台服务后台常驻，锁屏后继续运行写入、音乐律动和音量监听
- 通知监听：默认响应微信、QQ、Telegram、Discord、电话、短信、提醒等常见通知
- 音乐律动：优先使用 Android `Visualizer` 采集真实波形/FFT，失败时回退到媒体音量律动
- 软件玩法减少整环清空：流光只熄灭上一颗，彩虹覆盖颜色，呼吸只改亮度
- GitHub Release 同时产出普通 APK 和 Magisk 模块 ZIP

## 构建

```powershell
gradle :app:assembleDebug
```

生成 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions Release

`.github/workflows/android.yml` 会在 push 到 `main`/`master` 时：

- 构建 debug APK
- 将 APK 打包进 Magisk 模块
- 更新 `v1.0.0` Release
- 上传 `app-debug.apk` 和 `meilit-halo-magisk.zip`

## Magisk 模块

模块模板位于 `magisk/`。Actions 会生成以下结构：

```text
system/priv-app/MeilitHalo/MeilitHalo.apk
system/etc/permissions/privapp-permissions-com.meilit.halo.xml
module.prop
service.sh
```

`service.sh` 会尝试授予录音、通知、前台服务、WakeLock 权限，加入电池优化白名单，开启通知监听服务，并启动后台前台服务。

## 开发入口

- `app/src/main/java/com/meilit/halo/Aw20072Controller.java`：sysfs/root 写入封装
- `app/src/main/java/com/meilit/halo/HaloEngine.java`：单线程写入队列、去重、音乐/通知帧写入
- `app/src/main/java/com/meilit/halo/HaloForegroundService.java`：后台常驻、Visualizer、音量回退、WakeLock
- `app/src/main/java/com/meilit/halo/HaloNotificationListenerService.java`：通知监听和颜色策略
- `app/src/main/java/com/meilit/halo/MainActivity.java`：主界面和手动控制
