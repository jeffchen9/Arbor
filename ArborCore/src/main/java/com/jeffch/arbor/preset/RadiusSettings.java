/*
 *     Arbor: Procedural Voxel Tree Generation.
 *     Copyright (C) 2026  Jeff Chen
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jeffch.arbor.preset;

/**
 * Branch thickness and taper.
 *
 * @param absolute           Base radius in blocks.
 * @param percentOfParent    Radius added as a fraction of the parent's radius at the attachment point.
 * @param taper              Radius multiplier along the branch, from base (0) to tip (1).
 * @param clampToParent      Whether the base radius may not exceed the parent's radius.
 * @param min                Smallest allowed radius, in blocks.
 * @param max                Largest allowed radius, in blocks.
 * @param lengthCompensation How much the taper is reduced toward the tip, from 0 to 1.
 * @param lengthKillRatio    Removes branches whose length divided by base radius exceeds this; 0 disables it.
 */
public record RadiusSettings(
        Param absolute,
        Param percentOfParent,
        Curve taper,
        boolean clampToParent,
        double min,
        double max,
        double lengthCompensation,
        double lengthKillRatio) {
}
