package com.example.sparkshelper

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {

    const val REQ_CODE = 1001

    /** Shizuku 服务是否在运行 */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    /** 本应用是否已获得 Shizuku 授权 */
    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    /** 弹出 Shizuku 授权框（需在 Activity 中调用） */
    fun requestPermission() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                // 用户曾拒绝，需引导去 Shizuku 应用中手动授权
                return
            }
            Shizuku.requestPermission(REQ_CODE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 执行 shell 命令，返回 stdout（失败返回 stderr 或 ERROR 信息）。
     * 注意：应在子线程调用，避免阻塞。
     */
    fun exec(cmd: Array<String>): String {
        return try {
            val process = Shizuku.newProcess(cmd, null, null)
            process.waitFor()
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            if (out.isNotBlank()) out else err
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}