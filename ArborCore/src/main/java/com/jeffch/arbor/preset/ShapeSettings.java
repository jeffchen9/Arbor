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
 * Bending applied as a branch grows. Rates are per block.
 *
 * @param gravity      Downward bend; negative values bend upward.
 * @param ancestor     Bend toward the parent; negative values bend away.
 * @param straightness Reduces noise near the base of the branch, from 0 to 1.
 * @param twist        Degrees the heading rotates around the branch's start direction.
 * @param curlUp       Degrees of bend toward the parent's direction.
 * @param curlRight    Degrees of bend sideways around the parent.
 * @param curlOut      Degrees of bend away from the parent.
 * @param zigzag       Angle in degrees of the side-to-side zigzag.
 * @param zigzagLength Distance in blocks of each zigzag leg.
 */
public record ShapeSettings(
        Param gravity,
        Param ancestor,
        Param straightness,
        Param twist,
        Param curlUp,
        Param curlRight,
        Param curlOut,
        Param zigzag,
        Param zigzagLength) {
}
