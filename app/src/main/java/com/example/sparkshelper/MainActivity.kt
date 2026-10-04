package com.example.sparkshelper

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var namesBox: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var nameInput: EditText

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku 授权成功", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Shizuku 授权被拒绝", Toast.LENGTH_SHORT).show()
        }
        refreshShizukuStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Shizuku.addRequestPermissionResultListener(permListener)

        statusText = findViewById(R.id.status)
        namesBox = findViewById(R.id.namesBox)
        nameInput = findViewById(R.id.nameInput)
        val addBtn = findViewById<Button>(R.id.addBtn)
        val shizukuBtn = findViewById<Button>(R.id.shizukuBtn)
        val a11yBtn = findViewById<Button>(R.id.a11yBtn)
        val runBtn = findViewById<Button>(R.id.runBtn)

        shizukuBtn.setOnClickListener {
            if (!ShizukuHelper.isAvailable()) {
                Toast.makeText(this, "Shizuku 未运行，请先启动 Shizuku", Toast.LENGTH_SHORT).show()
            } else {
                ShizukuHelper.requestPermission()
            }
        }

        a11yBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        addBtn.setOnClickListener {
            val n = nameInput.text.toString().trim()
            if (n.isNotEmpty()) {
                lifecycleScope.launch {
                    val list = TargetStore.load(this@MainActivity).toMutableList()
                    list.add(n)
                    TargetStore.save(this@MainActivity, list)
                    nameInput.setText("")
                    renderList()
                }
            }
        }

        runBtn.setOnClickListener {
            if (ShizukuHelper.isAvailable() && ShizukuHelper.hasPermission()) {
                val svc = AutoClickServiceHolder.service
                if (svc == null) {
                    Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                } else {
                    lifecycleScope.launch {
                        val targets = TargetStore.load(this@MainActivity)
                        if (targets.isEmpty()) {
                            Toast.makeText(this, "名单为空，请先添加", Toast.LENGTH_SHORT).show()
                        } else {
                            val dm = resources.displayMetrics
                            svc.runTask(dm.widthPixels, dm.heightPixels, targets)
                            statusText.text = "任务已下发（${targets.size} 人）"
                        }
                    }
                }
            } else {
                Toast.makeText(this, "请先授权 Shizuku", Toast.LENGTH_SHORT).show()
            }
        }

        renderList()
    }

    private fun refreshShizukuStatus() {
        statusText.text = when {
            !ShizukuHelper.isAvailable() -> "Shizuku：未运行"
            ShizukuHelper.hasPermission() -> "Shizuku：已授权 ✅"
            else -> "Shizuku：未授权"
        }
    }

    private fun renderList() {
        lifecycleScope.launch {
            val list = TargetStore.load(this@MainActivity)
            namesBox.removeAllViews()
            list.forEach { name ->
                namesBox.addView(TextView(this@MainActivity).apply { text = "• $name" })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }
}