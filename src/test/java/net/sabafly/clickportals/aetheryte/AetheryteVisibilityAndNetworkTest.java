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
package net.sabafly.clickportals.aetheryte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.config.CastSection;
import net.sabafly.clickportals.config.ConfigManager;
import net.sabafly.clickportals.config.DistanceSection;
import net.sabafly.clickportals.config.PluginConfig;
import net.sabafly.clickportals.config.PluginConfig.HologramSection;
import net.sabafly.clickportals.config.PluginConfig.LanguageSection;
import net.sabafly.clickportals.config.PluginConfig.LimitsSection;
import net.sabafly.clickportals.config.PluginConfig.WarpSection;
import net.sabafly.clickportals.config.VisibilityCostSection;
import net.sabafly.clickportals.cost.CostBundle;
import net.sabafly.clickportals.cost.CostBundle.QuoteDelta;
import net.sabafly.clickportals.cost.CostEntry;
import net.sabafly.clickportals.cost.CostQuote;
import net.sabafly.clickportals.cost.CostType;
import net.sabafly.clickportals.dialog.WarpDestinationQuery;
import net.sabafly.clickportals.economy.EconomyProvider;
import net.sabafly.clickportals.message.Messages;
import net.sabafly.clickportals.permission.Permissions;
import net.sabafly.clickportals.route.DimensionRouter;
import net.sabafly.clickportals.route.DimensionRoutesConfig;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation;
import net.sabafly.clickportals.service.AetheryteService.WarpEvaluation.DenyReason;
import net.sabafly.clickportals.service.ConfiguredWarpCostEvaluator;
import net.sabafly.clickportals.variation.AetheryteVariation;
import net.sabafly.clickportals.variation.AetheryteVariation.PlacementSection;
import net.sabafly.clickportals.variation.AetheryteVariation.WarpOverrideSection;
import net.sabafly.clickportals.variation.VariationLoader;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class AetheryteVisibilityAndNetworkTest {

    @TempDir
    Path tempDir;

    private static final UUID WORLD_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVITED_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID STRANGER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void visibilityAccessAndInviteeManagementWorkAsExpected() {
        final Aetheryte publicOne = aetheryte("Public", "default", AetheryteVisibility.PUBLIC, Set.of());
        assertThat(publicOne.isAccessibleTo(OWNER_ID)).isTrue();
        assertThat(publicOne.isAccessibleTo(STRANGER_ID)).isTrue();

        final Aetheryte inviteOnly = publicOne
                .withVisibility(AetheryteVisibility.INVITE_ONLY)
                .addInvitee(INVITED_ID);
        assertThat(inviteOnly.isAccessibleTo(OWNER_ID)).isTrue();
        assertThat(inviteOnly.isAccessibleTo(INVITED_ID)).isTrue();
        assertThat(inviteOnly.isAccessibleTo(STRANGER_ID)).isFalse();

        final Aetheryte afterRemoval = inviteOnly.removeInvitee(INVITED_ID);
        assertThat(afterRemoval.isAccessibleTo(INVITED_ID)).isFalse();
        assertThat(inviteOnly.isAccessibleTo(INVITED_ID)).isTrue();

        final Aetheryte ownerOnly = inviteOnly.withVisibility(AetheryteVisibility.OWNER_ONLY);
        assertThat(ownerOnly.isAccessibleTo(OWNER_ID)).isTrue();
        assertThat(ownerOnly.isAccessibleTo(INVITED_ID)).isFalse();
        assertThat(ownerOnly.isAccessibleTo(STRANGER_ID)).isFalse();

        final Player stranger = mockPlayer(STRANGER_ID, "Stranger");
        assertThat(Permissions.canAccessVisibility(stranger, STRANGER_ID, ownerOnly)).isFalse();
        when(stranger.hasPermission(Permissions.BYPASS_VISIBILITY)).thenReturn(true);
        assertThat(Permissions.canAccessVisibility(stranger, STRANGER_ID, ownerOnly)).isTrue();

        final Player owner = mockPlayer(OWNER_ID, "Owner");
        assertThat(Permissions.canManageVisibility(owner, OWNER_ID, ownerOnly)).isTrue();
        assertThat(Permissions.canManageVisibility(stranger, STRANGER_ID, ownerOnly)).isFalse();
        when(stranger.hasPermission(Permissions.ADMIN)).thenReturn(true);
        assertThat(Permissions.canManageVisibility(stranger, STRANGER_ID, ownerOnly)).isTrue();
    }

    @Test
    void networkRoutingRulesAllowSameNetworkAndWildcardWhileBlockingIsolatedNetworks() {
        final AetheryteVariation defVar = AetheryteVariation.defaultVariation();
        final AetheryteVariation urbanVar = variationWithNetwork("urban", "urban", List.of("urban"),
                VisibilityCostSection.inherit());
        final AetheryteVariation hubVar = variationWithNetwork("hub", "default", List.of("*"),
                VisibilityCostSection.inherit());

        assertThat(WarpDestinationQuery.canRouteBetweenVariations(defVar, defVar)).isTrue();
        assertThat(WarpDestinationQuery.canRouteBetweenVariations(defVar, urbanVar)).isFalse();
        assertThat(WarpDestinationQuery.canRouteBetweenVariations(urbanVar, defVar)).isFalse();
        assertThat(WarpDestinationQuery.canRouteBetweenVariations(urbanVar, urbanVar)).isTrue();
        assertThat(WarpDestinationQuery.canRouteBetweenVariations(hubVar, urbanVar)).isTrue();
        assertThat(WarpDestinationQuery.canRouteBetweenVariations(hubVar, defVar)).isTrue();
    }

    @Test
    void visibilityCostMultipliersScaleQuotesAndComputeDeltaChargesAndRefunds() {
        final VisibilityCostSection global = new VisibilityCostSection(1.0, 1.5, 2.0);
        final VisibilityCostSection partialOverride = new VisibilityCostSection(null, null, 3.0);
        final VisibilityCostSection resolved = partialOverride.resolve(global);

        assertThat(resolved.multiplierFor(AetheryteVisibility.PUBLIC)).isEqualTo(1.0);
        assertThat(resolved.multiplierFor(AetheryteVisibility.INVITE_ONLY)).isEqualTo(1.5);
        assertThat(resolved.multiplierFor(AetheryteVisibility.OWNER_ONLY)).isEqualTo(3.0);

        final CostBundle bundle = new CostBundle(List.of(
                CostEntry.of(CostType.VAULT, "100"),
                CostEntry.of(CostType.EXP_LEVELS, "10")
        ));
        final EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(any())).thenReturn(250.0);

        final Player player = mockPlayer(OWNER_ID, "Owner");
        when(player.getLevel()).thenReturn(20);

        final CostQuote publicQuote = bundle.quote(player, Map.of(), economy, resolved.multiplierFor(AetheryteVisibility.PUBLIC));
        final CostQuote inviteQuote = bundle.quote(player, Map.of(), economy, resolved.multiplierFor(AetheryteVisibility.INVITE_ONLY));
        final CostQuote ownerQuote = bundle.quote(player, Map.of(), economy, resolved.multiplierFor(AetheryteVisibility.OWNER_ONLY));

        assertThat(publicQuote.items().get(0).amount()).isEqualTo(100.0);
        assertThat(publicQuote.items().get(1).amount()).isEqualTo(10.0);
        assertThat(inviteQuote.items().get(0).amount()).isEqualTo(150.0);
        assertThat(inviteQuote.items().get(1).amount()).isEqualTo(15.0);
        assertThat(ownerQuote.items().get(0).amount()).isEqualTo(300.0);
        assertThat(ownerQuote.items().get(1).amount()).isEqualTo(30.0);

        // PUBLIC -> INVITE_ONLY: extra charge of 50 Vault and 5 levels, affordable with 250 / 20
        final QuoteDelta upgradeSmall = CostBundle.computeQuoteDelta(player, economy, publicQuote, inviteQuote);
        assertThat(upgradeSmall.refund().isFree()).isTrue();
        assertThat(upgradeSmall.additionalCharge().affordable()).isTrue();
        assertThat(upgradeSmall.additionalCharge().items()).hasSize(2);
        assertThat(upgradeSmall.additionalCharge().items().get(0).amount()).isEqualTo(50.0);
        assertThat(upgradeSmall.additionalCharge().items().get(1).amount()).isEqualTo(5.0);

        // Make player poorer and verify unaffordable upgrade check
        when(player.getLevel()).thenReturn(5);
        final QuoteDelta unaffordableUpgrade = CostBundle.computeQuoteDelta(player, economy, publicQuote, ownerQuote);
        assertThat(unaffordableUpgrade.additionalCharge().affordable()).isFalse();

        // OWNER_ONLY -> PUBLIC: refund of 200 Vault and 20 levels, no charge
        final QuoteDelta downgrade = CostBundle.computeQuoteDelta(player, economy, ownerQuote, publicQuote);
        assertThat(downgrade.additionalCharge().isFree()).isTrue();
        assertThat(downgrade.refund().items()).hasSize(2);
        assertThat(downgrade.refund().items().get(0).amount()).isEqualTo(200.0);
        assertThat(downgrade.refund().items().get(1).amount()).isEqualTo(20.0);
    }

    @Test
    void configuredWarpCostEvaluatorEnforcesVisibilityNetworkAndVisibilityMultiplier() {
        final Messages messages = Messages.defaults();
        final World world = mock(World.class);
        when(world.getUID()).thenReturn(WORLD_ID);
        when(world.getName()).thenReturn("world");

        final VisibilityCostSection globalVisCost = new VisibilityCostSection(1.0, 1.5, 2.0);
        final WarpSection warpSection = new WarpSection(
                DistanceSection.defaults(),
                List.of(CostEntry.of(CostType.VAULT, "100")),
                true,
                CastSection.defaults(),
                globalVisCost
        );
        final PluginConfig config = new PluginConfig(
                LanguageSection.defaults(),
                HologramSection.defaults(),
                LimitsSection.defaults(),
                warpSection,
                DimensionRoutesConfig.defaults()
        );

        final AetheryteVariation defVar = AetheryteVariation.defaultVariation();
        final AetheryteVariation urbanVar = variationWithNetwork("urban", "urban", List.of("urban"),
                VisibilityCostSection.inherit());
        final VariationRegistry registry = new VariationRegistry(
                Map.of(
                        Aetheryte.DEFAULT_VARIATION, defVar,
                        "urban", urbanVar
                ),
                LoggerFactory.getLogger(VariationRegistry.class)
        );

        final EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(any())).thenReturn(500.0);
        when(economy.has(any(), anyDouble())).thenAnswer(inv -> (double) inv.getArgument(1) <= 500.0);
        when(economy.format(anyDouble())).thenAnswer(inv -> {
            final double amt = inv.getArgument(0);
            return "$" + (long) amt;
        });

        final ConfiguredWarpCostEvaluator evaluator = new ConfiguredWarpCostEvaluator(
                () -> config,
                registry,
                () -> economy,
                () -> new DimensionRouter(DimensionRoutesConfig.defaults()),
                messages,
                id -> WORLD_ID.equals(id) ? world : null
        );

        final Player stranger = mockPlayer(STRANGER_ID, "Stranger");
        when(stranger.getWorld()).thenReturn(world);
        when(stranger.getLocation()).thenReturn(new Location(world, 0, 64, 0));

        final Aetheryte source = aetheryte("Source", Aetheryte.DEFAULT_VARIATION, AetheryteVisibility.PUBLIC, Set.of());
        final Aetheryte privateDest = new Aetheryte(
                UUID.randomUUID(), "Secret", WORLD_ID, "world", 50, 64, 0, OWNER_ID, 2L,
                Aetheryte.DEFAULT_VARIATION, "Owner", AetheryteVisibility.OWNER_ONLY, Set.of());
        final Aetheryte invitedDest = new Aetheryte(
                UUID.randomUUID(), "Invited", WORLD_ID, "world", 60, 64, 0, OWNER_ID, 3L,
                Aetheryte.DEFAULT_VARIATION, "Owner", AetheryteVisibility.INVITE_ONLY, Set.of(STRANGER_ID));
        final Aetheryte urbanDest = new Aetheryte(
                UUID.randomUUID(), "Urban", WORLD_ID, "world", 70, 64, 0, OWNER_ID, 4L,
                "urban", "Owner", AetheryteVisibility.PUBLIC, Set.of());

        final List<WarpEvaluation> batch = evaluator.evaluateBatch(
                stranger,
                source,
                List.of(privateDest, invitedDest, urbanDest)
        );
        assertThat(batch).hasSize(3);
        assertThat(batch.get(0).allowed()).isFalse();
        assertThat(batch.get(0).denyReasonCode()).isEqualTo(DenyReason.VISIBILITY_DENIED);

        // Invited player can access INVITE_ONLY and sees 1.5x visibility cost ($150 instead of $100)
        assertThat(batch.get(1).allowed()).isTrue();
        assertThat(PlainTextComponentSerializer.plainText().serialize(batch.get(1).costDisplay()))
                .contains("$150");

        // Cross-network from default -> urban is denied with NETWORK_MISMATCH
        assertThat(batch.get(2).allowed()).isFalse();
        assertThat(batch.get(2).denyReasonCode()).isEqualTo(DenyReason.NETWORK_MISMATCH);

        // Single evaluate matches evaluateBatch
        assertThat(evaluator.evaluate(stranger, source, privateDest).denyReasonCode())
                .isEqualTo(DenyReason.VISIBILITY_DENIED);
        assertThat(evaluator.evaluate(stranger, source, urbanDest).denyReasonCode())
                .isEqualTo(DenyReason.NETWORK_MISMATCH);
    }

    @Test
    void yamlConfigAndVariationLoaderLoadVisibilityCostAndNetworkFields() throws Exception {
        final Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                warp:
                  visibility-cost:
                    public-multiplier: 0.5
                    invite-only-multiplier: 1.25
                    owner-only-multiplier: 2.5
                """);
        final ConfigManager cm = new ConfigManager(configPath, LoggerFactory.getLogger(ConfigManager.class));
        final PluginConfig loadedConfig = cm.load(false);
        assertThat(loadedConfig.warp().visibilityCost().publicMultiplier()).isEqualTo(0.5);
        assertThat(loadedConfig.warp().visibilityCost().inviteOnlyMultiplier()).isEqualTo(1.25);
        assertThat(loadedConfig.warp().visibilityCost().ownerOnlyMultiplier()).isEqualTo(2.5);

        final Path varDir = tempDir.resolve("aetheryte");
        Files.createDirectories(varDir);
        Files.writeString(varDir.resolve("urban.yml"), """
                display-name: "<gold>Urban"
                placement:
                  visibility-cost:
                    owner-only-multiplier: 3.0
                warp:
                  network: "urban"
                  allowed-target-networks:
                    - "urban"
                """);
        final Map<String, AetheryteVariation> loadedVars = VariationLoader.loadDirectory(
                varDir, LoggerFactory.getLogger(VariationLoader.class));
        final AetheryteVariation urban = loadedVars.get("urban");
        assertThat(urban).isNotNull();
        if (urban == null) {
            throw new AssertionError("urban variation missing");
        }
        assertThat(urban.placement().visibilityCost().publicMultiplier()).isNull();
        assertThat(urban.placement().visibilityCost().ownerOnlyMultiplier()).isEqualTo(3.0);
        assertThat(urban.warp().network()).isEqualTo("urban");
        assertThat(urban.warp().allowedTargetNetworks()).containsExactly("urban");
    }

    private static Player mockPlayer(final UUID id, final String name) {
        final Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        when(p.locale()).thenReturn(Locale.ENGLISH);
        return p;
    }

    private static Aetheryte aetheryte(
            final String name,
            final String variation,
            final AetheryteVisibility visibility,
            final Set<UUID> invited
    ) {
        return new Aetheryte(
                UUID.randomUUID(),
                name,
                WORLD_ID,
                "world",
                0,
                64,
                0,
                OWNER_ID,
                1L,
                variation,
                "Owner",
                visibility,
                invited
        );
    }

    private static AetheryteVariation variationWithNetwork(
            final String id,
            final String network,
            final List<String> allowedTargets,
            final VisibilityCostSection visCost
    ) {
        final AetheryteVariation def = AetheryteVariation.defaultVariation();
        return new AetheryteVariation(
                "<white>" + id + "</white>",
                def.item(),
                def.recipe(),
                def.model(),
                def.hitbox(),
                def.teleport(),
                new PlacementSection(
                        def.placement().clearance(),
                        def.placement().cost(),
                        def.placement().minDistance(),
                        visCost
                ),
                def.limit(),
                new WarpOverrideSection(null, null, null, network, allowedTargets),
                def.bettermodel(),
                def.sounds()
        );
    }
}
