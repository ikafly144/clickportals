package net.sabafly.clickportals.visual;

import java.util.Collection;
import java.util.UUID;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import org.bukkit.entity.Player;

/**
 * No-op implementation of {@link HologramService} used when PacketEvents is unavailable or disabled.
 */
public final class NoopHologramService implements HologramService {

    public static final NoopHologramService INSTANCE = new NoopHologramService();

    private NoopHologramService() {
    }

    @Override
    public void showAetheryte(final Aetheryte aetheryte) {
    }

    @Override
    public void hideAetheryte(final UUID aetheryteId) {
    }

    @Override
    public void refreshPlayer(final Player player, final Collection<Aetheryte> worldAetherytes) {
    }

    @Override
    public void destroyAll() {
    }
}
