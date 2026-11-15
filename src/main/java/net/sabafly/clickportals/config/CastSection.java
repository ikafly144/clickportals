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
package net.sabafly.clickportals.config;

import java.lang.reflect.Type;
import java.util.Collections;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

/**
 * Warp casting (cast time and player animation) configuration (#65).
 */
@ConfigSerializable
public record CastSection(
        @Setting("ticks")
        @Comment("Cast time in ticks before warping (0 or less for instant warp; in variations: -1 to inherit global)")
        @Nullable Integer ticks,

        @Setting("player-model")
        @Comment("BetterModel player limb model name used for player animations (e.g. steve; in variations: null to inherit global)")
        @Nullable String playerModel,

        @Setting("cast-animation")
        @Comment("BetterModel player animation played during casting with HOLD_ON_LAST (empty to disable; in variations: null to inherit global)")
        @Nullable String castAnimation,

        @Setting("arrive-animation")
        @Comment("BetterModel player animation played after teleport arrival with PLAY_ONCE (empty to disable; in variations: null to inherit global)")
        @Nullable String arriveAnimation,

        @Setting("immobilize")
        @Comment("Whether to prevent the player from moving via PacketEvents while casting (head rotation remains allowed; in variations: null to inherit global)")
        @Nullable Boolean immobilize,

        @Setting("cancel")
        @Comment("Cancellation conditions while casting")
        @Nullable CastCancelSection cancel
) {

    /**
     * Backward-compatible constructor preserving 5-argument call sites (inherits {@code immobilize}).
     */
    public CastSection(
            final @Nullable Integer ticks,
            final @Nullable String playerModel,
            final @Nullable String castAnimation,
            final @Nullable String arriveAnimation,
            final @Nullable CastCancelSection cancel
    ) {
        this(ticks, playerModel, castAnimation, arriveAnimation, null, cancel);
    }

    /**
     * Concrete, non-null resolved cast settings after merging variation overrides with global defaults (#65, #67).
     */
    public record Resolved(
            int ticks,
            String playerModel,
            String castAnimation,
            String arriveAnimation,
            boolean immobilize,
            boolean cancelOnMove,
            boolean cancelOnDamage,
            boolean cancelOnInteract
    ) {
        /**
         * Backward-compatible constructor preserving 6-argument call sites.
         */
        public Resolved(
                final int ticks,
                final String playerModel,
                final String castAnimation,
                final String arriveAnimation,
                final boolean cancelOnMove,
                final boolean cancelOnDamage
        ) {
            this(ticks, playerModel, castAnimation, arriveAnimation, true, cancelOnMove, cancelOnDamage, true);
        }

        /**
         * Backward-compatible constructor preserving 7-argument call sites.
         */
        public Resolved(
                final int ticks,
                final String playerModel,
                final String castAnimation,
                final String arriveAnimation,
                final boolean immobilize,
                final boolean cancelOnMove,
                final boolean cancelOnDamage
        ) {
            this(ticks, playerModel, castAnimation, arriveAnimation, immobilize, cancelOnMove, cancelOnDamage, true);
        }

        /**
         * Returns whether casting is enabled ({@code ticks > 0}).
         */
        public boolean hasCastTime() {
            return ticks > 0;
        }
    }

    /**
     * Cast cancellation conditions (#65).
     */
    @ConfigSerializable
    public record CastCancelSection(
            @Setting("on-move")
            @Comment("Cancel casting when the player moves (head rotation is allowed; in variations: null to inherit global)")
            @Nullable Boolean onMove,

            @Setting("on-damage")
            @Comment("Cancel casting when the player takes damage (in variations: null to inherit global)")
            @Nullable Boolean onDamage,

            @Setting("on-interact")
            @Comment("Cancel casting when breaking/placing blocks, attacking entities, or interacting with other blocks/entities (in variations: null to inherit global)")
            @Nullable Boolean onInteract
    ) {
        /**
         * Backward-compatible constructor preserving 2-argument call sites (inherits {@code onInteract}).
         */
        public CastCancelSection(
                final @Nullable Boolean onMove,
                final @Nullable Boolean onDamage
        ) {
            this(onMove, onDamage, null);
        }

        /**
         * Returns the global default cancellation settings.
         */
        public static CastCancelSection defaults() {
            return new CastCancelSection(true, true, true);
        }

        /**
         * Returns an inherit-all cancellation section for variations.
         */
        public static CastCancelSection inherit() {
            return new CastCancelSection(null, null, null);
        }

        /**
         * Returns {@code onMove} if configured, otherwise {@code fallback}.
         */
        public boolean onMoveOrDefault(final boolean fallback) {
            return onMove != null ? onMove : fallback;
        }

        /**
         * Returns {@code onDamage} if configured, otherwise {@code fallback}.
         */
        public boolean onDamageOrDefault(final boolean fallback) {
            return onDamage != null ? onDamage : fallback;
        }

        /**
         * Returns {@code onInteract} if configured, otherwise {@code fallback}.
         */
        public boolean onInteractOrDefault(final boolean fallback) {
            return onInteract != null ? onInteract : fallback;
        }
    }

    /**
     * Returns the global default cast section (0 ticks = instant warp, immobilize = true).
     */
    public static CastSection defaults() {
        return new CastSection(0, "steve", "", "", true, CastCancelSection.defaults());
    }

    /**
     * Returns an inherit-all cast section for variations.
     */
    public static CastSection inherit() {
        return new CastSection(-1, null, null, null, null, CastCancelSection.inherit());
    }

    /**
     * Returns {@code ticks} if >= 0, otherwise {@code fallback}.
     */
    public int ticksOrDefault(final int fallback) {
        return (ticks != null && ticks >= 0) ? ticks : fallback;
    }

    /**
     * Returns {@code playerModel} if non-null, otherwise {@code fallback}.
     */
    public String playerModelOrDefault(final String fallback) {
        return playerModel != null ? playerModel : fallback;
    }

    /**
     * Returns {@code castAnimation} if non-null, otherwise {@code fallback}.
     */
    public String castAnimationOrDefault(final String fallback) {
        return castAnimation != null ? castAnimation : fallback;
    }

    /**
     * Returns {@code arriveAnimation} if non-null, otherwise {@code fallback}.
     */
    public String arriveAnimationOrDefault(final String fallback) {
        return arriveAnimation != null ? arriveAnimation : fallback;
    }

    /**
     * Returns {@code immobilize} if non-null, otherwise {@code fallback}.
     */
    public boolean immobilizeOrDefault(final boolean fallback) {
        return immobilize != null ? immobilize : fallback;
    }

    /**
     * Returns {@code cancel.onMove} if non-null, otherwise {@code fallback}.
     */
    public boolean cancelOnMoveOrDefault(final boolean fallback) {
        return cancel != null ? cancel.onMoveOrDefault(fallback) : fallback;
    }

    /**
     * Returns {@code cancel.onDamage} if non-null, otherwise {@code fallback}.
     */
    public boolean cancelOnDamageOrDefault(final boolean fallback) {
        return cancel != null ? cancel.onDamageOrDefault(fallback) : fallback;
    }

    /**
     * Returns {@code cancel.onInteract} if non-null, otherwise {@code fallback}.
     */
    public boolean cancelOnInteractOrDefault(final boolean fallback) {
        return cancel != null ? cancel.onInteractOrDefault(fallback) : fallback;
    }

    /**
     * Resolves this (potentially partial/inheriting) cast section against {@code global} defaults.
     */
    public Resolved resolve(final CastSection global) {
        return resolve(new Resolved(
                global.ticksOrDefault(0),
                global.playerModelOrDefault("steve"),
                global.castAnimationOrDefault(""),
                global.arriveAnimationOrDefault(""),
                global.immobilizeOrDefault(true),
                global.cancelOnMoveOrDefault(true),
                global.cancelOnDamageOrDefault(true),
                global.cancelOnInteractOrDefault(true)
        ));
    }

    /**
     * Resolves this (potentially partial/inheriting) cast section against an already-resolved fallback (#86).
     */
    public Resolved resolve(final Resolved fallback) {
        return new Resolved(
                ticksOrDefault(fallback.ticks()),
                playerModelOrDefault(fallback.playerModel()),
                castAnimationOrDefault(fallback.castAnimation()),
                arriveAnimationOrDefault(fallback.arriveAnimation()),
                immobilizeOrDefault(fallback.immobilize()),
                cancelOnMoveOrDefault(fallback.cancelOnMove()),
                cancelOnDamageOrDefault(fallback.cancelOnDamage()),
                cancelOnInteractOrDefault(fallback.cancelOnInteract())
        );
    }

    /**
     * Resolves this global cast section into concrete settings.
     */
    public Resolved resolveGlobal() {
        return resolve(defaults());
    }

    /**
     * Validates this cast section as an inherit-capable override (preserving {@code -1} / {@code null} inheritance) (#65, #86).
     */
    public CastSection validatedOverride(final String settingPath, final Logger logger) {
        final Integer validTicks;
        if (ticks != null && ticks < -1) {
            logger.warn("Invalid {}.ticks {} (must be >= -1); falling back to -1 (inherit)", settingPath, ticks);
            validTicks = -1;
        } else {
            validTicks = ticks;
        }
        final String validPlayerModel = (playerModel == null || playerModel.isBlank()) ? null : playerModel.strip();
        final String validCastAnim = castAnimation == null ? null : castAnimation.strip();
        final String validArriveAnim = arriveAnimation == null ? null : arriveAnimation.strip();
        final CastCancelSection validCancel = cancel != null ? cancel : CastCancelSection.inherit();
        return new CastSection(
                validTicks,
                validPlayerModel,
                validCastAnim,
                validArriveAnim,
                immobilize,
                validCancel
        );
    }

    private static final String COMMENT_TICKS =
            "Cast time in ticks before warping (0 or less for instant warp; -1 to inherit)";
    private static final String COMMENT_PLAYER_MODEL =
            "BetterModel player limb model name used for player animations (e.g. steve; null to inherit)";
    private static final String COMMENT_CAST_ANIMATION =
            "BetterModel player animation played during casting with HOLD_ON_LAST (empty to disable; null to inherit)";
    private static final String COMMENT_ARRIVE_ANIMATION =
            "BetterModel player animation played after teleport arrival with PLAY_ONCE (empty to disable; null to inherit)";
    private static final String COMMENT_IMMOBILIZE =
            "Whether to prevent the player from moving via PacketEvents while casting (head rotation remains allowed; null to inherit)";
    private static final String COMMENT_CANCEL =
            "Cancellation conditions while casting";
    private static final String COMMENT_CANCEL_MOVE =
            "Cancel casting when the player moves (head rotation is allowed; null to inherit)";
    private static final String COMMENT_CANCEL_DAMAGE =
            "Cancel casting when the player takes damage (null to inherit)";
    private static final String COMMENT_CANCEL_INTERACT =
            "Cancel casting when breaking/placing blocks, attacking entities, or interacting with other blocks/entities (null to inherit)";

    /**
     * Replaces {@code null} inheritance fields with {@link VisibilityCostSection#NULL_SENTINEL} so dumped
     * YAML files explicitly write {@code null} with comments (#86).
     */
    public static void applyNullSentinels(
            final CommentedConfigurationNode node,
            final @Nullable CastSection section,
            final String sectionComment
    ) throws SerializationException {
        final CastSection target = section != null ? section : inherit();
        setCommentIfBlank(node, sectionComment);
        if (target.ticks() == null) {
            final CommentedConfigurationNode ticksNode = node.node("ticks");
            ticksNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(ticksNode, COMMENT_TICKS);
        }
        if (target.playerModel() == null) {
            final CommentedConfigurationNode modelNode = node.node("player-model");
            modelNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(modelNode, COMMENT_PLAYER_MODEL);
        }
        if (target.castAnimation() == null) {
            final CommentedConfigurationNode castNode = node.node("cast-animation");
            castNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(castNode, COMMENT_CAST_ANIMATION);
        }
        if (target.arriveAnimation() == null) {
            final CommentedConfigurationNode arriveNode = node.node("arrive-animation");
            arriveNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(arriveNode, COMMENT_ARRIVE_ANIMATION);
        }
        if (target.immobilize() == null) {
            final CommentedConfigurationNode immNode = node.node("immobilize");
            immNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(immNode, COMMENT_IMMOBILIZE);
        }
        final CommentedConfigurationNode cancelNode = node.node("cancel");
        setCommentIfBlank(cancelNode, COMMENT_CANCEL);
        final CastCancelSection cancel = target.cancel() != null ? target.cancel() : CastCancelSection.inherit();
        if (cancel.onMove() == null) {
            final CommentedConfigurationNode moveNode = cancelNode.node("on-move");
            moveNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(moveNode, COMMENT_CANCEL_MOVE);
        }
        if (cancel.onDamage() == null) {
            final CommentedConfigurationNode dmgNode = cancelNode.node("on-damage");
            dmgNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(dmgNode, COMMENT_CANCEL_DAMAGE);
        }
        if (cancel.onInteract() == null) {
            final CommentedConfigurationNode interactNode = cancelNode.node("on-interact");
            interactNode.set(VisibilityCostSection.NULL_SENTINEL);
            setCommentIfBlank(interactNode, COMMENT_CANCEL_INTERACT);
        }
    }

    private static void setCommentIfBlank(final ConfigurationNode node, final String comment) {
        if (node instanceof CommentedConfigurationNode commented) {
            final String existing = commented.comment();
            if (existing == null || existing.isBlank()) {
                commented.comment(comment);
            }
        }
    }

    /**
     * Custom {@link TypeSerializer} preserving explicit {@code null} inheritance fields, non-virtual map structure
     * for schema-sync idempotency, and inline comments (#65, #86).
     */
    public static final class Serializer implements TypeSerializer<CastSection> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public CastSection deserialize(final Type type, final ConfigurationNode node)
                throws SerializationException {
            if (node == null || node.virtual() || node.isNull()) {
                return inherit();
            }
            final Integer ticks = readNullableInteger(node.node("ticks"));
            final String playerModel = readNullableString(node.node("player-model"));
            final String castAnim = readNullableString(node.node("cast-animation"));
            final String arriveAnim = readNullableString(node.node("arrive-animation"));
            final Boolean immobilize = readNullableBoolean(node.node("immobilize"));
            final ConfigurationNode cancelNode = node.node("cancel");
            final CastCancelSection cancel = (cancelNode.virtual() || cancelNode.isNull())
                    ? CastCancelSection.inherit()
                    : new CastCancelSection(
                            readNullableBoolean(cancelNode.node("on-move")),
                            readNullableBoolean(cancelNode.node("on-damage")),
                            readNullableBoolean(cancelNode.node("on-interact"))
                    );
            return new CastSection(ticks, playerModel, castAnim, arriveAnim, immobilize, cancel);
        }

        private static @Nullable Integer readNullableInteger(final ConfigurationNode child)
                throws SerializationException {
            if (child == null || child.virtual() || child.isNull() || child.raw() == null
                    || VisibilityCostSection.NULL_SENTINEL.equals(child.raw())) {
                return null;
            }
            return child.get(Integer.class);
        }

        private static @Nullable String readNullableString(final ConfigurationNode child) {
            if (child == null || child.virtual() || child.isNull() || child.raw() == null
                    || VisibilityCostSection.NULL_SENTINEL.equals(child.raw())) {
                return null;
            }
            return child.getString();
        }

        private static @Nullable Boolean readNullableBoolean(final ConfigurationNode child)
                throws SerializationException {
            if (child == null || child.virtual() || child.isNull() || child.raw() == null
                    || VisibilityCostSection.NULL_SENTINEL.equals(child.raw())) {
                return null;
            }
            return child.getBoolean();
        }

        @Override
        public void serialize(
                final Type type,
                final @Nullable CastSection obj,
                final ConfigurationNode node
        ) throws SerializationException {
            final CastSection target = obj != null ? obj : inherit();
            node.raw(Collections.emptyMap());

            writeField(node.node("ticks"), target.ticks(), COMMENT_TICKS);
            writeField(node.node("player-model"), target.playerModel(), COMMENT_PLAYER_MODEL);
            writeField(node.node("cast-animation"), target.castAnimation(), COMMENT_CAST_ANIMATION);
            writeField(node.node("arrive-animation"), target.arriveAnimation(), COMMENT_ARRIVE_ANIMATION);
            writeField(node.node("immobilize"), target.immobilize(), COMMENT_IMMOBILIZE);

            final ConfigurationNode cancelNode = node.node("cancel");
            cancelNode.raw(Collections.emptyMap());
            setCommentIfBlank(cancelNode, COMMENT_CANCEL);
            final CastCancelSection cancel = target.cancel() != null ? target.cancel() : CastCancelSection.inherit();
            writeField(cancelNode.node("on-move"), cancel.onMove(), COMMENT_CANCEL_MOVE);
            writeField(cancelNode.node("on-damage"), cancel.onDamage(), COMMENT_CANCEL_DAMAGE);
            writeField(cancelNode.node("on-interact"), cancel.onInteract(), COMMENT_CANCEL_INTERACT);
        }

        private static void writeField(
                final ConfigurationNode child,
                final @Nullable Object value,
                final String comment
        ) throws SerializationException {
            if (value == null) {
                child.raw(null);
            } else {
                child.set(value);
                setCommentIfBlank(child, comment);
            }
        }
    }
}
