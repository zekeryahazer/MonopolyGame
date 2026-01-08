package com.example.monopolygame

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    private lateinit var etHumanCount: EditText
    private lateinit var etBotCount: EditText
    private lateinit var namesContainer: LinearLayout
    private lateinit var btnSetNames: Button
    private lateinit var btnStartGame: Button

    private val nameInputs = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        etHumanCount = findViewById(R.id.etHumanCount)
        etBotCount = findViewById(R.id.etBotCount)
        namesContainer = findViewById(R.id.namesContainer)
        btnSetNames = findViewById(R.id.btnSetNames)
        btnStartGame = findViewById(R.id.btnStartGame)

        // İnsan sayısını onayla ve kutucukları aç
        btnSetNames.setOnClickListener {
            generateNameInputs()
        }

        // Oyunu Başlat
        btnStartGame.setOnClickListener {
            startGame()
        }
    }

    private fun generateNameInputs() {
        val countStr = etHumanCount.text.toString()
        if (countStr.isEmpty()) {
            Toast.makeText(this, "Lütfen insan sayısını girin.", Toast.LENGTH_SHORT).show()
            return
        }

        val count = countStr.toInt()

        // KURAL 1: En az 1 İnsan olmak ZORUNDA
        if (count < 1) {
            Toast.makeText(this, "En az 1 insan oyuncu olmalı!", Toast.LENGTH_SHORT).show()
            etHumanCount.setText("1") // Otomatik düzelt
            return
        }

        // KURAL 2: Sadece insan sayısı bile 6'yı geçemez
        if (count > 6) {
            Toast.makeText(this, "En fazla 6 kişilik bir oyun kurabilirsin.", Toast.LENGTH_SHORT).show()
            etHumanCount.setText("6") // Otomatik düzelt
            return
        }

        namesContainer.removeAllViews()
        nameInputs.clear()

        for (i in 1..count) {
            val et = EditText(this)
            et.hint = "$i. Oyuncu İsmi"
            namesContainer.addView(et)
            nameInputs.add(et)
        }
    }

    private fun startGame() {
        // İsim kutucukları oluşturulmamışsa uyar
        if (nameInputs.isEmpty()) {
            Toast.makeText(this, "Önce 'Onayla' butonuna basmalısın.", Toast.LENGTH_SHORT).show()
            return
        }

        // Bot sayısını al (Boşsa 0 say)
        val botCountStr = etBotCount.text.toString()
        val botCount = if (botCountStr.isEmpty()) 0 else botCountStr.toInt()

        // İnsan sayısı
        val humanCount = nameInputs.size

        // TOPLAM KONTROLÜ
        val totalPlayers = humanCount + botCount

        // HATA 1: Toplam oyuncu 2'den azsa (Örn: 1 İnsan, 0 Bot)
        if (totalPlayers < 2) {
            Toast.makeText(this, "Tek başına oynayamazsın aşkım! En az 1 Bot ekle.", Toast.LENGTH_LONG).show()
            return
        }

        // HATA 2: Toplam oyuncu 6'dan fazlaysa
        if (totalPlayers > 6) {
            val fazlalik = totalPlayers - 6
            Toast.makeText(this, "Masa doldu! Toplam sınır 6 kişidir. Bot sayısını $fazlalik azalt.", Toast.LENGTH_LONG).show()
            return
        }

        // --- LİSTEYİ OLUŞTUR VE BAŞLA ---
        val players = ArrayList<Player>()
        val colors = listOf(Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.CYAN)
        var colorIndex = 0

        // İnsanları Ekle
        for (et in nameInputs) {
            val name = et.text.toString().trim()
            // İsim boşsa otomatik isim ver
            val finalName = if (name.isEmpty()) "Oyuncu ${colorIndex + 1}" else name

            players.add(Player(finalName, colors[colorIndex % colors.size], isBot = false))
            colorIndex++
        }

        // Botları Ekle
        for (i in 1..botCount) {
            players.add(Player("Bot $i", colors[colorIndex % colors.size], isBot = true))
            colorIndex++
        }

        // Main Activity'ye geç
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("PLAYERS", players)
        startActivity(intent)
        finish()
    }
}