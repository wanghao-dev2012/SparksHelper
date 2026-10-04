# 火花助手 (SparksHelper) — 交接文档

安卓自动化小工具：**滑动解锁 → 用 Shizuku 启动多闪 → 给名单里每个人发送"续火花"**。

> 适用机型：**OCE-AN10 = 华为 Mate 40（非 Pro）**，屏幕分辨率 **1080 × 2376**（竖屏）。
> 目标 App：多闪，包名 **`my.maya.android`**。

---

## 一、整体流程

```
① 判断是否锁屏 → 是则 唤醒(KEYCODE_WAKEUP=224) + 上滑解锁
② 用 Shizuku 启动多闪：monkey -p my.maya.android -c android.intent.category.LAUNCHER 1
③ 对名单里每个昵称：
     a. 连按 3 次返回 → 回到会话列表第一页
     b. 用无障碍按 text 精确查找该昵称
          - 第一页有  → 直接点进会话
          - 第一页没有 → 走「搜索流程」（见下）
     c. 聊天页：输入"续火花" → 点发送
     d. 返回，处理下一个人
```

### 搜索流程（第一页找不到时）

| 步骤 | 操作 | 坐标 |
|---|---|---|
| 1 | 点会话列表右上角搜索图标 | **(860, 173)** |
| 2 | 点搜索输入框 | **(260, 238)** |
| 3 | 输入昵称（无障碍 ACTION_SET_TEXT，支持中文） | — |
| 4 | 点搜索结果第一条联系人的「发私信」按钮 | **(904, 564)** |
| 5 | 进入私信界面 → 发送"续火花" | — |

> 坐标 (860,173)、(260,238)、(904,564) 均为**用户真机实测**。

### 聊天页发送

| 元素 | 优先方式 | 坐标 |
|---|---|---|
| 底部输入栏（唤起键盘） | 无障碍找 `isEditable` 节点 | **(230, 2368)** ✅ 实测 |
| 键盘弹出后的输入框 | `ACTION_SET_TEXT` 写"续火花" | **(249, 1470)** ✅ 实测 |
| 发送按钮（键盘未弹出） | 无障碍找 text 含"发送" | **(960, 2284)** ✅ 实测 |
| 发送按钮（键盘已弹出） | 无障碍找 text 含"发送" | **(988, 1473)** ✅ 实测 |

> 聊天页输入采用**两段式**：先点 (230,2368) 唤起键盘 → 再点 (249,1470) 输入；
> 若任一步能直接抓到 `isEditable` 节点，则优先走无障碍写入（中文必须用 `ACTION_SET_TEXT`）。

---

## 二、文件结构

```
SparksHelper/
├── app/src/main/java/com/example/sparkshelper/
│   ├── MainActivity.kt          // 界面：填名单 + 开始按钮；检查 Shizuku 权限
│   ├── SparkService.kt          // 无障碍服务：核心流程（搜索·坐标版）
│   ├── ScreenController.kt      // 屏幕控制 + 全部坐标常量
│   └── ShizukuHelper.kt         // 用 Shizuku 执行 shell 命令
├── app/src/main/AndroidManifest.xml   // 注册服务 .SparkService
└── 给DS的指令.md
```

### 关键类

- **SparkService**（继承 `AccessibilityService`）
  - `runTask(width, height, targets)`：主调度（解锁→启动→逐个）
  - `renewOne(name)`：单人流程
  - `searchAndOpenDM(name)`：搜索流程（全坐标）
  - `sendSpark()`：输入并发送"续火花"
  - 工具：`findNodeByText` / `findNodeByAny` / `findEditText` / `setText` / `clickNode`
- **ScreenController**：包名、分辨率、全部坐标常量，`wakeUp/swipeToUnlock/launchTargetApp/tap`
- **AutoClickServiceHolder**：`object`，持有服务实例供 MainActivity 调用
- **ShizukuHelper.exec**：通过 `Shizuku.newProcess` 执行 shell

---

## 三、使用前提

1. 手机已安装并激活 **Shizuku**（无线调试或 Root 启动均可）。
2. 授权本 App 的 **Shizuku 权限**。
3. 在系统设置里**手动开启本 App 的无障碍服务**。
4. 锁屏类型必须是 **「滑动解锁（无密码）」**（有 PIN/图案锁无法自动解锁）。
5. 多闪保持在**已登录**状态；会话列表停在能直接看到的位置。

---

## 四、使用步骤

1. 打开"火花助手"，先点 **检查/申请 Shizuku 权限**。
2. 到系统设置开启无障碍服务。
3. 在输入框里**每行一个昵称**填名单（昵称需与多闪里显示的完全一致）。
4. 点**开始**。
5. 观察：自动解锁 → 打开多闪 → 逐个续火花。

---

## 五、待实测 / 待调项

| 项 | 状态 |
|---|---|
| 滑动解锁 (571,1964)→(595,593)，150ms | ✅ 实测 |
| 聊天输入栏 (230,2368) / 键盘后输入框 (249,1470) | ✅ 实测 |
| 发送键 未弹键盘 (960,2284) / 弹键盘 (988,1473) | ✅ 实测 |
| 搜索结果是否可能分"联系人/聊天记录"分组 | 若误点，需改为优先点"联系人"分组 |

单点坐标实测方法（手机终端或 Shizuku 终端）：
```
input tap 360 2210     # 看是否点到输入框
input tap 980 2210     # 看是否点到发送
```

---

## 六、合规声明（不可突破）

- ❌ 不绕过密码 / PIN / 图案锁屏
- ❌ 不爬取任何平台未公开接口
- ❌ 不批量操作他人账号

> 本工具仅用于**用户本人账号**的日常续火花，全流程在用户自己设备上操作，名单由用户手动填写。
