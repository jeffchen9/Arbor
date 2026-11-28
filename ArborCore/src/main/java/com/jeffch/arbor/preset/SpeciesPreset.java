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

import com.jeffch.arbor.math.TreeRandom;

import java.util.List;

/**
 * A complete tree species, as loaded from a preset file.
 *
 * @param name         Species name.
 * @param tree         Whole-tree settings.
 * @param shapeControl Crown shape limits.
 * @param forceList    Named forces that bend branches.
 * @param shapes       Voxelizer settings.
 * @param blocks       Block choices for each role.
 * @param levels       Branch levels, with the root first and every parent before its children.
 * @param leaves       Leaf cluster generators.
 * @param plantLayers  Plant layers, in priority order.
 * @param survival     Survival mode's own voxelizer settings and blocks.
 * @param simple       Whole-tree adjustments from the Simple view.
 * @param decorators   Finishing touches added to the finished tree.
 */
public record SpeciesPreset(String name, TreeSettings tree, ShapeControl shapeControl, List<ForceSettings> forceList,
                            VoxelSettings shapes, BlockSettings blocks, List<LevelPreset> levels,
                            List<LeafGenerator> leaves, List<PlantLayer> plantLayers, SurvivalSettings survival,
                            SimpleSettings simple, DecoratorSettings decorators) {

  /**
   * This preset at the overall size rolled for one tree, so every generation step sees the same scale.
   *
   * @param seed Tree seed.
   */
  public SpeciesPreset forSeed(long seed) {
    double variance = tree.sizeVariance();
    if (variance <= 0) {
      return this;
    }
    double factor = 1 + TreeRandom.forPath(seed, "tree/sizeVariance").range(-variance, variance);
    TreeSettings sized = new TreeSettings(tree.sizeScale() * factor, tree.maxHeight(), tree.roll(), tree.trunkSpread(), 0, tree.ground(), tree.slopeRecovery());
    return new SpeciesPreset(name, sized, shapeControl, forceList, shapes, blocks, levels, leaves, plantLayers, survival, simple, decorators);
  }

  /**
   * This preset voxelized with survival mode's own settings and blocks, and its own branch density.
   */
  public SpeciesPreset inSurvival() {
    return new SpeciesPreset(name, tree, shapeControl, forceList, survival.shapes(), survival.blocks(),
            SimpleAdjuster.branchDensity(levels, survival.branchDensity()), leaves, plantLayers, survival, simple, decorators);
  }
}
