package arena

import arena.dao.ArenaDao
import arena.services.KeycloakService
import arena.services.PairingService
import arena.services.StandingsService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.quarkus.security.Authenticated
import io.smallrye.common.annotation.Blocking
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
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

    @Inject
    lateinit var standingsService: StandingsService

    @Inject
    lateinit var pairingService: PairingService

    @GET
    @Path("me")
    @NoCache
    @Authenticated
    fun getMe(): Player = Player(
            id = UuidAsText.fromString(jsonWebToken.subject),
            name = jsonWebToken.name,
            nafNumber = jsonWebToken.getClaim("naf"),
    )

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
    ): Uni<String> = arenaDao.createTournament(tournament = tournament, organizer = jsonWebToken.subject, author = jsonWebToken.subject.toUuid())

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
    ): Uni<Boolean> = authTournament(tournamentId) {
        arenaDao.updateTournament(tournamentId, settings, jsonWebToken.subject.toUuid())
    }

    @PUT
    @Path("tournaments/{tournamentId}/inscriptions")
    fun updateTournamentInscriptions(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            inscriptions: TournamentInscriptions,
    ): Uni<Boolean> = authTournament(tournamentId) {
        arenaDao.updateTournamentInscriptions(it, inscriptions.open, jsonWebToken.subject.toUuid())
    }

    @PUT
    @Path("tournaments/{tournamentId}/naf")
    fun updateTournamentNaf(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            naf: TournamentNaf,
    ): Uni<Boolean> = authTournament(tournamentId) {
        arenaDao.updateTournamentNaf(it, naf.official, jsonWebToken.subject.toUuid())
    }

    @POST
    @Path("tournaments/{tournamentId}/players")
    fun inscribePlayer(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            playerInscription: PlayerInscription,
    ): Uni<String> = authPlayerInscription(tournamentId, playerInscription.playerId) { tid ->
        arenaDao.inscribePlayer(
                tournamentId = tid,
                playerInscription = playerInscription,
                author = jsonWebToken.subject.toUuid(),
        )
    }

    @GET
    @Path("tournaments/{tournamentId}/players")
    fun getInscribedPlayers(
            @PathParam("tournamentId") tournamentId: UuidAsText,
    ): Multi<TournamentPlayer> = arenaDao.getTournamentInscriptions(tournamentId)
            .onItem().transformToUni { inscription ->
                keycloakService.getPlayer(inscription.playerId)
                        .onItem().transform {
                            TournamentPlayer(
                                    it,
                                    inscription.teamName,
                                    inscription.teamRace,
                                    inscription.nafScore,
                                    inscription.substitute,
                            )
                        }
            }
            .concatenate()

    @POST
    @Path("tournaments/{tournamentId}/squads")
    fun inscribeSquad(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            squad: SquadInscription,
    ): Uni<String> = authSquadCreation(tournamentId, jsonWebToken.subject.toUuid()) { playerInscription ->
        arenaDao.inscribeSquad(
                tournamentId = tournamentId,
                name = squad.name,
                author = jsonWebToken.subject.toUuid(),
        )
    }

    @GET
    @Path("tournaments/{tournamentId}/squads")
    fun getSquad(
            @PathParam("tournamentId") tournamentId: UuidAsText,
    ): Multi<Squad> = arenaDao.getSquads(tournamentId)
            .onItem().transformToUni { squad ->

                if (squad.players.isNotEmpty())
                    Uni.join().all(
                            squad.players.parallelStream()
                                    .map { squadPlayer ->
                                        keycloakService.getPlayer(squadPlayer.playerInscription.playerId)
                                                .onItem().transform { player ->
                                                    SquadPlayer(
                                                            TournamentPlayer(
                                                                    player,
                                                                    squadPlayer.playerInscription.teamName,
                                                                    squadPlayer.playerInscription.teamRace,
                                                                    squadPlayer.playerInscription.nafScore,
                                                                    squadPlayer.playerInscription.substitute,
                                                            ),
                                                            squadPlayer.role,
                                                    )
                                                }
                                    }
                                    .toList()

                    )
                            .andCollectFailures()
                            .onItem().transform { players ->
                                Squad(
                                        squad.id,
                                        squad.name,
                                        players,
                                )
                            }
                else
                    Uni.createFrom().item(Squad(
                            squad.id,
                            squad.name,
                            listOf(),
                    ))
            }
            .concatenate()

    @PUT
    @Path("tournaments/{tournamentId}/squads/{squadId}")
    fun assignSquadPlayer(
            @PathParam("tournamentId") tournamentId: UuidAsText,
            @PathParam("squadId") squadId: UuidAsText,
            inscription: SquadPlayerInscription,
    ): Uni<String> =
            arenaDao.inscribeSquadPlayer(
                    squadId = squadId,
                    playerId = inscription.playerId,
                    role = inscription.role,
                    author = jsonWebToken.subject.toUuid(),
            )

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
    ): Uni<SquadsRound> = authTournament(tournamentId) {

        arenaDao.createRound(
                tournamentId = tournamentId,
                author = jsonWebToken.subject.toUuid(),
        )
                .onItem().transformToUni { round ->
                    standingsService.calculateStandings(tournamentId, round-1)
                            .onItem().transform { round }
                }

                .onItem().transformToUni { round ->
                    pairingService.calculatePairings(tournamentId, round)
                }
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
            val members: List<SquadPlayer>,
    )

    @Serializable
    data class TournamentPlayer(
            val player: Player,
            val teamName: String,
            val teamRace: String,
            val nafScore: String,
            val substitute: Boolean = false,
    )

    @Serializable
    data class SquadPlayer(
            val member: TournamentPlayer,
            val role: SquadRoles,
    )

    @Serializable
    data class SquadPlayerInscription(
            val playerId: UuidAsText,
            val role: SquadRoles,
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
            val nafScore: String = "",
            val substitute: Boolean = false,
    )

    @Serializable
    data class SquadInscription(
            val name: String,
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
    data class TournamentInscriptions(
            val open: Boolean,
    )

    @Serializable
    data class TournamentNaf(
            val official: Boolean,
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

    enum class SquadRoles {
        captain,
        member,
    }

    private fun <T> authTournament(tournamentId: UuidAsText, update: (id: UuidAsText) -> Uni<T>) =
            arenaDao.getTournament(tournamentId)
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
                        tournamentId
                    }
                    .onItem().transformToUni(update)

    private fun <T> authPlayerInscription(tournamentId: UuidAsText, playerId: UuidAsText, update: (id: UuidAsText) -> Uni<T>) =
            arenaDao.getTournament(tournamentId)
                    .onItem().transform {
                        it.organizer.id
                    }
                    .onItem().transform {
                        if (
                                !jsonWebToken.groups.contains(Roles.admin.name)
                                && jsonWebToken.subject != playerId.toString()
                        ) {
                            logger.debug { "User ${jsonWebToken.name} is not allowed to inscribe $playerId to tournament $tournamentId" }
                            throw WebApplicationException(403)
                        }
                        tournamentId
                    }
                    .onItem().transformToUni(update)

    private fun <T> authSquadCreation(tournamentId: UuidAsText, playerId: UuidAsText, update: (playerInscription: PlayerInscription) -> Uni<T>) =
            arenaDao.getTournamentInscriptions(tournamentId)
                    .filter {
                        it.playerId == playerId
                    }
                    .toUni()
                    .onItem().transformToUni(update)

}