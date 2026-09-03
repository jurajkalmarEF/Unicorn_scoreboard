package com.example.scorebuddystats

/**
 * Converts a leg result into points.
 *
 * Rule: last place gets 1 point, second-to-last gets 2, ...,
 * the winner (1st place) gets N points, where N = number of players.
 *
 * [orderedPlayers] must be sorted from 1st place (winner) to last place,
 * i.e. the same order Scorebuddy shows the leg result in.
 */
data class PlayerResult(
    val playerName: String,
    val placement: Int,   // 1 = winner
    val points: Int
)

object PlacementScorer {

    fun score(orderedPlayers: List<String>): List<PlayerResult> {
        val n = orderedPlayers.size
        return orderedPlayers.mapIndexed { index, name ->
            val placement = index + 1          // 1st, 2nd, 3rd, ...
            val points = n - index              // winner -> n, last -> 1
            PlayerResult(playerName = name, placement = placement, points = points)
        }
    }
}
