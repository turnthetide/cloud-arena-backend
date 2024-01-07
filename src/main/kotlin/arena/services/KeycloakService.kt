package arena.services

import arena.ArenaResource.Player
import arena.UuidAsText
import io.quarkus.cache.CacheResult
import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.UserRepresentation
import java.util.*

private const val realm = "arena"

@ApplicationScoped
class KeycloakService {

    @Inject
    lateinit var keycloak: Keycloak

    @Inject
    lateinit var vertx: Vertx

    @ActivateRequestContext
    @CacheResult(cacheName = "players")
    fun getPlayer(playerId: UUID): Uni<Player> =
            vertx.executeBlocking(
                    Uni.createFrom().item {
                        keycloak.realm(realm)
                                .users()
                                .get(playerId.toString())
                                .toRepresentation()
                                .toPlayer()
                    }
            )

    private fun UserRepresentation.toPlayer(): Player =
            Player(
                    UuidAsText.fromString(this.id),
                    this.username,
                    this.attributes["naf"]?.first() ?: "",
            )

    fun UuidAsText.toPlayer() = getPlayer(this)
    
}
