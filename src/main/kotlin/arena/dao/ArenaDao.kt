package arena.dao

import arena.ArenaResource.*
import arena.UuidAsText
import arena.services.KeycloakService
import arena.services.StandingsService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import io.vertx.core.json.JsonObject
import io.vertx.mutiny.pgclient.PgPool
import io.vertx.mutiny.sqlclient.Row
import io.vertx.mutiny.sqlclient.RowSet
import io.vertx.mutiny.sqlclient.SqlResult
import io.vertx.mutiny.sqlclient.templates.RowMapper
import io.vertx.mutiny.sqlclient.templates.SqlTemplate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.intellij.lang.annotations.Language
import java.util.*

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class ArenaDao {

    @Inject
    lateinit var keycloakService: KeycloakService

    @Inject
    lateinit var client: PgPool

    fun PgPool.forQuery(@Language("PostgreSQL") sql: String): SqlTemplate<MutableMap<String, Any>, RowSet<Row>> =
            SqlTemplate.forQuery(this, sql)

    fun PgPool.forUpdate(@Language("PostgreSQL") sql: String): SqlTemplate<MutableMap<String, Any>, SqlResult<Void>> =
            SqlTemplate.forUpdate(this, sql)

    private fun mapToTournamentEntity() =
            RowMapper<TournamentEntity> { row ->
                TournamentEntity(
                        row.getUUID("id"),
                        row.getString("name"),
                        TournamentSettings(
                                variant = Variant.valueOf(row.getString("variant")),
                                location = row.getJsonObject("location").mapTo(Location::class.java),
                                start = row.getLocalDate("start").toKotlinLocalDate(),
                                end = row.getLocalDate("end").toKotlinLocalDate(),
                                type = TournamentType.valueOf(row.getString("type")),
                                style = TournamentStyle.valueOf(row.getString("style")),
                                rounds = row.getInteger("rounds"),
                                squads = row.getBoolean("squads"),
                                note = row.getString("note"),
                        ),
                        row.getBoolean("naf"),
                        row.getUUID("organizer"),
                )
            }

    private fun mapToPlayerInscription() =
            RowMapper<PlayerInscription> { row ->
                PlayerInscription(
                        row.getUUID("playerid"),
                        row.getString("teamname"),
                        row.getString("teamrace"),
                        row.getString("nafscore"),
                        row.getBoolean("substitute"),
                )
            }

    data class TournamentEntity(
            val id: UuidAsText,
            val name: String,
            val settings: TournamentSettings,
            val naf: Boolean,
            val organizerId: UuidAsText,
    )

    data class SquadPlayerEntity(
            val playerInscription: PlayerInscription,
            val role: SquadRoles,
    )

    data class SquadPlayersEntity(
            val id: UuidAsText,
            val name: String,
            val players: MutableList<SquadPlayerEntity>,
    )

    fun getTournaments(): Multi<Tournament> =
            client.forQuery(
                    """
                    SELECT 
                        id, 
                        name, 
                        naf, 
                        variant, 
                        location, 
                        start,
                        "end",
                        type, 
                        style, 
                        rounds,
                        squads,
                        note,
                        organizer
                    FROM
                        ca_tournaments
                    ORDER BY start DESC
            """.trimIndent()
            )
                    .mapTo(mapToTournamentEntity())
                    .execute(mutableMapOf())
                    .onItem().transformToMulti(RowSet<TournamentEntity>::toMulti)
                    .onItem().transformToUni { entity ->
                        entityToTournament(entity)
                    }.concatenate()

    private fun entityToTournament(entity: TournamentEntity): Uni<Tournament> =
            keycloakService.getPlayer(entity.organizerId)
                    .onItem().transform { organizer ->
                        Tournament(
                                entity.id,
                                entity.name,
                                entity.settings,
                                entity.naf,
                                organizer,
                        )
                    }

    fun getTournament(tournamentId: UuidAsText): Uni<Tournament> =
            client.forQuery(
                    """
                    SELECT 
                        id,
                        name, 
                        naf, 
                        variant, 
                        location, 
                        start,
                        "end",
                        type, 
                        style, 
                        rounds,
                        squads,
                        note,
                        organizer
                    FROM
                        ca_tournaments
                    WHERE
                        id = #{id}
            """.trimIndent()
            )
                    .mapTo(mapToTournamentEntity())
                    .execute(mutableMapOf("id" to tournamentId))
                    .onItem().transformToMulti(RowSet<TournamentEntity>::toMulti)
                    .toUni()
                    .onItem().transformToUni { entity ->
                        entityToTournament(entity)
                    }

    fun createTournament(
            id: UuidAsText = UuidAsText.randomUUID(),
            tournament: TournamentCreation,
            organizer: String,
            author: UuidAsText,
    ): Uni<String> =

            client.forUpdate(
                    """
            INSERT INTO ca_tournaments (id, name, variant, location, start, "end", type, style, rounds, squads, note, organizer, created, createdBy, updated, updatedBy)
            VALUES (#{id}, #{name}, #{variant}, #{location}, #{start}, #{end}, #{type}, #{style}, #{rounds}, #{squads}, #{note}, #{organizer}, now(), #{author}, now(), #{author})
        """.trimIndent()
            ).execute(
                    mutableMapOf(
                            "id" to id,
                            "name" to tournament.name,
                            "variant" to tournament.settings.variant,
                            "location" to JsonObject(Json.encodeToJsonElement(tournament.settings.location).toString()),
                            "start" to tournament.settings.start.toJavaLocalDate(),
                            "end" to tournament.settings.end.toJavaLocalDate(),
                            "type" to tournament.settings.type,
                            "style" to tournament.settings.style,
                            "rounds" to tournament.settings.rounds,
                            "squads" to tournament.settings.squads,
                            "note" to tournament.settings.note,
                            "organizer" to organizer,
                            "author" to author,
                    )
            ).onItem().transform { id.toString() }

    fun updateTournament(tournamentId: UuidAsText, settings: TournamentSettings, author: UuidAsText): Uni<Boolean> =

            client.forUpdate(
                    """
            UPDATE ca_tournaments SET
                variant = #{variant},
                location = #{location},
                start = #{start},
                "end" = #{end},
                type = #{type},
                style = #{style},
                rounds = #{rounds},
                squads = #{squads},
                note = #{note},
                created = now(),
                createdBy = #{author},
                updated = now(),
                updatedBy = #{author}
            WHERE id = #{id}
            """.trimIndent()
            ).execute(
                    mutableMapOf(
                            "id" to tournamentId,
                            "variant" to settings.variant,
                            "location" to JsonObject(Json.encodeToJsonElement(settings.location).toString()),
                            "start" to settings.start.toJavaLocalDate(),
                            "end" to settings.end.toJavaLocalDate(),
                            "type" to settings.type,
                            "style" to settings.style,
                            "rounds" to settings.rounds,
                            "squads" to settings.squads,
                            "note" to settings.note,
                            "author" to author,
                    )
            ).onItem().transform { true }

    fun updateTournamentInscriptions(tournamentId: UuidAsText, open: Boolean, author: UuidAsText): Uni<Boolean> =

            client.forUpdate(
                    """
                UPDATE ca_tournaments SET
                inscriptions = #{inscriptions},
                updated = now(),
                updatedBy = #{author}
                WHERE id = #{id}
                """.trimIndent()
            ).execute(
                    mutableMapOf(
                            "id" to tournamentId,
                            "inscriptions" to open,
                            "author" to author,
                    )
            ).onItem().transform { true }

    fun updateTournamentNaf(tournamentId: UuidAsText, naf: Boolean, author: UuidAsText): Uni<Boolean> =

            client.forUpdate(
                    """
            UPDATE ca_tournaments SET
            naf = #{naf},
            updated = now(),
            updatedBy = #{author}
            WHERE id = #{id}
            """.trimIndent()
            ).execute(
                    mutableMapOf(
                            "id" to tournamentId,
                            "naf" to naf,
                            "author" to author,
                    )
            ).onItem().transform { true }

    fun inscribePlayer(
            id: UuidAsText = UuidAsText.randomUUID(),
            tournamentId: UuidAsText,
            playerInscription: PlayerInscription,
            author: UuidAsText,
    ): Uni<String> =

            client.forUpdate(
                    """
            INSERT INTO ca_tournaments_inscriptions (id, tournamentId, playerId, teamName, teamRace, nafScore, substitute, created, createdBy, updated, updatedBy)
            VALUES (#{id}, #{tournamentId}, #{playerId}, #{teamName}, #{teamRace}, #{nafScore}, #{substitute}, now(), #{author}, now(), #{author})
            """.trimIndent()
            ).execute(
                    mutableMapOf(
                            "id" to id,
                            "tournamentId" to tournamentId,
                            "playerId" to playerInscription.playerId,
                            "teamName" to playerInscription.teamName,
                            "teamRace" to playerInscription.teamRace,
                            "nafScore" to playerInscription.nafScore,
                            "substitute" to playerInscription.substitute,
                            "author" to author,
                    )
            ).onItem().transform { id.toString() }

    fun inscribeSquad(
            id: UuidAsText = UuidAsText.randomUUID(),
            tournamentId: UuidAsText,
            name: String,
            author: UuidAsText,
    ): Uni<String> =

            client.forUpdate(
                    """
            INSERT INTO ca_squads (id, tournamentId, name, created, createdBy, updated, updatedBy)
            VALUES (#{id}, #{tournamentId}, #{name}, now(), #{author}, now(), #{author})
            """.trimIndent()
            ).execute(
                    mutableMapOf(
                            "id" to id,
                            "tournamentId" to tournamentId,
                            "name" to name,
                            "author" to author,
                    )
            ).onItem().transform { id.toString() }

    fun getTournamentInscriptions(tournamentId: UuidAsText): Multi<PlayerInscription> =
            client.forQuery(
                    """
            SELECT
                playerId,
                teamName,
                teamRace,
                nafScore,
                substitute
            FROM ca_tournaments_inscriptions
            WHERE tournamentId = #{tournamentId}
            ORDER BY created DESC
            """.trimIndent()
            )
                    .mapTo(mapToPlayerInscription())
                    .execute(mutableMapOf("tournamentId" to tournamentId))
                    .onItem().transformToMulti(RowSet<PlayerInscription>::toMulti)

    fun getSquads(tournamentId: UuidAsText): Multi<SquadPlayersEntity> =
            client.forQuery(
                    """
            SELECT
                s.id,
                s.name,
                pi.playerid,
                pi.teamName,
                pi.teamRace,
                pi.nafScore,
                pi.substitute,
                sm.role
            FROM ca_squads s
            LEFT OUTER JOIN ca_squads_members sm ON s.id = sm.squadId
            LEFT OUTER JOIN ca_tournaments_inscriptions pi ON sm.playerId = pi.playerId
            WHERE s.tournamentId = #{tournamentId}
            ORDER BY s.name ASC
            """.trimIndent()
            )
                    .execute(mutableMapOf("tournamentId" to tournamentId))
                    .onItem().transformToMulti(RowSet<Row>::toMulti)
                    .collect().`in`(
                            { mutableMapOf<UUID, SquadPlayersEntity>() }
                    ) { map, row ->
                        val squadId = row.getUUID("id")
                        val squad = map[squadId] ?: SquadPlayersEntity(
                                squadId,
                                row.getString("name"),
                                mutableListOf(),
                        )
                        if (row.getUUID("playerid") != null)
                            squad.players.add(SquadPlayerEntity(
                                    mapToPlayerInscription().map(row),
                                    SquadRoles.valueOf(row.getString("role"))
                            ))
                        map[squadId] = squad

                    }
                    .map { map ->
                        map.values.toList()
                    }
                    .onItem().transformToMulti {
                        Multi.createFrom().items(it.stream())
                    }

    fun inscribeSquadPlayer(
            id: UuidAsText = UuidAsText.randomUUID(),
            squadId: UuidAsText,
            playerId: UuidAsText,
            role: SquadRoles,
            author: UUID,
    ): Uni<String> =

            client.forUpdate(
                    """
            INSERT INTO ca_squads_members (id, squadId, playerId, role, created, createdBy, updated, updatedBy)
            VALUES (#{id}, #{squadId}, #{playerId}, #{role}, now(), #{author}, now(), #{author})
            """.trimIndent()
            ).execute(
                    mutableMapOf(
                            "id" to id,
                            "squadId" to squadId,
                            "playerId" to playerId,
                            "role" to role,
                            "author" to author,
                    )
            ).onItem().transform { id.toString() }

    fun createRound(
            id: UuidAsText = UuidAsText.randomUUID(),
            tournamentId: UuidAsText,
            author: UUID,
    ): Uni<Int> =

            client.forQuery(
                    """
            select coalesce(
                        (
            	select round + 1 from ca_rounds
            	where
            		tournamentId = #{tournamentId}
            		order by round desc limit 1),
            	1) as round
            """.trimIndent()
            )
                    .execute(mutableMapOf("tournamentId" to tournamentId))
                    .onItem().transform { rowSet ->
                        rowSet.first().getInteger("round")
                    }
                    .onItem().transformToUni { round ->
                        client.forUpdate(
                                """
                    INSERT INTO ca_rounds (id, tournamentId, round, created, createdBy, updated, updatedBy)
                    VALUES (#{id}, #{tournamentId}, #{round}, now(), #{author}, now(), #{author})
                    """.trimIndent()
                        ).execute(
                                mutableMapOf(
                                        "id" to id,
                                        "tournamentId" to tournamentId,
                                        "round" to round,
                                        "author" to author,
                                )
                        ).onItem().transform { round }
                    }

    fun setStandings(tournamentId: UuidAsText, round: Int, name: String, scores: List<StandingsService.Score>): Uni<Void> {
        TODO("Not yet implemented")
    }

    fun getPlayerScore(tournamentId: UuidAsText, round: Int, playerId: UuidAsText): StandingsService.Score {
        TODO("Not yet implemented")
    }
}