package com.example.sparkshelper

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

object ShizukuHelper {

    const val REQ_CODE = 1001

    /** UserService 绑定结果（远程 shell 进程里的实例） */
    @Volatile
    private var remote: IUserService? = null

    @Volatile
    private var bound = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("sparks_user_service")
        .debuggable(BuildConfig.DEBUG)
        .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = IUserService.Stub.asInterface(service)
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            bound = false
        }
    }

    /** Shizuku 服务是否在运行 */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    /** 本应用是否已获得 Shizuku 授权 */
    fun hasPermission(): Boolean = try {
        isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    /** 是否已连上远程 shell 通道 */
    fun isRemoteReady(): Boolean = remote != null

    /** 弹出 Shizuku 授权框（需在 Activity 中调用） */
    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQ_CODE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 绑定远程 UserService。绑定成功后 [exec] 才会真正走 shell 身份。
     * 建议在获得授权后调用一次。
     */
    fun bindUserService() {
        if (bound) return
        if (!hasPermission()) return
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 解除绑定 */
    fun unbindUserService() {
        try {
            if (bound) Shizuku.unbindUserService(userServiceArgs, connection, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        remote = null
        bound = false
    }

    /**
     * 执行 shell 命令。
     * - 优先走已绑定的远程 UserService（真实 shell 身份，可执行 input/monkey/am）
     * - 未绑定时回退到本进程 ProcessBuilder（仅作兜底提示）
     */
    fun exec(cmd: Array<String>): String {
        val realCmd = cmdToShell(cmd)

        var s = remote
        if (s == null) {
            bindUserService()
            s = remote
        }
        if (s != null) {
            return try {
                s.exec(realCmd)
            } catch (e: Exception) {
                "ERROR(remote): ${e.message}"
            }
        }

        // 回退：本进程执行（大概率无权限，但给出明确错误）
        return try {
            val process = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.ifBlank { "OK(no-remote)" }
        } catch (e: Exception) {
            "ERROR(no-remote): ${e.message} (command=$realCmd)"
        }
    }

    /** 把命令数组拼成一条 shell 字符串（我们调用形式固定为 arrayOf("sh","-c","<real>")） */
    private fun cmdToShell(cmd: Array<String>): String {
        return if (cmd.size >= 3 && cmd[0] == "sh" && cmd[1] == "-c") {
            cmd[2]
        } else {
            cmd.joinToString(" ") { shellQuote(it) }
        }
    }

    private fun shellQuote(s: String): String {
        if (s.isEmpty()) return "''"
        return "'" + s.replace("'", "'\\''") + "'"
    }
}