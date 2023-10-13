package arena

import arena.ArenaResource.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
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

    private fun mapToTournament() =
        RowMapper<Tournament> { row ->
            Tournament(
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
                keycloakService.getPlayer(row.getUUID("organizer")),
            )
        }

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
            .mapTo(mapToTournament())
            .execute(mutableMapOf())
            .onItem().transformToMulti(RowSet<Tournament>::toMulti)

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
            .mapTo(mapToTournament())
            .execute(mutableMapOf("id" to tournamentId))
            .map { it.first() }

    fun createTournament(
        tournament: TournamentCreation,
        organizer: String,
        id: UuidAsText = UuidAsText.randomUUID()
    ): Uni<String> =

        client.forUpdate(
            """
            INSERT INTO ca_tournaments (id, name, variant, location, start, "end", type, style, rounds, squads, note, organizer)
            VALUES (#{id}, #{name}, #{variant}, #{location}, #{start}, #{end}, #{type}, #{style}, #{rounds}, #{squads}, #{note}, #{organizer})
        """.trimIndent()
        ).execute(
            mutableMapOf(
                "id" to id,
                "name" to tournament.name,
                "variant" to tournament.settings.variant,
                "location" to Json.encodeToJsonElement(tournament.settings.location).toString(),
                "start" to tournament.settings.start.toJavaLocalDate(),
                "end" to tournament.settings.end.toJavaLocalDate(),
                "type" to tournament.settings.type,
                "style" to tournament.settings.style,
                "rounds" to tournament.settings.rounds,
                "squads" to tournament.settings.squads,
                "note" to tournament.settings.note,
                "organizer" to organizer,
            )
        ).onItem().transform { id.toString() }


}