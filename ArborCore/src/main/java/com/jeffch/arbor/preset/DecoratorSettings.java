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
 * Finishing touches added after a tree is fully voxelized.
 *
 * @param pinecones Buttons on leaves.
 * @param vines     Strands hanging from leaves and logs.
 * @param moss      Patches of another block replacing logs.
 */
public record DecoratorSettings(Pinecones pinecones, Vines vines, Moss moss) {
  public boolean anyEnabled() {
    return pinecones.enabled() || vines.enabled() || moss.enabled();
  }

  /**
   * @param enabled        Whether this decorator runs.
   * @param chance         Chance for each open spot beside a leaf.
   * @param undersideShare Share of pinecones under a block rather than on its side; 1 is underneath only.
   * @param minHeight      Lowest position, as a fraction of tree height.
   * @param maxHeight      Highest position, as a fraction of tree height.
   * @param minLevel       Lowest branch level that holds pinecones; 0 is the trunk, so 2 skips the trunk and main branches.
   * @param blocks         Weighted blocks to place.
   */
  public record Pinecones(boolean enabled, double chance, double undersideShare, double minHeight, double maxHeight,
                          int minLevel, WeightedBlocks blocks) {
  }

  /**
   * @param enabled   Whether this decorator runs.
   * @param chance    Chance for each open spot to start a strand.
   * @param minLength Shortest strand in blocks.
   * @param maxLength Longest strand in blocks.
   * @param blocks    Weighted blocks to place.
   */
  public record Vines(boolean enabled, double chance, int minLength, int maxLength, WeightedBlocks blocks) {
  }

  /**
   * @param enabled   Whether this decorator runs.
   * @param amount    How much exposed log is covered, from 0 to 1.
   * @param patchSize Approximate patch width in blocks.
   * @param upward    How strongly moss keeps to logs with an open top, from 0 to 1.
   * @param minHeight Lowest position, as a fraction of tree height.
   * @param maxHeight Highest position, as a fraction of tree height.
   * @param blocks    Weighted blocks to place.
   */
  public record Moss(boolean enabled, double amount, int patchSize, double upward, double minHeight, double maxHeight,
                     WeightedBlocks blocks) {
  }
}
