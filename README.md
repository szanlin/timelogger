# TimeLogger

TimeLogger 是一款离线 Android 时间记录应用。按活动类型开始或结束记录，并以日、周、月维度查看时间分配。

## 功能

- 本地 Room 数据库，时间以 UTC 毫秒保存
- 多个不同活动可并行计时；同一活动只能有一条进行中记录
- 记录页支持图标点击开始、再次点击结束，以及当天时间轴
- 日、周、月统计：覆盖时长、类型累计时长、类型排行与趋势图
- 统计时区支持跟随系统或固定 IANA 时区；周起始日支持周一或周日
- 类型支持新增、编辑图标/颜色、排序与归档
- 历史已结束记录支持编辑；夏令时重复时刻可选择 UTC 偏移量

## 构建与测试

环境要求：JDK 21、Android SDK，以及可选的 Android 模拟器或真机。项目的 Java 源码兼容级别为 11，但构建由 JDK 21 运行。

```bash
./gradlew testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest
```

可安装的调试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

通过 USB 调试安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 数据与备份

应用不提供账号、云同步、导出、提醒或多人协作。应用数据仅保存在本地，备份规则排除应用存储内容。

## 发布说明

`debug` APK 适合开发和内部测试。正式发布前需要配置私有 release keystore，并生成已签名的 release APK 或 AAB。

## License

本项目使用 [MIT License](LICENSE)。
