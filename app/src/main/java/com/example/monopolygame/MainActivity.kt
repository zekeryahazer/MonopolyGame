package com.example.monopolygame

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.pow

// --- YARDIMCI SINIFLAR ---
data class GameCard(val text: String, val action: (Player) -> Unit)
data class TradeRequest(val requester: Player, val target: Player, val wantedSquare: Square, val offeredSquare: Square?, val offeredMoney: Int)

class MainActivity : AppCompatActivity() {

    // --- DEĞİŞKENLER ---
    private lateinit var players: ArrayList<Player>
    private var currentPlayerIndex = 0
    private var turnCount = 0

    private lateinit var board: List<Square>
    private var bankPot = 0

    private lateinit var chanceDeck: List<GameCard>
    private lateinit var communityDeck: List<GameCard>

    // --- UI BİLEŞENLERİ ---
    private lateinit var monopolyView: MonopolyView
    private lateinit var txtPlayerMoney: TextView
    private lateinit var txtBotMoney: TextView
    private lateinit var actionButtonsLayout: LinearLayout
    private lateinit var btnBuy: Button
    private lateinit var btnPass: Button
    private lateinit var btnTrade: Button
    private lateinit var btnProperties: Button
    private lateinit var btnPause: ImageButton

    // Ticaret ve Bilgi Ekranları
    private lateinit var tradeOverlay: RelativeLayout
    private lateinit var txtTradeAlert: TextView

    // Borç Takibi
    private var pendingDebtAmount: Int = 0
    private var pendingCreditor: Player? = null
    private val PREFS_NAME = "MonopolySaveGame"
    private val gson = Gson()

    // Animasyon
    private val handler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null

    // ==========================================
    // 1. BAŞLANGIÇ (ON CREATE)
    // ==========================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_main)

        // XML elemanlarını bağla
        monopolyView = findViewById(R.id.monopolyView)
        txtPlayerMoney = findViewById(R.id.txtPlayerMoney)
        txtBotMoney = findViewById(R.id.txtBotMoney)
        actionButtonsLayout = findViewById(R.id.actionButtonsLayout)
        btnBuy = findViewById(R.id.btnBuy)
        btnPass = findViewById(R.id.btnPass)
        btnTrade = findViewById(R.id.btnTrade)
        btnProperties = findViewById(R.id.btnProperties)
        btnPause = findViewById(R.id.btnPause)
        tradeOverlay = findViewById(R.id.tradeOverlay)
        txtTradeAlert = findViewById(R.id.txtTradeAlert)

        // Verileri hazırla
        initBoard()
        initCards()
        monopolyView.board = board

        // IntroActivity'den gelen oyuncuları al
        val incomingPlayers = intent.getSerializableExtra("PLAYERS") as? ArrayList<Player>

        if (incomingPlayers == null && hasSavedGame()) {
            AlertDialog.Builder(this)
                .setTitle("KAYITLI OYUN")
                .setMessage("Yarım kalmış bir oyunun var. Devam etmek ister misin?")
                .setPositiveButton("DEVAM ET") { _, _ -> loadGame() }
                .setNegativeButton("YENİ OYUN") { _, _ ->
                    val intent = Intent(this, IntroActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                .setCancelable(false).show()
        } else {
            // Yeni Oyun (Varsayılan veya Intro'dan gelen)
            players = incomingPlayers ?: arrayListOf(
                Player("Zekerya", Color.BLUE, isBot = false),
                Player("Bot 1", Color.RED, isBot = true)
            )
            monopolyView.players = players
            updateUI()
            startTurn(getCurrentPlayer())
        }

        // Tıklama olayları
        monopolyView.onRollClick = {
            val p = getCurrentPlayer()
            if (!p.isBot) playTurn(p)
        }

        monopolyView.onSquareClick = { index ->
            handleSquareClick(index)
        }

        // Butonları Kur
        setupButtons()

        // Pause Menüsü
        btnPause.setOnClickListener { showPauseDialog() }
    }

    private fun getCurrentPlayer(): Player = players[currentPlayerIndex]

    // --- SİSTEM AYARLARI ---
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }
    override fun onResume() { super.onResume(); hideSystemUI() }
    override fun onPause() { super.onPause(); saveGame() }
    override fun onDestroy() { super.onDestroy(); animationRunnable?.let { handler.removeCallbacks(it) } }

