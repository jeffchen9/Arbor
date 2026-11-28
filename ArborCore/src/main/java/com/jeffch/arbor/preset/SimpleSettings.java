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
 * Whole-tree adjustments from the Simple view, stored apart from the Advanced settings they adjust.
 *
 * @param size           Multiplier on every distance, so the whole tree grows or shrinks evenly.
 * @param height         Multiplier on trunk height.
 * @param crownStart     Share of the trunk, from 0 to 0.95, below which no branches grow, or null to keep the preset's.
 * @param crownWidth     Multiplier on branch lengths and crown radius.
 * @param trunkThickness Multiplier on trunk radius.
 * @param branchDensity  Multiplier on the total number of final branches.
 * @param branchAngle    Degrees added to each branch's start angle.
 * @param droop          Vertical pull on branches, from -1 (lift) to 1 (droop).
 * @param twist          Multiplier on branch wander.
 * @param logBranches    Divides the thickness at which branches become logs or walls, so higher draws more of them as logs.
 * @param leafFullness   Multiplier on leaf cluster size and strength.
 * @param windLean       Steady sideways lean, from 0 to 1.
 * @param sizeVariety    Replacement size variance, or null to keep the preset's.
 */
public record SimpleSettings(double size, double height, Double crownStart, double crownWidth, double trunkThickness,
                             double branchDensity, double branchAngle, double droop, double twist, double logBranches,
                             double leafFullness, double windLean, Double sizeVariety) {
  public static final SimpleSettings NEUTRAL = new SimpleSettings(1, 1, null, 1, 1, 1, 0, 0, 1, 1, 1, 0, null);
  /**
   * Start of the names of the hidden forces the Simple view adds.
   */
  static final String RESERVED_FORCE_PREFIX = "__simple";

  /**
   * Whether these adjustments leave the tree unchanged.
   */
  public boolean neutral() {
    return equals(NEUTRAL);
  }
}
