package arena.services

import arena.ArenaResource
import arena.dao.ArenaDao
import arena.UuidAsText
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlin.Comparable

@ApplicationScoped
class StandingsService {

    @Inject
    lateinit var prizesService: PrizesService
    
    @Inject
    lateinit var arenaDao: ArenaDao

    data class Score(
            val squadId: UuidAsText,
            val points: Int,
            val tiebreak: IntArray,
    ): Comparable<Score> {

        fun compareTiebreak(other: Score): Int {
            for (i in tiebreak.indices) {
                val result = tiebreak[i].compareTo(other.tiebreak[i])
                if (result != 0) return result
            }
            return 0
        }

        override fun compareTo(other: Score): Int {
            val pointsDiff = points - other.points
            if (pointsDiff != 0) return pointsDiff
            return compareTiebreak(other)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Score

            if (squadId != other.squadId) return false
            if (points != other.points) return false
            if (!tiebreak.contentEquals(other.tiebreak)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = squadId.hashCode()
            result = 31 * result + points
            result = 31 * result + tiebreak.contentHashCode()
            return result
        }
    }

    fun calculateStandings(tournamentId: UuidAsText, round: Int): Uni<Unit> {
        return prizesService.getSquadsPrizes(tournamentId).onItem().transformToUni { prize ->
            arenaDao.getSquads(tournamentId).onItem().transformToUni { squad ->

                prize.scoring.calculate(tournamentId, round, arenaDao)
                        .onItem().transformToUni { points ->
                            prize.tiebreak.calculate(tournamentId, round, points, arenaDao)
                                    .onItem().transform { tiebreak ->
                                        Score(
                                                squad.id,
                                                points,
                                                tiebreak,
                                        )
                                    }
                        }

            }
                    .concatenate()
                    .collect().asList()
                    .onItem().transform { scores ->
                        scores.sortWith { a, b -> a.compareTiebreak(b) }
                        scores
                    }
                    .onItem().transformToUni { scores ->
                        arenaDao.setStandings(tournamentId, round, prize.name, scores)
                                .onItem().transform { scores }
                    }
                    .onItem().transform {

                    }
        }
                .concatenate().toUni()

    }

    fun getSquadStandings(tournamentId: UuidAsText, round: Int, prizeId: UuidAsText): Uni<List<ArenaResource.Squad>> {
        TODO("Not yet implemented")
    }

}

interface Scoring {
    fun calculate(tournamentId: UuidAsText, round: Int, arenaDao: ArenaDao): Uni<Int>
}

interface Tiebreak {
    fun calculate(tournamentId: UuidAsText, round: Int, points: Int, arenaDao: ArenaDao): Uni<IntArray>
}

class TeamScoring : Scoring {
    override fun calculate(tournamentId: UuidAsText, round: Int, arenaDao: ArenaDao): Uni<Int> {
        TODO("Not yet implemented")
    }
}

class TDScoring : Scoring {
    override fun calculate(tournamentId: UuidAsText, round: Int, arenaDao: ArenaDao): Uni<Int> {
        TODO("Not yet implemented")
    }
}

class RookieScoreTiebreak : Tiebreak {
    override fun calculate(tournamentId: UuidAsText, round: Int, points: Int, arenaDao: ArenaDao): Uni<IntArray> {
        TODO("Not yet implemented")
    }
}

class ScoreTiebreak : Tiebreak {
    override fun calculate(tournamentId: UuidAsText, round: Int, points: Int, arenaDao: ArenaDao): Uni<IntArray> {
        TODO("Not yet implemented")
    }
}
