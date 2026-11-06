# 2026-11-06 — v1 范围与行为确认

## 背景

在 `docs/requirements.md` 与两份 2026-11-05 决策文档的基础上，通过一轮设计评审确认了以下实现前未定义的点。文中“按建议”指评审中给出的推荐项，均已由项目所有者确认。

## 交付与工程

1. **里程碑拆分为 M0–M4**，每个里程碑可独立构建、独立 review：
   - M0：可构建骨架 + `READ_CALL_LOG` 权限探针。
   - M1：权限引导、设置、前台服务开关与常驻通知。
   - M2：通话状态机、号码识别、开机恢复。
   - M3：统计计算、浮层 UI、debug 模拟入口。
   - M4：边界处理、单元测试补齐、真机验收。
2. **构建环境**：使用 JDK 21（`brew install openjdk@21`，仅本项目通过 `JAVA_HOME` 使用）；Gradle wrapper `8.14.6`、AGP `8.13.x`、Kotlin `2.2.x`、Compose BOM 稳定版；版本集中于 `gradle/libs.versions.toml`。
3. **Git**：执行 `git init` 并添加 Android `.gitignore`；不自动 commit，commit 需项目所有者明确要求。
4. **应用显示名**“来电速查”；启动图标先用自适应图标占位。
5. **主界面范围**：单 Activity + Compose，只包含监听状态、权限清单、设置项、关于页；不做历史通话列表、号码搜索。关于页为静态 Compose 页面，说明仅读取本机 CallLog、无 `INTERNET` 权限及各权限用途。
6. **debug 模拟入口**：仅 debug 构建可见，入口在主界面底部；注入完整状态机事件 `IDLE → RINGING → OFFHOOK → IDLE`，走真实统计查询与浮层生命周期；模拟号码手工输入。

## 统计与展示口径

7. **号码匹配边界**（在需求文档归一化规则之上补充）：
   - 长度 < 7 位的短号（110、10086、95588、400 号码等）只做去非数字字符后的精确匹配，不做国家码归一化；仍然展示浮层，但不参与重复来电高亮。
   - `PRIVATE_NUMBER`（-1）、`UNKNOWN_NUMBER`（-2）、`PAYPHONE`（-3）不展示浮层、不计入统计。
   - 分机与拨号控制符：在第一个 `,` 或 `;` 处截断后再匹配。
   - 不使用“后 N 位”模糊匹配（沿用需求文档）。
8. **“首次”与“最近一次”口径**：
   - 首次判定使用全量通话记录；统计周期内为 0 次但全量有记录时，浮层显示“近期无记录 · 历史 N 次”。
   - “最近一次接听/去电”取全量记录，并标注该次是否落在统计周期内。
9. **重复来电提示**：窗口内第 N 次（N ≥ 2）始终显示次数；仅当窗口内存在未接/未接听记录时使用警告色，否则用中性样式。
10. **场景化文案**：来电显示“首次来电 / 第 N 次来电”，去电显示“首次联系 / 第 N 次联系”；号码下方增加一行摘要（如“近 6 个月 3 次来电，0 次接听”）。
11. **统计周期**：滚动窗口，按毫秒时间戳计算 `now - N 个月` 的边界，不使用自然月。

## 浮层

12. **形态与交互**：宽约 300dp 的圆角卡片；默认贴右上角并避开状态栏；记住上次拖动位置（DataStore）并限制在屏幕内；接通后 5 秒自动折叠为单行，点击展开；跟随系统深浅色；不透明度约 92%；`FLAG_NOT_FOCUSABLE`，不抢占触摸与系统来电界面输入。
13. **呼叫等待**：通话中收到新来电时切换到新号码的统计；新通话结束后不恢复旧号码浮层。
14. **锁屏策略**：仍使用 `TYPE_APPLICATION_OVERLAY`。官方文档不保证 overlay 显示在 keyguard 之上，因此在 M2 第一个真机检查项验证；若不可见，先记为已知限制，不引入会遮挡系统来电界面的 Activity 方案。

## 权限、服务与恢复

