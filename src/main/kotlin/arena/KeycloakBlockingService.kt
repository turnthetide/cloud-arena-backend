package arena

import arena.ArenaResource.Player
import io.smallrye.common.annotation.Blocking
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class KeycloakBlockingService {

    @Inject
    lateinit var keycloakService: KeycloakService

    @Blocking
    fun getPlayer(playerId: UUID): Uni<Player> =
        keycloakService.getPlayer(playerId)

}