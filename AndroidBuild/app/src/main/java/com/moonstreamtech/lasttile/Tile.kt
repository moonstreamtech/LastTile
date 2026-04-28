package com.moonstreamtech.lasttile

sealed class Tile {
    data object Empty : Tile()
    data class Normal(val value: Int) : Tile()
    data class Fire(val value: Int, val age: Int = 0) : Tile()
    data class Ice(val value: Int, val age: Int = 0) : Tile()
    data class Poison(val value: Int, val age: Int = 0) : Tile()
}
