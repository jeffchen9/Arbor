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
 * One layer of plants: weighted blocks and the rules for where they may go. {@code nearBranch} is a level name, or
 * null for any level.
 *
 * @param name         Unique name, which also keeps this layer's random placement stable.
 * @param enabled      Whether the layer is used at all.
 * @param treeChance   Probability from 0 to 1 that a given tree uses this layer.
 * @param blocks       Weighted plants this layer places.
 * @param chance       Probability from 0 to 1 of planting each eligible voxel.
 * @param minDensity   Lowest leaf density where plants may go.
 * @param maxDensity   Highest leaf density where plants may go.
 * @param minHeight    Lowest position, from 0 (lowest tree block) to 1 (highest).
 * @param maxHeight    Highest position, from 0 (lowest tree block) to 1 (highest).
 * @param side         Part of the crown allowed: anywhere, on top of, under, outer, inner, or on a tall plant.
 * @param nearBranch   Level name plants must be near, or null for any leaves or branch.
 * @param nearDistance Maximum distance in blocks from that branch level.
 * @param clumpSize    Size in blocks of the noise that groups plants into clumps; 0 disables clumping.
 * @param doubleHalf   Which halves of a two-block plant to place: both, only the bottom, or only the top.
 */
public record PlantLayer(String name, boolean enabled, double treeChance, WeightedBlocks blocks, double chance,
                         double minDensity, double maxDensity, double minHeight, double maxHeight, Side side,
                         String nearBranch, int nearDistance, double clumpSize, DoubleHalf doubleHalf) {
  /**
   * Where in the crown a layer may plant: anywhere, on top of a block, hanging under one, toward the outside, near
   * the middle, or on top of a two-block plant.
   */
  public enum Side {ANY, TOP, UNDERSIDE, OUTER, INNER, ON_TALL_PLANT}

  /**
   * Which halves of a two-block plant are placed.
   */
  public enum DoubleHalf {BOTH, BOTTOM, TOP}
}
