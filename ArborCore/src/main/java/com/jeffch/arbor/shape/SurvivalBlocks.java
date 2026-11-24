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

import com.jeffch.arbor.preset.LevelPreset;
import com.jeffch.arbor.preset.SpeciesPreset;
import com.jeffch.arbor.skeleton.Branch;
import com.jeffch.arbor.skeleton.Skeleton;

import java.util.*;

/**
 * Survival trees: only logs, wood and leaves, with every leaf close enough to a log to survive Java Edition decay.
 * A leaf lasts if a chain of face-touching leaves reaches a log in at most {@value #MAX_LEAF_DISTANCE} steps.
 */
public final class SurvivalBlocks {
  public static final int MAX_LEAF_DISTANCE = 6;
  private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
  private static final Shape RIB = new Shape.Fence(false, false, false, false);
  private static final int LEAFY_TIP = 3;

  private SurvivalBlocks() {
  }

  /**
   * Levels drawn without wood, such as palm fronds, normally appear only as plants. Here they become leaves. One
   * growing straight from wood gets a log rib up to its tip, so a frond longer than the decay limit can survive.
   *
   * @param grid     The tree's shapes; logs are added to it.
   * @param skeleton The tree's branches.
   * @param preset   Preset whose levels may turn wood off.
   */
  public static void addFoliageSpines(ShapeGrid grid, Skeleton skeleton, SpeciesPreset preset) {
    Map<String, Boolean> woodLevels = new HashMap<>();
    preset.levels().forEach(level -> woodLevels.put(level.name(), level.wood()));
    for (Branch branch : skeleton.branches()) {
      LevelPreset level = preset.levels().get(Math.min(branch.level(), preset.levels().size() - 1));
      if (level.wood()) {
        continue;
      }
      boolean ribbed = Boolean.TRUE.equals(woodLevels.get(level.parent()));
      List<int[]> path = new ArrayList<>();
      for (int i = 0; i + 1 < branch.nodes().size(); i++) {
        path.addAll(VoxelPaths.sixConnected(branch.nodes().get(i).position(),
                branch.nodes().get(i + 1).position()));
      }
      for (int i = 0; i < path.size(); i++) {
        int[] block = path.get(i);
        Shape existing = grid.get(block[0], block[1], block[2]);
        if (ribbed && i < path.size() - LEAFY_TIP) {
          if (existing == null || existing instanceof Shape.Leaves) {
            grid.put(block[0], block[1], block[2], RIB);
          }
        } else if (existing == null) {
          grid.put(block[0], block[1], block[2], new Shape.Leaves());
        }
      }
    }
  }

  /**
   * Turns thin wood into logs, removes leaves that would decay and floating pieces, and records leaf distances.
   *
   * @param grid     The tree's shapes; changed in place.
   * @param skeleton The tree's branches, whose trunk bases anchor what stays.
   */
  public static void finish(ShapeGrid grid, Skeleton skeleton) {
    thinWoodToLogs(grid);
    Map<Long, int[]> leaves = lastingLeaves(grid);
    grid.removeIf((x, y, z) -> grid.get(x, y, z) instanceof Shape.Leaves
            && !leaves.containsKey(ShapeGrid.key(x, y, z)));
    // A leaf's chain to its log touches all the way, so removing separate pieces cannot lengthen it.
    DetachedBlocks.apply(grid, skeleton);
    for (int[] leaf : leaves.values()) {
      if (grid.get(leaf[0], leaf[1], leaf[2]) != null) {
        grid.put(leaf[0], leaf[1], leaf[2], new Shape.Leaves(leaf[3]));
      }
    }
  }

  /**
   * Fences, walls and gates: wood thinner than a full block.
   *
   * @param shape Shape to check.
   */
  private static boolean isThinWood(Shape shape) {
    return shape instanceof Shape.Wall || shape instanceof Shape.Fence || shape instanceof Shape.FenceGate;
  }

  /**
   * Survival has no fences or walls, so each becomes a log whose grain follows the wood around it.
   *
   * @param grid The tree's shapes; changed in place.
   */
  private static void thinWoodToLogs(ShapeGrid grid) {
    List<int[]> thin = new ArrayList<>();
    grid.forEachSorted((x, y, z, shape) -> {
      if (isThinWood(shape)) {
        thin.add(new int[]{x, y, z});
      }
    });
    Map<int[], GrainAxis> axes = new HashMap<>();
    thin.forEach(block -> axes.put(block, grainAt(grid, block[0], block[1], block[2])));
    axes.forEach((block, axis) -> grid.put(block[0], block[1], block[2], new Shape.FullBlock(axis)));
  }

  /**
   * The axis with wood on the most sides; a block with wood along several axes is a junction.
   *
   * @param grid The tree's shapes.
   * @param x    Block x.
   * @param y    Block y.
   * @param z    Block z.
   */
  private static GrainAxis grainAt(ShapeGrid grid, int x, int y, int z) {
    int alongX = wood(grid, x - 1, y, z) + wood(grid, x + 1, y, z);
    int alongY = wood(grid, x, y - 1, z) + wood(grid, x, y + 1, z);
    int alongZ = wood(grid, x, y, z - 1) + wood(grid, x, y, z + 1);
    int most = Math.max(alongX, Math.max(alongY, alongZ));
    int tied = (alongX == most ? 1 : 0) + (alongY == most ? 1 : 0) + (alongZ == most ? 1 : 0);
    if (most == 0) {
      return GrainAxis.Y;
    }
    if (tied > 1) {
      return GrainAxis.NONE;
    }
    return alongY == most ? GrainAxis.Y : alongX == most ? GrainAxis.X : GrainAxis.Z;
  }

  /**
   * 1 when a position holds wood of any thickness, else 0.
   *
   * @param grid The tree's shapes.
   * @param x    Block x.
   * @param y    Block y.
   * @param z    Block z.
   */
  private static int wood(ShapeGrid grid, int x, int y, int z) {
    Shape shape = grid.get(x, y, z);
    return shape instanceof Shape.FullBlock || isThinWood(shape) ? 1 : 0;
  }

  /**
   * Each lasting leaf as {x, y, z, distance}, found by spreading outward from every log one step at a time.
   *
   * @param grid The tree's shapes.
   */
  private static Map<Long, int[]> lastingLeaves(ShapeGrid grid) {
    Map<Long, int[]> reached = new HashMap<>();
    ArrayDeque<int[]> queue = new ArrayDeque<>();
    grid.forEachSorted((x, y, z, shape) -> {
      if (shape instanceof Shape.FullBlock) {
        queue.add(new int[]{x, y, z, 0});
      }
    });
    while (!queue.isEmpty()) {
      int[] block = queue.poll();
      if (block[3] == MAX_LEAF_DISTANCE) {
        continue;
      }
      for (int[] step : NEIGHBORS) {
        int x = block[0] + step[0];
        int y = block[1] + step[1];
        int z = block[2] + step[2];
        long key = ShapeGrid.key(x, y, z);
        if (grid.get(x, y, z) instanceof Shape.Leaves && !reached.containsKey(key)) {
          int[] leaf = {x, y, z, block[3] + 1};
          reached.put(key, leaf);
          queue.add(leaf);
        }
      }
    }
    return reached;
  }
}
