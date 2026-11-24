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
 * Finds which branch level each placed voxel belongs to.
 */
public final class BranchLevels {
  private static final double STEPS_PER_BLOCK = 2;

  private BranchLevels() {
  }

  /**
   * Level by voxel key. Voxels on a branch take its level, lowest level first where branches overlap. Every other
   * connected voxel takes the level of the nearest one.
   *
   * @param grid     The placed shapes.
   * @param skeleton The tree's branches.
   */
  public static Map<Long, Integer> of(ShapeGrid grid, Skeleton skeleton) {
    Map<Long, Integer> levels = new HashMap<>();
    ArrayDeque<int[]> open = new ArrayDeque<>();
    List<Branch> ordered = skeleton.branches().stream().sorted(Comparator.comparingInt(Branch::level)).toList();
    for (Branch branch : ordered) {
      List<Node> nodes = branch.nodes();
      for (int i = 0; i + 1 < nodes.size(); i++) {
        Vec3 from = nodes.get(i).position();
        Vec3 to = nodes.get(i + 1).position();
        int steps = Math.max(1, (int) Math.ceil(to.sub(from).length() * STEPS_PER_BLOCK));
        for (int step = 0; step <= steps; step++) {
          Vec3 point = from.lerp(to, step / (double) steps);
          claim(grid, levels, open, (int) Math.floor(point.x()), (int) Math.floor(point.y()),
                  (int) Math.floor(point.z()), branch.level());
        }
      }
    }
    while (!open.isEmpty()) {
      int[] voxel = open.poll();
      int level = levels.get(ShapeGrid.key(voxel[0], voxel[1], voxel[2]));
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          for (int dz = -1; dz <= 1; dz++) {
            claim(grid, levels, open, voxel[0] + dx, voxel[1] + dy, voxel[2] + dz, level);
          }
        }
      }
    }
    return levels;
  }

  /**
   * Gives an unclaimed, occupied voxel this level and queues it to spread further.
   *
   * @param grid   The placed shapes.
   * @param levels Level by voxel key; receives the claim.
   * @param open   Voxels waiting to spread; receives the claim.
   * @param x      Voxel x.
   * @param y      Voxel y.
   * @param z      Voxel z.
   * @param level  Level to give.
   */
  private static void claim(ShapeGrid grid, Map<Long, Integer> levels, ArrayDeque<int[]> open, int x, int y, int z,
                            int level) {
    if (grid.get(x, y, z) != null && levels.putIfAbsent(ShapeGrid.key(x, y, z), level) == null) {
      open.add(new int[]{x, y, z});
    }
  }
}
