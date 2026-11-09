package net.sabafly.clickportals.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.dialog.AetheryteDialogFactory;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.visual.NoopHologramService;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AetheryteListenerTest {

    private static final UUID WORLD_ID = UUID.fromString("12345678-1234-1234-1234-1234567890ab");

    private AetheryteRegistry registry;
    private AetheryteListener listener;
    private World world;
    private Block aetheryteBlock;
    private Block normalBlock;

    @BeforeEach
    void setUp() {
        final Plugin plugin = mock(Plugin.class);
        registry = new AetheryteRegistry();
        final AetheryteService service = new AetheryteService(plugin, registry, NoopHologramService.INSTANCE);
        final AetheryteDialogFactory dialogFactory = new AetheryteDialogFactory(service);
        listener = new AetheryteListener(plugin, registry, dialogFactory, NoopHologramService.INSTANCE);

        world = mock(World.class);
        when(world.getUID()).thenReturn(WORLD_ID);
        when(world.getName()).thenReturn("world");
        when(world.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));

        aetheryteBlock = mock(Block.class);
        when(aetheryteBlock.getWorld()).thenReturn(world);
        when(aetheryteBlock.getX()).thenReturn(5);
        when(aetheryteBlock.getY()).thenReturn(64);
        when(aetheryteBlock.getZ()).thenReturn(5);

        normalBlock = mock(Block.class);
        when(normalBlock.getWorld()).thenReturn(world);
        when(normalBlock.getX()).thenReturn(6);
        when(normalBlock.getY()).thenReturn(64);
        when(normalBlock.getZ()).thenReturn(5);

        registry.register(world, new Aetheryte(
                UUID.randomUUID(),
                "保護テスト用",
                WORLD_ID,
                "world",
                5,
                64,
                5,
                UUID.randomUUID(),
                1000L
        ));
    }

    @Test
    void blockBreakOnAetheryteIsCancelled() {
        final Player player = mock(Player.class);
        final BlockBreakEvent breakEvent = new BlockBreakEvent(aetheryteBlock, player);

        listener.onBlockBreak(breakEvent);

        assertThat(breakEvent.isCancelled()).isTrue();
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void entityExplosionExcludesAetheryteBlock() {
        final List<Block> explodedBlocks = new ArrayList<>(List.of(aetheryteBlock, normalBlock));
        final EntityExplodeEvent explodeEvent = new EntityExplodeEvent(
                mock(Entity.class),
                new Location(world, 5, 64, 5),
                explodedBlocks,
                1.0f,
                ExplosionResult.DESTROY
        );

        listener.onEntityExplode(explodeEvent);

        assertThat(explodeEvent.blockList()).containsExactly(normalBlock);
    }

    @Test
    void pistonExtendMovingAetheryteIsCancelled() {
        final Block pistonBlock = mock(Block.class);
        final BlockPistonExtendEvent pistonEvent = new BlockPistonExtendEvent(
                pistonBlock,
                List.of(normalBlock, aetheryteBlock),
                BlockFace.EAST
        );

        listener.onPistonExtend(pistonEvent);

        assertThat(pistonEvent.isCancelled()).isTrue();
    }
}
