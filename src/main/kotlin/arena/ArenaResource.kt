package arena

import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.security.Authenticated
import io.smallrye.common.annotation.Blocking
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import io.vertx.codegen.annotations.DataObject
import jakarta.inject.Inject
import jakarta.ws.rs.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.eclipse.microprofile.jwt.JsonWebToken
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import org.jboss.resteasy.reactive.NoCache

private val logger = KotlinLogging.logger {}

@Tag(name = "arena")
@Path("/arena/api/v1/")
@Blocking
class ArenaResource {

    @Inject
    lateinit var jsonWebToken: JsonWebToken

    @Inject
    lateinit var arenaDao: ArenaDao

    @Inject
    lateinit var keycloakService: KeycloakService

    @GET
    @Path("me")
    @NoCache
    @Authenticated
    fun getMe(): Player {

        logger.info("getMe")

        return Player(
                id = UuidAsText.fromString(jsonWebToken.subject),
                name = jsonWebToken.name,
                nafNumber = jsonWebToken.getClaim("naf"),
        )
    }

    @GET
    @Path("tournaments")
    @Blocking
    fun getTournaments(): Multi<Tournament> = arenaDao.getTournaments()

    @GET
    @Path("tournaments/{tournamentId}")
    fun getTournament(
            @PathParam("tournamentId") tournamentId: UuidAsText,
    ): Uni<Tournament> = arenaDao.getTournament(tournamentId)

    @POST
    @Path("tournaments")
    fun createTournament(
            tournament: TournamentCreation,
    ): Uni<String> = arenaDao.createTournament(tournament, jsonWebToken.subject)

    @GET
    @Path("players/{playerId}")
    fun getPlayer(
            @PathParam("playerId") playerId: UuidAsText,
    ): Uni<Player> = keycloakService.getPlayer(playerId)

    @PUT
    @Path("tournaments/{tournamentId}")
    fun updateTournament(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            settings: TournamentSettings,
    ): Uni<Boolean> = arenaDao.getTournament(tournamentId)
            .onItem().transform {
                it.organizer.id
            }
            .onItem().transform {
                if (
                        !jsonWebToken.groups.contains(Roles.admin.name)
                        && jsonWebToken.subject != it.toString()
                ) {
                    logger.debug { "User ${jsonWebToken.name} is not allowed to modify tournament $tournamentId" }
                    throw WebApplicationException(403)
                }
                it
            }
            .onItem().transformToUni { id ->
                arenaDao.updateTournament(tournamentId, settings)
            }

    @PUT
    @Path("tournaments/{tournamentId}/inscriptions")
    fun updateTournamentInscriptions(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            open: Boolean
    ): Uni<Boolean> = arenaDao.updateTournamentInscriptions(tournamentId, jsonWebToken.subject)

    @PUT
    @Path("tournaments/{tournamentId}/naf")
    fun updateTournamentNaf(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            naf: Boolean
    ) {
        TODO("Not yet implemented")
    }

    @POST
    @Path("tournaments/{tournamentId}/players")
    fun inscribePlayer(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            playerInscription: PlayerInscription,
    ) {
        TODO("Not yet implemented")
    }

    @GET
    @Path("tournaments/{tournamentId}/players")
    fun getInscribedPlayers(
            @PathParam("tournamentId") tournamentId: UuidAsText,
    ): Multi<Player> {
        TODO("Not yet implemented")
    }

    @POST
    @Path("tournaments/{tournamentId}/squads")
    fun inscribeSquad(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            squadName: String,
    ) {
        TODO("Not yet implemented")
    }

    @PUT
    @Path("tournaments/{tournamentId}/squads/{squadId}")
    fun assignSquadPlayer(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            @PathParam("squadId") squadId: UuidAsText,
            playerId: UuidAsText,
    ) {
        TODO("Not yet implemented")
    }

    @PUT
    @Path("tournaments/{tournamentId}/players/{playerId}/substitute")
    fun substitutePlayer(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            @PathParam("playerId") playerId: UuidAsText,
            substitute: Boolean,
    ) {
        TODO("Not yet implemented")
    }

    @POST
    @Path("tournaments/{tournamentId}/rounds/squads")
    fun prepareNextSquadsRound(
            @PathParam("tournamentId") tournamentId: UuidAsText,
    ): SquadsRound {
        TODO("Not yet implemented")
    }

    @PUT
    @Path("tournaments/{tournamentId}/rounds/squads/{round}")
    fun substituteNextSquadsRound(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            @PathParam("round") round: Int,
            newPairing: SquadsPairing,
    ): SquadsRound {
        TODO("Not yet implemented")
    }

    @POST
    @Path("tournaments/{tournamentId}/rounds/players")
    fun prepareNextPlayersRound(
            @PathParam("tournamentId") tournamentId: UuidAsText,
    ): PlayersRound {
        TODO("Not yet implemented")
    }

    @Serializable
    data class SquadsRound(
            val number: Int,
            val squadsPairings: List<SquadsPairing>,
    )

    @Serializable
    data class PlayersRound(
            val number: Int,
            val squadPairings: List<PlayersPairing>,
    )

    @Serializable
    data class SquadsPairing(
            val table: Int,
            val home: Squad,
            val away: Squad,
            val playersPairings: List<PlayersPairing>
    )

    @Serializable
    data class PlayersPairing(
            val table: Int,
            val home: TournamentPlayer,
            val away: TournamentPlayer,
    )

    @Serializable
    data class Squad(
            val id: UuidAsText,
            val name: String,
    )

    @Serializable
    data class TournamentPlayer(
            val player: Player,
            val teamName: String,
            val teamRace: String,
            val nafScore: String,
    )

    @Serializable
    data class Player(
            val id: UuidAsText,
            val name: String,
            val nafNumber: String,
    )

    @Serializable
    data class PlayerInscription(
            val playerId: UuidAsText,
            val teamName: String,
            val teamRace: String,
            val substitute: Boolean = false,
    )

    @Serializable
    data class Tournament(
            val id: UuidAsText,
            val name: String,
            val settings: TournamentSettings,
            val naf: Boolean,
            val organizer: Player,
    )

    @Serializable
    data class TournamentCreation(
            val name: String,
            val settings: TournamentSettings,
    )

    @Serializable
    data class TournamentSettings(
            val variant: Variant,
            val location: Location,
            val start: LocalDate,
            val end: LocalDate,
            val type: TournamentType,
            val style: TournamentStyle,
            val rounds: Int,
            val squads: Boolean,
            val note: String,
    )

    @Serializable
    enum class TournamentStyle {
        Swiss,
    }

    @Serializable
    data class Location(
            val address1: String,
            val address2: String,
            val city: String,
            val state: String,
            val zip: String,
            val nation: String,
    )

    enum class TournamentType {
        Open,
        Invitational,
    }

    enum class Variant {
        BloodBowl2020,
        Dungeonbowl,
        Classic,
        Online,
        Seven,
        GutterBowl,
        Deathbowl,
        Streetbowl,
        Beachbowl,
        Dungeon7s,
        Deathbowl7s,
        Specialist,
        Draft,
    }

    enum class Roles {
        admin,
        naf,
    }

}