package arena

import arena.ArenaResource.Player
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.UserRepresentation
import java.util.*

private const val realm = "arena"

@ApplicationScoped
class KeycloakService {

    @Inject
    lateinit var keycloak: Keycloak

//    @Blocking
//    @CacheResult(cacheName = "players")
    fun getPlayer(playerId: UUID): Player {
        return keycloak.realm(realm)
            .users()
            .get(playerId.toString())
            .toRepresentation()
            .toPlayer()
    }

    private fun UserRepresentation.toPlayer(): Player =
        Player(
            UuidAsText.fromString(this.id),
            this.username,
            this.attributes["naf"]?.first() ?: "",
        )

}