15. **权限引导**：单页清单式引导，顺序为通知 → 通话记录 + 电话状态 → 悬浮窗 → 忽略电池优化 → 小米自启动说明；每项解释用途。缺任一关键权限时不允许开启监听，首页显示缺失项。
16. **监听开关**：默认关闭，由用户主动开启。前台服务通知使用最低重要性、常驻、含“停止监听”操作，无声音无振动。
17. **权限被回收**：自动停止监听，更新通知，首页显示提示。
18. **`READ_CALL_LOG` 探针**：Android 10+ 该权限为 hard-restricted，由安装器 allowlist，侧载行为无官方文档保证。M0 必须在 K80 上安装最小 APK、真实申请权限并成功读取一条通话记录；M0 的完成定义是“能构建 + 读取到真实记录”。若无法取得权限，暂停开发并重新评估安装方式。
19. **不使用 `CallScreeningService`**：v1 继续使用 `PHONE_STATE`。调试构建记录“号码为空”的次数，用数据决定后续是否引入。
20. **服务被系统杀死后**：不从 `PHONE_STATE` 广播中尝试启动前台服务（Android 12+ 后台启动限制，`SYSTEM_ALERT_WINDOW` 豁免在 Android 15 起还要求已有可见浮层）；使用 `START_STICKY` 让系统主动重启；通知栏与首页提示“监听已停止，请重新开启”。
21. **开机恢复**：`BOOT_COMPLETED` 接收器，声明 `RECEIVE_BOOT_COMPLETED`。Android 12 明文豁免从 `BOOT_COMPLETED` 启动前台服务，Android 15 的受限类型列表不含 `specialUse`。
22. **前台服务类型**：`specialUse`，声明 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`，并写入 `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 说明字符串；无运行时权限要求。

## 测试与验收

23. **自动化**：纯 Kotlin 逻辑（号码归一化、统计口径、重复计数、首次判定、时间窗口）单元测试先行；构建验证为 `assembleDebug`、`lintDebug`、`testDebugUnitTest`。
24. **真机双机测试**：
    - 被测机：Redmi K80（Android 16 / SDK 36）。
    - 拨号端：Redmi Note 7 Pro（Android 10 / SDK 29），同时覆盖 Android 10 的权限与 CallLog 行为。
    - 来电由 Note 7 Pro 拨给 K80；去电由 K80 拨给 Note 7 Pro。发起呼叫前先告知项目所有者号码与目的。
    - 测试号码只在运行时输入，不写入仓库或文档。
25. **验收边界**：锁屏可见性为“尽力验证”，失败时按第 14 条记为已知限制；其余验收项按需求文档执行。

## 官方文档核查结论（2026-11-06）

1. `ACTION_PHONE_STATE_CHANGED` 从 Android 8.0 起在隐式广播例外清单中，清单注册可用。接收需 `READ_PHONE_STATE`；`EXTRA_INCOMING_NUMBER` 需同时持有 `READ_CALL_LOG`（Android 9 起）。
   - https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
   - https://developer.android.com/about/versions/pie/android-9.0-changes-all
2. 号码保证时机：来电在 `IDLE → RINGING`，去电在 `IDLE → OFFHOOK`。系统可能先后发送“带号码”和“不带号码/无 extra”两次广播，代码需同时兼容缺失、`null` 与空字符串。
   - https://developer.android.com/reference/android/telephony/TelephonyManager#EXTRA_INCOMING_NUMBER
3. `READ_CALL_LOG` 自 Android 10 起为 hard-restricted，需安装器 allowlist，用户无法手动开启；adb 侧载行为无官方说明。
   - https://developer.android.com/reference/android/Manifest.permission#READ_CALL_LOG
   - https://source.android.com/docs/core/permissions/runtime_perms
4. Android 12+ 前台服务后台启动限制中，`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` 是明文豁免；Android 15 的 `BOOT_COMPLETED` 受限类型不含 `specialUse`。
   - https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
   - https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed
5. `TYPE_APPLICATION_OVERLAY` 的官方描述只保证位于普通 Activity 之上、系统关键窗口之下；锁屏显示只有 Activity 级 `setShowWhenLocked` 的受支持路径。
   - https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY
   - https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean)
6. `CallScreeningService` 可同时处理来电与去电，但默认只送非联系人号码（除非授予 `READ_CONTACTS`），隐藏/受限号码不送。
   - https://developer.android.com/reference/android/telecom/CallScreeningService

## M0 验证结果（2026-11-06，Redmi K80 / Android 16 / HyperOS V816）

- 构建：`assembleDebug`、`lintDebug`、`testDebugUnitTest` 全部通过；debug APK 约 11 MB。
- APK 权限清单只有 `READ_CALL_LOG` 与 `READ_PHONE_STATE`，无 `INTERNET` 权限。
- 侧载安装：`adb install` 成功，`READ_CALL_LOG` 带 `RESTRICTION_INSTALLER_EXEMPT` 标记，安装器已放行该 hard-restricted 权限。
- 运行时授权：HyperOS 弹窗“允许‘来电速查’读取通话记录？”提供“仅在使用中允许 / 本次使用允许 / 拒绝”；选择“仅在使用中允许”后，`READ_CALL_LOG` 与 `READ_PHONE_STATE` 均授予。
- 读取结果：探针读取到真实通话记录，总数、脱敏号码、类型、时间、时长显示正确。
- 结论：M0 通过。**但权限不能假设安装即授予**：首次安装曾出现自动授予，卸载重装后必须经运行时弹窗授予，应用需要同时处理两种路径。
- 输出：`org.isayme.callstats`，版本 `0.1.0`；Gradle 8.14.6、AGP 8.13.2、Kotlin 2.2.21、Compose BOM 2025.10.01。