    // --- BUTON KURULUMU ---
    private fun setupButtons() {
        // Satın Al Butonu
        btnBuy.setOnClickListener {
            val p = getCurrentPlayer()
            val sq = board[p.currentPosition]
            val id = players.indexOf(p) + 1

            if (sq.ownerId == -1) {
                if (p.money >= sq.price) {
                    buyProperty(p, sq)
                    nextTurnOrDouble(p)
                } else {
                    showMessage("Yetersiz Bakiye!")
                }
            }
            else if (sq.ownerId == id && canBuildHouse(p, sq)) {
                buildHouse(p, sq)
                nextTurnOrDouble(p)
            }
            else if (p.isInJail) {
                payBail(p)
            }
        }

        // Pas Geç Butonu
        btnPass.setOnClickListener {
            val p = getCurrentPlayer()
            if (p.isInJail) {
                tryDoubleToEscape(p)
            } else {
                showMessage("Pas geçildi")
                actionButtonsLayout.visibility = View.GONE
                nextTurnOrDouble(p)
            }
        }

        btnTrade.setOnClickListener { initiateTradeFlow() }
        btnProperties.setOnClickListener { showPropertyManager(getCurrentPlayer(), false) }
    }

    // ==========================================
    // 2. OYUN AKIŞI
    // ==========================================
    private fun startTurn(p: Player) {
        updateUI()
        actionButtonsLayout.visibility = View.GONE

        if (p.isBankrupt) {
            nextTurnOrDouble(p)
            return
        }

        if (p.isBot) {
            manageBotFinances(p, 0)
            showMessage("Sıra ${p.name} (Bot)...")
            handler.postDelayed({ playTurn(p) }, 1000)
        } else {
            showMessage("Sıra ${p.name}!")
            monopolyView.isRollButtonEnabled = true
            monopolyView.invalidate()

            actionButtonsLayout.visibility = View.VISIBLE
            btnBuy.visibility = View.GONE
            btnPass.visibility = View.GONE
            btnTrade.visibility = View.VISIBLE
            btnProperties.visibility = View.VISIBLE
        }
    }

    private fun playTurn(p: Player) {
        monopolyView.isRollButtonEnabled = false
        monopolyView.invalidate()
        actionButtonsLayout.visibility = View.GONE

        if (p.isInJail) {
            handleJailTurn(p)
            return
        }

        rollDiceWithAnimation { d1, d2 ->
            if (d1 == d2) p.doubleDiceCount++ else p.doubleDiceCount = 0

            if (p.doubleDiceCount >= 3) {
                showMessage("3 Kere Çift: HAPİS!")
                sendToJail(p)
                return@rollDiceWithAnimation
            }

            movePlayerStepByStep(p, d1 + d2) {
                handleSquare(p, board[p.currentPosition])
                updateUI()
            }
        }
    }

    private fun handleSquare(p: Player, sq: Square) {
        // -- KÖŞELER VE VERGİLER --
        if (sq.name == "HAPSE GİR") { sendToJail(p); return }

        // !!! KODES VE ZİYARETÇİ KONTROLÜ (SERT KURAL) !!!
        if (sq.name.contains("KODES") || sq.name.contains("Ziyaret")) {
            showMessage("Sadece Ziyaretçisin.")
            nextTurnOrDouble(p)
            return
        }

        if (sq.name == "Gelir V.") { payToPot(p, 200); return }
        if (sq.name == "Lüks V.") { payToPot(p, 100); return }

        // -- BANKA / POT --
        if (sq.name == "BANKA") {
            if (bankPot > 0) {
                p.money += bankPot
                showMessage("JACKPOT! $bankPot₺ Kazandın!")
                bankPot = 0
            }
            nextTurnOrDouble(p)
            return
        }

        // -- KARTLAR --
        if (sq.name == "ŞANS") { drawCard(p, "ŞANS KARTI", chanceDeck); return }
        if (sq.name == "K.Fonu") { drawCard(p, "KAMU FONU", communityDeck); return }

        val pId = players.indexOf(p) + 1

        // -- MÜLK YÖNETİMİ --
        if (sq.ownerId != -1 && sq.ownerId != pId) {
            // Başkasının mülkü
            payRent(p, sq)
        }
        else if (sq.ownerId == pId && canBuildHouse(p, sq)) {
            // Kendi mülkü
            if (!p.isBot) {
                showActionButtons("EV KUR", "PAS GEÇ", "İnşaat?", true)
            } else {
                buildHouse(p, sq)
                nextTurnOrDouble(p)
            }
        }
        else if (sq.ownerId == -1 && sq.price > 0) {
            // Sahipsiz mülk
            if (!p.isBot) {
                showActionButtons("SATIN AL", "PAS GEÇ", "Satılık", true)
            } else {
                // Bot Mantığı
                if (p.money > sq.price + 150) {
                    buyProperty(p, sq)
                    nextTurnOrDouble(p) // Bot alınca tur geçer
                } else {
                    showMessage("Bot pas geçti.")
                    nextTurnOrDouble(p)
                }
            }
        }
        else {
            nextTurnOrDouble(p)
        }
    }

