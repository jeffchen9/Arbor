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

import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.skeleton.Branch;
import com.jeffch.arbor.skeleton.Node;
import com.jeffch.arbor.skeleton.Skeleton;

import java.util.*;

/**
 * Removes blocks that have no chain of touching blocks, diagonals included, back to a trunk.
 */
public final class DetachedBlocks {
  private DetachedBlocks() {
  }

  /**
   * Keeps only what connects to the base of a trunk; floating pieces left by trimming or the ground are removed.
   *
   * @param grid     The tree's shapes; detached ones are removed.
   * @param skeleton The tree's branches, whose trunk bases anchor what stays.
   */
  public static void apply(ShapeGrid grid, Skeleton skeleton) {
    List<int[]> trunkBlocks = new ArrayList<>();
    for (Branch branch : skeleton.branches()) {
      if (branch.level() != 0) {
        continue;
      }
      // Only the lowest placed block counts: a higher piece may be cut off from the base.
      for (Node node : branch.nodes()) {
        Vec3 p = node.position();
        int[] block = {(int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z())};
        if (grid.get(block[0], block[1], block[2]) != null) {
          trunkBlocks.add(block);
          break;
        }
      }
    }
    keepConnected(grid, trunkBlocks);
  }

  /**
   * Keeps what touches the given blocks. If none of them is occupied, the grid is left alone.
   *
   * @param grid    The tree's shapes; unconnected ones are removed.
   * @param anchors Blocks to keep, with everything touching them.
   */
  static void keepConnected(ShapeGrid grid, List<int[]> anchors) {
    Set<Long> reached = new HashSet<>();
    ArrayDeque<int[]> queue = new ArrayDeque<>();
    for (int[] anchor : anchors) {
      if (grid.get(anchor[0], anchor[1], anchor[2]) != null
              && reached.add(ShapeGrid.key(anchor[0], anchor[1], anchor[2]))) {
        queue.add(anchor);
      }
    }
    if (queue.isEmpty()) {
      return;
    }
    while (!queue.isEmpty()) {
      int[] block = queue.poll();
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          for (int dz = -1; dz <= 1; dz++) {
            int x = block[0] + dx;
            int y = block[1] + dy;
            int z = block[2] + dz;
            if (grid.get(x, y, z) != null && reached.add(ShapeGrid.key(x, y, z))) {
              queue.add(new int[]{x, y, z});
            }
          }
        }
      }
    }
    grid.removeIf((x, y, z) -> !reached.contains(ShapeGrid.key(x, y, z)));
  }
}
