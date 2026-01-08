package com.example.monopolygame
import java.io.Serializable

data class Square(
    val name: String,
    val price: Int,
    val rents: List<Int>, // [Arsa, 1Ev, 2Ev, 3Ev, 4Ev, Otel]
    val colorGroup: String,
    val housePrice: Int = 0,
    var ownerId: Int = -1,
    var houseCount: Int = 0,
    var isMortgaged: Boolean = false
) : Serializable