package arena

import arena.ArenaResource.*
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


}