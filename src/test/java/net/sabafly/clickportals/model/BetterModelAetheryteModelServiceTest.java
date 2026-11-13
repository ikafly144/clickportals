/*
 * ClickPortals
 * Copyright (C) 2026 ikafly144
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.sabafly.clickportals.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.AnimationSection;
import net.sabafly.clickportals.variation.AetheryteVariation.BetterModelSection;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class BetterModelAetheryteModelServiceTest {

    private Logger logger;
    private World world;
    private Aetheryte aetheryte;

    @BeforeEach
    void setUp() {
        logger = mock(Logger.class);
        world = mock(World.class);
        final UUID worldUid = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldUid);
        aetheryte = new Aetheryte(
                UUID.randomUUID(),
                "Crystal Aetheryte",
                worldUid,
                "world",
                100,
                64,
                200,
                UUID.randomUUID(),
                System.currentTimeMillis(),
                "crystal"
        );
    }

    @Test
    void noopServiceReturnsFalseAndPerformsNoOperations() {
        final AetheryteModelService noop = NoopAetheryteModelService.INSTANCE;
        noop.registerAetheryte(aetheryte, AetheryteVariation.defaultVariation(), world);
        assertThat(noop.hasModel(aetheryte.id())).isFalse();

        noop.triggerAnimation(aetheryte.id(), ModelAnimationTrigger.ON_INTERACT);
        noop.unregisterAetheryte(aetheryte.id());
        noop.destroyAll();
    }

    @Test
    void disabledBetterModelVariationDoesNotCreateTracker() {
        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.empty(),
                (renderer, loc) -> Optional.empty()
        );

        final AetheryteVariation variation = new AetheryteVariation(
                "Default",
                null, null, null, null, null, null, null,
                BetterModelSection.disabled()
        );

        service.registerAetheryte(aetheryte, variation, world);
        assertThat(service.hasModel(aetheryte.id())).isFalse();
    }

    @Test
    void unknownModelIdLogsWarningAndFallsBack() {
        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.empty(),
                (renderer, loc) -> Optional.empty()
        );

        final AetheryteVariation variation = new AetheryteVariation(
                "Crystal",
                null, null, null, null, null, null, null,
                new BetterModelSection(true, "unknown_model", AnimationSection.defaults())
        );

        service.registerAetheryte(aetheryte, variation, world);
        assertThat(service.hasModel(aetheryte.id())).isFalse();
        verify(logger).warn(
                eq("BetterModel model '{}' not found for Aetheryte variation '{}'; falling back to BLOCK_DISPLAY"),
                eq("unknown_model"),
                eq("crystal")
        );
    }

    @Test
    void registeredModelStartsIdleAnimationAndRespondsToEventTriggers() {
        final ModelRenderer mockRenderer = mock(ModelRenderer.class);
        final ModelTracker mockTracker = mock(ModelTracker.class);
        when(mockTracker.isClosed()).thenReturn(false);

        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.of(mockRenderer),
                (renderer, loc) -> Optional.of(mockTracker)
        );

        final AetheryteVariation variation = new AetheryteVariation(
                "Crystal",
                null, null, null, null, null, null, null,
                new BetterModelSection(
                        true,
                        "crystal_model",
                        new AnimationSection("custom_idle", "spin", "depart", "arrive")
                )
        );

        service.registerAetheryte(aetheryte, variation, world);
        assertThat(service.hasModel(aetheryte.id())).isTrue();
        verify(mockTracker).animate("custom_idle");

        // Trigger on-interact
        final ArgumentCaptor<AnimationModifier> modifierCaptor = ArgumentCaptor.forClass(AnimationModifier.class);
        service.triggerAnimation(aetheryte.id(), ModelAnimationTrigger.ON_INTERACT);
        verify(mockTracker).animate(eq("spin"), modifierCaptor.capture());
        assertThat(modifierCaptor.getValue().priority()).isEqualTo(1);
        assertThat(modifierCaptor.getValue().type()).isEqualTo(AnimationIterator.Type.PLAY_ONCE);

        // Trigger warp departure and arrival
        service.triggerAnimation(aetheryte.id(), ModelAnimationTrigger.ON_WARP_DEPART);
        verify(mockTracker).animate(eq("depart"), any(AnimationModifier.class));

        service.triggerAnimation(aetheryte.id(), ModelAnimationTrigger.ON_WARP_ARRIVE);
        verify(mockTracker).animate(eq("arrive"), any(AnimationModifier.class));

        // Unregister closes tracker
        service.unregisterAetheryte(aetheryte.id());
        verify(mockTracker).close();
        when(mockTracker.isClosed()).thenReturn(true);
        assertThat(service.hasModel(aetheryte.id())).isFalse();
    }

    @Test
    void unreadyBetterModelLogsDebugInsteadOfWarning() {
        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.empty(),
                (renderer, loc) -> Optional.empty(),
                () -> false
        );

        final AetheryteVariation variation = new AetheryteVariation(
                "Crystal",
                null, null, null, null, null, null, null,
                new BetterModelSection(true, "crystal_model", AnimationSection.defaults())
        );

        service.registerAetheryte(aetheryte, variation, world);
        assertThat(service.hasModel(aetheryte.id())).isFalse();
        verify(logger, never()).warn(any(String.class), any(), any());
        verify(logger).debug(
                eq("BetterModel model '{}' not loaded yet for Aetheryte variation '{}'; will sync when BetterModel completes loading"),
                eq("crystal_model"),
                eq("crystal")
        );
    }

    @Test
    void sameAnimationAsIdlePlaysDirectlyWithoutModifier() {
        final ModelRenderer mockRenderer = mock(ModelRenderer.class);
        final ModelTracker mockTracker = mock(ModelTracker.class);
        when(mockTracker.isClosed()).thenReturn(false);

        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.of(mockRenderer),
                (renderer, loc) -> Optional.of(mockTracker)
        );

        final AetheryteVariation variation = new AetheryteVariation(
                "Crystal",
                null, null, null, null, null, null, null,
                new BetterModelSection(
                        true,
                        "crystal_model",
                        new AnimationSection("spin", "spin", "", "")
                )
        );

        service.registerAetheryte(aetheryte, variation, world);
        verify(mockTracker).animate("spin");

        service.triggerAnimation(aetheryte.id(), ModelAnimationTrigger.ON_INTERACT);
        verify(mockTracker, org.mockito.Mockito.times(2)).animate("spin");
        verify(mockTracker, never()).animate(eq("spin"), any(AnimationModifier.class));
    }

    @Test
    void emptyAnimationNameDoesNotTriggerAnimation() {
        final ModelRenderer mockRenderer = mock(ModelRenderer.class);
        final ModelTracker mockTracker = mock(ModelTracker.class);
        when(mockTracker.isClosed()).thenReturn(false);

        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.of(mockRenderer),
                (renderer, loc) -> Optional.of(mockTracker)
        );

        final AetheryteVariation variation = new AetheryteVariation(
                "Crystal",
                null, null, null, null, null, null, null,
                new BetterModelSection(
                        true,
                        "crystal_model",
                        new AnimationSection("idle", "", "", "")
                )
        );

        service.registerAetheryte(aetheryte, variation, world);
        service.triggerAnimation(aetheryte.id(), ModelAnimationTrigger.ON_INTERACT);

        verify(mockTracker, never()).animate(any(String.class), any(AnimationModifier.class), any());
    }

    @Test
    void destroyAllClosesAllActiveTrackers() {
        final ModelRenderer mockRenderer = mock(ModelRenderer.class);
        final ModelTracker tracker1 = mock(ModelTracker.class);
        final ModelTracker tracker2 = mock(ModelTracker.class);
        when(tracker1.isClosed()).thenReturn(false);
        when(tracker2.isClosed()).thenReturn(false);

        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.of(mockRenderer),
                (renderer, loc) -> loc.getBlockX() == 100 ? Optional.of(tracker1) : Optional.of(tracker2)
        );

        final Aetheryte aetheryte2 = new Aetheryte(
                UUID.randomUUID(), "Second", aetheryte.worldId(), "world", 200, 64, 300,
                UUID.randomUUID(), System.currentTimeMillis(), "crystal"
        );

        final AetheryteVariation variation = new AetheryteVariation(
                "Crystal",
                null, null, null, null, null, null, null,
                new BetterModelSection(true, "model", AnimationSection.defaults())
        );

        service.registerAetheryte(aetheryte, variation, world);
        service.registerAetheryte(aetheryte2, variation, world);
        assertThat(service.hasModel(aetheryte.id())).isTrue();
        assertThat(service.hasModel(aetheryte2.id())).isTrue();

        service.destroyAll();
        verify(tracker1).close();
        verify(tracker2).close();
        assertThat(service.hasModel(aetheryte.id())).isFalse();
        assertThat(service.hasModel(aetheryte2.id())).isFalse();
    }

    @Test
    void trackerIsSpawnedForWorldPlayersAndSyncedOnRefreshAndQuit() {
        final ModelRenderer mockRenderer = mock(ModelRenderer.class);
        final ModelTracker mockTracker = mock(ModelTracker.class);
        final Player inWorld = mock(Player.class);
        when(inWorld.getWorld()).thenReturn(world);
        when(world.getPlayers()).thenReturn(List.of(inWorld));

        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.of(mockRenderer),
                (renderer, loc) -> Optional.of(mockTracker)
        );
        final AetheryteVariation variation = new AetheryteVariation(
                "Crystal",
                null, null, null, null, null, null, null,
                new BetterModelSection(true, "crystal_model", AnimationSection.defaults())
        );

        service.registerAetheryte(aetheryte, variation, world);
        verify(mockTracker).spawn(inWorld);

        // Player moved to another world: model must be removed
        final World other = mock(World.class);
        when(other.getUID()).thenReturn(UUID.randomUUID());
        final Player traveler = mock(Player.class);
        when(traveler.getWorld()).thenReturn(other);
        when(mockTracker.isSpawned(traveler)).thenReturn(true);
        service.refreshPlayer(traveler, List.of());
        verify(mockTracker).remove(traveler);
        verify(mockTracker, never()).spawn(traveler);

        // Player in the same world gets (re)spawned on refresh
        service.refreshPlayer(inWorld, List.of(aetheryte));
        verify(mockTracker, org.mockito.Mockito.times(2)).spawn(inWorld);

        // Quit removes the player from spawned trackers
        when(mockTracker.isSpawned(inWorld)).thenReturn(true);
        service.removePlayer(inWorld);
        verify(mockTracker).remove(inWorld);
    }

    @Test
    void playerAnimationHoldOnLastPlaysAndClosesOnStopOrDestroy() {
        final ModelRenderer limbRenderer = mock(ModelRenderer.class);
        final ModelTracker playerTracker = mock(ModelTracker.class);
        when(playerTracker.animate(eq("cast_pose"), any(AnimationModifier.class))).thenReturn(true);

        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Steve");

        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.empty(),
                (r, loc) -> Optional.empty(),
                limbId -> "steve".equals(limbId) ? Optional.of(limbRenderer) : Optional.empty(),
                (r, p) -> Optional.of(playerTracker),
                () -> true
        );

        service.playPlayerAnimation(player, "steve", "cast_pose", AetheryteModelService.PlayerAnimationMode.HOLD_ON_LAST);

        final ArgumentCaptor<AnimationModifier> modifierCaptor = ArgumentCaptor.forClass(AnimationModifier.class);
        verify(playerTracker).animate(eq("cast_pose"), modifierCaptor.capture());
        assertThat(modifierCaptor.getValue().type()).isEqualTo(AnimationIterator.Type.HOLD_ON_LAST);
        verify(playerTracker, never()).close();

        service.stopPlayerAnimation(player);
        verify(playerTracker).close();
    }

    @Test
    void playerAnimationPlayOnceClosesAutomaticallyViaOnEndCallbackAndSupportsColonLimbSyntax() {
        final ModelRenderer customLimb = mock(ModelRenderer.class);
        final ModelTracker playerTracker = mock(ModelTracker.class);
        when(playerTracker.animate(eq("arrive_flourish"), any(AnimationModifier.class), any(Runnable.class))).thenReturn(true);

        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");

        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.empty(),
                (r, loc) -> Optional.empty(),
                limbId -> "custom_player".equals(limbId) ? Optional.of(customLimb) : Optional.empty(),
                (r, p) -> Optional.of(playerTracker),
                () -> true
        );

        service.playPlayerAnimation(
                player,
                "steve",
                "custom_player:arrive_flourish",
                AetheryteModelService.PlayerAnimationMode.PLAY_ONCE
        );

        final ArgumentCaptor<AnimationModifier> modifierCaptor = ArgumentCaptor.forClass(AnimationModifier.class);
        final ArgumentCaptor<Runnable> onEndCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(playerTracker).animate(eq("arrive_flourish"), modifierCaptor.capture(), onEndCaptor.capture());
        assertThat(modifierCaptor.getValue().type()).isEqualTo(AnimationIterator.Type.PLAY_ONCE);
        verify(playerTracker, never()).close();

        onEndCaptor.getValue().run();
        verify(playerTracker).close();
    }

    @Test
    void playerAnimationNoOpsSafelyWhenBlankMissingOrAnimateReturnsFalse() {
        final ModelRenderer limbRenderer = mock(ModelRenderer.class);
        final ModelTracker failingTracker = mock(ModelTracker.class);
        when(failingTracker.animate(eq("missing_anim"), any(AnimationModifier.class))).thenReturn(false);

        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");

        final BetterModelAetheryteModelService service = new BetterModelAetheryteModelService(
                logger,
                id -> Optional.empty(),
                (r, loc) -> Optional.empty(),
                limbId -> "steve".equals(limbId) ? Optional.of(limbRenderer) : Optional.empty(),
                (r, p) -> Optional.of(failingTracker),
                () -> true
        );

        service.playPlayerAnimation(player, "steve", "", AetheryteModelService.PlayerAnimationMode.HOLD_ON_LAST);
        service.playPlayerAnimation(player, "unknown_limb", "cast", AetheryteModelService.PlayerAnimationMode.HOLD_ON_LAST);
        verify(failingTracker, never()).close();

        service.playPlayerAnimation(player, "steve", "missing_anim", AetheryteModelService.PlayerAnimationMode.HOLD_ON_LAST);
        verify(failingTracker).close();
    }
}