    // --- KİRA VE ÖDEME ---
    private fun payRent(p: Player, sq: Square) {
        if (sq.isMortgaged) {
            showMessage("Mülk İpotekli. Kira yok.")
            nextTurnOrDouble(p)
            return
        }

        val owner = players[sq.ownerId - 1]
        if (owner.isBankrupt) {
            nextTurnOrDouble(p)
            return
        }

        var rent = 0
        if (sq.colorGroup == "İstasyon" || sq.colorGroup == "İskele") {
            val count = board.count { (it.colorGroup == "İstasyon" || it.colorGroup == "İskele") && it.ownerId == sq.ownerId }
            rent = 25 * (2.0.pow(count - 1)).toInt()
        }
        else if (sq.colorGroup == "Şirket") {
            val c = board.count { it.colorGroup == "Şirket" && it.ownerId == sq.ownerId }
            val d = monopolyView.dice1 + monopolyView.dice2
            rent = if (c == 2) d * 10 else d * 4
        }
        else if (sq.rents.isNotEmpty()) {
            if (sq.houseCount == 0 && hasFullColorSet(sq.ownerId, sq.colorGroup)) {
                rent = sq.rents[0] * 2
            } else {
                rent = sq.rents[sq.houseCount.coerceAtMost(5)]
            }
        }

        processPayment(p, owner, rent)
    }

    private fun processPayment(d: Player, c: Player?, amt: Int) {
        if (d.money >= amt) {
            d.money -= amt
            if (c != null) c.money += amt else bankPot += amt
            showMessage("Ödendi: $amt₺")
            updateUI()
            nextTurnOrDouble(d)
        } else {
            val total = calculateTotalAssets(d)
            if (total >= amt) {
                pendingDebtAmount = amt; pendingCreditor = c
                showMessage("PARA YETMİYOR! İpotek yap.")

                if (d.isBot) {
                    manageBotFinances(d, amt)
                    if (d.money >= amt) processPayment(d, c, amt)
                    else handleBankruptcy(d, c)
                }
                else {
                    AlertDialog.Builder(this)
                        .setTitle("BORÇ: $amt₺")
                        .setMessage("Nakit bitti. İpotek yapmalısın.")
                        .setPositiveButton("TAPULARI YÖNET") { _, _ -> showPropertyManager(d, true) }
                        .setCancelable(false).show()
                }
            } else {
                handleBankruptcy(d, c)
            }
        }
    }

    private fun handleBankruptcy(d: Player, c: Player?) {
        d.isBankrupt = true
        val dId = players.indexOf(d) + 1
        val cId = if (c != null) players.indexOf(c) + 1 else -1

        if (c != null) {
            c.money += d.money
            showMessage("${d.name} İFLAS ETTİ! Varlıkları devredildi.")
        } else {
            bankPot += d.money
            showMessage("${d.name} İFLAS ETTİ! Tapular bankaya.")
        }

        d.money = 0
        board.forEach {
            if (it.ownerId == dId) {
                if (c != null) it.ownerId = cId
                else {
                    it.ownerId = -1
                    it.isMortgaged = false
                    it.houseCount = 0
                }
            }
        }
        updateUI()

        val active = players.filter { !it.isBankrupt }
        if (active.size == 1) {
            AlertDialog.Builder(this)
                .setTitle("OYUN BİTTİ")
                .setMessage("KAZANAN: ${active[0].name} 🏆")
                .setPositiveButton("ÇIK") { _, _ -> finish() }
                .setCancelable(false).show()
        } else {
            nextTurnOrDouble(d)
        }
    }

