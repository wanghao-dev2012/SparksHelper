package com.example.sparkshelper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 视觉客户端：把当前屏幕截图交给 deepseek-flash（V4.1-Flash，支持 image 输入），
 * 让它返回需要点击的坐标或判断。
 *
 * 设计原则：
 * - API key 从本地文件读取（不硬编码进 APK）
 * - 截图由 App 自己抓（需要 Shizuku/无障碍权限；这里用 screencap）
 * - 只负责"看图 → 出坐标"，点击仍由 ShizukuHelper.exec 执行
 */
object VisionClient {

    /** key 存放路径（用户提供） */
    private const val KEY_FILE = "/sdcard/dsk/dsk.txt"

    /** 模型名（用户指定） */
    private const val MODEL = "deepseek-flash"

    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"

    /** 截图目标分辨率（送模型前缩放，省 token；坐标按原图分辨率换算） */
    private const val MAX_SIDE = 1080

    // ---------------------------------------------------------------
    // key
    // ---------------------------------------------------------------

    /**
     * 读取 API key。
     *
     * 注意：Android 11+ 的 scoped storage 会让 App 进程读不到 /sdcard 根目录文件，
     * 所以优先走 Shizuku shell 读（`cat`），失败再尝试直接读文件。
     */
    fun readApiKey(): String {
        // 方案 A：走 shell 读（最稳）
        try {
            val out = ShizukuHelper.exec(arrayOf("sh", "-c", "cat $KEY_FILE 2>/dev/null"))
            val k = out.trim()
            if (k.startsWith("sk-")) return k
        } catch (e: Exception) {
            // ignore
        }
        // 方案 B：直接读文件（App 若已获得存储权限可用）
        return try {
            java.io.File(KEY_FILE).readText().trim()
        } catch (e: Exception) {
            ""
        }
    }

    // ---------------------------------------------------------------
    // 截图
    // ---------------------------------------------------------------

    /**
     * 抓取当前屏幕。
     *
     * 实现说明：
     * AIDL 的 exec 只返回文本，无法直接传二进制 PNG，所以走"落盘 + 放开权限"：
     *   1) shell 执行 `screencap -p /data/local/tmp/sparks_cap.png`
     *   2) `chmod 666` 让 App 可读
     *   3) App 侧 BitmapFactory.decodeFile
     * 若 A 方案失败，再尝试直接读到 App 私有目录（/sdcard/Android/data/<pkg>/cache）。
     */
    fun captureScreen(): Bitmap? {
        // 方案 A：/data/local/tmp（shell 可写、chmod 后 App 可读）
        val tmp = "/data/local/tmp/sparks_cap.png"
        try {
            ShizukuHelper.exec(arrayOf("sh", "-c", "screencap -p $tmp"))
            ShizukuHelper.exec(arrayOf("sh", "-c", "chmod 666 $tmp"))
            val bmp = BitmapFactory.decodeFile(tmp)
            if (bmp != null) return bmp
            android.util.Log.w("SparksHelper", "A 方案解码失败，尝试 B 方案")
        } catch (e: Exception) {
            android.util.Log.e("SparksHelper", "captureScreen A 失败: ${e.message}")
        }
        return null
    }

    // ---------------------------------------------------------------
    // 图片编码
    // ---------------------------------------------------------------

    private fun bitmapToBase64(bmp: Bitmap): Pair<String, IntArray> {
        val w = bmp.width
        val h = bmp.height
        val maxSide = maxOf(w, h)
        val scale = if (maxSide > MAX_SIDE) MAX_SIDE.toFloat() / maxSide else 1f
        val sw = (w * scale).toInt().coerceAtLeast(1)
        val sh = (h * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, sw, sh, true) else bmp

        val bos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, bos)
        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        return b64 to intArrayOf(w, h, sw, sh)
    }

    // ---------------------------------------------------------------
    // 核心：问模型"该点哪儿"
    // ---------------------------------------------------------------

    /**
     * 请求模型在截图中定位某个目标，返回原始坐标（按原图分辨率）。
     *
     * @param targetDesc 自然语言描述，例如"聊天页底部的输入框"
     * @return VisionResult
     */
    fun locate(targetDesc: String): VisionResult {
        val key = readApiKey()
        if (key.isEmpty()) return VisionResult(false, -1, -1, "未找到 API key（$KEY_FILE）")

        val bmp = captureScreen()
            ?: return VisionResult(false, -1, -1, "截图失败（screencap 无权限？）")

        val (b64, dims) = bitmapToBase64(bmp)
        val ow = dims[0]; val oh = dims[1]; val sw = dims[2]; val sh = dims[3]

        val prompt = """
            你是手机 UI 自动化助手。这是一张 Android 手机截图，原始分辨率 ${ow}x${oh}（你看到的图已缩放到 ${sw}x${sh}）。
            请在图中找到：$targetDesc
            要求：
            1. 只输出一个 JSON 对象，不要任何多余文字或 markdown 代码块。
            2. 格式：{"found": true/false, "x": 整数, "y": 整数, "desc": "简短说明"}
            3. x/y 必须是【原始分辨率 ${ow}x${oh}】下的像素坐标，即点击该控件中心的位置。
            4. 若没找到，found 设为 false，x/y 给 0。
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 300)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                    })
                })
            }))
        }

        return try {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 60000
                readTimeout = 120000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                return VisionResult(false, -1, -1, "HTTP $code: ${resp.take(200)}")
            }

            val content = JSONObject(resp)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")

            parseResult(content)
        } catch (e: Exception) {
            VisionResult(false, -1, -1, "请求异常: ${e.message}")
        }
    }

    private fun parseResult(content: String): VisionResult {
        // 容错：从返回里抠出第一个 JSON 对象
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return VisionResult(false, -1, -1, "模型返回无法解析: ${content.take(160)}")
        }
        return try {
            val o = JSONObject(content.substring(start, end + 1))
            VisionResult(
                found = o.optBoolean("found", false),
                x = o.optInt("x", 0),
                y = o.optInt("y", 0),
                desc = o.optString("desc", "")
            )
        } catch (e: Exception) {
            VisionResult(false, -1, -1, "JSON 解析失败: ${content.take(160)}")
        }
    }

    /**
     * 定位并点击。成功返回描述，失败返回错误信息。
     */
    fun locateAndTap(targetDesc: String): String {
        val r = locate(targetDesc)
        if (!r.found) return "未找到「$targetDesc」: ${r.desc}"
        val res = ShizukuHelper.exec(arrayOf("sh", "-c", "input tap ${r.x} ${r.y}"))
        return "已点击「$targetDesc」(${r.x},${r.y})；desc=${r.desc}；exec=${res.trim()}"
    }

    /**
     * 让模型判断当前屏幕状态（用于流程分支），返回纯文本。
     */
    fun ask(question: String): String {
        val key = readApiKey()
        if (key.isEmpty()) return "未找到 API key（$KEY_FILE）"
        val bmp = captureScreen() ?: return "截图失败"
        val (b64, _) = bitmapToBase64(bmp)
        val body = JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", 300)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply { put("type", "text"); put("text", question) })
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
                    })
                })
            }))
        }
        return try {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 60000
                readTimeout = 120000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(resp).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        } catch (e: Exception) {
            "请求异常: ${e.message}"
        }
    }

    data class VisionResult(
        val found: Boolean,
        val x: Int,
        val y: Int,
        val desc: String
    )
}