## M1 验证结果（2026-11-06，Redmi K80 / Android 16 / HyperOS V816）

- 构建：`assembleDebug`、`lintDebug`、`testDebugUnitTest` 全部通过。
- 主界面：监听开关与状态、权限清单、统计设置、关于页均正常；关于页返回正常。
- 权限引导：
  - 通知权限真实运行时弹窗验证通过（拒绝后重新申请，选择“始终允许”后变为已授予）。
  - 悬浮窗按钮打开 `com.android.settings/.Settings$OverlaySettingsActivity`。
  - 忽略电池优化按钮被 HyperOS 接管，打开 `com.miui.securitycenter/.../PowerDetailActivity`（电量详情/省电策略），用户可在此选择“无限制”。
  - 自启动按钮打开 `com.miui.securitycenter/.../AutoStartManagementActivity`。
- 前台服务：开启监听后服务以 `specialUse`（`types=0x40000000`）运行，通知 `id=1001`、`category=service`、常驻且无声音；在应用内关闭后服务停止、通知移除。
- 设置持久化：重复来电窗口从 10 改为 11 分钟后，强制停止并重启应用仍为 11 分钟；测试完成后已恢复为 10。
- 权限缺失拦截：撤销悬浮窗权限后，首页显示“权限不完整，无法开启监听”，点击开关出现提示且不会启动服务。
- 服务被强制停止：`monitorEnabled` 保留为 true，重新打开应用显示“监听已停止，请重新开启”，符合 Q13。
- 未覆盖：通知上的“停止监听”操作未通过 UI 自动化验证（MIUI 通知栏无法用 adb 展开）；该路径为 `PendingIntent.getService` + `ACTION_STOP`，服务写入 `monitorEnabled=false` 后 `stopSelf()`，留待 M2 或手动验证。
- 注意：HyperOS 的“省电策略”与 AOSP 的电池优化白名单不是同一状态，`PowerManager.isIgnoringBatteryOptimizations` 可能始终返回 false；该项仅作为可选引导，不阻塞监听。

## M2 验证结果（2026-11-06，Redmi K80 / Android 16 / HyperOS V816；Note 7 Pro 拨号）

- 实现：`PHONE_STATE` 清单接收器、进程内事件总线、纯 Kotlin 状态机、`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` 恢复接收器；状态机 9 个场景单元测试通过。
- 来电：确认平台先发“无号码”广播、再发“带号码”广播；应用输出 `Started(INCOMING, empty) → NumberResolved(158***69)`，挂断输出 `Ended`。第二次 `IDLE` 带号码广播被忽略。
- 去电：`IDLE → OFFHOOK` 正确判定为去电，双广播处理与来电一致，号码补齐为 `158***69`。
- 接听：`RINGING → OFFHOOK` 输出 `Answered(INCOMING, 158***69)`；重复 `OFFHOOK` 带号码广播不产生重复事件。
- 锁屏/息屏来电：广播接收与状态机链路完整。
- 号码缺失计数：改为“整通电话结束仍未补齐号码”才累计；正常通话计数保持 0，避免把平台的首次无号码广播误判为缺失。
- **开机恢复关键结论**：HyperOS 默认未允许自启动时，`BOOT_COMPLETED` 不会投递到应用（接收器已注册，但广播分发列表中没有应用日志）。在“自启动管理”中允许“来电速查”后再次重启，`BootReceiver` 记录 `restore requested: enabled=true criticalGranted=true`，服务以 `code=BOOT_COMPLETED`、20 秒临时白名单启动，`isForeground=true`、通知 `id=1001`、首页显示“监听中”。
- 结论：在 HyperOS 上，“自启动”是开机恢复的硬性前提，应用内必须明确引导用户开启；Android 层面的 `BOOT_COMPLETED` 豁免并不能绕过 ROM 的投递限制。
- 测试结束状态：监听关闭，设置为默认值。

## M3a 验证结果（2026-11-06，Redmi K80）

