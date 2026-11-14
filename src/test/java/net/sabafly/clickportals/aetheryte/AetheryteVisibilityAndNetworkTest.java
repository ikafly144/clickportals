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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.sabafly.clickportals.aetheryte.AetheryteRegistry.RegistrationOutcome;
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
import net.sabafly.clickportals.variation.AetheryteVariation.NetworkCostSection;
import net.sabafly.clickportals.variation.AetheryteVariation.NetworkLimitSection;
import net.sabafly.clickportals.variation.AetheryteVariation.NetworkSection;
import net.sabafly.clickportals.variation.AetheryteVariation.PlacementSection;
import net.sabafly.clickportals.variation.AetheryteVariation.VariationType;
import net.sabafly.clickportals.variation.AetheryteVariation.WarpOverrideSection;
import net.sabafly.clickportals.variation.VariationLoader;
import net.sabafly.clickportals.variation.VariationRegistry;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
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
    void strictRoutingAndNetworkAccessRulesEnforceIsolationAndPermissions() {
        final UUID netA = UUID.randomUUID();
        final UUID netB = UUID.randomUUID();
        final Aetheryte normalA = aetheryte("NormalA", "default", AetheryteVisibility.PUBLIC, Set.of());
        final Aetheryte normalB = aetheryte("NormalB", "default", AetheryteVisibility.PUBLIC, Set.of());
        final Aetheryte netAMember1 = aetheryteWithNetwork("NetA1", "net_var", netA);
        final Aetheryte netAMember2 = aetheryteWithNetwork("NetA2", "net_var", netA);
        final Aetheryte netBMember = aetheryteWithNetwork("NetB1", "net_var", netB);

        assertThat(WarpDestinationQuery.canRouteBetweenAetherytes(normalA, normalB)).isTrue();
        assertThat(WarpDestinationQuery.canRouteBetweenAetherytes(normalA, netAMember1)).isFalse();
        assertThat(WarpDestinationQuery.canRouteBetweenAetherytes(netAMember1, normalA)).isFalse();
        assertThat(WarpDestinationQuery.canRouteBetweenAetherytes(netAMember1, netAMember2)).isTrue();
        assertThat(WarpDestinationQuery.canRouteBetweenAetherytes(netAMember1, netBMember)).isFalse();

        final AetheryteNetwork network = new AetheryteNetwork(
                netA, "AlphaNet", WORLD_ID, "world", OWNER_ID, "Owner", 1L,
                AetheryteVisibility.INVITE_ONLY, Set.of(INVITED_ID));
        final Player owner = mockPlayer(OWNER_ID, "Owner");
        final Player invited = mockPlayer(INVITED_ID, "Invited");
        final Player stranger = mockPlayer(STRANGER_ID, "Stranger");

        assertThat(network.canJoin(OWNER_ID, false)).isTrue();
        assertThat(network.canJoin(INVITED_ID, false)).isTrue();
        assertThat(network.canJoin(STRANGER_ID, false)).isFalse();
        assertThat(network.canJoin(STRANGER_ID, true)).isTrue();

        assertThat(Permissions.canAccessVisibility(owner, OWNER_ID, network)).isTrue();
        assertThat(Permissions.canAccessVisibility(invited, INVITED_ID, network)).isTrue();
        assertThat(Permissions.canAccessVisibility(stranger, STRANGER_ID, network)).isFalse();
        when(stranger.hasPermission(Permissions.BYPASS_VISIBILITY)).thenReturn(true);
        assertThat(Permissions.canAccessVisibility(stranger, STRANGER_ID, network)).isTrue();

        assertThat(Permissions.canManageNetwork(owner, OWNER_ID, network)).isTrue();
        assertThat(Permissions.canManageNetwork(invited, INVITED_ID, network)).isFalse();
        when(stranger.hasPermission(Permissions.ADMIN)).thenReturn(true);
        assertThat(Permissions.canManageNetwork(stranger, STRANGER_ID, network)).isTrue();
    }

    @Test
    void visibilityCostListsSupportNullFallbackAndExplicitEmptyFree() {
        final List<CostEntry> baseCost = List.of(CostEntry.of(CostType.VAULT, "100"));
        final List<CostEntry> ownerCost = List.of(CostEntry.of(CostType.VAULT, "500"), CostEntry.of(CostType.EXP_LEVELS, "5"));
        final VisibilityCostSection override = new VisibilityCostSection(null, List.of(), ownerCost);
        final VisibilityCostSection fallback = new VisibilityCostSection(
                List.of(CostEntry.of(CostType.VAULT, "25")),
                List.of(CostEntry.of(CostType.VAULT, "200")),
                null
        );

        final VisibilityCostSection resolved = override.resolve(fallback);
        assertThat(resolved.costFor(AetheryteVisibility.PUBLIC)).containsExactly(CostEntry.of(CostType.VAULT, "25"));
        // Explicit [] (empty list) is preserved and does NOT inherit fallback or base cost
        assertThat(resolved.costFor(AetheryteVisibility.INVITE_ONLY)).isNotNull().isEmpty();
        assertThat(resolved.costFor(AetheryteVisibility.OWNER_ONLY)).isEqualTo(ownerCost);

        // Standalone visibility cost with null PUBLIC -> falls back to caller's base cost
        final List<CostEntry> effectivePublic = override.costFor(AetheryteVisibility.PUBLIC) != null
                ? override.costFor(AetheryteVisibility.PUBLIC)
                : baseCost;
        assertThat(effectivePublic).isEqualTo(baseCost);

        // Full target visibility quote always reflects target tier directly
        final EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(any())).thenReturn(1000.0);
        final Player player = mockPlayer(OWNER_ID, "Owner");
        when(player.getLevel()).thenReturn(10);

        final CostQuote ownerQuote = new CostBundle(Objects.requireNonNull(resolved.costFor(AetheryteVisibility.OWNER_ONLY)))
                .quote(player, Map.of(), economy);
        assertThat(ownerQuote.items()).hasSize(2);
        assertThat(ownerQuote.items().get(0).amount()).isEqualTo(500.0);
        assertThat(ownerQuote.items().get(1).amount()).isEqualTo(5.0);

        final CostQuote freeInviteQuote = new CostBundle(Objects.requireNonNull(resolved.costFor(AetheryteVisibility.INVITE_ONLY)))
                .quote(player, Map.of(), economy);
        assertThat(freeInviteQuote.isFree()).isTrue();
    }

    @Test
    void configuredWarpCostEvaluatorResolvesPerVisibilityCostAndNetworkVisibility() {
        final Messages messages = Messages.defaults();
        final Map<NamespacedKey, Object> worldPdcStore = new HashMap<>();
        final World world = mockWorld(WORLD_ID, "world", worldPdcStore);

        final AetheryteRegistry aetheryteRegistry = new AetheryteRegistry();
        final UUID privateNetId = UUID.randomUUID();
        final UUID invitedNetId = UUID.randomUUID();
        final UUID otherNetId = UUID.randomUUID();

        aetheryteRegistry.registerNetwork(world, new AetheryteNetwork(
                privateNetId, "PrivateNet", WORLD_ID, "world", OWNER_ID, "Owner", 1L,
                AetheryteVisibility.OWNER_ONLY, Set.of()));
        aetheryteRegistry.registerNetwork(world, new AetheryteNetwork(
                invitedNetId, "InvitedNet", WORLD_ID, "world", OWNER_ID, "Owner", 2L,
                AetheryteVisibility.INVITE_ONLY, Set.of(STRANGER_ID)));
        aetheryteRegistry.registerNetwork(world, new AetheryteNetwork(
                otherNetId, "OtherNet", WORLD_ID, "world", OWNER_ID, "Owner", 3L,
                AetheryteVisibility.PUBLIC, Set.of()));

        final VisibilityCostSection globalVisCost = new VisibilityCostSection(
                List.of(CostEntry.of(CostType.VAULT, "20")),
                List.of(CostEntry.of(CostType.VAULT, "75")),
                null
        );
        final WarpSection warpSection = new WarpSection(
                DistanceSection.defaults(),
                List.of(CostEntry.of(CostType.VAULT, "40")),
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

        // Source variation overrides PUBLIC as free ([]), inherits INVITE_ONLY (null -> falls back to var base null -> global visibility-cost 75),
        // and overrides OWNER_ONLY to 300
        final AetheryteVariation defVar = AetheryteVariation.defaultVariation();
        final AetheryteVariation netVar = networkVariation(
                "net_var",
                new VisibilityCostSection(List.of(), null, List.of(CostEntry.of(CostType.VAULT, "300"))),
                null,
                new NetworkSection(NetworkLimitSection.defaults(), NetworkCostSection.defaults())
        );
        // Variation with explicit base warp cost [VAULT 120] and null invite-only override -> falls back to var warp.cost (120), NOT global visibility-cost
        final AetheryteVariation baseFallbackVar = networkVariation(
                "base_var",
                new VisibilityCostSection(null, null, null),
                List.of(CostEntry.of(CostType.VAULT, "120")),
                NetworkSection.defaults()
        );

        final VariationRegistry variationRegistry = new VariationRegistry(
                Map.of(
                        Aetheryte.DEFAULT_VARIATION, defVar,
                        "net_var", netVar,
                        "base_var", baseFallbackVar
                ),
                LoggerFactory.getLogger(VariationRegistry.class)
        );

        final EconomyProvider economy = mock(EconomyProvider.class);
        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(any())).thenReturn(500.0);
        when(economy.has(any(), anyDouble())).thenAnswer(inv -> (double) inv.getArgument(1) <= 500.0);
        when(economy.format(anyDouble())).thenAnswer(inv -> "$" + (long) (double) inv.getArgument(0));

        final ConfiguredWarpCostEvaluator evaluator = new ConfiguredWarpCostEvaluator(
                () -> config,
                variationRegistry,
                () -> economy,
                () -> new DimensionRouter(DimensionRoutesConfig.defaults()),
                messages,
                id -> WORLD_ID.equals(id) ? world : null,
                aetheryteRegistry
        );

        final Player stranger = mockPlayer(STRANGER_ID, "Stranger");
        when(stranger.getWorld()).thenReturn(world);
        when(stranger.getLocation()).thenReturn(new Location(world, 0, 64, 0));

        final Aetheryte sourceInInvitedNet = new Aetheryte(
                UUID.randomUUID(), "SourceNet", WORLD_ID, "world", 0, 64, 0, OWNER_ID, 1L,
                "net_var", "Owner", AetheryteVisibility.PUBLIC, Set.of(), invitedNetId);
        // Stored PUBLIC on member, but its network is OWNER_ONLY -> network visibility wins for non-owner!
        final Aetheryte destInInvitedNet = new Aetheryte(
                UUID.randomUUID(), "InvitedMember", WORLD_ID, "world", 50, 64, 0, OWNER_ID, 2L,
                "net_var", "Owner", AetheryteVisibility.PUBLIC, Set.of(), invitedNetId);
        final Aetheryte destInOtherNet = new Aetheryte(
                UUID.randomUUID(), "OtherNetMember", WORLD_ID, "world", 60, 64, 0, OWNER_ID, 3L,
                "net_var", "Owner", AetheryteVisibility.PUBLIC, Set.of(), otherNetId);
        final Aetheryte normalDest = new Aetheryte(
                UUID.randomUUID(), "NormalDest", WORLD_ID, "world", 70, 64, 0, OWNER_ID, 4L,
                Aetheryte.DEFAULT_VARIATION, "Owner", AetheryteVisibility.INVITE_ONLY, Set.of(STRANGER_ID), null);

        // Same invitedNetId with global INVITE_ONLY cost $75 (since net_var had null for INVITE_ONLY and null base warp.cost)
        final List<WarpEvaluation> batch = evaluator.evaluateBatch(
                stranger,
                sourceInInvitedNet,
                List.of(destInInvitedNet, destInOtherNet, normalDest)
        );
        assertThat(batch).hasSize(3);
        assertThat(batch.get(0).allowed()).isTrue();
        assertThat(PlainTextComponentSerializer.plainText().serialize(batch.get(0).costDisplay()))
                .contains("$75");
        assertThat(batch.get(1).allowed()).isFalse();
        assertThat(batch.get(1).denyReasonCode()).isEqualTo(DenyReason.NETWORK_MISMATCH);
        assertThat(batch.get(2).allowed()).isFalse();
        assertThat(batch.get(2).denyReasonCode()).isEqualTo(DenyReason.NETWORK_MISMATCH);

        // Network visibility overrides member visibility: privateNet is OWNER_ONLY even though member says PUBLIC
        final Aetheryte privateNetSource = new Aetheryte(
                UUID.randomUUID(), "PrivateSource", WORLD_ID, "world", 10, 64, 0, OWNER_ID, 5L,
                "base_var", "Owner", AetheryteVisibility.PUBLIC, Set.of(), privateNetId);
        final Aetheryte privateNetDest = new Aetheryte(
                UUID.randomUUID(), "PrivateDest", WORLD_ID, "world", 20, 64, 0, OWNER_ID, 6L,
                "base_var", "Owner", AetheryteVisibility.PUBLIC, Set.of(), privateNetId);
        assertThat(evaluator.evaluate(stranger, privateNetSource, privateNetDest).denyReasonCode())
                .isEqualTo(DenyReason.VISIBILITY_DENIED);

        // Owner warping on base_var falls back to variation warp.cost ($120), NOT global visibility-cost
        final Player owner = mockPlayer(OWNER_ID, "Owner");
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(new Location(world, 10, 64, 0));
        final WarpEvaluation ownerEval = evaluator.evaluate(owner, privateNetSource, privateNetDest);
        assertThat(ownerEval.allowed()).isTrue();
        assertThat(PlainTextComponentSerializer.plainText().serialize(ownerEval.costDisplay()))
                .contains("$120");
    }

    @Test
    void registryTryRegisterInNetworkEnforcesLimitsDistanceAndDeletesEmptyNetworks() {
        final Map<NamespacedKey, Object> worldPdcStore = new HashMap<>();
        final World world = mockWorld(WORLD_ID, "world", worldPdcStore);
        final AetheryteRegistry registry = new AetheryteRegistry();

        final UUID net1Id = UUID.randomUUID();
        final AetheryteNetwork net1 = new AetheryteNetwork(
                net1Id, "Net1", WORLD_ID, "world", OWNER_ID, "Owner", 1L, AetheryteVisibility.PUBLIC, Set.of());
        final Aetheryte node1 = new Aetheryte(
                UUID.randomUUID(), "Node1", WORLD_ID, "world", 0, 64, 0, OWNER_ID, 1L,
                "net_var", "Owner", AetheryteVisibility.PUBLIC, Set.of(), net1Id);

        assertThat(registry.tryRegisterInNetwork(world, node1, net1, -1, -1, 0.0, 1, 1, 2, 100.0))
                .isEqualTo(RegistrationOutcome.REGISTERED);
        assertThat(registry.countNetworksByOwner(OWNER_ID)).isEqualTo(1);
        assertThat(registry.countNetworksByOwnerAndVariation(OWNER_ID, "net_var")).isEqualTo(1);

        // Creating a second network fails global limit when maxGlobalNetworks = 1
        final UUID net2Id = UUID.randomUUID();
        final AetheryteNetwork net2 = new AetheryteNetwork(
                net2Id, "Net2", WORLD_ID, "world", OWNER_ID, "Owner", 2L, AetheryteVisibility.PUBLIC, Set.of());
        final Aetheryte otherPlayerNode = new Aetheryte(
                UUID.randomUUID(), "Other", WORLD_ID, "world", 200, 64, 0, OWNER_ID, 2L,
                "net_var", "Owner", AetheryteVisibility.PUBLIC, Set.of(), net2Id);
        assertThat(registry.tryRegisterInNetwork(world, otherPlayerNode, net2, -1, -1, 0.0, 1, -1, 2, 100.0))
                .isEqualTo(RegistrationOutcome.GLOBAL_NETWORK_LIMIT_REACHED);
        assertThat(registry.tryRegisterInNetwork(world, otherPlayerNode, net2, -1, -1, 0.0, -1, 1, 2, 100.0))
                .isEqualTo(RegistrationOutcome.VARIATION_NETWORK_LIMIT_REACHED);

        // Joining beyond max-distance fails with NETWORK_TOO_FAR
        final Aetheryte farNode = new Aetheryte(
                UUID.randomUUID(), "FarNode", WORLD_ID, "world", 500, 64, 0, OWNER_ID, 3L,
                "net_var", "Owner", AetheryteVisibility.PUBLIC, Set.of(), net1Id);
        assertThat(registry.tryRegisterInNetwork(world, farNode, null, -1, -1, 0.0, 5, 5, 2, 100.0))
                .isEqualTo(RegistrationOutcome.NETWORK_TOO_FAR);

        // Joining within max-distance succeeds; 3rd member hits NETWORK_AETHERYTE_LIMIT_REACHED
        final Aetheryte node2 = new Aetheryte(
                UUID.randomUUID(), "Node2", WORLD_ID, "world", 50, 64, 0, INVITED_ID, 4L,
                "net_var", "Invited", AetheryteVisibility.PUBLIC, Set.of(), net1Id);
        assertThat(registry.tryRegisterInNetwork(world, node2, null, -1, -1, 0.0, 5, 5, 2, 100.0))
                .isEqualTo(RegistrationOutcome.REGISTERED);

        final Aetheryte node3 = new Aetheryte(
                UUID.randomUUID(), "Node3", WORLD_ID, "world", 60, 64, 0, INVITED_ID, 5L,
                "net_var", "Invited", AetheryteVisibility.PUBLIC, Set.of(), net1Id);
        assertThat(registry.tryRegisterInNetwork(world, node3, null, -1, -1, 0.0, 5, 5, 2, 100.0))
                .isEqualTo(RegistrationOutcome.NETWORK_AETHERYTE_LIMIT_REACHED);

        // Unregistering 1st node keeps network alive; unregistering last node auto-deletes empty network
        registry.unregister(world, node1.id());
        assertThat(registry.getNetworkById(net1Id)).isPresent();
        registry.unregister(world, node2.id());
        assertThat(registry.getNetworkById(net1Id)).isEmpty();
        assertThat(registry.countNetworksByOwner(OWNER_ID)).isZero();
    }

    @Test
    void yamlConfigAndVariationLoaderRoundTripVisibilityCostAndNetworkSchema() throws Exception {
        final Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                limits:
                  max-networks-per-player: 5
                warp:
                  visibility-cost:
                    public: []
                    invite-only: null
                    owner-only:
                      - type: vault
                        amount: "250"
                        required: true
                """);
        final ConfigManager cm = new ConfigManager(configPath, LoggerFactory.getLogger(ConfigManager.class));
        final PluginConfig loadedConfig = cm.load(true);
        assertThat(loadedConfig.limits().maxNetworksPerPlayer()).isEqualTo(5);
        assertThat(loadedConfig.warp().visibilityCost().publicCost()).isNotNull().isEmpty();
        assertThat(loadedConfig.warp().visibilityCost().inviteOnlyCost()).isNull();
        assertThat(loadedConfig.warp().visibilityCost().ownerOnlyCost())
                .containsExactly(CostEntry.of(CostType.VAULT, "250"));

        final String savedConfig = Files.readString(configPath);
        assertThat(savedConfig).doesNotContain(VisibilityCostSection.NULL_SENTINEL);
        assertThat(savedConfig).contains("invite-only: null");

        // Second reload does not emit sentinel or corrupt null overrides
        final ConfigManager reloadedCm = new ConfigManager(configPath, LoggerFactory.getLogger(ConfigManager.class));
        final PluginConfig reloadedConfig = reloadedCm.load(true);
        assertThat(reloadedConfig.warp().visibilityCost().publicCost()).isEmpty();
        assertThat(reloadedConfig.warp().visibilityCost().inviteOnlyCost()).isNull();

        final Path varDir = tempDir.resolve("aetheryte");
        Files.createDirectories(varDir);
        Files.writeString(varDir.resolve("urban.yml"), """
                display-name: "<gold>Urban"
                type: network
                placement:
                  visibility-cost:
                    public: []
                    invite-only: null
                    owner-only:
                      - type: vault
                        amount: "500"
                        required: true
                warp:
                  visibility-cost:
                    public: []
                network:
                  limit:
                    max-per-player: 3
                    max-aetheryte: 8
                    max-distance: 1500.0
                  cost:
                    create:
                      - type: vault
                        amount: "1000"
                        required: true
                    join:
                      - type: exp-levels
                        amount: "2"
                        required: true
                """);
        final Map<String, AetheryteVariation> loadedVars = VariationLoader.loadDirectory(
                varDir, LoggerFactory.getLogger(VariationLoader.class), true);
        final AetheryteVariation urban = loadedVars.get("urban");
        assertThat(urban).isNotNull();
        if (urban == null) {
            throw new AssertionError("urban variation missing");
        }
        assertThat(urban.type()).isEqualTo(VariationType.NETWORK);
        assertThat(urban.isNetwork()).isTrue();
        assertThat(urban.placement().visibilityCost().publicCost()).isNotNull().isEmpty();
        assertThat(urban.placement().visibilityCost().inviteOnlyCost()).isNull();
        assertThat(urban.placement().visibilityCost().ownerOnlyCost())
                .containsExactly(CostEntry.of(CostType.VAULT, "500"));
        assertThat(urban.warp().visibilityCost().publicCost()).isNotNull().isEmpty();
        assertThat(urban.warp().visibilityCost().ownerOnlyCost()).isNull();
        assertThat(urban.network().limit().maxPerPlayerOrDefault()).isEqualTo(3);
        assertThat(urban.network().limit().maxAetheryteOrDefault()).isEqualTo(8);
        assertThat(urban.network().limit().maxDistanceOrDefault()).isEqualTo(1500.0);
        assertThat(urban.network().cost().create()).containsExactly(CostEntry.of(CostType.VAULT, "1000"));
        assertThat(urban.network().cost().join()).containsExactly(CostEntry.of(CostType.EXP_LEVELS, "2"));

        final String savedUrban = Files.readString(varDir.resolve("urban.yml"));
        assertThat(savedUrban).doesNotContain(VisibilityCostSection.NULL_SENTINEL);
        assertThat(savedUrban).contains("type: network");
    }

    private static World mockWorld(final UUID id, final String name, final Map<NamespacedKey, Object> pdcStore) {
        final World world = mock(World.class);
        when(world.getUID()).thenReturn(id);
        when(world.getName()).thenReturn(name);
        final PersistentDataContainer pdc = AetheryteDataTypeTest.createInMemoryContainer(pdcStore);
        when(world.getPersistentDataContainer()).thenReturn(pdc);
        return world;
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

    private static Aetheryte aetheryteWithNetwork(
            final String name,
            final String variation,
            final UUID networkId
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
                AetheryteVisibility.PUBLIC,
                Set.of(),
                networkId
        );
    }

    private static AetheryteVariation networkVariation(
            final String id,
            final VisibilityCostSection warpVisCost,
            final @Nullable List<CostEntry> warpCost,
            final NetworkSection networkSection
    ) {
        final AetheryteVariation def = AetheryteVariation.defaultVariation();
        return new AetheryteVariation(
                "<white>" + id + "</white>",
                VariationType.NETWORK,
                def.item(),
                def.recipe(),
                def.model(),
                def.hitbox(),
                def.teleport(),
                new PlacementSection(
                        def.placement().clearance(),
                        def.placement().cost(),
                        def.placement().minDistance(),
                        VisibilityCostSection.inherit()
                ),
                def.limit(),
                new WarpOverrideSection(DistanceSection.inherit(), warpCost, CastSection.inherit(), warpVisCost),
                networkSection,
                def.bettermodel(),
                def.sounds()
        );
    }
}
