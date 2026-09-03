package com.example.scorebuddystats

import kotlin.math.abs

/**
 * Detects the moment a leg ends on the live Scorebuddy game screen.
 *
 * Layout (per user's screenshot):
 *  - Left panel: current thrower's name (big, above) and remaining score
 *    (big yellow number, below) — this is whoever's turn it currently is.
 *  - Right panel: one row per other player, each row = name + remaining score.
 *  - A leg ends the instant the LEFT score reaches exactly 0. That player is
 *    the winner (placement 1). The remaining players are ranked by their
 *    remaining score ascending (lower remaining score = closer to finishing
 *    = better placement).
 *
 * This works purely on relative text positions (leftmost score = current
 * thrower, row-grouping by vertical position) so it doesn't depend on fixed
 * pixel coordinates or on Scorebuddy exposing view IDs.
 */
object LegDetector {

    data class LegEndResult(val legKey: String, val orderedPlayers: List<String>)

    private val legHeaderRegex = Regex("(?i)LEG\\s*\\d+.*SET\\s*\\d+")
    private val pureNumberRegex = Regex("^\\d{1,3}$")

    fun detect(nodes: List<NodeText>): LegEndResult? {
        val legHeaderNode = nodes.firstOrNull { legHeaderRegex.containsMatchIn(it.text) } ?: return null

        val scoreCandidates = nodes.filter { pureNumberRegex.matches(it.text) }
        if (scoreCandidates.isEmpty()) return null

        // Current thrower = leftmost score on screen.
        val leftScore = scoreCandidates.minByOrNull { it.left } ?: return null
        if (leftScore.text != "0") return null // leg not finished yet

        val leftName = findNameAbove(nodes, leftScore) ?: return null

        val otherScores = scoreCandidates.filter { it !== leftScore }
        val rowHeight = (leftScore.bottom - leftScore.top).coerceAtLeast(20)

        val otherPlayers = otherScores.mapNotNull { scoreNode ->
            val name = findNameLeftOf(nodes, scoreNode, rowHeight) ?: return@mapNotNull null
            name to scoreNode.text.toInt()
        }

        // Rank the rest: lowest remaining score first (closest to finishing).
        val rankedOthers = otherPlayers.sortedBy { it.second }.map { it.first }

        val orderedPlayers = listOf(leftName) + rankedOthers
        return LegEndResult(legKey = legHeaderNode.text, orderedPlayers = orderedPlayers)
    }

    private fun findNameAbove(nodes: List<NodeText>, scoreNode: NodeText): String? {
        return nodes
            .filter {
                it !== scoreNode &&
                    it.top < scoreNode.top &&
                    it.left < scoreNode.right && it.right > scoreNode.left && // horizontal overlap
                    it.text.isNotBlank() &&
                    !legHeaderRegex.containsMatchIn(it.text) &&
                    !pureNumberRegex.matches(it.text)
            }
            .maxByOrNull { it.top } // closest one above the score
            ?.text
    }

    private fun findNameLeftOf(nodes: List<NodeText>, scoreNode: NodeText, rowHeight: Int): String? {
        return nodes
            .filter {
                it !== scoreNode &&
                    abs(it.centerY - scoreNode.centerY) < rowHeight &&
                    it.left < scoreNode.left &&
                    it.text.isNotBlank() &&
                    !pureNumberRegex.matches(it.text)
            }
            .maxByOrNull { it.left } // the name label immediately to the left of the number
            ?.text
    }
}
