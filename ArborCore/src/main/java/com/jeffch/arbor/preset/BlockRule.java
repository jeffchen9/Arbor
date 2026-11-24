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

import java.util.*;

/**
 * How one block type chooses its block at each position in a tree. A single block is a noise rule with one entry.
 */
public sealed interface BlockRule {
  int MAX_CLUMP = 16;

  /**
   * Every block this rule names, without repeats.
   */
  default List<String> blockNames() {
    Set<String> names = new LinkedHashSet<>();
    switch (this) {
      case Noise noise -> noise.blocks().entries().forEach(entry -> names.add(entry.block()));
      case Height height -> height.bands().forEach(band -> names.add(band.block()));
      case ByLevel level -> {
        names.addAll(level.levels().values());
        names.add(level.other());
      }
    }
    return List.copyOf(names);
  }

  /**
   * The rule's blocks as a weighted list, for choosing one block that stands for the whole tree.
   */
  default WeightedBlocks weights() {
    if (this instanceof Noise noise) {
      return noise.blocks();
    }
    List<WeightedBlocks.Entry> entries = new ArrayList<>();
    blockNames().forEach(block -> entries.add(new WeightedBlocks.Entry(block, 1)));
    return new WeightedBlocks(entries);
  }

  /**
   * Weighted blocks mixed through the tree.
   *
   * @param blocks The choices. An {@link WeightedBlocks#OFF} weight is a per-tree chance the type is absent.
   * @param clump  Patch size in blocks; 1 is speckled.
   */
  record Noise(WeightedBlocks blocks, int clump) implements BlockRule {
  }

  /**
   * One height band.
   *
   * @param below Top of the band as a fraction of tree height; the last band is 1.
   * @param block Block used in this band.
   */
  record Band(double below, String block) {
  }

  /**
   * Blocks by height, lowest band first.
   *
   * @param bands The bands, with strictly increasing tops ending at 1.
   */
  record Height(List<Band> bands) implements BlockRule {
    public Height {
      bands = List.copyOf(bands);
    }
  }

  /**
   * Blocks by branch level.
   *
   * @param levels Block for each level name.
   * @param other  Block for levels not listed.
   */
  record ByLevel(Map<String, String> levels, String other) implements BlockRule {
    public ByLevel {
      levels = Collections.unmodifiableMap(new TreeMap<>(levels));
    }
  }
}
