package arena.services

import arena.ArenaResource
import arena.UuidAsText
import arena.dao.ArenaDao
import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.Vertx
import io.vertx.mutiny.core.eventbus.EventBus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class PairingService {

    @Inject
    lateinit var prizesService: PrizesService

    @Inject
    lateinit var standingsService: StandingsService

    @Inject
    lateinit var arenaDao: ArenaDao
    
    @Inject
    lateinit var vertx: Vertx
    
    @Inject
    lateinit var eventBus: EventBus

    fun calculatePairings(tournamentId: UuidAsText, round: Int): Uni<ArenaResource.SquadsRound> =
        prizesService.getSquadsPrizes(tournamentId)
                .select().first()
                .toUni()
                .onItem().transformToUni { prize ->
                    standingsService.getSquadStandings(tournamentId, round - 1, prize.id)
                            .onItem().transform { standings ->

                                val pairs = mutableListOf<Pair<ArenaResource.Squad, ArenaResource.Squad>>()
                                val paired = mutableSetOf<ArenaResource.Squad>()

                                while (paired.size < standings.size) { // While all the players are not paired

                                    for (h in standings.indices) {
                                        val home = standings[h]
                                        if (paired.contains(home)) {
                                            // Already paired, skip
                                            continue
                                        }

                                        pickNext(home, standings, h, paired, pairs, tournamentId)
                                    }
                                }

                                val pairings = mutableListOf<ArenaResource.SquadsPairing>()
                                var table = 1
                                
                                pairs.forEach { pair ->
                                    pairings.add(ArenaResource.SquadsPairing(
                                            table,
                                            pair.first,
                                            pair.second,
                                            calculatePlayersPairings(tournamentId, round, table, pair.first, pair.second)
                                    ))
                                }
                                table++
                                
                                ArenaResource.SquadsRound(
                                        round,
                                        pairings
                                )
                            }
                }

    private fun pickNext(home: ArenaResource.Squad, standings: List<ArenaResource.Squad>, h: Int, paired: MutableSet<ArenaResource.Squad>, pairs: MutableList<Pair<ArenaResource.Squad, ArenaResource.Squad>>, tournamentId: UuidAsText) {
        var distance = 1
        var a = h+distance
        if (a >= standings.size) {
            a = h-distance
            
        } // End of the list
        
        if (a < 0) {} // Beginning of the list
        
        val away = standings[a] 
        if (!paired.contains(away)) {
            paired.add(home)
            paired.add(away)
            
        } 
    }
    
    private fun checkSquad(home: ArenaResource.Squad, a: Int, standings: List<ArenaResource.Squad>, paired: MutableSet<ArenaResource.Squad>, tournamentId: UuidAsText): Boolean =
        a < standings.size && !paired.contains(standings[a]) && !arenaDao.getPastOpponents(tournamentId, home.id).contains(standings[a])

    private fun calculatePlayersPairings(tournamentId: UuidAsText, round: Int, table: Int, home: ArenaResource.Squad, away: ArenaResource.Squad): List<ArenaResource.PlayersPairing> {
        val homePlayers = home.members.map { player ->
            player to arenaDao.getPlayerScore(tournamentId, round - 1, player.member.player.id)
        }.sortedByDescending { it.second }

        val awayPlayers = away.members.map { player ->
            player to arenaDao.getPlayerScore(tournamentId, round - 1, player.member.player.id)
        }.sortedByDescending { it.second }

        val pairings = mutableListOf<ArenaResource.PlayersPairing>()
        for (i in homePlayers.indices) {
            pairings.add(
                    ArenaResource.PlayersPairing(
                            table,
                            homePlayers[i].first.member,
                            awayPlayers[i].first.member,
                    )
            )
        }
        return pairings
    }

}

