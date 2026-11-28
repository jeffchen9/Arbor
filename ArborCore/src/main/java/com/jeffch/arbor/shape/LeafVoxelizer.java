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

import com.jeffch.arbor.foliage.DensityField;
import com.jeffch.arbor.math.TreeRandom;
import com.jeffch.arbor.preset.VoxelSettings;
import com.jeffch.arbor.skeleton.GenerationBudget;
import com.jeffch.arbor.skeleton.GenerationLimitException;

/**
 * Converts dense leaf density into leaf blocks. Plants are placed afterwards by {@link PlantLayerPlacer}.
 */
public final class LeafVoxelizer {
  private static final double EDGE_SPREAD = 0.25;

  private LeafVoxelizer() {
  }

  /**
   * Fills only empty voxels, so wood always stays. A leaf block density of 0 turns this off, and so does a leaf
   * block that is off for the tree.
   *
   * @param density      Leaf density.
   * @param settings     Voxelizer settings.
   * @param seed         Tree seed.
   * @param grid         The tree's shapes; leaves are added to it.
   * @param budget       Work limit.
   * @param availability Which shapes the tree blocks allow.
   */
  public static void apply(DensityField density, VoxelSettings settings, long seed, ShapeGrid grid,
                           GenerationBudget budget, ShapeAvailability availability) {
    if (settings.leafBlockDensity() <= 0 || density.isEmpty() || !availability.leaves()) {
      return;
    }
    int[] min = density.min();
    int[] size = density.dimensions();
    float[] data = density.data();
    budget.charge(data.length);
    TreeRandom random = TreeRandom.forPath(seed, "leaf-shapes");
    double lowest = settings.leafBlockDensity() * (1 - settings.voxelRandomness() * EDGE_SPREAD);
    int index = 0;
    for (int dz = 0; dz < size[2]; dz++) {
      for (int dy = 0; dy < size[1]; dy++) {
        for (int dx = 0; dx < size[0]; dx++, index++) {
          double value = data[index];
          if (value < lowest) {
            continue;
          }
          double edgeRoll = random.nextDouble();
          int x = min[0] + dx;
          int y = min[1] + dy;
          int z = min[2] + dz;
          if (grid.get(x, y, z) != null) {
            continue;
          }
          double blockDensity = settings.leafBlockDensity()
                  * (1 + settings.voxelRandomness() * EDGE_SPREAD * (2 * edgeRoll - 1));
          if (value >= blockDensity) {
            grid.put(x, y, z, new Shape.Leaves());
          }
          if (grid.size() > ShapeGrid.MAX_SHAPES) {
            throw new GenerationLimitException("tree exceeds the limit of " + ShapeGrid.MAX_SHAPES + " shapes");
          }
        }
      }
    }
  }
}
