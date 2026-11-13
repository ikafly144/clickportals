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
package net.sabafly.clickportals.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.sabafly.clickportals.aetheryte.Aetheryte;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry;
import net.sabafly.clickportals.config.DistanceSection;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.config.PluginConfig.WarpSection;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.CostType;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.route.DimensionRouter;
import net.sabafly.clickportals.route.DimensionRoutesConfig;
import net.sabafly.clickportals.route.RoutePlan;
import net.sabafly.clickportals.service.AetheryteService;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.service.ConfiguredWarpCostEvaluator;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.VariationRegistry;
import net.sabafly.clickportals.visual.NoopHologramService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Automated scale and N+1 regression benchmarks for Issue #20 (N=1,000 and N=5,000 Aetherytes).
 */
class Issue20PerformanceScaleTest {

    private static final class CountingEconomyProvider implements EconomyProvider {
        private final AtomicInteger balanceCalls = new AtomicInteger();
        private final double balance;

        CountingEconomyProvider(final double balance) {
            this.balance = balance;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public double getBalance(final Player player) {
            balanceCalls.incrementAndGet();
            return balance;
        }

        @Override
        public boolean has(final Player player, final double amount) {
            return balance >= amount;
        }

        @Override
        public boolean withdraw(final Player player, final double amount) {
            return true;
        }

        @Override
        public boolean deposit(final Player player, final double amount) {
            return true;
        }

        @Override
        public String format(final double amount) {
            final long cents = Math.round(amount * 100.0);
            final long whole = cents / 100L;
            final long frac = Math.abs(cents % 100L);
            return "$" + whole + (frac < 10 ? ".0" : ".") + frac;
        }

        int balanceCallCount() {
            return balanceCalls.get();
        }

        void resetBalanceCalls() {
            balanceCalls.set(0);
        }
    }

    private record Fixture(
            AetheryteRegistry registry,
            AetheryteService service,
            Messages messages,
            CountingEconomyProvider economy,
            DimensionRouter router,
            Player player,
            Aetheryte source,
            UUID overworldId,
            UUID netherId,
            UUID ownerId
    ) {}

    private Fixture createScaleFixture(final int count) {
        final Plugin plugin = mock(Plugin.class);
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger("Issue20ScaleTest"));

        final UUID overworldId = UUID.randomUUID();
        final UUID netherId = UUID.randomUUID();
        final UUID ownerId = UUID.randomUUID();

        final World overworld = mock(World.class);
        when(overworld.getUID()).thenReturn(overworldId);
        when(overworld.getName()).thenReturn("world");
        when(overworld.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));

