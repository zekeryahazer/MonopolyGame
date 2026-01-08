package com.example.monopolygame

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IntroActivity : AppCompatActivity() {

    private var humanCount = 1
    private var botCount = 1
    private val nameInputs = ArrayList<EditText>() // İsim kutularını tutar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        val txtHuman = findViewById<TextView>(R.id.txtHumanTitle)
        val seekHuman = findViewById<SeekBar>(R.id.seekHuman)
        val namesContainer = findViewById<LinearLayout>(R.id.namesContainer)
        val txtBot = findViewById<TextView>(R.id.txtBotTitle)
        val seekBot = findViewById<SeekBar>(R.id.seekBot)
        val txtTotal = findViewById<TextView>(R.id.txtTotalSummary)
        val btnStart = findViewById<Button>(R.id.btnStartGame)

        seekHuman.max = 6
        seekBot.max = 5

        // İsim Kutucuklarını Oluşturan Fonksiyon
        fun updateNameFields() {
            namesContainer.removeAllViews() // Eskileri temizle
            nameInputs.clear()

            for (i in 1..humanCount) {
                val input = EditText(this)
                input.hint = "$i. Oyuncu İsmi"
                input.setText("Oyuncu $i") // Varsayılan isim
                input.inputType = InputType.TYPE_CLASS_TEXT
                input.gravity = Gravity.CENTER
                input.setBackgroundColor(Color.WHITE)
                input.setPadding(20, 20, 20, 20)

                // Kutular arası boşluk
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 10, 0, 10)
                input.layoutParams = params

                namesContainer.addView(input)
                nameInputs.add(input)
            }
        }

        fun updateUI() {
            txtHuman.text = "İnsan Oyuncular: $humanCount 👤"
            txtBot.text = "Yapay Zeka (Bot): $botCount 🤖"
            val total = humanCount + botCount
            txtTotal.text = "Toplam: $total Oyuncu"

            if (total > 6) {
                txtTotal.setTextColor(Color.RED)
                btnStart.isEnabled = false
                btnStart.text = "MAX 6 KİŞİ!"
            } else if (total < 2) {
                txtTotal.setTextColor(Color.RED)
                btnStart.isEnabled = false
                btnStart.text = "EN AZ 2 KİŞİ!"
            } else {
                txtTotal.setTextColor(Color.BLACK)
                btnStart.isEnabled = true
                btnStart.text = "OYUNA BAŞLA 🚀"
            }
        }

        seekHuman.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                humanCount = if (progress < 1) 1 else progress
                if (progress < 1) seekHuman.progress = 1

                if (humanCount + botCount > 6) {
                    botCount = 6 - humanCount
                    seekBot.progress = botCount
                }
                updateNameFields() // Kutucukları güncelle
                updateUI()
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        seekBot.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                botCount = progress
                if (humanCount + botCount > 6) {
                    humanCount = 6 - botCount
                    seekHuman.progress = humanCount
                    updateNameFields()
                }
                updateUI()
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        btnStart.setOnClickListener {
            val players = ArrayList<Player>()

            // SENİN İSTEDİĞİN ÖZEL RENKLER 🎨
            val customColors = listOf(
                Color.BLACK,                        // Siyah
                Color.BLUE,                         // Mavi
                Color.RED,                          // Kırmızı
                Color.DKGRAY,                       // Koyu Gri
                Color.parseColor("#F9A825"),  // Kapalı Sarı (Dark Yellow)
                Color.parseColor("#E65100")   // Kapalı Turuncu (Dark Orange)
            )

            // İnsanları Ekle (Yazdıkları isimlerle)
            for (i in 0 until humanCount) {
                val name = nameInputs[i].text.toString().ifEmpty { "Oyuncu ${i+1}" }
                players.add(Player(name, customColors[i], isBot = false))
            }

            // Botları Ekle
            for (i in 0 until botCount) {
                val colorIndex = humanCount + i
                players.add(Player("Bot ${i+1}", customColors[colorIndex], isBot = true))
            }

            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("PLAYERS", players)
            startActivity(intent)
            finish()
        }

        // Başlangıç ayarı
        updateNameFields()
        updateUI()
    }
}