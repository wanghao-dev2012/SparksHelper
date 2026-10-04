package com.example.sparkshelper

import android.app.KeyguardManager
import android.content.Context

/**
 * 屏幕控制 + 坐标常量
 *
 * 机型：OCE-AN10（华为 Mate 40，1080 x 2376）
 * 以下坐标均为该机实测 / 用户提供。
 */
object ScreenController {

    /** 多闪真实包名 */
    const val TARGET_PKG = "my.maya.android"

    /** OCE-AN10（Mate 40）屏幕分辨率（px，竖屏） */
    const val SCREEN_W = 1080
    const val SCREEN_H = 2376

    /** 是否处于锁屏 */
    fun isLocked(ctx: Context): Boolean {
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return km.isKeyguardLocked
    }

    /** 唤醒屏幕（KEYCODE_WAKEUP = 224） */
    fun wakeUp(): String =
        ShizukuHelper.exec(arrayOf("sh", "-c", "input keyevent 224"))

    /**
     * 滑动解锁（向上斜滑）。
     * OCE-AN10（Mate 40）真机实测：
     *   从 (571, 1964) 划到 (595, 593)，"立马松手" → 时长取 150ms。
     */
    fun swipeToUnlock(width: Int, height: Int): String {
        return ShizukuHelper.exec(
            arrayOf("sh", "-c", "input swipe $UNLOCK_X1 $UNLOCK_Y1 $UNLOCK_X2 $UNLOCK_Y2 $UNLOCK_MS")
        )
    }

    /**
     * 启动多闪。
     * 优先用 am start 拉起主 Activity（比 monkey 更可靠），
     * 失败再用 monkey 兜底。
     */
    fun launchTargetApp(): String {
        val byAm = ShizukuHelper.exec(
            arrayOf(
                "sh", "-c",
                "am start -n $TARGET_PKG/com.ss.android.ugc.aweme.main.MainActivity"
            )
        )
        if (byAm.contains("Starting") || byAm.contains("Activity") || byAm.contains("Warning")) {
            return byAm
        }
        // 兜底：monkey
        return ShizukuHelper.exec(
            arrayOf("sh", "-c", "monkey -p $TARGET_PKG -c android.intent.category.LAUNCHER 1")
        )
    }

    /** 点击屏幕坐标 */
    fun tap(x: Int, y: Int): String =
        ShizukuHelper.exec(arrayOf("sh", "-c", "input tap $x $y"))

    /**
     * 视觉定位点击：把当前屏幕截图交给 deepseek-flash，让它返回控件坐标，再 input tap。
     *
     * @param targetDesc 自然语言描述，例如"会话列表右上角的搜索图标"
     * @param fallbackX  视觉失败时的兜底坐标
     * @param fallbackY  视觉失败时的兜底坐标
     * @return 人类可读的执行结果
     */
    fun tapByVision(targetDesc: String, fallbackX: Int, fallbackY: Int): String {
        return try {
            val r = VisionClient.locate(targetDesc)
            if (r.found && r.x > 0 && r.y > 0) {
                tap(r.x, r.y)
                "视觉命中「$targetDesc」→ tap(${r.x},${r.y}) ; ${r.desc}"
            } else {
                tap(fallbackX, fallbackY)
                "视觉未命中「$targetDesc」（${r.desc}），回退坐标 tap($fallbackX,$fallbackY)"
            }
        } catch (e: Exception) {
            tap(fallbackX, fallbackY)
            "视觉异常（${e.message}），回退坐标 tap($fallbackX,$fallbackY)"
        }
    }

    // ==================== 解锁坐标（Mate 40 实测） ====================

    /** 上滑解锁：起点 */
    const val UNLOCK_X1 = 571
    const val UNLOCK_Y1 = 1964

    /** 上滑解锁：终点 */
    const val UNLOCK_X2 = 595
    const val UNLOCK_Y2 = 593

    /** 上滑时长（ms）："立马松手" → 取 150ms */
    const val UNLOCK_MS = 150

    // ==================== 实测坐标（Mate 40） ====================

    /** 会话列表右上角「搜索」图标 */
    const val SEARCH_ICON_X = 860
    const val SEARCH_ICON_Y = 173

    /** 搜索页的搜索输入框 */
    const val SEARCH_BOX_X = 260
    const val SEARCH_BOX_Y = 238

    /** 搜索结果第一条联系人右侧的「发私信」按钮 */
    const val SEND_DM_X = 904
    const val SEND_DM_Y = 564

    // ==================== 聊天页坐标（Mate 40 实测） ====================

    /**
     * 聊天页底部的「点我唤起键盘」输入栏。
     * 真机实测：点这里会弹出键盘。
     */
    const val INPUT_X = 230
    const val INPUT_Y = 2368

    /**
     * 键盘弹出后，实际可输入的输入框位置。
     * 真机实测：键盘起来后在这里输入文字。
     */
    const val INPUT_KB_X = 249
    const val INPUT_KB_Y = 1470

    /**
     * 发送按钮（键盘未弹出时）。
     * 真机实测。
     */
    const val SEND_X = 960
    const val SEND_Y = 2284

    /**
     * 发送按钮（键盘已弹出时）。
     * 真机实测。
     */
    const val SEND_KB_X = 988
    const val SEND_KB_Y = 1473
}