        final World nether = mock(World.class);
        when(nether.getUID()).thenReturn(netherId);
        when(nether.getName()).thenReturn("world_nether");
        when(nether.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));

        final PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getStorageContents()).thenReturn(new org.bukkit.inventory.ItemStack[36]);

        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.getWorld()).thenReturn(overworld);
        when(player.getLocation()).thenReturn(new Location(overworld, 0.0, 64.0, 0.0));
        when(player.getSendViewDistance()).thenReturn(10);
        when(player.locale()).thenReturn(Locale.ENGLISH);
        when(player.hasPermission(Permissions.USE)).thenReturn(true);
        when(player.calculateTotalExperiencePoints()).thenReturn(5000);
        when(player.getLevel()).thenReturn(50);
        when(player.getHealth()).thenReturn(20.0);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.getInventory()).thenReturn(inventory);

        final VariationRegistry variationRegistry = mock(VariationRegistry.class);
        when(variationRegistry.getOrDefault(any())).thenReturn(AetheryteVariation.defaultVariation());

        final Messages messages = Messages.defaults();
        final CountingEconomyProvider economy = new CountingEconomyProvider(10_000.0);

        final WarpSection warpSection = new WarpSection(
                new DistanceSection(0.0, -1.0),
                List.of(CostEntry.of(CostType.VAULT, "10 + $block_distance * 0.05")),
                true
        );
        final PluginConfig config = new PluginConfig(
                PluginConfig.LanguageSection.defaults(),
                PluginConfig.HologramSection.defaults(),
                PluginConfig.LimitsSection.defaults(),
                warpSection,
                DimensionRoutesConfig.defaults()
        );

        final DimensionRouter router = mock(DimensionRouter.class);
        when(router.findRoute(any(), any(), any())).thenAnswer(inv -> {
            final World src = inv.getArgument(0);
            final World dst = inv.getArgument(1);
            if (src.getUID().equals(overworldId) && dst.getUID().equals(netherId)) {
                return Optional.of(new RoutePlan(
                        List.of("world", "world_nether"),
                        50.0,
                        List.of(CostEntry.of(CostType.VAULT, "$amount")),
                        new Location(nether, 0.0, 64.0, 0.0)
                ));
            }
            return Optional.empty();
        });

        final ConfiguredWarpCostEvaluator evaluator = new ConfiguredWarpCostEvaluator(
                () -> config,
                variationRegistry,
                () -> economy,
                () -> router,
                messages,
                id -> id.equals(overworldId) ? overworld : (id.equals(netherId) ? nether : null)
        );

        final AetheryteRegistry registry = new AetheryteRegistry();
        final Aetheryte source = new Aetheryte(
                UUID.randomUUID(),
                "Hub",
                overworldId,
                "world",
                0,
                64,
                0,
                ownerId,
                System.currentTimeMillis()
        );
        registry.register(overworld, source);

        for (int i = 1; i < count; i++) {
            final boolean isNether = (i % 10 == 9);
            final World targetWorld = isNether ? nether : overworld;
            final UUID wId = isNether ? netherId : overworldId;
            final String wName = isNether ? "world_nether" : "world";
            // Spread coordinates deterministically across chunks (~48 blocks spacing), avoiding hub (0, 64, 0)
            final int gridX = ((i % 75) - 37) * 48;
            int gridZ = ((i / 75) - 37) * 48;
            if (!isNether && gridX == 0 && gridZ == 0) {
                gridZ = 16;
            }
            registry.register(targetWorld, new Aetheryte(
                    UUID.randomUUID(),
                    "Point-" + i,
                    wId,
                    wName,
                    gridX,
                    64,
                    gridZ,
                    ownerId,
                    System.currentTimeMillis() + i
            ));
        }

        final AetheryteService service = new AetheryteService(
                plugin,
                registry,
                variationRegistry,
                NoopHologramService.INSTANCE,
                messages,
                () -> -1,
                () -> 0,
                p -> 0L,
                (p, t) -> {},
                evaluator
        );
        service.setEconomyProvider(economy);

        return new Fixture(registry, service, messages, economy, router, player, source, overworldId, netherId, ownerId);
    }

    private List<Component> formatDialogButtons(final Fixture f, final List<Aetheryte> destinations) {
        final List<WarpEvaluation> evaluations = f.service().evaluateWarps(f.player(), f.source(), destinations);
        final Locale playerLocale = f.messages().locale(f.player());
        final List<Component> labels = new ArrayList<>(destinations.size());
        for (int i = 0; i < destinations.size(); i++) {
            final WarpEvaluation eval = evaluations.get(i);
            if (eval.denyReasonCode() == WarpEvaluation.DenyReason.NO_ROUTE) {
                continue;
            }
            final Aetheryte dest = destinations.get(i);
            labels.add(eval.allowed()
                    ? f.messages().warpDestinationLabel(playerLocale, dest, eval.costDisplay())
                    : f.messages().warpDestinationUnavailableLabel(playerLocale, dest, eval.costDisplay()));
        }
        return labels;
    }

    @Test
    void openWarpDialogScaleCallsVaultGetBalanceOnceAcross1000Destinations() {
        final Fixture f = createScaleFixture(1_000);
        final List<Aetheryte> destinations = f.registry().getAll().stream()
                .filter(a -> !a.id().equals(f.source().id()))
                .toList();

        // Warmup JIT and caches
        for (int w = 0; w < 3; w++) {
            formatDialogButtons(f, destinations);
        }
        f.economy().resetBalanceCalls();

        final long startNs = System.nanoTime();
        final List<Component> labels = formatDialogButtons(f, destinations);
        final long elapsedNs = System.nanoTime() - startNs;

        assertThat(labels).hasSize(999);
        assertThat(f.economy().balanceCallCount()).isEqualTo(1);
        verify(f.router(), times(4)).findRoute(any(), any(), any()); // 1 per pass across warmup (3) + measured (1)
        System.out.printf(Locale.US,
                "[Issue20 Benchmark N=1000] dialog evaluate+format=%.2f ms, Vault getBalance calls=%d%n",
                elapsedNs / 1_000_000.0, f.economy().balanceCallCount());
    }

    private record Scale5000Result(long getAllNs, long radiusNs, double evalMs, int visibleCount) {
    }

    private Scale5000Result runAndAssertScale5000Invariants() {
        final Fixture f = createScaleFixture(5_000);
        final List<Aetheryte> destinations = f.registry().getAll().stream()
                .filter(a -> !a.id().equals(f.source().id()))
                .toList();

        // Warmup JIT and message/expression caches
        for (int w = 0; w < 5; w++) {
            assertThat(f.registry().getAll()).hasSize(5_000);
            assertThat(f.registry().getByWorld(f.overworldId())).hasSize(4_500);
            f.registry().countByOwner(f.ownerId());
            f.registry().findWithinDistance(f.overworldId(), 0, 64, 0, 128.0);
            AetheryteService.collectVisibleAetherytes(f.registry(), f.player(), f.overworldId());
            formatDialogButtons(f, destinations);
        }

        // Verify cached reference returns and O(1) queries
        final long tGetAllStart = System.nanoTime();
        final List<Aetheryte> allA = f.registry().getAll();
        final List<Aetheryte> allB = f.registry().getAll();
        final List<Aetheryte> worldA = f.registry().getByWorld(f.overworldId());
        final List<Aetheryte> worldB = f.registry().getByWorld(f.overworldId());
        final int owned = f.registry().countByOwner(f.ownerId());
        final long tGetAllNs = System.nanoTime() - tGetAllStart;

        assertThat(allA).isSameAs(allB).hasSize(5_000);
        assertThat(worldA).isSameAs(worldB).hasSize(4_500);
        assertThat(owned).isEqualTo(5_000);

        // Measure spatial radius lookup and view-distance visual collection
        final long tRadiusStart = System.nanoTime();
        final Optional<Aetheryte> nearby = f.registry().findWithinDistance(f.overworldId(), 0, 64, 0, 128.0);
        final List<Aetheryte> visible = AetheryteService.collectVisibleAetherytes(f.registry(), f.player(), f.overworldId());
        final long tRadiusNs = System.nanoTime() - tRadiusStart;

        assertThat(nearby).isPresent();
        assertThat(visible).isNotEmpty().hasSizeLessThan(500);

        // Measure 5,000-destination dialog batch evaluation + component formatting within 1 tick (50 ms)
        f.economy().resetBalanceCalls();
        final long tEvalStart = System.nanoTime();
        final List<Component> labels = formatDialogButtons(f, destinations);
        final long tEvalNs = System.nanoTime() - tEvalStart;
        final double evalMs = tEvalNs / 1_000_000.0;

        assertThat(labels).hasSize(4_999);
        assertThat(f.economy().balanceCallCount()).isEqualTo(1);

        System.out.printf(Locale.US,
                "[Issue20 Benchmark N=5000] cached lookups=%.4f ms, findWithinDistance+visible=%.3f ms (visible=%d), dialog evaluate+format=%.2f ms, Vault calls=%d%n",
                tGetAllNs / 1_000_000.0, tRadiusNs / 1_000_000.0, visible.size(), evalMs, f.economy().balanceCallCount());
        return new Scale5000Result(tGetAllNs, tRadiusNs, evalMs, visible.size());
    }

    @Test
    void scale5000RegistryCachesReferencesAndCollapsesBatchQueries() {
        runAndAssertScale5000Invariants();
    }

    @Test
    @Tag("performance")
    void scale5000RegistryAndBatchEvaluationCompletesWithinOneTick() {
        final Scale5000Result result = runAndAssertScale5000Invariants();
        assertThat(result.getAllNs()).isLessThan(1_000_000L); // < 1 ms
        assertThat(result.radiusNs()).isLessThan(10_000_000L); // < 10 ms
        assertThat(result.evalMs()).isLessThan(50.0);
    }
}
