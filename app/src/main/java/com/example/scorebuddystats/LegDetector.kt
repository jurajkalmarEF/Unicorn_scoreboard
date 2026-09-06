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
           *
            * Column-alignment matching: a rank digit and a player name are only ever
             * accepted if their horizontal bounds line up with the "Rank" and "Score"
              * header cells (name = column between Rank's right edge and Score's left
               * edge). This is stricter than clustering purely by row height, and avoids
                * stray off-screen text (e.g. a "D20" quick-score button from the live game
                 * screen underneath) getting picked up as a player name once there are
                  * enough rows for a coincidental vertical overlap.
                   */
object LegDetector {

        data class LegEndResult(val legKey: String, val orderedPlayers: List<String>)

            private val rankRegex = Regex("^\\d{1,2}$")
                private const val COL_TOLERANCE = 25 // px slack for column-alignment matching

        fun detect(nodes: List<NodeText>): LegEndResult? {
                    val titleNode = nodes.firstOrNull { it.text.trim().equals("GAME SUMMARY", ignoreCase = true) }
                                ?: return null
                    val rankHeader = nodes.firstOrNull { it.text.trim().equals("Rank", ignoreCase = true) }
                                ?: return null
                    val scoreHeader = nodes.firstOrNull { it.text.trim().equals("Score", ignoreCase = true) }
                                ?: return null


            // Rank digits must align (left+right) with the "Rank" header cell itself.
            val rankCandidates = nodes.filter {
                it !== titleNode &&
                it.top > rankHeader.bottom &&
                abs(it.left - rankHeader.left) <= COL_TOLERANCE &&
                abs(it.right - rankHeader.right) <= COL_TOLERANCE &&
                rankRegex.matches(it.text.trim())
            }
            if (rankCandidates.isEmpty()) return null


            data class RankedRow(val rank: Int, val name: String, val rowSignature: String)

            val rankedRows = rankCandidates.mapNotNull { rankNode ->
                val rowHeight = (rankNode.bottom - rankNode.top).coerceAtLeast(20)

                val nameNode = nodes.filter { candidate ->
                    candidate !== rankNode &&
                    abs(candidate.centerY - rankNode.centerY) < rowHeight * 0.6 &&
                    abs(candidate.left - rankHeader.right) <= COL_TOLERANCE &&
                    abs(candidate.right - scoreHeader.left) <= COL_TOLERANCE &&
                    !rankRegex.matches(candidate.text.trim())
                }.minByOrNull { abs(it.centerY - rankNode.centerY) } ?: return@mapNotNull null

                val scoreNode = nodes.firstOrNull { candidate ->
                    abs(candidate.centerY - rankNode.centerY) < rowHeight * 0.6 &&
                    abs(candidate.left - scoreHeader.left) <= COL_TOLERANCE &&
                    abs(candidate.right - scoreHeader.right) <= COL_TOLERANCE
                }

                val rank = rankNode.text.trim().toIntOrNull() ?: return@mapNotNull null
                val name = nameNode.text.trim()
                val score = scoreNode?.text?.trim().orEmpty()
                RankedRow(rank = rank, name = name, rowSignature = "$rank|$name|$score")
            }


            if (rankedRows.isEmpty()) return null

            val orderedPlayers = rankedRows.sortedBy { it.rank }.map { it.name }
            val legKey = rankedRows.sortedBy { it.rank }.joinToString(";") { it.rowSignature }

            return LegEndResult(legKey = legKey, orderedPlayers = orderedPlayers)
        }
}
