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
 * Makes each fence's north, east, south and west arms agree with its neighbors. Arms into air
 * stay, because fences may float, and adjacent fences connect only when a branch links them.
 */
public final class FenceConnections {
  private FenceConnections() {
  }

  /**
   * For every fence, gives a neighboring fence the matching arm back, joins it to the end of a gate's rail, and drops
   * an arm that runs into the side of a closed gate.
   *
   * @param grid The tree's shapes; fences are updated in place.
   */
  public static void apply(ShapeGrid grid) {
    for (long key : grid.sortedKeys()) {
      int x = ShapeGrid.keyX(key);
      int y = ShapeGrid.keyY(key);
      int z = ShapeGrid.keyZ(key);
      if (!(grid.get(x, y, z) instanceof Shape.Fence)) {
        continue;
      }
      for (Direction direction : Direction.values()) {
        Shape.Fence fence = (Shape.Fence) grid.get(x, y, z);
        int nx = x + direction.dx();
        int nz = z + direction.dz();
        Shape neighbor = grid.get(nx, y, nz);
        if (neighbor instanceof Shape.Fence other) {
          if (hasArm(fence, direction)) {
            grid.put(nx, y, nz, withArm(other, direction.opposite(), true));
          }
        } else if (neighbor instanceof Shape.FenceGate gate) {
          boolean railEnd = gate.facing().isXAxis() != direction.isXAxis();
          if (railEnd) {
            grid.put(x, y, z, withArm(fence, direction, true));
          } else if (!gate.open()) {
            grid.put(x, y, z, withArm(fence, direction, false));
          }
        }
      }
    }
  }

  /**
   * Whether the fence has an arm on that side.
   *
   * @param fence     Fence to check.
   * @param direction Side to check.
   */
  public static boolean hasArm(Shape.Fence fence, Direction direction) {
    return switch (direction) {
      case NORTH -> fence.north();
      case EAST -> fence.east();
      case SOUTH -> fence.south();
      case WEST -> fence.west();
    };
  }

  /**
   * The fence with one arm set or removed.
   *
   * @param fence     Fence to change.
   * @param direction Side of the arm.
   * @param value     Whether the arm is present.
   */
  static Shape.Fence withArm(Shape.Fence fence, Direction direction, boolean value) {
    return new Shape.Fence(
            direction == Direction.NORTH ? value : fence.north(),
            direction == Direction.EAST ? value : fence.east(),
            direction == Direction.SOUTH ? value : fence.south(),
            direction == Direction.WEST ? value : fence.west());
  }
}
