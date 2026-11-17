# 构建说明

本项目源码基于 v1.0.3 反编译整理，Java 源码在 `src/com/jarvis/bubblenotify/`，
`AndroidManifest.xml`、`META-INF/xposed/`（module.prop、scope.list）与图标在对应目录。

## 环境

- JDK 21（`javac` 必须用 JDK 21，系统 JRE 不够）
- Android SDK 34 的 `android.jar`（公开 API 桩；隐藏 API 只能用反射）
- build-tools 34（aapt2、zipalign、apksigner）
- apktool（独立 jar，不要用系统自带的）
- r8 8.5.35 的 D8（`java -cp r8.jar com.android.tools.r8.D8 --min-api 26`，不要加 `--dex`，不要用 `java -jar`）

## 步骤

1. `javac -source 17 -target 17` 编译全部 `.java`，打包成 `classes.jar`
2. D8 转 dex：`java -cp r8.jar com.android.tools.r8.D8 --min-api 26 --lib android-34.jar --output <dir> classes.jar`
3. apktool 打包：`java -jar apktool.jar b --use-aapt2`
4. 用 Python zip 重建 APK：替换 `classes.dex`，补 `META-INF/xposed/*`（注意跳过源头 APK 里的 `scope.list`，再写入工程自己的 `scope.list`，否则 apksigner 拒签）
5. `zipalign` → `apksigner`（debug.keystore，v2/v3）→ `verify`
6. 每次打包必须升 `apktool.yml` 里的 versionCode；versionName 保持 1.0.4

## 注意

- 作用域只有两行：`com.android.systemui`、`com.android.incallui`
- 图标是自适应双层：`res/mipmap-xxxhdpi/ic_launcher_foreground.png` + `ic_launcher_background.png` + `res/mipmap-anydpi-v26/ic_launcher.xml`（PNG 是二进制文件，未随源码入库，构建时自行补齐）
- 构建细节与逐轮改动见项目内的开发记录。
