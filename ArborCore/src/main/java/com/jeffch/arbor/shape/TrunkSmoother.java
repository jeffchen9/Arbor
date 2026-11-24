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

import com.jeffch.arbor.math.TreeRandom;
import com.jeffch.arbor.preset.VoxelSettings;
import com.jeffch.arbor.skeleton.GenerationBudget;

import java.util.Arrays;
import java.util.Map;

/**
 * Rounds the blocky trunk surface with partial shapes and adds button burls. Clearance is how far
 * a voxel center lies inside the true trunk surface, so outside voxels have negative values.
 * Shapes are set directly because placed blocks are not updated by Minecraft afterward.
 */
public final class TrunkSmoother {
  private static final Shape WALL = new Shape.Wall(true, WallSide.NONE, WallSide.NONE, WallSide.NONE, WallSide.NONE);
  private static final int[][] DIAGONALS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
  private static final Direction[] DIAGONAL_FACING = {Direction.NORTH, Direction.NORTH, Direction.SOUTH, Direction.SOUTH};
  private static final StairForm[] DIAGONAL_FORM = {
          StairForm.OUTER_LEFT, StairForm.OUTER_RIGHT, StairForm.OUTER_LEFT, StairForm.OUTER_RIGHT,
  };

  private final ShapeGrid grid;
  private final Map<Long, Double> clearance;
  private final VoxelSettings settings;
  private final long seed;
  private final GenerationBudget budget;
  private final ShapeAvailability availability;

  /**
   * @param grid         The tree's shapes; smoothing shapes are added to it.
   * @param clearance    How far each voxel near the trunk lies inside the true surface, keyed by voxel.
   * @param settings     Voxelizer settings.
   * @param seed         Tree seed.
   * @param budget       Work limit.
   * @param availability Which shapes the tree blocks allow.
   */
  private TrunkSmoother(ShapeGrid grid, Map<Long, Double> clearance, VoxelSettings settings, long seed,
                        GenerationBudget budget, ShapeAvailability availability) {
    this.availability = availability;
    this.grid = grid;
    this.clearance = clearance;
    this.settings = settings;
    this.seed = seed;
    this.budget = budget;
  }

  /**
   * Smoothing never falls back to a thicker shape: an unavailable wall, fence or slab is left empty.
   *
   * @param grid         The tree's shapes; smoothing shapes are added to it.
   * @param clearance    How far each voxel near the trunk lies inside the true surface, keyed by voxel.
   * @param settings     Voxelizer settings.
   * @param seed         Tree seed.
   * @param budget       Work limit.
   * @param availability Which shapes the tree blocks allow.
   */
  public static void apply(ShapeGrid grid, Map<Long, Double> clearance, VoxelSettings settings, long seed,
                           GenerationBudget budget, ShapeAvailability availability) {
    long[] keys = clearance.keySet().stream().mapToLong(Long::longValue).toArray();
    Arrays.sort(keys);
    TrunkSmoother smoother = new TrunkSmoother(grid, clearance, settings, seed, budget, availability);
    budget.charge(keys.length * 12.0);
    if (settings.smoothTrunk()) {
      for (long key : keys) {
        smoother.smooth(ShapeGrid.keyX(key), ShapeGrid.keyY(key), ShapeGrid.keyZ(key), -clearance.get(key));
      }
    }
    if (settings.buttonChance() > 0 && availability.button()) {
      for (long key : keys) {
        smoother.placeButton(ShapeGrid.keyX(key), ShapeGrid.keyY(key), ShapeGrid.keyZ(key));
      }
    }
  }

  /**
   * Fills one empty voxel just outside the trunk. Resting on or under a full block, a shallow gap gets a stair and a
   * deeper one a slab. Beside the trunk only, a shallow gap gets a wall post and a deeper one a fence arm reaching in.
   *
   * @param x     Block x.
   * @param y     Block y.
   * @param z     Block z.
   * @param depth How far the voxel lies inside the true trunk surface; 0 or less leaves it empty.
   */
  private void smooth(int x, int y, int z, double depth) {
    if (depth <= 0 || grid.get(x, y, z) != null) {
      return;
    }
    boolean below = isFull(x, y - 1, z);
    boolean above = isFull(x, y + 1, z);
    Direction inward = deepestNeighbor(x, y, z);
    if (below || above) {
      Half half = below ? Half.BOTTOM : Half.TOP;
      if (depth <= settings.smoothStairDepth()) {
        Shape stair = inward == null ? outerStair(x, y, z, half) : fittedStair(x, y, z, inward, half);
        if (stair != null && availability.stairs()) {
          grid.put(x, y, z, stair);
        } else if (stair != null && availability.slab()) {
          grid.put(x, y, z, new Shape.Slab(half));
        }
      } else if (inward != null && depth <= settings.smoothSlabDepth() && availability.slab()) {
        grid.put(x, y, z, new Shape.Slab(half));
      }
    } else if (inward != null) {
      if (depth <= settings.smoothWallDepth()) {
        if (availability.wall()) {
          grid.put(x, y, z, WALL);
        }
      } else if (depth <= settings.smoothFenceDepth() && availability.fence()) {
        grid.put(x, y, z, new Shape.Fence(inward == Direction.NORTH, inward == Direction.EAST,
                inward == Direction.SOUTH, inward == Direction.WEST));
      }
    }
  }

