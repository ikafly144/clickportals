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
package net.sabafly.clickportals.update;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Semantic Versioning 2.0.0 parser and comparator used by the Modrinth update checker (#76).
 */
public record SemanticVersion(
        int major,
        int minor,
        int patch,
        @Nullable String preRelease,
        @Nullable String buildMetadata
) implements Comparable<SemanticVersion> {

    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^[vV]?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*))?"
                    + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$"
    );

    public SemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version components must be non-negative");
        }
        preRelease = (preRelease != null && preRelease.isBlank()) ? null : preRelease;
        buildMetadata = (buildMetadata != null && buildMetadata.isBlank()) ? null : buildMetadata;
    }

    /**
     * Parses a SemVer string, throwing {@link IllegalArgumentException} if invalid.
     */
    public static SemanticVersion parse(final String raw) {
        return tryParse(raw)
                .orElseThrow(() -> new IllegalArgumentException("Invalid SemVer string: " + raw));
    }

    /**
     * Attempts to parse a SemVer string, returning empty if {@code raw} is null or malformed.
     */
    public static Optional<SemanticVersion> tryParse(final @Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        final String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        final Matcher matcher = SEMVER_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            final int major = Integer.parseInt(matcher.group(1));
            final int minor = Integer.parseInt(matcher.group(2));
            final int patch = Integer.parseInt(matcher.group(3));
            final String pre = matcher.group(4);
            final String build = matcher.group(5);
            return Optional.of(new SemanticVersion(major, minor, patch, pre, build));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns {@code true} if this version carries a pre-release tag (e.g. {@code -alpha.1}).
     */
    public boolean isPreRelease() {
        return preRelease != null;
    }

    /**
     * Returns {@code true} if this version has strictly higher SemVer precedence than {@code other}.
     */
    public boolean isNewerThan(final SemanticVersion other) {
        return compareTo(other) > 0;
    }

    /**
     * Returns the canonical version string without build metadata or leading {@code v}.
     */
    public String canonical() {
        final String base = major + "." + minor + "." + patch;
        return preRelease != null ? base + "-" + preRelease : base;
    }

    @Override
    public int compareTo(final SemanticVersion other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.patch, other.patch);
        if (cmp != 0) {
            return cmp;
        }

        final String leftPre = this.preRelease;
        final String rightPre = other.preRelease;
        if (Objects.equals(leftPre, rightPre)) {
            return 0;
        }
        if (leftPre == null) {
            return 1;
        }
        if (rightPre == null) {
            return -1;
        }
        return comparePreRelease(leftPre, rightPre);
    }

    private static int comparePreRelease(final String left, final String right) {
        final String[] leftParts = left.split("\\.", -1);
        final String[] rightParts = right.split("\\.", -1);
        final int minLength = Math.min(leftParts.length, rightParts.length);
        for (int i = 0; i < minLength; i++) {
            final String a = leftParts[i];
            final String b = rightParts[i];
            if (a.equals(b)) {
                continue;
            }
            final boolean aNum = isDigits(a);
            final boolean bNum = isDigits(b);
            if (aNum && bNum) {
                final int numCmp = new BigInteger(a).compareTo(new BigInteger(b));
                if (numCmp != 0) {
                    return numCmp;
                }
            } else if (aNum) {
                return -1;
            } else if (bNum) {
                return 1;
            } else {
                return a.compareTo(b);
            }
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    private static boolean isDigits(final String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
