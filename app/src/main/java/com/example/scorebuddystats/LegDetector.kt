package com.example.scorebuddystats

import kotlin.math.abs

/**
 * Detects Scorebuddy's "GAME SUMMARY" screen, which appears after each leg
 * and shows a table already sorted by placement:
 *
 *   Rank   Name       Score   Darts   3d Av.  9d Av.  180  140+  100+
 *   1      ROBOT 3    501     44      34,2    74,0    0    0     0
 *   2      ROBOT 2    387     42      27,6    34,0    0    0     0
 *   3      AI         137     42      9,8     0,0     0    0     0
 *
 * We don't need to compute placement ourselves here - Scorebuddy already
 * ranks the players. We just read off (rank, name) pairs from the table.
 */
object LegDetector {

    data class LegEndResult(val legKey: String, val orderedPlayers: List<String>)

    private val rankRegex = Regex("^\\d{1,2}$")
    private val excludedLabels = setOf(
        "EXIT", "NEXT LEG", "GAME SUMMARY", "SCROLL", "RANK", "NAME",
        "SCORE", "DARTS", "3D AV.", "9D AV.", "180", "140+", "100+"
    )

    fun detect(nodes: List<NodeText>): LegEndResult? {
        val titleNode = nodes.firstOrNull { it.text.equals("GAME SUMMARY", ignoreCase = true) }
            ?: return null
        val rankHeader = nodes.firstOrNull { it.text.equals("Rank", ignoreCase = true) }
            ?: return null
        val scoreHeader = nodes.firstOrNull { it.text.equals("Score", ignoreCase = true) }
            ?: return null

        // Only look at nodes below the header row, and left of the Score column
        // (that region can only contain Rank numbers and player Names).
        val bodyNodes = nodes.filter {
            it.top > rankHeader.bottom &&
                it !== titleNode &&
                it.text.uppercase() !in excludedLabels
        }
        if (bodyNodes.isEmpty()) return null

        // Row height reference, used to cluster nodes into rows.
        val avgHeight = bodyNodes.map { it.bottom - it.top }.average().takeIf { it > 0 } ?: 30.0
        val rowTolerance = (avgHeight * 0.7).toInt().coerceAtLeast(10)

        // Cluster into rows by vertical position.
        val sorted = bodyNodes.sortedBy { it.top }
        val rows = mutableListOf<MutableList<NodeText>>()
        for (node in sorted) {
            val row = rows.lastOrNull { row -> abs(row.first().centerY - node.centerY) < rowTolerance }
            if (row != null) row.add(node) else rows.add(mutableListOf(node))
        }

        data class RankedRow(val rank: Int, val name: String, val rowSignature: String)

        val rankedRows = rows.mapNotNull { row ->
            val sortedRow = row.sortedBy { it.left }
            val rankNode = sortedRow.getOrNull(0) ?: return@mapNotNull null
            val nameNode = sortedRow.getOrNull(1) ?: return@mapNotNull null
            if (!rankRegex.matches(rankNode.text)) return@mapNotNull null
            if (rankRegex.matches(nameNode.text)) return@mapNotNull null // name must not be numeric

            val rowSignature = sortedRow.joinToString("|") { it.text }
            RankedRow(rank = rankNode.text.toInt(), name = nameNode.text, rowSignature = rowSignature)
        }

        if (rankedRows.isEmpty()) return null

        val orderedPlayers = rankedRows.sortedBy { it.rank }.map { it.name }
        val legKey = rankedRows.sortedBy { it.rank }.joinToString(";") { it.rowSignature }

        return LegEndResult(legKey = legKey, orderedPlayers = orderedPlayers)
    }
}
