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
import com.jeffch.arbor.preset.GroundPlane;
import com.jeffch.arbor.shape.ShapeGrid;
import com.jeffch.arbor.shape.TreeBlocks;
import com.jeffch.arbor.skeleton.Skeleton;

import java.util.List;

/**
 * Everything produced for one preset and seed.
 *
 * @param seed            Seed that produced this tree.
 * @param skeleton        Branch hierarchy, also shown as the wireframe.
 * @param shapes          Final grid of wood, leaf and plant shapes.
 * @param density         3D leaf density field.
 * @param generationNanos Time the pipeline took, in nanoseconds.
 * @param blocks          Block chosen for each role on this tree.
 * @param plantLayers     Names of the plant layers active on this tree.
 * @param ground          Plane this tree stands on.
 */
public record GeneratedTree(long seed, Skeleton skeleton, ShapeGrid shapes, DensityField density, long generationNanos,
                            TreeBlocks blocks, List<String> plantLayers, GroundPlane ground) {
  public GeneratedTree(long seed, Skeleton skeleton, ShapeGrid shapes, DensityField density, long generationNanos) {
    this(seed, skeleton, shapes, density, generationNanos, TreeBlocks.oakDefaults(), List.of(), GroundPlane.FLAT);
  }
}
