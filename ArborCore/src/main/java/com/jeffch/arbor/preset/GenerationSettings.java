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

/**
 * Where and how many children a level places on each parent.
 *
 * @param mode            Placement method: random, even count, by length, in groups, leaf-like patterns, at bends, or at the
 *                        origin.
 * @param first           Earliest position on the parent, from 0 (base) to 1 (tip).
 * @param last            Latest position on the parent, from 0 (base) to 1 (tip).
 * @param clip            Whether children moved outside first and last by offsets are removed instead of kept.
 * @param rotation        Degrees added to each child's roll around the parent.
 * @param position        Offset added to each child's position, as a fraction of the parent's length.
 * @param sink            Distance in blocks each child starts behind its attachment point, inside the parent.
 * @param pruning         Curve over parent position; children where it is below 0.5 are removed.
 * @param knockout        Probability from 0 to 1 of removing each child.
 * @param extendParent    Extra children placed at the parent's tip that continue its direction.
 * @param count           Children in total for flood and absolute modes, or per group for interval mode; also the root trunk
 *                        count.
 * @param density         Children per block of parent length in proportional mode.
 * @param frequency       Distance in blocks between groups in interval mode.
 * @param phyllotaxy      Leaf-like arrangement used in phyllotaxy mode.
 * @param childrenPerNode Children in each ring for the whorled phyllotaxy pattern.
 * @param internodeLength Distance in blocks between nodes in phyllotaxy mode.
 * @param spread          Random shift of each child along the parent, as a fraction of the group spacing.
 * @param spiral          Degrees of roll added per child or group.
 * @param limit           Maximum groups or bends per parent; 0 means no limit.
 * @param align           How far children turn toward the sky around the parent, from 0 to 1.
 * @param balance         How far alternate groups rotate to fill the gaps of the previous group, from 0 to 1.
 * @param threshold       Smallest parent bend, in degrees, that can grow a child in bifurcation mode.
 * @param spacing         Minimum distance in blocks between chosen bends in bifurcation mode.
 * @param locality        Bend preference in bifurcation mode, from 0 (sharpest first) to 1 (gentlest first).
 */
public record GenerationSettings(Mode mode, double first, double last, boolean clip, Param rotation, Param position,
                                 Param sink, Curve pruning, Param knockout, int extendParent, Param count,
                                 Param density, Param frequency, Phyllotaxy phyllotaxy, int childrenPerNode,
                                 Param internodeLength, double spread, double spiral, int limit, double align,
                                 double balance, double threshold, double spacing, double locality) {

  /**
   * Placement method: random positions, a fixed count spaced evenly, a count per block of length, groups at a set
   * spacing, plant-like node patterns, at the parent's bends, or all at the parent's base.
   */
  public enum Mode {FLOOD, ABSOLUTE, PROPORTIONAL, INTERVAL, PHYLLOTAXY, BIFURCATION, PARENT}

  /**
   * Node patterns: pairs that face one way, pairs that turn 90 degrees each node, pairs at random rolls, single
   * children on alternate sides, single children at random rolls, or rings of several.
   */
  public enum Phyllotaxy {
    OPPOSITE_DISTICHOUS, OPPOSITE_DECUSSATE, OPPOSITE_RANDOM, ALTERNATE_DISTICHOUS, ALTERNATE_RANDOM, WHORLED
  }
}
