package com.trollsound

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val serverInput = findViewById<EditText>(R.id.serverInput)
        val connectBtn = findViewById<Button>(R.id.connectBtn)
        val statusText = findViewById<TextView>(R.id.statusText)

        val prefs = getSharedPreferences("troll", MODE_PRIVATE)
        serverInput.setText(prefs.getString("server", ""))

        requestNotificationPermission()

        connectBtn.setOnClickListener {
            val url = serverInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Введи адрес сервера", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("server", url).apply()

            val intent = Intent(this, SoundService::class.java).apply {
                putExtra("server", url)
            }
            ContextCompat.startForegroundService(this, intent)

            statusText.text = "Подключено к $url\nМожно свернуть приложение."
            Toast.makeText(this, "Сервис запущен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }
}