    private fun nextTurnOrDouble(p: Player) {
        actionButtonsLayout.visibility = View.GONE
        pendingDebtAmount = 0
        pendingCreditor = null

        val canPlay = (!p.isBankrupt && monopolyView.dice1 == monopolyView.dice2 && !p.isInJail)

        handler.postDelayed({
            if (canPlay) {
                showMessage("ÇİFT GELDİ! Tekrar oynuyorsun...")
                if (!p.isBot) {
                    monopolyView.isRollButtonEnabled = true
                    monopolyView.invalidate()
                    actionButtonsLayout.visibility = View.VISIBLE
                    btnBuy.visibility = View.GONE; btnPass.visibility = View.GONE
                    btnTrade.visibility = View.VISIBLE; btnProperties.visibility = View.VISIBLE
                } else {
                    handler.postDelayed({ playTurn(p) }, 1500)
                }
            } else {
                var next = (currentPlayerIndex + 1) % players.size
                var safe = 0
                while (players[next].isBankrupt && safe < players.size) {
                    next = (next + 1) % players.size
                    safe++
                }
                currentPlayerIndex = next
                if (currentPlayerIndex == 0) turnCount++
                players[currentPlayerIndex].doubleDiceCount = 0
                startTurn(getCurrentPlayer())
            }
        }, 800)
    }

    // --- KART HAREKETİ ---
    private fun cardMoveTo(p: Player, t: Int) {
        if (p.currentPosition > t && t != 10) {
            p.money += 200
            Toast.makeText(this, "Başlangıçtan geçtin: +200₺", Toast.LENGTH_SHORT).show()
        }
        p.currentPosition = t
        updateUI()
        handler.postDelayed({ handleSquare(p, board[t]) }, 1000)
    }

    private fun drawCard(p: Player, title: String, deck: List<GameCard>) {
        val card = deck.random()
        if (p.isBot) {
            showMessage("$title: ${card.text}")
            handler.postDelayed({ card.action(p); updateUI() }, 1500)
        } else {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(card.text)
                .setCancelable(false)
                .setPositiveButton("TAMAM") { _, _ -> card.action(p); updateUI() }
                .show()
        }
    }

    // --- DİĞER YARDIMCI FONKSİYONLAR ---
    private fun payBail(p: Player) {
        if(p.money >= 50) {
            p.money -= 50; p.isInJail = false; p.jailTurnCount = 0
            showMessage("Kefaret ödendi!"); actionButtonsLayout.visibility = View.GONE; playTurn(p)
        } else showMessage("Yetersiz Bakiye!")
    }

    private fun payToPot(p: Player, a: Int) { processPayment(p, null, a) }
    private fun showMessage(m: String) { monopolyView.infoText = m; monopolyView.invalidate() }
    private fun showActionButtons(b: String, ps: String, i: String, t: Boolean) {
        actionButtonsLayout.visibility = View.VISIBLE
        btnBuy.visibility = View.VISIBLE; btnPass.visibility = View.VISIBLE
        btnBuy.text = b; btnPass.text = ps
        btnTrade.visibility = if (t) View.VISIBLE else View.GONE
        btnProperties.visibility = View.VISIBLE
        showMessage(i)
    }
    private fun calculateTotalAssets(p: Player): Int {
        var t = p.money
        board.filter { it.ownerId == players.indexOf(p) + 1 && !it.isMortgaged }.forEach { t += (it.houseCount * it.housePrice / 2) + (it.price / 2) }
        return t
    }
    private fun buyProperty(p: Player, sq: Square) { p.money -= sq.price; sq.ownerId = players.indexOf(p) + 1; showMessage("Mülk alındı!"); actionButtonsLayout.visibility = View.GONE; updateUI() }
    private fun buildHouse(p: Player, sq: Square) { p.money -= sq.housePrice; sq.houseCount++; showMessage(if (sq.houseCount == 5) "OTEL" else "EV"); actionButtonsLayout.visibility = View.GONE; updateUI() }