- 实现：`NumberNormalizer`、`CallStatsCalculator`（纯 Kotlin）、`CallLogRepository`、debug 统计预览页；统计逻辑不依赖 Android 类型，可用 JVM 单测覆盖。
- 归一化：默认模式去非数字字符、去 `0086`/`86`（剩余 10–11 位）、分机在 `,`/`;` 截断；严格模式只去非数字；短号精确匹配；`-1/-2/-3` 返回不可匹配。
- 统计：滚动周期、窗口内来电/已接/未接、去电/接通、累计时长、全量最近接听/去电并标注周期外、首次来电/首次联系、重复窗口次数与未接标记。
- 单测：归一化 6 个场景、统计 8 个场景全部通过；构建与 lint 通过。
- 真机预览验证：
  - 号码 `15850603069`：头部“第 8 次来电”；窗口内来电 7 次（已接 2、未接 5），去电 3 次（接通 0）；累计时长来电 11 秒；最近接听 12:32、3 秒；最近去电 12:32、0 秒；全量来电 8 次、去电 8 次。
  - 号码 `-1`：显示“无法匹配该号码（隐藏号码或没有有效数字）”。
  - 号码 `10086`：显示“近期无记录 · 历史 9 次”，最近接听/去电均标注“（统计周期外）”，验证了 Q6 的口径。
- 预览入口仅在 debuggable 构建显示；统计引擎尚未接入前台服务（M3b 接入）。

## M3b 验证结果（2026-11-06，Redmi K80 / HyperOS）

- 实现：`TYPE_APPLICATION_OVERLAY` + ComposeView、浮层控制器（拖动、位置持久化、5 秒自动折叠、点击展开）、统计协调器、debug 模拟通话入口。
- 来电浮层：号码、方向、首次/第 N 次来电、摘要、来电/去电计数、累计时长、最近接听/去电均正确；重复窗口内有未接时使用警告色。
- 交互：接听后 5 秒自动折叠为单行；点击重新展开；拖动后位置写入 DataStore，下一次来电回到上次位置；通话结束窗口移除。
- 去电浮层：显示“第 N 次联系”和去电摘要；关闭“去电时展示浮层”后，去电事件仍被处理但不再创建窗口；重新开启立即恢复。
- **锁屏限制**：锁屏来电时浮层窗口已创建（`ty=APPLICATION_OVERLAY`），但被系统 keyguard 遮挡，用户不可见；按 Q10 方案 A 记为已知限制。
- **HyperOS 后台广播限制（关键）**：默认“智能限制后台运行”下，应用在后台时收不到 `PHONE_STATE`，即使前台服务正常运行；将“省电策略”改为“无限制”后，应用被加入 `deviceidle` 白名单，后台来电广播恢复正常。该设置是 HyperOS 上的硬性前提，应用引导必须明确要求选择“无限制”，不能只提示“忽略电池优化”。
- 未覆盖：通知栏“停止监听”操作仍未通过 UI 自动化验证（MIUI 通知栏无法用 adb 展开）。
- 测试结束状态：监听关闭；“去电时展示浮层”已恢复开启；浮层位置保留在测试时拖动的位置。

## M4 验证结果（2026-11-06，Redmi K80 / HyperOS）

- 引导修正：权限项改为“省电策略”，说明 HyperOS 需设为“无限制”；未设置时首页显示提醒；About 页与 README 增加自启动、省电策略、悬浮窗、通知四项设置步骤。
- 隐藏号码：浮层协调器在创建窗口前检查归一化结果，`-1` 等隐藏号码不再创建任何窗口（模拟验证窗口数为 0）。
- 去电浮层：最终构建下再次验证通过，显示“第 8 次联系”、去电摘要与计数，位置沿用上次拖动位置。
- 通知“停止监听”：手动点击后服务停止、通知移除，重新打开应用显示“监听未开启”，说明 `monitorEnabled=false` 已持久化。
- 开机恢复：最终构建下再次验证，`BootReceiver: restore requested: enabled=true criticalGranted=true`，服务以 `code=BOOT_COMPLETED` 启动，通知与前台状态正常。
- 最终检查：`assembleDebug`、`lintDebug`、`testDebugUnitTest` 全部通过；APK 权限清单不含 `INTERNET`；单元测试覆盖归一化、统计口径、状态机、设置范围与号码脱敏。
- 已确认的 HyperOS 前置条件：自启动开启、省电策略“无限制”、悬浮窗权限、通知权限。
- 已知限制：锁屏来电时浮层被系统 keyguard 遮挡，用户需解锁后查看。
- Android 10 兼容性：尝试在 Redmi Note 7 Pro 上侧载，被 MIUI 的安装确认弹窗拦截（`INSTALL_FAILED_USER_RESTRICTED`），未完成；目标机 K80（Android 16）已完成全部验证。minSdk 26 的更低版本未实机覆盖。

## 未决风险

- `READ_CALL_LOG` 在部分安装器/ROM 上可能无法授予，M0 探针是硬性关卡。
- 锁屏浮层、HyperOS 保活、自启动策略仍需真机验证。
- `PHONE_STATE` 的号码 extra 已废弃，长期方案可能是 `CallScreeningService`；v1 先用数据衡量缺失率。
