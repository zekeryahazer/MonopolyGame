package com.example.monopolygame
import java.io.Serializable

data class Player(
    val name: String,
    val color: Int,
    var money: Int = 1500,
    var currentPosition: Int = 0,
    var isInJail: Boolean = false,
    var jailTurnCount: Int = 0,
    var doubleDiceCount: Int = 0,
    val isBot: Boolean = false,
    var isBankrupt: Boolean = false
) : Serializable