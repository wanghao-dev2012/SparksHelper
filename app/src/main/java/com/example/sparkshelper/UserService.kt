package com.example.sparkshelper

/**
 * 运行在 Shizuku shell 进程里的远程服务。
 *
 * 这个类会被 Shizuku 或 root 以 shell/root 身份加载，因此它内部执行
 * Runtime.exec 时拥有系统级权限，可以正常调用
 *   input / monkey / am / settings 等命令 —— 这正是 App 主进程沙箱做不到的。
 */
class UserService : IUserService.Stub() {

    override fun exec(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            if (output.isBlank()) "OK (exit=$code)" else output.trim() + "\n(exit=$code)"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    override fun ping(): String = "pong from uid=" + android.os.Process.myUid()
}