  /**
   * A stair rising toward the trunk, turned into an inner corner when the trunk also fills a side.
   *
   * @param x      Block x.
   * @param y      Block y.
   * @param z      Block z.
   * @param facing Side the stair rises toward.
   * @param half   Whether the stair sits in the top or bottom half.
   */
  private Shape fittedStair(int x, int y, int z, Direction facing, Half half) {
    StairForm form = StairForm.STRAIGHT;
    if (isFull(x + facing.counterClockwise().dx(), y, z + facing.counterClockwise().dz())) {
      form = StairForm.INNER_LEFT;
    } else if (isFull(x + facing.clockwise().dx(), y, z + facing.clockwise().dz())) {
      form = StairForm.INNER_RIGHT;
    }
    return new Shape.Stair(facing, half, form);
  }

  /**
   * An outer-corner stair toward the deepest diagonal trunk block, or null when no diagonal touches the trunk.
   *
   * @param x    Block x.
   * @param y    Block y.
   * @param z    Block z.
   * @param half Whether the stair sits in the top or bottom half.
   */
  private Shape outerStair(int x, int y, int z, Half half) {
    int best = -1;
    double deepest = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < DIAGONALS.length; i++) {
      int nx = x + DIAGONALS[i][0];
      int nz = z + DIAGONALS[i][1];
      if (isFull(nx, y, nz)) {
        double value = clearance.getOrDefault(ShapeGrid.key(nx, y, nz), 0.0);
        if (value > deepest) {
          deepest = value;
          best = i;
        }
      }
    }
    return best < 0 ? null : new Shape.Stair(DIAGONAL_FACING[best], half, DIAGONAL_FORM[best]);
  }

  /**
   * The side whose full trunk block lies deepest inside the trunk, or null when no side touches it.
   *
   * @param x Block x.
   * @param y Block y.
   * @param z Block z.
   */
  private Direction deepestNeighbor(int x, int y, int z) {
    Direction best = null;
    double deepest = Double.NEGATIVE_INFINITY;
    for (Direction direction : Direction.values()) {
      int nx = x + direction.dx();
      int nz = z + direction.dz();
      if (isFull(nx, y, nz)) {
        double value = clearance.getOrDefault(ShapeGrid.key(nx, y, nz), 0.0);
        if (value > deepest) {
          deepest = value;
          best = direction;
        }
      }
    }
    return best;
  }

  /**
   * By chance, adds a button burl on the trunk's side, unless another button is within the spacing.
   *
   * @param x Block x.
   * @param y Block y.
   * @param z Block z.
   */
  private void placeButton(int x, int y, int z) {
    if (grid.get(x, y, z) != null) {
      return;
    }
    Direction support = deepestNeighbor(x, y, z);
    if (support == null || !TreeRandom.forPath(seed, "button/" + x + "," + y + "," + z).chance(settings.buttonChance())) {
      return;
    }
    int spacing = settings.buttonSpacing();
    budget.charge((2.0 * spacing + 1) * (2 * spacing + 1) * (2 * spacing + 1));
    for (int dy = -spacing; dy <= spacing; dy++) {
      for (int dz = -spacing; dz <= spacing; dz++) {
        for (int dx = -spacing; dx <= spacing; dx++) {
          if (grid.get(x + dx, y + dy, z + dz) instanceof Shape.Button) {
            return;
          }
        }
      }
    }
    grid.put(x, y, z, new Shape.Button(ButtonFace.WALL, support.opposite()));
  }

  /**
   * Whether a position holds a full block.
   *
   * @param x Block x.
   * @param y Block y.
   * @param z Block z.
   */
  private boolean isFull(int x, int y, int z) {
    return grid.get(x, y, z) instanceof Shape.FullBlock;
  }
}