    private fun tryDoubleToEscape(p: Player) {
        actionButtonsLayout.visibility = View.GONE
        rollDiceWithAnimation { d1, d2 ->
            if (d1 == d2) {
                p.isInJail = false; p.jailTurnCount = 0; showMessage("Çift attın! Özgürsün.")
                movePlayerStepByStep(p, d1 + d2) { handleSquare(p, board[p.currentPosition]); updateUI() }
            } else {
                p.jailTurnCount++; showMessage("Çift gelmedi.")
                if (p.jailTurnCount >= 3) { p.isInJail = false; p.money -= 50; nextTurnOrDouble(p) } else nextTurnOrDouble(p)
            }
        }
    }

    private fun sendToJail(p: Player) { p.isInJail = true; p.currentPosition = 10; p.jailTurnCount = 0; p.doubleDiceCount = 0; showMessage("HAPİS"); updateUI(); nextTurnOrDouble(p) }
    private fun handleJailTurn(p: Player) {
        showMessage("Hapistesin.")
        if (!p.isBot) showActionButtons("KEFARET (50₺)", "ZAR AT", "Hapis", true)
        else { if (p.money >= 500) payBail(p) else tryDoubleToEscape(p) }
    }

    private fun rollDiceWithAnimation(cb: (Int, Int) -> Unit) {
        var c = 0
        val r = object : Runnable {
            override fun run() {
                monopolyView.dice1 = (1..6).random(); monopolyView.dice2 = (1..6).random(); monopolyView.invalidate()
                if (c++ < 8) handler.postDelayed(this, 100) else cb(monopolyView.dice1, monopolyView.dice2)
            }
        }
        handler.post(r)
    }

    private fun movePlayerStepByStep(p: Player, s: Int, onEnd: () -> Unit) {
        var c = 0
        animationRunnable = object : Runnable {
            override fun run() {
                if (c++ < s) {
                    monopolyView.animPlayerIndex = players.indexOf(p); monopolyView.animScale = 1.7f; monopolyView.invalidate()
                    handler.postDelayed({
                        val old = p.currentPosition; p.currentPosition = (p.currentPosition + 1) % 40
                        if (p.currentPosition < old) { p.money += 200; Toast.makeText(this@MainActivity, "Maaş: +200₺", Toast.LENGTH_SHORT).show() }
                        monopolyView.animScale = 1.0f; updateUI(); handler.postDelayed(this, 150)
                    }, 100)
                } else { monopolyView.animPlayerIndex = -1; onEnd() }
            }
        }
        handler.post(animationRunnable!!)
    }

    private fun handleSquareClick(idx: Int) {
        val p = getCurrentPlayer(); val sq = board[idx]; val id = players.indexOf(p) + 1
        if (p.isBot || p.isBankrupt) return
        if (sq.ownerId == id) {
            if (canBuildHouse(p, sq)) {
                val t = if (sq.houseCount == 4) "OTEL" else "EV"
                AlertDialog.Builder(this).setTitle("İNŞAAT").setMessage("$t kur? (${sq.housePrice}₺)").setPositiveButton("EVET") { _, _ -> buildHouse(p, sq); updateUI() }.setNegativeButton("İPTAL", null).show()
            } else showMortgageActionDialog(p, sq, false)
        } else Toast.makeText(this, "${sq.name} (${sq.colorGroup})", Toast.LENGTH_SHORT).show()
    }

    private fun canBuildHouse(p: Player, sq: Square): Boolean {
        if (sq.isMortgaged || sq.housePrice == 0) return false
        val id = players.indexOf(p) + 1
        val grp = board.filter { it.colorGroup == sq.colorGroup }
        if (grp.any { it.isMortgaged } || !hasFullColorSet(id, sq.colorGroup)) return false
        if (p.money < sq.housePrice || sq.houseCount >= 5) return false
        return sq.houseCount == grp.minOf { it.houseCount }
    }

    private fun hasFullColorSet(id: Int, g: String): Boolean {
        if (g in listOf("Nötr", "İstasyon", "İskele", "Şirket")) return false
        return board.filter { it.colorGroup == g }.all { it.ownerId == id }
    }

