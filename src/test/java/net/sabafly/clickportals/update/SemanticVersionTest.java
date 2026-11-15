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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {

    @Test
    void parsesValidSemVerWithOptionalPrefixPreReleaseAndBuildMetadata() {
        final SemanticVersion simple = SemanticVersion.parse("v0.4.0");
        assertThat(simple.major()).isZero();
        assertThat(simple.minor()).isEqualTo(4);
        assertThat(simple.patch()).isZero();
        assertThat(simple.isPreRelease()).isFalse();
        assertThat(simple.canonical()).isEqualTo("0.4.0");

        final SemanticVersion pre = SemanticVersion.parse("1.2.3-beta.10+build.42");
        assertThat(pre.major()).isEqualTo(1);
        assertThat(pre.minor()).isEqualTo(2);
        assertThat(pre.patch()).isEqualTo(3);
        assertThat(pre.preRelease()).isEqualTo("beta.10");
        assertThat(pre.buildMetadata()).isEqualTo("build.42");
        assertThat(pre.isPreRelease()).isTrue();
        assertThat(pre.canonical()).isEqualTo("1.2.3-beta.10");
    }

    @Test
    void rejectsInvalidVersionStrings() {
        assertThat(SemanticVersion.tryParse(null)).isEmpty();
        assertThat(SemanticVersion.tryParse("")).isEmpty();
        assertThat(SemanticVersion.tryParse("01.2.3")).isEmpty();
        assertThat(SemanticVersion.tryParse("1.2")).isEmpty();
        assertThatThrownBy(() -> SemanticVersion.parse("not-a-version"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesAccordingToSemVer2PrecedenceRules() {
        assertThat(SemanticVersion.parse("0.5.0").isNewerThan(SemanticVersion.parse("0.4.9"))).isTrue();
        assertThat(SemanticVersion.parse("1.0.0").isNewerThan(SemanticVersion.parse("1.0.0-rc.1"))).isTrue();
        assertThat(SemanticVersion.parse("1.0.0-rc.1").isNewerThan(SemanticVersion.parse("1.0.0"))).isFalse();

        // Numeric pre-release vs numeric: numerical comparison (beta.2 < beta.11)
        assertThat(SemanticVersion.parse("1.0.0-beta.11").compareTo(SemanticVersion.parse("1.0.0-beta.2"))).isGreaterThan(0);
        // alpha < beta < rc
        assertThat(SemanticVersion.parse("1.0.0-beta.1").compareTo(SemanticVersion.parse("1.0.0-alpha.9"))).isGreaterThan(0);
        assertThat(SemanticVersion.parse("1.0.0-rc.1").compareTo(SemanticVersion.parse("1.0.0-beta.5"))).isGreaterThan(0);
        // Alphanumeric > numeric identifier
        assertThat(SemanticVersion.parse("1.0.0-beta").compareTo(SemanticVersion.parse("1.0.0-1"))).isGreaterThan(0);
        // Shorter field set < longer when prefix matches
        assertThat(SemanticVersion.parse("1.0.0-alpha.1").compareTo(SemanticVersion.parse("1.0.0-alpha"))).isGreaterThan(0);
        // Build metadata ignored for precedence
        assertThat(SemanticVersion.parse("1.0.0+001").compareTo(SemanticVersion.parse("1.0.0+exp.sha"))).isZero();
    }
}
