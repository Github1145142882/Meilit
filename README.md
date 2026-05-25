# 灵动光环 AW20072 控制台

这是一个给魅族 AW20072 灵动光环节点使用的 Android 控制 App。工程不依赖 AndroidX、Kotlin 或 Compose，核心逻辑直接按 sysfs 协议写入：

- `/sys/class/leds/aw20072_led/effect`
- `/sys/class/leds/aw20072_led/light`
- `/sys/class/leds/aw20072_led/all_light`
- `/sys/class/leds/aw20072_led/alone_light`
- 以及 `hwen`、`imax`、`i2c_log`、`rgbcolor`、`allrgbcolor`、`rgbbrightness`、`allrgbbrightness`

## 功能

- 检测 `/sys/class/leds/aw20072_led` 和 `/dev/aw20072_led`
- 支持直接写入，也支持 `su -c` root 写入
- 16 颗灯珠选择、单灯独占点亮、叠加点亮、单灯熄灭
- 全部点亮、清空叠加、固件效果 0..16
- IMAX、硬复位、I2C log 开关
- 内置玩法：流光、呼吸
- 屏幕上的 16 灯珠预览会同步当前 App 写入状态

## 构建

需要本机安装 JDK、Android SDK 和 Gradle 8.9。安装后可以在 Android Studio 中打开本目录，或执行：

```powershell
gradle :app:assembleDebug
```

生成的 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions 构建

已添加工作流：

```text
.github/workflows/android.yml
```

触发方式：

- push 到 `main` 或 `master`
- pull request
- 在 GitHub Actions 页面手动执行 `workflow_dispatch`

构建完成后，在 Actions 运行记录的 Artifacts 中下载 `meilit-halo-debug-apk`。

## 权限说明

普通 Android App 通常不能直接写 `/sys/class/leds/...`。这个 App 的写入顺序是：

1. 未打开「使用 su 写入」时，先尝试直接 `FileOutputStream` 写 sysfs。
2. 直接写失败后自动 fallback 到 `su -c`。
3. 打开「使用 su 写入」后，总是使用 `su -c`。

如果 root 仍然失败，通常是 SELinux 或设备 ROM 权限策略限制，需要把 App 做成系统/特权应用，或通过 Magisk/service 脚本代理写入。

## 写入映射

- 关灯：`effect = 0`
- 单颗独占：`light = "{light_number} {brightness} {rrggbb}"`
- 叠加点亮：`alone_light = "{light_number} {brightness} {rrggbb}"`
- 清空叠加：`alone_light = "0 0 0"`
- 全部点亮：`all_light = "{brightness} {rrggbb}"`
- 固件效果：`effect = 0..16`
- IMAX：`imax = 0x0..0xF`

亮度按内核约束裁剪到 `1..63`，`alone_light` 允许 `brightness = 0`。

## 开发入口

- sysfs/root 写入封装：[Aw20072Controller.java](app/src/main/java/com/meilit/halo/Aw20072Controller.java)
- 主界面和玩法循环：[MainActivity.java](app/src/main/java/com/meilit/halo/MainActivity.java)
- 16 灯珠预览：[HaloRingView.java](app/src/main/java/com/meilit/halo/HaloRingView.java)

要新增玩法，建议在 `MainActivity` 中参考 `startChase()`、`startBreath()`，通过 `controller.setLight()`、`controller.setAloneLight()` 或 `controller.setAllLight()` 组合写入。
