package arena.services

import arena.UuidAsText
import arena.dao.ArenaDao
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class PrizesService {

    // TODO: Move prizes on DB
    @Inject
    lateinit var arenaDao: ArenaDao

    fun getSquadsPrizes(tournamentId: UuidAsText): Multi<Prize> =
            Multi.createFrom().iterable(
                    listOf(
                            Prize(
                                    UuidAsText.fromString("9fd84e03-648a-4061-a41b-25b316e78b61"),
                                    tournamentId,
                                    "Team Score",
                                    TeamScoring(),
                                    RookieScoreTiebreak()
                            ),
                            Prize(
                                    UuidAsText.fromString("5025d3dd-c021-4727-8a3a-aed94b643728"),
                                    tournamentId,
                                    "Most TD",
                                    TDScoring(),
                                    ScoreTiebreak()
                            ),
                    )
            )

    fun getPlayersPrizes(tournamentId: UuidAsText): Multi<Prize> =
            Multi.createFrom().iterable(
                    listOf(
                            Prize(
                                    UuidAsText.fromString("0df7298d-9224-4995-a051-8b09c4841bda"),
                                    tournamentId,
                                    "Player Score",
                                    TeamScoring(),
                                    RookieScoreTiebreak()
                            ),
                            Prize(
                                    UuidAsText.fromString("bb03757a-e62e-460f-8320-5142dc07c21d"),
                                    tournamentId,
                                    "Most TD",
                                    TDScoring(),
                                    ScoreTiebreak()
                            ),
                    )
            )

}

data class Prize(
        val id: UuidAsText,
        val tournamentId: UuidAsText,
        val name: String,
        val scoring: Scoring,
        val tiebreak: Tiebreak,
)