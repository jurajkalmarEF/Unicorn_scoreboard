package com.example.scorebuddystats

/** A lightweight, framework-independent snapshot of one on-screen text node. */
data class NodeText(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerY: Int get() = (top + bottom) / 2
}
