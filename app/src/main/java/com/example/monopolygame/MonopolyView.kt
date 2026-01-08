package com.example.monopolygame

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.min

class MonopolyView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 1. VERİLER
    var board: List<Square> = emptyList()
        set(value) { field = value; invalidate() }
    var players: List<Player> = emptyList()
        set(value) { field = value; invalidate() }

    var animPlayerIndex: Int = -1
    var animScale: Float = 1.0f
    var dice1: Int = 1
    var dice2: Int = 1
    var currentPot: Int = 0
    var infoText: String = "Oyna!"
    var isRollButtonEnabled = true

    var onRollClick: (() -> Unit)? = null
    var onSquareClick: ((Int) -> Unit)? = null

    private var mScaleFactor = 1.0f
    private var mPosX = 0f
    private var mPosY = 0f
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f
    private val mScaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val mGestureDetector = GestureDetector(context, GestureListener())

    // 2. BOYA FIRÇALARI
    private val tablePaint = Paint().apply { color = Color.parseColor("#CDEAC0"); style = Paint.Style.FILL }
    private val boardBgPaint = Paint().apply { color = Color.parseColor("#FDFEFE") }
    private val strokePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }

    private val textPaint = Paint().apply { color = Color.BLACK; textSize = 20f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val pricePaint = Paint().apply { color = Color.DKGRAY; textSize = 18f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val potPaint = Paint().apply { color = Color.parseColor("#2E7D32"); textSize = 24f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val buildingPaint = Paint().apply { textSize = 20f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val iconPaint = Paint().apply { textSize = 35f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val taxPaint = Paint().apply { color = Color.RED; textSize = 18f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

    // İstatistikler (MERKEZLİ)
    private val statsTitlePaint = Paint().apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val statsMoneyPaint = Paint().apply { color = Color.DKGRAY; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val statsPropPaint = Paint().apply { color = Color.BLACK; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val underlinePaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

    // UI
    private val buttonBgPaint = Paint().apply { color = Color.parseColor("#FF9800"); style = Paint.Style.FILL; isAntiAlias = true }
    private val buttonTextPaint = Paint().apply { color = Color.WHITE; textSize = 35f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val ownerTextPaint = Paint().apply { color = Color.WHITE; textSize = 20f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

    private val colorStripPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val ownerMarkerPaint = Paint().apply { isAntiAlias = true }
    private val pionPaint = Paint().apply { isAntiAlias = true }
    private val diceEmptyPaint = Paint().apply { color = Color.WHITE }

    private val diceBitmaps = mutableMapOf<Int, Bitmap>()
    private var buttonRect = RectF()

    init {
        for (i in 1..6) {
            val resId = context.resources.getIdentifier("dice_$i", "drawable", context.packageName)
            if (resId != 0) diceBitmaps[i] = BitmapFactory.decodeResource(context.resources, resId)
        }
    }

    // 3. EKRAN ÇİZİMİ
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tablePaint)
        if (board.isEmpty()) return

        canvas.save()
        if (mScaleFactor <= 1.0f) { mPosX = 0f; mPosY = 0f }
        else {
            val maxDx = (width * mScaleFactor) - width
            val maxDy = (height * mScaleFactor) - height
            mPosX = mPosX.coerceIn(-maxDx / 2, maxDx / 2)
            mPosY = mPosY.coerceIn(-maxDy / 2, maxDy / 2)
        }
        canvas.translate(width / 2f + mPosX, height / 2f + mPosY)
        canvas.scale(mScaleFactor, mScaleFactor)
        canvas.translate(-width / 2f, -height / 2f)

        val sideLength = min(width, height).toFloat()
        val offsetX = (width - sideLength) / 2f
        val offsetY = (height - sideLength) / 2f
        val squareSize = sideLength / 11f

        canvas.drawRect(offsetX, offsetY, offsetX + sideLength, offsetY + sideLength, boardBgPaint)

        // Kareleri Çiz
        for (i in board.indices) {
            val rect = calculateSquareRect(i, squareSize, offsetX, offsetY, sideLength)
            canvas.drawRect(rect, strokePaint)
            drawColorStrip(canvas, rect, board[i], squareSize, i)
            drawBuildings(canvas, rect, board[i], squareSize, i)

            val name = if (board[i].name.length > 9) board[i].name.substring(0, 8) + "." else board[i].name
            val nameY = if (hasSpecialIcon(board[i])) rect.centerY() - 15f else rect.centerY() - 5f
            canvas.drawText(name, rect.centerX(), nameY, textPaint)

            if (board[i].name == "BAŞLANGIÇ") {
                canvas.drawText("MAAŞ", rect.centerX(), rect.centerY() + 20f, pricePaint)
                canvas.drawText("+200₺", rect.centerX(), rect.centerY() + 40f, potPaint)
            } else if (board[i].name == "BANKA") {
                canvas.drawText("KASA:", rect.centerX(), rect.centerY() + 20f, pricePaint)
                canvas.drawText("$currentPot₺", rect.centerX(), rect.centerY() + 40f, potPaint)
            } else if (board[i].name == "Gelir V.") {
                canvas.drawText("ÖDE", rect.centerX(), rect.centerY() + 20f, pricePaint)
                canvas.drawText("200₺", rect.centerX(), rect.centerY() + 40f, taxPaint)
            } else if (board[i].name == "Lüks V.") {
                canvas.drawText("ÖDE", rect.centerX(), rect.centerY() + 20f, pricePaint)
                canvas.drawText("100₺", rect.centerX(), rect.centerY() + 40f, taxPaint)
            } else {
                if (hasSpecialIcon(board[i])) {
                    drawSpecialIcon(canvas, rect, board[i])
                    // Fiyatı sadece satın alınabilirse yaz
                    if (board[i].ownerId == -1 && board[i].price > 0 && !board[i].name.contains("KODES")) {
                        canvas.drawText("${board[i].price}₺", rect.centerX(), rect.bottom - 5f, pricePaint)
                    }
                } else {
                    if (board[i].ownerId == -1 && board[i].price > 0) {
                        canvas.drawText("${board[i].price}₺", rect.centerX(), rect.centerY() + 25f, pricePaint)
                    }
                }
            }
            if (board[i].ownerId != -1) drawOwnerMarker(canvas, rect, board[i].ownerId)
        }

        drawEnhancedCenterStats(canvas, offsetX, offsetY, sideLength, squareSize)

        // Zar ve Buton
        val centerX = offsetX + sideLength / 2f
        val centerY = offsetY + sideLength / 2f
        val diceSize = squareSize * 1.5f
        val diceY = centerY - diceSize + 20f

        drawDiceBitmap(canvas, dice1, centerX - diceSize - 10f, diceY, diceSize)
        drawDiceBitmap(canvas, dice2, centerX + 10f, diceY, diceSize)

        val btnW = diceSize * 3f
        val btnH = diceSize * 0.8f
        val btnTop = centerY + 40f
        buttonRect.set(centerX - btnW / 2, btnTop, centerX + btnW / 2, btnTop + btnH)

        buttonBgPaint.color = if (isRollButtonEnabled) Color.parseColor("#FF9800") else Color.LTGRAY
        canvas.drawRoundRect(buttonRect, 20f, 20f, buttonBgPaint)
        canvas.drawText(if (isRollButtonEnabled) "ZAR AT" else "BEKLE...", centerX, btnTop + btnH / 1.6f, buttonTextPaint)
        canvas.drawText(infoText, centerX, btnTop + btnH + 40f, textPaint)

        // Piyonlar
        for ((index, player) in players.withIndex()) {
            val scale = if (index == animPlayerIndex) animScale else 1.0f
            val shift = (index * 12f) - 25f
            val initial = player.name.take(1).uppercase()
            pionPaint.color = player.color
            drawPion(canvas, player.currentPosition, pionPaint, initial, squareSize, offsetX, offsetY, sideLength, shift, scale)
        }
        canvas.restore()
    }

    // 4. ORTA EKRAN (DÜZELTİLDİ: YAZILAR ÜSTTE)
    private fun drawEnhancedCenterStats(canvas: Canvas, boardX: Float, boardY: Float, boardSize: Float, sqSize: Float) {

        val safePadding = sqSize * 1.3f
        val innerLeft = boardX + safePadding
        val innerRight = boardX + boardSize - safePadding
        val innerTop = boardY + safePadding // Üstten başla

        val totalWidth = innerRight - innerLeft
        // Yükseklik hesaplamasına gerek yok, üstten başlayıp aşağı akacağız.

        val numCols = 2
        val numRows = if (players.size <= 2) 1 else if (players.size <= 4) 2 else 3
        val cellWidth = totalWidth / numCols
        val cellHeight = (boardSize - (safePadding * 2)) / numRows

        for ((index, player) in players.withIndex()) {
            val col = index % numCols
            val row = index / numCols

            // X KONUMU (Hala ortalıyoruz ki sağa sola taşmasın)
            val cellX = innerLeft + (col * cellWidth)
            val cellY = innerTop + (row * cellHeight)
            val centerX = cellX + (cellWidth / 2)

            // Y KONUMU (DÜZELTME: ARTIK ORTADA DEĞİL, ÜSTTE)
            // Kutu sınırının tepesinden 20 birim aşağıdan başla
            var cursorY = cellY + 30f

            // Yazı Boyutları
            val nameSize = if (players.size > 4) 20f else 26f
            val moneySize = if (players.size > 4) 18f else 22f
            val propSize = if (players.size > 4) 16f else 20f
            val lineGap = if (players.size > 4) 22f else 30f

            // İsim
            statsTitlePaint.color = player.color
            statsTitlePaint.textSize = nameSize
            canvas.drawText(player.name, centerX, cursorY, statsTitlePaint)
            cursorY += lineGap

            // Para
            statsMoneyPaint.textSize = moneySize
            canvas.drawText("${player.money}₺", centerX, cursorY, statsMoneyPaint)
            cursorY += lineGap

            // Mülkler
            statsPropPaint.textSize = propSize

            val props = board.filter { it.ownerId == index + 1 }
                .sortedBy { square ->
                    when (square.colorGroup) {
                        "Kahverengi" -> 1; "Açık Mavi" -> 2; "Pembe" -> 3
                        "Turuncu" -> 4; "Kırmızı" -> 5; "Sarı" -> 6
                        "Yeşil" -> 7; "Lacivert" -> 8; "İstasyon" -> 99; "Şirket" -> 100
                        else -> 50
                    }
                }

            if (props.isEmpty()) {
                statsPropPaint.color = Color.LTGRAY
                canvas.drawText("-", centerX, cursorY, statsPropPaint)
            } else {
                val maxLines = if (players.size <= 2) 7 else if (players.size <= 4) 3 else 2
                val displayProps = props.take(maxLines)

                for (prop in displayProps) {
                    val propColor = getColorFromGroup(prop.colorGroup)
                    val limit = if (players.size > 4) 8 else 12
                    val shortName = if (prop.name.length > limit) prop.name.substring(0, limit) + "." else prop.name

                    if (prop.isMortgaged) statsPropPaint.color = Color.RED else statsPropPaint.color = Color.BLACK
                    canvas.drawText(shortName, centerX, cursorY, statsPropPaint)

                    if (prop.colorGroup != "İstasyon" && prop.colorGroup != "Şirket" && prop.colorGroup != "Nötr" && !prop.isMortgaged) {
                        val textWidth = statsPropPaint.measureText(shortName)
                        underlinePaint.color = propColor
                        canvas.drawRect(centerX - textWidth/2, cursorY + 5f, centerX + textWidth/2, cursorY + 10f, underlinePaint)
                    }
                    cursorY += lineGap
                }

                if (props.size > maxLines) {
                    statsPropPaint.color = Color.DKGRAY
                    canvas.drawText("+${props.size - maxLines}...", centerX, cursorY, statsPropPaint)
                }
            }
        }
    }

    // 5. YARDIMCI FONKSİYONLAR
    private fun getColorFromGroup(group: String): Int {
        return when (group) {
            "Kahverengi" -> Color.parseColor("#795548"); "Açık Mavi" -> Color.parseColor("#29B6F6")
            "Pembe" -> Color.parseColor("#EC407A"); "Turuncu" -> Color.parseColor("#FFA726")
            "Kırmızı" -> Color.parseColor("#EF5350"); "Sarı" -> Color.parseColor("#FDD835")
            "Yeşil" -> Color.parseColor("#27AE60"); "Lacivert" -> Color.parseColor("#1A237E")
            "İstasyon", "İskele" -> Color.BLACK; "Şirket" -> Color.DKGRAY; else -> Color.GRAY
        }
    }

    private fun drawBuildings(canvas: Canvas, rect: RectF, sq: Square, sz: Float, index: Int) {
        if (sq.houseCount == 0) return
        if (sq.houseCount == 5) {
            buildingPaint.textSize = 30f; val icon = "🏨"; val x: Float; val y: Float
            when (index) {
                in 0..10 -> { x = rect.centerX(); y = rect.top + sz / 5 }
                in 11..20 -> { x = rect.right - sz / 5; y = rect.centerY() + 10f }
                in 21..30 -> { x = rect.centerX(); y = rect.bottom - 5f }
                else -> { x = rect.left + sz / 5; y = rect.centerY() + 10f }
            }
            canvas.drawText(icon, x, y, buildingPaint)
        } else {
            buildingPaint.textSize = 20f; val icon = "🏠"
            for (i in 0 until sq.houseCount) {
                var x = 0f; var y = 0f; val offset = (i - (sq.houseCount - 1) / 2f) * 22f
                when (index) {
                    in 0..10 -> { x = rect.centerX() + offset; y = rect.top + sz / 5 }
                    in 11..20 -> { x = rect.right - sz / 5; y = rect.centerY() + offset + 10f }
                    in 21..30 -> { x = rect.centerX() + offset; y = rect.bottom - 5f }
                    else -> { x = rect.left + sz / 5; y = rect.centerY() + offset + 10f }
                }
                canvas.drawText(icon, x, y, buildingPaint)
            }
        }
    }

    private fun drawDiceBitmap(canvas: Canvas, value: Int, x: Float, y: Float, size: Float) {
        val bitmap = diceBitmaps[value]
        if (bitmap != null) { canvas.drawBitmap(bitmap, null, RectF(x, y, x + size, y + size), null) }
        else { canvas.drawRect(x, y, x + size, y + size, diceEmptyPaint); canvas.drawText("$value", x + size / 2, y + size / 2, textPaint) }
    }

    private fun hasSpecialIcon(sq: Square) = sq.colorGroup in listOf("İstasyon", "İskele", "Şirket") || "ŞANS" in sq.name || "K.Fonu" in sq.name

    private fun drawSpecialIcon(canvas: Canvas, rect: RectF, sq: Square) {
        val icon = when {
            sq.colorGroup == "İstasyon" -> "🚂"; sq.colorGroup == "İskele" -> "🚢"
            "Elek" in sq.name -> "💡"; "Su" in sq.name -> "🚰"; "ŞANS" in sq.name -> "❓"; "K.Fonu" in sq.name -> "📦"; else -> ""
        }
        canvas.drawText(icon, rect.centerX(), rect.centerY() + 10f, iconPaint)
    }

    private fun drawOwnerMarker(canvas: Canvas, rect: RectF, ownerId: Int) {
        val playerIndex = ownerId - 1
        if (playerIndex in players.indices) {
            val owner = players[playerIndex]
            ownerMarkerPaint.color = owner.color
            canvas.drawCircle(rect.right - 15f, rect.top + 15f, 10f, ownerMarkerPaint)
        }
    }

    private fun calculateSquareRect(index: Int, sz: Float, offX: Float, offY: Float, totalLen: Float): RectF {
        return when (index) {
            in 0..10 -> RectF(offX + (totalLen - (index + 1) * sz), offY + totalLen - sz, offX + (totalLen - index * sz), offY + totalLen)
            in 11..20 -> RectF(offX, offY + (totalLen - (index - 9) * sz), offX + sz, offY + (totalLen - (index - 10) * sz))
            in 21..30 -> RectF(offX + (index - 20) * sz, offY, offX + (index - 19) * sz, offY + sz)
            else -> RectF(offX + totalLen - sz, offY + (index - 30) * sz, offX + totalLen, offY + (index - 29) * sz)
        }
    }

    private fun drawColorStrip(canvas: Canvas, rect: RectF, square: Square, sz: Float, index: Int) {
        val colorHex = when (square.colorGroup) {
            "Kahverengi" -> "#795548"; "Açık Mavi" -> "#29B6F6"; "Pembe" -> "#EC407A"
            "Turuncu" -> "#FFA726"; "Kırmızı" -> "#EF5350"; "Sarı" -> "#FDD835"
            "Yeşil" -> "#27AE60"; "Lacivert" -> "#1A237E"; else -> return
        }
        colorStripPaint.color = Color.parseColor(colorHex)
        when (index) {
            in 0..10 -> canvas.drawRect(rect.left, rect.top, rect.right, rect.top + sz / 4, colorStripPaint)
            in 11..20 -> canvas.drawRect(rect.right - sz / 4, rect.top, rect.right, rect.bottom, colorStripPaint)
            in 21..30 -> canvas.drawRect(rect.left, rect.bottom - sz / 4, rect.right, rect.bottom, colorStripPaint)
            else -> canvas.drawRect(rect.left, rect.top, rect.left + sz / 4, rect.bottom, colorStripPaint)
        }
    }

    private fun drawPion(canvas: Canvas, pos: Int, paint: Paint, text: String, sz: Float, offX: Float, offY: Float, totalLen: Float, shift: Float, scale: Float) {
        val rect = calculateSquareRect(pos, sz, offX, offY, totalLen)
        val radius = 18f * scale
        canvas.drawCircle(rect.centerX() + shift, rect.centerY() + shift, radius, paint)
        ownerTextPaint.textSize = 20f * scale
        canvas.drawText(text, rect.centerX() + shift, rect.centerY() + shift + (6f * scale), ownerTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(event)
        mGestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            val touchX = (event.x - mPosX) / mScaleFactor + width / 2 - width / 2 / mScaleFactor
            val translatedX = event.x - (width / 2f + mPosX)
            val translatedY = event.y - (height / 2f + mPosY)
            val finalX = translatedX / mScaleFactor + width / 2f
            val finalY = translatedY / mScaleFactor + height / 2f
            if (isRollButtonEnabled && buttonRect.contains(finalX, finalY)) { performClick(); onRollClick?.invoke(); return true }
            if (board.isNotEmpty()) {
                val sideLength = min(width, height).toFloat()
                val offsetX = (width - sideLength) / 2f
                val offsetY = (height - sideLength) / 2f
                val squareSize = sideLength / 11f
                for (i in board.indices) {
                    val rect = calculateSquareRect(i, squareSize, offsetX, offsetY, sideLength)
                    if (rect.contains(finalX, finalY)) { performClick(); onSquareClick?.invoke(i); return true }
                }
            }
        }
        if (!mScaleDetector.isInProgress && event.action == MotionEvent.ACTION_MOVE && event.pointerCount == 1) { mPosX += event.x - mLastTouchX; mPosY += event.y - mLastTouchY; invalidate() }
        mLastTouchX = event.x; mLastTouchY = event.y; return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() { override fun onScale(d: ScaleGestureDetector): Boolean { mScaleFactor = (mScaleFactor * d.scaleFactor).coerceIn(1.0f, 5.0f); invalidate(); return true } }
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() { override fun onDoubleTap(e: MotionEvent): Boolean { mScaleFactor = 1.0f; mPosX = 0f; mPosY = 0f; invalidate(); return true } }
}