    // --- MENÜ (YENİ OYUN EKLENDİ) ---
    private fun showPauseDialog() {
        val options = arrayOf("DEVAM ET ▶️", "YENİ OYUN 🔄", "KAYDET & ÇIK 💾")
        AlertDialog.Builder(this)
            .setTitle("OYUN MENÜSÜ ⏸️")
            .setItems(options) { d, w ->
                when(w) {
                    0 -> { hideSystemUI(); d.dismiss() }
                    1 -> { val intent = Intent(this, IntroActivity::class.java); startActivity(intent); finish() }
                    2 -> { saveGame(); finish() }
                }
            }
            .setCancelable(false).show()
    }

    // KAYIT SİSTEMİ
    private fun hasSavedGame(): Boolean = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).contains("PLAYERS_DATA")
    private fun saveGame() {
        val editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        editor.putString("PLAYERS_DATA", gson.toJson(players))
        editor.putString("BOARD_DATA", gson.toJson(board))
        editor.putInt("CURRENT_INDEX", currentPlayerIndex)
        editor.putInt("TURN_COUNT", turnCount)
        editor.putInt("BANK_POT", bankPot)
        editor.apply()
    }
    private fun loadGame() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        players = gson.fromJson(prefs.getString("PLAYERS_DATA", null), object : TypeToken<ArrayList<Player>>() {}.type)
        board = gson.fromJson(prefs.getString("BOARD_DATA", null), object : TypeToken<List<Square>>() {}.type)
        currentPlayerIndex = prefs.getInt("CURRENT_INDEX", 0)
        turnCount = prefs.getInt("TURN_COUNT", 0)
        bankPot = prefs.getInt("BANK_POT", 0)
        monopolyView.board = board; monopolyView.players = players
        updateUI(); startTurn(getCurrentPlayer())
    }

    private fun updateUI() {
        val c = getCurrentPlayer()
        txtPlayerMoney.text = "SIRA: ${c.name} (${c.money}₺)"
        txtBotMoney.text = "KASA: ${bankPot}₺"
        monopolyView.currentPot = bankPot
        monopolyView.invalidate()
    }

    // --- TİCARET & YÖNETİM ---
    private fun initiateTradeFlow() {
        val r = getCurrentPlayer(); if (r.isInJail || turnCount < 2) return
        val others = players.filter { it != r && !it.isBankrupt }
        if (others.isEmpty()) return
        val names = others.map { it.name }.toTypedArray()

        AlertDialog.Builder(this).setTitle("Kiminle?").setItems(names) { _, w ->
            val t = others[w]
            val tp = board.filter { it.ownerId == players.indexOf(t) + 1 && !it.isMortgaged && it.houseCount == 0 }
            if (tp.isEmpty()) { showMessage("Mülk yok"); return@setItems }

            val pn = tp.map { "${it.name}" }.toTypedArray()
            AlertDialog.Builder(this).setItems(pn) { _, wp ->
                val mp = board.filter { it.ownerId == players.indexOf(r) + 1 && !it.isMortgaged && it.houseCount == 0 }
                val mn = mp.map { "${it.name}" }.toMutableList()
                mn.add(0, "Para")
                AlertDialog.Builder(this).setItems(mn.toTypedArray()) { _, mpp ->
                    val off = if (mpp == 0) null else mp[mpp - 1]
                    val i = EditText(this); i.inputType = InputType.TYPE_CLASS_NUMBER
                    AlertDialog.Builder(this).setView(i).setPositiveButton("OK") { _, _ ->
                        val m = i.text.toString().toIntOrNull() ?: 0
                        if (m > r.money) Toast.makeText(this, "Para yok", Toast.LENGTH_SHORT).show()
                        else playTradeAnimation(r, t) { processBotTrade(r, t, tp[wp], off, m) }
                    }.show()
                }.show()
            }.show()
        }.show()
    }

    private fun processBotTrade(r: Player, b: Player, w: Square, o: Square?, m: Int) {
        if (!b.isBot) { askHuman(r, b, w, o, m); return }
        var vg = w.price.toDouble() * 1.3
        var vr = m.toDouble() + (o?.price ?: 0)
        if (willComplete(players.indexOf(b) + 1, o?.colorGroup ?: "")) vr += 300
        if (vr >= vg) { performTrade(r, b, w, o, m); Toast.makeText(this, "Kabul", Toast.LENGTH_SHORT).show() }
        else Toast.makeText(this, "Red", Toast.LENGTH_SHORT).show()
    }

    private fun willComplete(id: Int, g: String): Boolean {
        if (g.isEmpty()) return false
        val c = board.count { it.ownerId == id && it.colorGroup == g }
        val t = if (g in listOf("Kahverengi", "Lacivert")) 2 else 3
        return c + 1 == t
    }

    private fun askHuman(r: Player, t: Player, w: Square, o: Square?, m: Int) {
        val txt = if (o != null) "${o.name}+$m" else "$m"
        AlertDialog.Builder(this).setTitle("TEKLİF").setMessage("${r.name}, [${w.name}] istiyor.\nVeriyor: $txt").setPositiveButton("KABUL") { _, _ -> performTrade(r, t, w, o, m) }.setNegativeButton("RED", null).show()
    }
    private fun performTrade(r: Player, t: Player, w: Square, o: Square?, m: Int) {
        w.ownerId = players.indexOf(r) + 1
        if (m > 0) { r.money -= m; t.money += m }
        if (o != null) o.ownerId = players.indexOf(t) + 1
        showMessage("ANLAŞMA TAMAM")
        updateUI()
    }
    private fun playTradeAnimation(r: Player, t: Player, end: () -> Unit) {
        txtTradeAlert.text = "PAZARLIK..."
        tradeOverlay.visibility = View.VISIBLE
        tradeOverlay.alpha = 0f
        tradeOverlay.animate().alpha(1f).setDuration(500).start()
        handler.postDelayed({
            tradeOverlay.animate().alpha(0f).setDuration(300).withEndAction {
                tradeOverlay.visibility = View.GONE
                end()
            }.start()
        }, 1000)
    }
    private fun showPropertyManager(p: Player, forced: Boolean) {
        val props = board.filter { it.ownerId == players.indexOf(p) + 1 }
        if (props.isEmpty()) { Toast.makeText(this, "Tapu yok", Toast.LENGTH_SHORT).show(); if(forced) handleBankruptcy(p, pendingCreditor); return }
        val names = props.map { "${it.name} (${if (it.isMortgaged) "🛑" else "✅"})" }.toTypedArray()
        val b = AlertDialog.Builder(this).setTitle(if (forced) "BORÇ: $pendingDebtAmount" else "TAPULAR")
        b.setItems(names) { _, w -> showMortgageActionDialog(p, props[w], forced) }
        b.setPositiveButton("KAPAT") { _, _ -> if (forced) { if (p.money >= pendingDebtAmount) processPayment(p, pendingCreditor, pendingDebtAmount) else { Toast.makeText(this, "Para yetersiz", Toast.LENGTH_SHORT).show(); showPropertyManager(p, true) } } }
        b.setCancelable(!forced).show()
    }
    private fun showMortgageActionDialog(p: Player, sq: Square, forced: Boolean) {
        val mv = sq.price / 2; val c = (mv * 1.1).toInt(); val b = AlertDialog.Builder(this).setTitle(sq.name)
        if (sq.isMortgaged) { b.setMessage("Kaldır: $c₺").setPositiveButton("KALDIR") { _, _ -> if (p.money >= c) { p.money -= c; sq.isMortgaged = false; updateUI(); showPropertyManager(p, forced) } else Toast.makeText(this, "Para yok", Toast.LENGTH_SHORT).show() } }
        else { b.setMessage("İpotek: +$mv₺").setPositiveButton("İPOTEK") { _, _ -> if (sq.houseCount > 0) Toast.makeText(this, "Evleri sat", Toast.LENGTH_SHORT).show() else { p.money += mv; sq.isMortgaged = true; updateUI(); showPropertyManager(p, forced) } } }
        b.setNegativeButton("İPTAL") { _, _ -> showPropertyManager(p, forced) }.show()
    }
    private fun manageBotFinances(b: Player, req: Int) {
        val props = board.filter { it.ownerId == players.indexOf(b) + 1 }
        while (b.money < req) { val toM = props.filter { !it.isMortgaged && it.houseCount == 0 }.minByOrNull { it.price } ?: break; b.money += toM.price / 2; toM.isMortgaged = true; showMessage("${b.name} ipotek yaptı") }
        if (req == 0 && b.money > 500) { val toL = props.filter { it.isMortgaged }.maxByOrNull { it.price }; if (toL != null && b.money >= (toL.price / 2 * 1.1).toInt() + 200) { b.money -= (toL.price / 2 * 1.1).toInt(); toL.isMortgaged = false } }
        updateUI()
    }

    private fun initCards() { chanceDeck=listOf(GameCard("Başlangıç", { cardMoveTo(it, 0) }), GameCard("Ceza 150", { payToPot(it, 150) }), GameCard("Kodes", { sendToJail(it) }), GameCard("100 kazan", { it.money+=100; nextTurnOrDouble(it) }), GameCard("Taksim'e git", { cardMoveTo(it, 14) }), GameCard("Herkes 50 ver", { p -> players.filter{it!=p&&!it.isBankrupt}.forEach{if(p.money>=50){p.money-=50;it.money+=50}}; nextTurnOrDouble(p) })); communityDeck=chanceDeck }
    private fun initBoard() { val e=listOf(0,0,0,0,0,0); board=listOf(Square("BAŞLANGIÇ",0,e,"KOSE"),Square("Kasımpaşa",60,listOf(2,10,30,90,160,250),"Kahverengi",50),Square("K.Fonu",0,e,"Nötr"),Square("Dolapdere",60,listOf(4,20,60,180,320,450),"Kahverengi",50),Square("Gelir V.",0,e,"Nötr"),Square("H.Paşa Gar",200,e,"İstasyon"),Square("Sultanahmet",100,listOf(6,30,90,270,400,550),"Açık Mavi",50),Square("ŞANS",0,e,"Nötr"),Square("Karaköy",100,listOf(6,30,90,270,400,550),"Açık Mavi",50),Square("Sirkeci",120,listOf(8,40,100,300,450,600),"Açık Mavi",50),Square("KODES (Ziyaret)",0,e,"KOSE"),Square("Beyoğlu",140,listOf(10,50,150,450,625,750),"Pembe",100),Square("Elek. İd",150,e,"Şirket"),Square("Beşiktaş",140,listOf(10,50,150,450,625,750),"Pembe",100),Square("Taksim",160,listOf(12,60,180,500,700,900),"Pembe",100),Square("Kadıköy Vap",200,e,"İskele"),Square("Harbiye",180,listOf(14,70,200,550,750,950),"Turuncu",100),Square("K.Fonu",0,e,"Nötr"),Square("Şişli",180,listOf(14,70,200,550,750,950),"Turuncu",100),Square("Mecidiyeköy",200,listOf(16,80,220,600,800,1000),"Turuncu",100),Square("BANKA",0,e,"KOSE"),Square("Bostancı",220,listOf(18,90,250,700,875,1050),"Kırmızı",150),Square("ŞANS",0,e,"Nötr"),Square("Erenköy",220,listOf(18,90,250,700,875,1050),"Kırmızı",150),Square("Caddebostan",240,listOf(20,100,300,750,925,1110),"Kırmızı",150),Square("Kabataş Vap",200,e,"İskele"),Square("Nişantaşı",260,listOf(22,110,330,800,975,1150),"Sarı",150),Square("Teşvikiye",260,listOf(22,110,330,800,975,1150),"Sarı",150),Square("Sular İd.",150,e,"Şirket"),Square("Maçka",280,listOf(24,120,360,850,1025,1200),"Sarı",150),Square("HAPSE GİR",0,e,"KOSE"),Square("Levent",300,listOf(26,130,390,900,1100,1275),"Yeşil",200),Square("Etiler",300,listOf(26,130,390,900,1100,1275),"Yeşil",200),Square("K.Fonu",0,e,"Nötr"),Square("Bebek",320,listOf(28,150,450,1000,1200,1400),"Yeşil",200),Square("Sir. Tren",200,e,"İstasyon"),Square("ŞANS",0,e,"Nötr"),Square("Tarabya",350,listOf(35,175,500,1100,1300,1500),"Lacivert",200),Square("Lüks V.",0,e,"Nötr"),Square("Yeniköy",400,listOf(50,200,600,1400,1700,2000),"Lacivert",200)) }
}