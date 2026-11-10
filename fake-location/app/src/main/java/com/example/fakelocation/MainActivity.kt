package com.example.fakelocation

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var selectedPointText: TextView
    private lateinit var dao: LocationsDao
    private lateinit var adapter: LocationAdapter

    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var marker: Marker? = null

    companion object {
        private const val REQ_PERMISSIONS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        dao = LocationsDao(this)
        map = findViewById(R.id.map)
        selectedPointText = findViewById(R.id.selectedPointText)

        // --- 地図初期化 ---
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(35.681236, 139.767125)) // 東京駅

        // --- タップで位置選択 ---
        val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                setSelectedPoint(p.latitude, p.longitude)
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean {
                showSaveDialog(p.latitude, p.longitude)
                return true
            }
        })
        map.overlays.add(0, tapOverlay)

        // --- 権限要求 ---
        requestPermissionsIfNeeded()

        // --- 事前登録地点リスト ---
        adapter = LocationAdapter(dao.getAll(),
            onSelect = { loc -> setSelectedPoint(loc.lat, loc.lng); moveTo(loc.lat, loc.lng) },
            onDelete = { index ->
                dao.removeAt(index)
                adapter.update(dao.getAll())
                Toast.makeText(this, "削除しました", Toast.LENGTH_SHORT).show()
            })
        findViewById<RecyclerView>(R.id.savedList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        // --- ボタン ---
        findViewById<Button>(R.id.btnStart).setOnClickListener { startFix() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, MockLocationService::class.java))
            Toast.makeText(this, "固定を解除しました", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnCurrent).setOnClickListener { fixAtCurrentLocation() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { runTest() }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            if (selectedLat != null) showSaveDialog(selectedLat!!, selectedLng!!)
            else Toast.makeText(this, "地図上で位置を選択してください", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 位置選択 ====================

    private fun setSelectedPoint(lat: Double, lng: Double) {
        selectedLat = lat
        selectedLng = lng
        selectedPointText.text = "選択中: %.6f, %.6f".format(lat, lng)

        marker?.let { map.overlays.remove(it) }
        marker = Marker(map).apply {
            position = GeoPoint(lat, lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "固定する位置"
        }
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun moveTo(lat: Double, lng: Double) {
        map.controller.animateTo(GeoPoint(lat, lng))
    }

    // ==================== 現在地で固定 ====================

    @SuppressLint("MissingPermission")
    private fun fixAtCurrentLocation() {
        if (!requestPermissionsIfNeeded()) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val last = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }

        if (last != null) {
            setSelectedPoint(last.latitude, last.longitude)
            moveTo(last.latitude, last.longitude)
            startFix()
        } else {
            Toast.makeText(this, "現在地を取得できません。地図で選択してください", Toast.LENGTH_LONG).show()
        }
    }

    // ==================== 固定開始 ====================

    private fun startFix() {
        val lat = selectedLat
        val lng = selectedLng
        if (lat == null || lng == null) {
            Toast.makeText(this, "地図上で位置をタップして選択してください", Toast.LENGTH_SHORT).show()
            return
        }
        if (!checkMockPermission()) return

        val intent = Intent(this, MockLocationService::class.java).apply {
            putExtra("lat", lat)
            putExtra("lng", lng)
        }
        startForegroundService(intent)
        Toast.makeText(this, "位置を固定しました", Toast.LENGTH_SHORT).show()
    }

    // ==================== テスト機能 ====================

    private fun runTest() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val builder = StringBuilder("【テスト結果】\n\n")

        // 1. モック権限チェック
        val hasMock = try {
            lm.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false,
                false, true, true, true, 3, 5)
            lm.removeTestProvider(LocationManager.GPS_PROVIDER)
            true
        } catch (e: SecurityException) {
            false
        }
        builder.append("① ダミー位置情報アプリ設定: ")
            .append(if (hasMock) "✅ OK" else "❌ 未設定(デベロッパーオプションで選択してください)")
            .append("\n\n")

        // 2. サービス稼働チェック
        val serviceRunning = MockLocationService.isRunning
        builder.append("② 固定サービス稼働中: ")
            .append(if (serviceRunning) "✅ 稼働中" else "❌ 停止中")
            .append("\n\n")

        // 3. 実際に報告されている位置を取得
        val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (last != null) {
            builder.append("③ 現在報告されている位置:\n")
                .append("   緯度: %.6f\n".format(last.latitude))
                .append("   経度: %.6f\n".format(last.longitude))
                .append("   モック位置: ")
                .append(if (last.isFromMockProvider) "✅ はい(固定が反映されています)" else "❌ いいえ")
                .append("\n\n")

            // 4. 選択位置との距離を計算
            if (selectedLat != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    selectedLat, selectedLng, last.latitude, last.longitude, results)
                val dist = results[0]
                builder.append("④ 選択位置との差: ")
                    .append(if (dist < 50) "✅ ${"%.1f".format(dist)}m (ほぼ一致)" else "❌ ${"%.1f".format(dist)}m (ズレあり)")
            }
        } else {
            builder.append("③ 位置情報をまだ取得できていません。\n   固定開始後、数秒待って再テストしてください。")
        }

        AlertDialog.Builder(this)
            .setTitle("テスト実行")
            .setMessage(builder.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    // ==================== ダイアログ・権限 ====================

    private fun showSaveDialog(lat: Double, lng: Double) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_save_location, null)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        nameInput.setHint("%.4f, %.4f".format(lat, lng))

        AlertDialog.Builder(this)
            .setTitle("この地点を登録")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString().ifBlank { "%.4f, %.4f".format(lat, lng) }
                dao.add(SavedLocation(name, lat, lng))
                adapter.update(dao.getAll())
                Toast.makeText(this, "「$name」を登録しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun requestPermissionsIfNeeded(): Boolean {
        val needed = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMISSIONS)
            return false
        }
        return true
    }

    private fun checkMockPermission(): Boolean {
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm.addTestProvider(LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true, 3, 5)
            lm.removeTestProvider(LocationManager.GPS_PROVIDER)
            true
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

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
