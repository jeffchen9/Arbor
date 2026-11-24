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
 * Finishes the arms the voxelizer chose. Placed blocks never update, so walls need not follow vanilla
 * connection rules: this only picks each arm's height and whether the wall shows a post.
 */
public final class WallResolver {
  private WallResolver() {
  }

  /**
   * Settles every wall, working from the top down so each wall sees the finished wall above it.
   *
   * @param grid The tree's shapes; walls are updated in place.
   */
  public static void apply(ShapeGrid grid) {
    long[] keys = grid.sortedKeys();
    for (int i = keys.length - 1; i >= 0; i--) {
      int x = ShapeGrid.keyX(keys[i]);
      int y = ShapeGrid.keyY(keys[i]);
      int z = ShapeGrid.keyZ(keys[i]);
      if (grid.get(x, y, z) instanceof Shape.Wall wall) {
        grid.put(x, y, z, resolve(wall, grid.get(x, y + 1, z)));
      }
    }
  }

  /**
   * The wall with each arm's height and its post decided by what sits on top of it.
   *
   * @param wall  Wall to settle.
   * @param above Shape on top of it, or null.
   */
  private static Shape.Wall resolve(Shape.Wall wall, Shape above) {
    WallSide north = side(wall.north(), above, 7, 0, 9, 9);
    WallSide east = side(wall.east(), above, 7, 7, 16, 9);
    WallSide south = side(wall.south(), above, 7, 7, 9, 16);
    WallSide west = side(wall.west(), above, 0, 7, 9, 9);
    return new Shape.Wall(post(above, north, east, south, west), north, east, south, west);
  }

  /**
   * An arm is tall when the block above covers the arm's area (given in sixteenths), and low otherwise.
   *
   * @param arm   The arm as placed.
   * @param above Shape on top of the wall, or null.
   * @param minX  West edge of the arm's area, in sixteenths.
   * @param minZ  North edge of the arm's area, in sixteenths.
   * @param maxX  East edge of the arm's area, in sixteenths.
   * @param maxZ  South edge of the arm's area, in sixteenths.
   */
  private static WallSide side(WallSide arm, Shape above, int minX, int minZ, int maxX, int maxZ) {
    if (arm == WallSide.NONE) {
      return WallSide.NONE;
    }
    return ShapeFootprint.covers(above, minX, minZ, maxX, maxZ) ? WallSide.TALL : WallSide.LOW;
  }

  /**
   * Whether the wall shows its center post: always under a post, for no arms, for a single arm or a corner; never for
   * two tall opposite arms; otherwise when something sits on the center.
   *
   * @param above Shape on top of the wall, or null.
   * @param north North arm.
   * @param east  East arm.
   * @param south South arm.
   * @param west  West arm.
   */
  private static boolean post(Shape above, WallSide north, WallSide east, WallSide south, WallSide west) {
    if (above instanceof Shape.Wall wall && wall.post()) {
      return true;
    }
    boolean northNone = north == WallSide.NONE;
    boolean eastNone = east == WallSide.NONE;
    boolean southNone = south == WallSide.NONE;
    boolean westNone = west == WallSide.NONE;
    if ((northNone && eastNone && southNone && westNone) || northNone != southNone || eastNone != westNone) {
      return true;
    }
    if ((north == WallSide.TALL && south == WallSide.TALL) || (east == WallSide.TALL && west == WallSide.TALL)) {
      return false;
    }
    return ShapeFootprint.covers(above, 7, 7, 9, 9);
  }
}
