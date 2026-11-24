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
 * Settings for the tree as a whole.
 *
 * @param sizeScale     Multiplier on every distance in the preset.
 * @param maxHeight     Height in blocks where branches are cut off.
 * @param sink          Distance in blocks, before size scale, the trunk base starts below ground.
 * @param roll          Rotation of the whole tree around the vertical axis, in degrees.
 * @param trunkSpread   Typical distance in blocks of each trunk from the center when a tree has several.
 * @param sizeVariance  Largest random change to each tree's overall size, as a fraction; 0.15 means 85% to 115%.
 * @param ground        Plane the tree stands on, set in JSON by its normal vector; flat by default.
 * @param slopeRecovery Fraction of the trunk over which it turns from the ground normal back to upright.
 */
public record TreeSettings(double sizeScale, double maxHeight, double sink, Param roll, double trunkSpread,
                           double sizeVariance, GroundPlane ground, double slopeRecovery) {
}
