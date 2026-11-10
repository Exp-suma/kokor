package com.example.fakelocation

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val latInput = findViewById<EditText>(R.id.latInput)
        val lngInput = findViewById<EditText>(R.id.lngInput)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)

        startBtn.setOnClickListener {
            val lat = latInput.text.toString().toDoubleOrNull()
            val lng = lngInput.text.toString().toDoubleOrNull()
            if (lat == null || lng == null) {
                Toast.makeText(this, "緯度・経度を入力してください", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!checkMockPermission()) return

            val intent = Intent(this, MockLocationService::class.java).apply {
                putExtra("lat", lat)
                putExtra("lng", lng)
            }
            startForegroundService(intent)
            Toast.makeText(this, "位置情報を固定しました: ($lat, $lng)", Toast.LENGTH_SHORT).show()
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, MockLocationService::class.java))
            Toast.makeText(this, "固定を解除しました", Toast.LENGTH_SHORT).show()
        }
    }

    // このアプリが「ダミーの位置情報アプリ」に選ばれているか確認
    private fun checkMockPermission(): Boolean {
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                lm.addTestProvider(
                    android.location.LocationManager.GPS_PROVIDER,
                    false, false, false, false, true, true, true, 3, 5
                )
                lm.removeTestProvider(android.location.LocationManager.GPS_PROVIDER)
                true
            } else true
        } catch (e: SecurityException) {
            AlertDialog.Builder(this)
                .setTitle("設定が必要です")
                .setMessage("「デベロッパーオプション → ダミーの位置情報アプリ」で\nこのアプリを選択してください。")
                .setPositiveButton("設定を開く") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
                .setNegativeButton("キャンセル", null)
                .show()
            false
        }
    }
}
