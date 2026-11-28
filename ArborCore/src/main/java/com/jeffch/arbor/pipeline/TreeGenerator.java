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

package com.jeffch.arbor.pipeline;

import com.jeffch.arbor.foliage.DensityField;
import com.jeffch.arbor.foliage.DensityGenerator;
import com.jeffch.arbor.preset.DecoratorSettings;
import com.jeffch.arbor.preset.LevelPreset;
import com.jeffch.arbor.preset.SimpleAdjuster;
import com.jeffch.arbor.preset.SpeciesPreset;
import com.jeffch.arbor.shape.*;
import com.jeffch.arbor.skeleton.GenerationBudget;
import com.jeffch.arbor.skeleton.Skeleton;
import com.jeffch.arbor.skeleton.SkeletonGenerator;

import java.util.List;
import java.util.Map;

/**
 * Runs the full generation pipeline for one preset and seed.
 */
public final class TreeGenerator {
  private TreeGenerator() {
  }

  /**
   * Generates one tree. The steps run in a fixed order: Simple sliders and size variety adjust the preset, the
   * skeleton grows, leaf density is built, block types are rolled, wood and leaves become shapes, plants or survival
   * fixes are added, the ground clips what it buries, loose pieces are removed, walls settle, block rules are
   * measured, and decorators run last. The same preset and seed always give the same tree.
   *
   * @param species Preset to generate.
   * @param seed    Tree seed.
   */
  public static GeneratedTree generate(SpeciesPreset species, long seed) {
    long start = System.nanoTime();
    SpeciesPreset preset = effective(species, seed);
    boolean survival = preset.shapes().survival();
    GenerationBudget budget = new GenerationBudget();
    Skeleton skeleton = SkeletonGenerator.generate(preset, seed, budget);
    DensityField density = DensityGenerator.generate(skeleton, preset, seed, budget);
    TreeBlocks blocks = TreeBlocks.pick(preset.blocks(), seed);
    ShapeAvailability availability = survival ? ShapeAvailability.survival() : ShapeAvailability.of(blocks);
    ShapeGrid shapes = new ShapeGrid();
    PlantLayerPlacer.Placed plants = new PlantLayerPlacer.Placed(List.of(), Map.of());
    if (!skeleton.branches().isEmpty()) {
      WoodVoxelizer.apply(skeleton, preset, seed, shapes, budget, density, availability);
      LeafVoxelizer.apply(density, preset.shapes(), seed, shapes, budget, availability);
      if (survival) {
        SurvivalBlocks.addFoliageSpines(shapes, skeleton, preset);
      } else {
        plants = PlantLayerPlacer.apply(skeleton, preset, density, seed, shapes, budget);
      }
      // Nothing goes below the ground, flat or sloped, such as long hanging strands.
      shapes.removeIf(preset.tree().ground()::buries);
      if (survival) {
        SurvivalBlocks.finish(shapes, skeleton);
      } else {
        // Nothing floats: whatever cannot reach a trunk through touching blocks, corners included, goes.
        DetachedBlocks.apply(shapes, skeleton);
      }
      WallResolver.apply(shapes);
      blocks = blocks.placed(shapes, skeleton, preset.levels().stream().map(LevelPreset::name).toList());
      // Last, so hanging decorations cannot change the height range the block rules were measured on.
      DecoratorSettings decorators = preset.decorators();
      if (!survival) {
        Decorators.apply(shapes, decorators, preset.tree().ground(), seed,
                decorators.pinecones().enabled() && decorators.pinecones().minLevel() > 0
                        ? BranchLevels.of(shapes, skeleton) : Map.of());
      }
    }
    return new GeneratedTree(seed, skeleton, shapes, density, System.nanoTime() - start, blocks, plants.counts(shapes),
            preset.tree().ground());
  }
  /**
   * The preset exactly as one seed's tree is grown from it: Simple sliders, this seed's size variety, and Survival's own
   * settings when the tree is in survival mode.
   *
   * @param species Preset as written.
   * @param seed    Tree seed.
   */
  public static SpeciesPreset effective(SpeciesPreset species, long seed) {
    SpeciesPreset sized = SimpleAdjuster.apply(species).forSeed(seed);
    return sized.shapes().survival() ? sized.inSurvival() : sized;
  }
}
