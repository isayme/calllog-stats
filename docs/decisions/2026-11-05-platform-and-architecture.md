# 2026-11-05 — 平台与技术架构

## 决策

1. **仅支持 Android。** iOS 与 HarmonyOS 不向第三方应用开放“来电号码 + 通话记录 + 通话中浮层”的组合能力，无法实现核心价值；v1 不为其做降级实现。
2. **原生 Kotlin + Jetpack Compose。** 核心能力依赖 Android 特有的通话状态广播、`CallLog` Provider、前台服务与悬浮窗，跨端框架只会增加桥接成本。
3. **单 module 应用。** 规模小，先保持 `:app` 单模块，统计逻辑以纯 Kotlin 对象隔离以便单元测试。
4. **数据完全本地。** 仅读取 `CallLog.Calls`，不使用联系人、云端或第三方 SDK。清单中不声明 `INTERNET` 权限。
5. **包名/版本。** `applicationId = org.isayme.callstats`，`namespace = org.isayme.callstats`，`minSdk 26`，`compileSdk/targetSdk 36`，版本从 `0.1.0` 开始。
6. **分发。** 国内应用市场 + GitHub APK；因此可以接受 `READ_CALL_LOG` 等受限权限，不按 Google Play 政策设计。CI 与 release 签名在验收后再补。

## 影响

- 不使用 AndroidX 中要求 `compileSdk 37` / AGP 9 的最新版本，依赖固定在兼容 AGP 8.13 的稳定版本。
- 所有用户可见文案使用中文字符串资源，代码与注释使用英文。
- 不引入网络、数据库、DI、导航等框架；设置使用 DataStore Preferences。
