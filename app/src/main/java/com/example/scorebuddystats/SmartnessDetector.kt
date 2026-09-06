package com.example.scorebuddystats

import kotlin.math.abs

object SmartnessDetector {

  data class LegEndResult(val legKey: String, val orderedPlayers: List<String>)

  private val scoreRegex = Regex("^\\d{1,3}$")

  private val excludedTexts = setOf(
    "MISS !", "MISS!", "BOUNCE OUT !", "BOUNCE OUT!", "NEXT PLAYER",
    "GAME RULES", "STATISTICS", "THROWS", "DART BOARD IS CONNECTED",
    "DOUBLE 20", "DOUBLE 25", "SINGLE 16", "YOU ARE PLAYING :"
    )

  fun detect(nodes: List<NodeText>): LegEndResult? {
    val filtered = nodes.filter { it.text.trim().uppercase() !in excludedTexts }
    if (filtered.isEmpty()) return null

    val roughHeights = filtered.map { it.bottom - it.top }.sorted()
    val roughMedianHeight = if (roughHeights.isEmpty()) 30 else roughHeights[roughHeights.size / 2]
    val candidates = filtered.filter { (it.bottom - it.top) <= roughMedianHeight * 3 }
    if (candidates.isEmpty()) return null

    val heights = candidates.map { it.bottom - it.top }.sorted()
    val medianHeight = if (heights.isEmpty()) 30 else heights[heights.size / 2]
    val rowTolerance = (medianHeight * 0.6).toInt().coerceAtLeast(10)

    val sorted = candidates.sortedBy { it.top }
    val rows = mutableListOf<MutableList<NodeText>>()
    for (node in sorted) {
      val row = rows.lastOrNull { r -> abs(r.first().centerY - node.centerY) < rowTolerance }
      if (row != null) row.add(node) else rows.add(mutableListOf(node))
    }

    data class PlayerRow(val name: String, val score: Int)

    val playerRows = rows.mapNotNull { row ->
      val scoreNode = row.singleOrNull { scoreRegex.matches(it.text.trim()) } ?: return@mapNotNull null
      val nameNode = row.filter { it !== scoreNode && !scoreRegex.matches(it.text.trim()) }
      .minByOrNull { abs(it.centerY - scoreNode.centerY) } ?: return@mapNotNull null
      val score = scoreNode.text.trim().toIntOrNull() ?: return@mapNotNull null
      PlayerRow(name = nameNode.text.trim(), score = score)
    }

    if (playerRows.isEmpty()) return null
    val winner = playerRows.firstOrNull { it.score == 0 } ?: return null

    val orderedPlayers = listOf(winner.name) +
    playerRows.filter { it !== winner }.sortedBy { it.score }.map { it.name }

    val legKey = playerRows.sortedBy { it.name }.joinToString(";") { "${it.name}=${it.score}" }

    return LegEndResult(legKey = legKey, orderedPlayers = orderedPlayers)
  }
}
