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

import java.util.EnumMap;
import java.util.Map;

/**
 * The block choices a preset sets. A role left out is derived from another role or, for plain roles, defaults to oak.
 *
 * @param roles Each role's blocks as one weighted list.
 * @param rules How each role chooses among its blocks at each position.
 */
public record BlockSettings(Map<BlockRole, WeightedBlocks> roles, Map<BlockRole, BlockRule> rules) {
  /**
   * Copies both maps so later changes to the originals cannot reach these settings.
   *
   * @param roles Weighted blocks for each block type.
   * @param rules Rule for each block type.
   */
  public BlockSettings {
    roles = Map.copyOf(roles);
    rules = Map.copyOf(rules);
  }

  /**
   * Plain weighted lists, each mixed as speckled noise.
   *
   * @param roles Weighted blocks for each block type.
   */
  public BlockSettings(Map<BlockRole, WeightedBlocks> roles) {
    this(roles, noise(roles));
  }

  /**
   * A noise rule with no clumping for each role, so every block picks independently.
   *
   * @param roles Weighted blocks for each block type.
   */
  private static Map<BlockRole, BlockRule> noise(Map<BlockRole, WeightedBlocks> roles) {
    Map<BlockRole, BlockRule> rules = new EnumMap<>(BlockRole.class);
    roles.forEach((role, blocks) -> rules.put(role, new BlockRule.Noise(blocks, 1)));
    return rules;
  }

  /**
   * Settings built from rules.
   *
   * @param rules Rule for each block type.
   */
  public static BlockSettings ofRules(Map<BlockRole, BlockRule> rules) {
    Map<BlockRole, WeightedBlocks> roles = new EnumMap<>(BlockRole.class);
    rules.forEach((role, rule) -> roles.put(role, rule.weights()));
    return new BlockSettings(roles, rules);
  }

  /**
   * The built-in oak choices for every role that is not derived from another.
   */
  public static BlockSettings defaults() {
    Map<BlockRole, WeightedBlocks> roles = new EnumMap<>(BlockRole.class);
    roles.put(BlockRole.LOG, WeightedBlocks.of("oak_log"));
    roles.put(BlockRole.PLANKS, WeightedBlocks.of("oak_planks"));
    roles.put(BlockRole.WALL, WeightedBlocks.of("mud_brick_wall"));
    roles.put(BlockRole.FENCE, WeightedBlocks.of("oak_fence"));
    roles.put(BlockRole.GATE, WeightedBlocks.of("oak_fence_gate"));
    roles.put(BlockRole.BUTTON, WeightedBlocks.of("oak_button"));
    roles.put(BlockRole.SAPLING, WeightedBlocks.of("oak_sapling"));
    roles.put(BlockRole.DEAD_BUSH, WeightedBlocks.of("dead_bush"));
    roles.put(BlockRole.LEAVES, WeightedBlocks.of("oak_leaves"));
    return new BlockSettings(roles);
  }
}
