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

/**
 * The bottom collision face of a shape, in sixteenths of a block.
 */
public final class ShapeFootprint {
  private ShapeFootprint() {
  }

  /**
   * True when the shape's bottom face fully covers the rectangle.
   *
   * @param above Shape to check.
   * @param minX  West edge of the rectangle, in sixteenths.
   * @param minZ  North edge of the rectangle, in sixteenths.
   * @param maxX  East edge of the rectangle, in sixteenths.
   * @param maxZ  South edge of the rectangle, in sixteenths.
   */
  public static boolean covers(Shape above, int minX, int minZ, int maxX, int maxZ) {
    if (above == null) {
      return false;
    }
    boolean[][] mask = mask(above);
    for (int x = minX; x < maxX; x++) {
      for (int z = minZ; z < maxZ; z++) {
        if (!mask[x][z]) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Which sixteenths of the block's bottom face a shape covers, as a 16 by 16 grid. Plants and other see-through shapes
   * cover nothing.
   *
   * @param shape Shape to measure.
   */
  private static boolean[][] mask(Shape shape) {
    boolean[][] mask = new boolean[16][16];
    switch (shape) {
      case Shape.DeadBush bush -> {
      }
      case Shape.Sapling sapling -> {
      }
      case Shape.Plant plant -> {
      }
      case Shape.Leaves leaves -> fill(mask, 0, 0, 16, 16);
      case Shape.Button button -> {
      }
      case Shape.Slab slab -> {
        if (slab.half() == Half.BOTTOM) {
          fill(mask, 0, 0, 16, 16);
        }
      }
      case Shape.Stair stair -> {
        if (stair.half() == Half.BOTTOM) {
          fill(mask, 0, 0, 16, 16);
        }
      }
      case Shape.FullBlock full -> fill(mask, 0, 0, 16, 16);
      case Shape.Fence fence -> {
        fill(mask, 6, 6, 10, 10);
        if (fence.north()) {
          fill(mask, 6, 0, 10, 10);
        }
        if (fence.south()) {
          fill(mask, 6, 6, 10, 16);
        }
        if (fence.west()) {
          fill(mask, 0, 6, 10, 10);
        }
        if (fence.east()) {
          fill(mask, 6, 6, 16, 10);
        }
      }
      case Shape.Wall wall -> {
        if (wall.post()) {
          fill(mask, 4, 4, 12, 12);
        }
        if (wall.north() != WallSide.NONE) {
          fill(mask, 5, 0, 11, 11);
        }
        if (wall.south() != WallSide.NONE) {
          fill(mask, 5, 5, 11, 16);
        }
        if (wall.west() != WallSide.NONE) {
          fill(mask, 0, 5, 11, 11);
        }
        if (wall.east() != WallSide.NONE) {
          fill(mask, 5, 5, 16, 11);
        }
      }
      case Shape.FenceGate gate -> {
        if (!gate.open()) {
          if (gate.facing().isXAxis()) {
            fill(mask, 6, 0, 10, 16);
          } else {
            fill(mask, 0, 6, 16, 10);
          }
        }
      }
    }
    return mask;
  }

  /**
   * Marks a rectangle of the grid as covered.
   *
   * @param mask Grid to mark; changed in place.
   * @param minX West edge of the rectangle, in sixteenths.
   * @param minZ North edge of the rectangle, in sixteenths.
   * @param maxX East edge of the rectangle, in sixteenths.
   * @param maxZ South edge of the rectangle, in sixteenths.
   */
  private static void fill(boolean[][] mask, int minX, int minZ, int maxX, int maxZ) {
    for (int x = minX; x < maxX; x++) {
      for (int z = minZ; z < maxZ; z++) {
        mask[x][z] = true;
      }
    }
  }
}
