# 给 DS / 编译者的指令

请帮我把这个 Kotlin Android 工程整理并编译成可安装的 APK。

## 一、工程信息

- 工程名：SparksHelper（火花助手）
- 语言：Kotlin
- 最低 SDK：Android 8.0 (API 26)
- 目标 SDK：Android 14 (API 34)
- 目标机型：**OCE-AN10 / 华为 Mate 40（非 Pro），1080 × 2376**
- 目标 App 包名：**`my.maya.android`**（多闪）

## 二、要实现的逻辑（务必按此逻辑，不要自行改成"逐个遍历"）

```
启动任务：
  1. 若锁屏 → 唤醒(KEYCODE_WAKEUP=224)
              上滑解锁：input swipe 571 1964 595 593 150
  2. Shizuku 执行：monkey -p my.maya.android -c android.intent.category.LAUNCHER 1
  3. for 名单里每个昵称 name：
       a. 连按 3 次 GLOBAL_ACTION_BACK → 回到会话列表第一页
       b. 用无障碍 findNodeByText 精确查找 name
            - 找到 → clickNode 直接进会话
            - 没找到 → searchAndOpenDM(name)（见下）
       c. sendSpark()：输入"续火花" → 点发送
       d. 返回
```

### searchAndOpenDM(name) —— 全坐标搜索流程

```
1. input tap 860 173        // 搜索图标
2. input tap 260 238        // 搜索输入框
3. 对输入框 setText(name)   // 中文必须用无障碍 ACTION_SET_TEXT
4. input tap 904 564        // 第一条联系人的「发私信」
5. 进入私信界面 → sendSpark()
```

### sendSpark()

```
输入框：优先无障碍找 isEditable 节点 → setText("续火花")
        抓不到 → 两段式坐标：
                  tap (230, 2368)   // 点底部输入栏，唤起键盘
                  tap (249, 1470)   // 点键盘上方的输入框
                  再 findEditText 写一次
发送：  优先无障碍找 text 含"发送"的节点 → click
        抓不到 → 按键盘状态选坐标：
                  键盘已弹出（走过上面的两段式兜底）→ tap (988, 1473)
                  键盘未弹出                            → tap (960, 2284)
```

## 三、关键约束

1. **中文无法用 shell `input text` 输入**，必须走无障碍 `ACTION_SET_TEXT`（代码里 `setText()` 已实现，用 `ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE` 传 Bundle）。
2. 所有坐标都是 **1080 × 2376** 体系下的像素值，**不要做分辨率缩放**。
3. 服务必须在 `AndroidManifest.xml` 里注册为 `.SparkService`，并声明：
   - `android.permission.BIND_ACCESSIBILITY_SERVICE`
   - `android:accessibilityFlags`、`canRetrieveWindowContent="true"`
4. Shizuku 相关：需要 `dev.rikka.shizuku:api` 与 `dev.rikka.shizuku:provider`，并在 Manifest 声明 Shizuku 的 provider 与权限。

## 四、依赖（参考版本，按需调整）

```
implementation "dev.rikka.shizuku:api:13.1.5"
implementation "dev.rikka.shizuku:provider:13.1.5"
implementation "androidx.core:core-ktx:1.12.0"
implementation "androidx.appcompat:appcompat:1.6.1"
implementation "com.google.android.material:material:1.11.0"
implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0"
implementation "androidx.work:work-runtime-ktx:2.9.1"
```

## 五、交付要求

- 输出可安装的 **debug APK**。
- 若编译报错，请把**完整报错日志**发回，不要自行改动业务逻辑（尤其坐标与流程）。
- 编译通过后，安装到 Mate 40 真机自测。

## 六、合规声明（不可突破）

- ❌ 不绕过密码 / PIN / 图案锁屏
- ❌ 不爬取任何平台未公开接口
- ❌ 不批量操作他人账号

> 仅用于用户本人账号的续火花，名单由用户在 App 内手动填写。
