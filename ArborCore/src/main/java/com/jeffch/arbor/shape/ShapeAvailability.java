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

package com.jeffch.arbor.shape;

import com.jeffch.arbor.preset.BlockRole;

/**
 * Which shapes a tree can use, because its picked block for the role is not off.
 *
 * @param wall     Whether walls are available.
 * @param fence    Whether fences are available.
 * @param gate     Whether fence gates are available.
 * @param stairs   Whether stairs are available.
 * @param slab     Whether slabs are available.
 * @param button   Whether buttons are available.
 * @param sapling  Whether saplings are available.
 * @param deadBush Whether dead bushes are available.
 * @param leaves   Whether leaf blocks are available.
 */
public record ShapeAvailability(boolean wall, boolean fence, boolean gate, boolean stairs, boolean slab,
                                boolean button, boolean sapling, boolean deadBush, boolean leaves) {
  /**
   * Which shapes this tree may use: those whose block type is switched on.
   *
   * @param blocks The tree's chosen blocks.
   */
  public static ShapeAvailability of(TreeBlocks blocks) {
    return new ShapeAvailability(blocks.has(BlockRole.WALL), blocks.has(BlockRole.FENCE), blocks.has(BlockRole.GATE),
            blocks.has(BlockRole.STAIRS), blocks.has(BlockRole.SLAB), blocks.has(BlockRole.BUTTON),
            blocks.has(BlockRole.SAPLING), blocks.has(BlockRole.DEAD_BUSH), blocks.has(BlockRole.LEAVES));
  }

  /**
   * Every shape available.
   */
  public static ShapeAvailability allOn() {
    return new ShapeAvailability(true, true, true, true, true, true, true, true, true);
  }

  /**
   * Survival lays out thin wood as walls and fences, which {@link SurvivalBlocks} then turns into logs.
   */
  public static ShapeAvailability survival() {
    return new ShapeAvailability(true, true, false, false, false, false, false, false, true);
  }

  /**
   * The class a wood segment uses when {@code wanted} may be unavailable: FULL, WALL or FENCE, or null to
   * leave it out. A wall falls back to a fence, then a log. A fence falls back to a wall, then a log on
   * the trunk, which is never left out, and otherwise nothing.
   *
   * @param wanted Class the segment's radius calls for.
   * @param trunk  Whether the segment is part of a trunk.
   */
  public ShapeClass woodClass(ShapeClass wanted, boolean trunk) {
    return switch (wanted) {
      case FULL -> ShapeClass.FULL;
      case WALL -> wall ? ShapeClass.WALL : fence ? ShapeClass.FENCE : ShapeClass.FULL;
      case FENCE -> fence ? ShapeClass.FENCE : wall ? ShapeClass.WALL : trunk ? ShapeClass.FULL : null;
      default -> throw new IllegalArgumentException("not a wood segment class: " + wanted);
    };
  }
}
