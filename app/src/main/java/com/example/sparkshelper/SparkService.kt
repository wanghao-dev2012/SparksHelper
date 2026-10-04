package com.example.sparkshelper

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 续火花无障碍服务（搜索·坐标版）
 *
 * 流程：
 *   唤醒 → 滑动解锁 → 启动多闪(my.maya.android)
 *   → for 名单里每个人：
 *       ① 回到会话列表第一页
 *       ② 第一页有这个名字吗？
 *          - 有   → 点进会话（无障碍按 text 找）
 *          - 没有 → 点搜索图标(860,173)
 *                   → 点搜索框(260,238) → 输入名字
 *                   → 点第一条的「发私信」(904,564)
 *       ③ 聊天页：点输入框 → 输入"续火花" → 点发送
 *       ④ 返回，做下一个人
 *
 * 说明：搜索相关步骤用固定坐标（用户实测），聊天页优先无障碍、抓不到再用坐标兜底。
 * 机型：OCE-AN10 / 华为 Mate 40（1080 x 2376）
 */
class SparkService : AccessibilityService() {

    private val executor = Executors.newSingleThreadScheduledExecutor()

    /** 要发送的内容 */
    private val sparkText = "续火花"

    /** 待处理的名单（由 MainActivity 设置） */
    @Volatile var pendingTargets: List<String> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutoClickServiceHolder.service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 由外部触发 */ }

    override fun onInterrupt() {}

    override fun onDestroy() {
        AutoClickServiceHolder.service = null
        executor.shutdownNow()
        super.onDestroy()
    }

    /**
     * 完整任务：解锁 → 启动 App → 逐个续火花
     */
    fun runTask(width: Int, height: Int, targets: List<String>) {
        pendingTargets = targets
        executor.schedule({
            try {
                // 1. 锁屏则唤醒 + 滑动解锁
                if (ScreenController.isLocked(applicationContext)) {
                    ScreenController.wakeUp()
                    Thread.sleep(500)
                    ScreenController.swipeToUnlock(width, height)
                    Thread.sleep(1200)
                }
                // 2. 启动多闪
                ScreenController.launchTargetApp()
                Thread.sleep(3500)

                // 3. 逐个续火花
                for (name in targets) {
                    try {
                        renewOne(name)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    Thread.sleep(1500)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 0, TimeUnit.MILLISECONDS)
    }

    /** 给单个昵称续火花 */
    private fun renewOne(name: String) {
        // ① 回到会话列表第一页
        backToList()
        Thread.sleep(900)

        // ② 第一页找这个名字（无障碍精确匹配）
        val found = findNodeByText(rootInActiveWindow, name)

        if (found != null) {
            // 第一页就有 → 直接点进会话
            clickNode(found)
            Thread.sleep(1300)
        } else {
            // 第一页没有 → 走搜索流程
            if (!searchAndOpenDM(name)) {
                backToList()
                return
            }
        }

        // ③ 聊天页 → 发送"续火花"
        sendSpark()
        Thread.sleep(600)

        // ④ 返回会话列表
        backToList()
    }

    // ==================== 搜索流程（全坐标） ====================

    /**
     * 点搜索图标 → 点搜索框 → 输入名字 → 点第一条的「发私信」。
     * @return true 表示已进入聊天页
     */
    private fun searchAndOpenDM(name: String): Boolean {
        // 1. 点搜索图标（视觉定位 → 固定坐标兜底）
        logMsg("① 定位搜索图标…")
        logMsg(
            ScreenController.tapByVision(
                "多闪会话列表页面，右上角的搜索图标（放大镜）",
                ScreenController.SEARCH_ICON_X,
                ScreenController.SEARCH_ICON_Y
            )
        )
        Thread.sleep(1000)

        // 2. 点搜索输入框（视觉定位 → 兜底）
        logMsg("② 定位搜索输入框…")
        logMsg(
            ScreenController.tapByVision(
                "多闪搜索页顶部，写着\"搜索\"的文本输入框",
                ScreenController.SEARCH_BOX_X,
                ScreenController.SEARCH_BOX_Y
            )
        )
        Thread.sleep(600)

        // 3. 输入名字：优先无障碍写入（中文必须走 ACTION_SET_TEXT），抓不到再兜底
        val box = findEditText(rootInActiveWindow)
        if (box != null) {
            setText(box, name)
            logMsg("③ 已通过无障碍写入名字：$name")
        } else {
            // 兜底：用 shell input text（仅英文有效）
            ShizukuHelper.exec(arrayOf("sh", "-c", "input text '$name'"))
            logMsg("③ 无障碍未抓到输入框，回退 input text（中文无效）：$name")
        }
        Thread.sleep(1800) // 等搜索结果

        // 4. 点第一条联系人的「发私信」按钮（视觉定位 → 兜底）
        logMsg("④ 定位第一条联系人的「发私信」按钮…")
        logMsg(
            ScreenController.tapByVision(
                "搜索结果中第一条联系人右侧的「发私信」按钮（用于给他发私信）",
                ScreenController.SEND_DM_X,
                ScreenController.SEND_DM_Y
            )
        )
        Thread.sleep(1600) // 等进入聊天页

        return true
    }

    /** 简单日志（logcat tag: SparksHelper） */
    private fun logMsg(msg: String) {
        android.util.Log.i("SparksHelper", msg)
    }

    // ==================== 发送续火花 ====================

    /** 在聊天页输入"续火花"并点发送 */
    private fun sendSpark() {
        // ===== 第一步：输入"续火花" =====
        // 优先无障碍直接写；抓不到则用实测坐标两段式（先唤起键盘 → 再点输入框）
        val input = findEditText(rootInActiveWindow)
        if (input != null) {
            setText(input, sparkText)
            Thread.sleep(400)
        } else {
            // ① 点底部输入栏，唤起键盘
            ScreenController.tap(ScreenController.INPUT_X, ScreenController.INPUT_Y)
            Thread.sleep(700)
            // ② 点键盘上方的输入框
            ScreenController.tap(ScreenController.INPUT_KB_X, ScreenController.INPUT_KB_Y)
            Thread.sleep(500)
            // ③ 再试一次无障碍写入（中文必须走 ACTION_SET_TEXT）
            val input2 = findEditText(rootInActiveWindow)
            if (input2 != null) {
                setText(input2, sparkText)
                Thread.sleep(400)
            }
        }

        // ===== 第二步：点发送 =====
        // 优先无障碍；抓不到则用坐标。
        // 注意：若走过键盘兜底，此时键盘是弹起的，用 SEND_KB_*；否则用 SEND_*。
        val send = findNodeByText(rootInActiveWindow, "发送")
            ?: findNodeByAny(rootInActiveWindow, listOf("发送", "send", "Send"))
        if (send != null) {
            clickNode(send)
        } else {
            if (input == null) {
                // 键盘已弹出
                ScreenController.tap(ScreenController.SEND_KB_X, ScreenController.SEND_KB_Y)
            } else {
                // 键盘未弹出
                ScreenController.tap(ScreenController.SEND_X, ScreenController.SEND_Y)
            }
        }
    }

    // ==================== 通用工具 ====================

    /** 连续回退，回到会话列表 */
    private fun backToList() {
        repeat(3) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Thread.sleep(400)
        }
    }

    /** 按 text 精确匹配节点 */
    private fun findNodeByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (i in 0 until node.childCount) {
            val r = findNodeByText(node.getChild(i), text)
            if (r != null) return r
        }
        return null
    }

    /** 按关键词包含匹配 */
    private fun findNodeByAny(node: AccessibilityNodeInfo?, keys: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val txt = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (keys.any { txt.contains(it) || desc.contains(it) }) return node
        for (i in 0 until node.childCount) {
            val r = findNodeByAny(node.getChild(i), keys)
            if (r != null) return r
        }
        return null
    }

    /** 找可编辑输入框 */
    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val r = findEditText(node.getChild(i))
            if (r != null) return r
        }
        return null
    }

    /** 给输入框写入文本（支持中文） */
    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val args = android.os.Bundle().apply {
            putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
    }

    /** 从节点向上找到可点击的祖先并点击 */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }
}

/** 持有当前无障碍服务实例，供 MainActivity 调用 */
object AutoClickServiceHolder {
    @Volatile var service: SparkService? = null
}