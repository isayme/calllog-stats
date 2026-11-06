# 2026-11-05 — 通话检测与浮层方案

## 决策

1. **通话检测使用 `PHONE_STATE` 广播。**
   - 同时持有 `READ_CALL_LOG` 与 `READ_PHONE_STATE` 时，`EXTRA_INCOMING_NUMBER` 会为来电和去电提供号码。
   - 不使用默认拨号器 / `InCallService`，避免接管系统拨号界面。
   - 不使用 `CallScreeningService`，避免额外申请“来电筛选”角色；其能力作为后续备选方案。
2. **常驻前台服务。**
   - 服务类型为 `specialUse`，通知栏常驻。
   - 用户开启监听时由 Activity 启动；接收 `BOOT_COMPLETED` 后按用户设置恢复。
   - 服务内维护简单状态机：`IDLE → RINGING → OFFHOOK → IDLE`，`IDLE → OFFHOOK` 判定为去电。
   - `PHONE_STATE` 可能收到“带号码”和“不带号码”两次广播，状态机需忽略空号码重复事件，并在后续广播补齐号码。
3. **浮层使用 `TYPE_APPLICATION_OVERLAY` + ComposeView。**
   - 浮层不可聚焦（`FLAG_NOT_FOCUSABLE`），不抢占系统来电界面输入。
   - 支持拖动与点击收起/展开；通话接通数秒后自动收起。
   - 浮层生命周期由自定义 `LifecycleOwner` / `SavedStateRegistryOwner` / `ViewModelStoreOwner` 提供。
4. **后台启动限制。** Android 12+ 从后台启动前台服务受限；Android 15+ 若依赖 `SYSTEM_ALERT_WINDOW` 豁免，必须已有可见浮层。因此方案以“常驻服务”为主，`PHONE_STATE` 广播只负责向已运行的服务转发事件。
5. **权限运行时申请。** 通话记录与电话状态使用运行时权限弹窗；悬浮窗与电池优化使用系统特殊授权页；Android 13+ 单独申请通知权限。

## 已知限制

- 服务被国产 ROM 杀死后，下一次来电可能无法展示浮层；需要用户关闭电池优化、允许自启动，并保持应用后台运行。
- 锁屏可见性、`EXTRA_INCOMING_NUMBER` 填充行为需要 Redmi K80 真机验证；若不满足，回退方案是 `CallScreeningService`（来电）与通话记录最近条目（去电）。
- 不区分双卡，号码归一化后仅按号码匹配。
