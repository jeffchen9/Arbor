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

import java.util.Map;

/**
 * One level of the branch hierarchy, such as trunk, bough or twig.
 *
 * @param name          Unique level name.
 * @param parent        Name of the level this one grows from; null for the root level.
 * @param segmentLength Length of each spine segment, in blocks.
 * @param generation    Where and how many branches this level places.
 * @param length        How long each branch grows.
 * @param orientation   The direction each branch starts in.
 * @param shape         Bending applied as the branch grows.
 * @param noise         Random wander of the branch.
 * @param jink          Straightening into angular pieces.
 * @param path          Regular wave along the branch.
 * @param breakage      Random shortening of branches.
 * @param prune         Random removal of whole branches.
 * @param forces        This level's response multiplier for each named force.
 * @param radius        Branch thickness and taper.
 * @param flares        Root-like widening at the branch base.
 * @param wood          Whether this level becomes wood; when false it only guides leaves and plant layers.
 */
public record LevelPreset(
        String name,
        String parent,
        double segmentLength,
        GenerationSettings generation,
        LengthSettings length,
        OrientationSettings orientation,
        ShapeSettings shape,
        NoiseSettings noise,
        JinkSettings jink,
        PathSettings path,
        BreakSettings breakage,
        PruneSettings prune,
        Map<String, Param> forces,
        RadiusSettings radius,
        FlareSettings flares,
        boolean wood) {
}
