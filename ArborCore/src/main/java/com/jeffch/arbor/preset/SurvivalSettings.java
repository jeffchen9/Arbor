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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Survival mode's own voxelizer settings and blocks, stored apart from Advanced. A value never set in Survival follows
 * Advanced; a value set here stays here.
 *
 * @param shapes Voxelizer settings used in survival mode.
 * @param blocks Log, six-sided and leaf choices used in survival mode.
 * @param branchDensity Multiplier on the number of branches in survival mode, where every branch is a log.
 */
public record SurvivalSettings(VoxelSettings shapes, BlockSettings blocks, double branchDensity) {
  private static final Set<String> TREE_FAMILIES = Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
          "mangrove", "cherry", "pale_oak");
  private static final Set<String> NETHER_FAMILIES = Set.of("crimson", "warped");
  private static final Set<String> AZALEAS = Set.of("azalea", "flowering_azalea");

  /**
   * Whether the block is in the game's {@code prevents_nearby_leaf_decay} tag: a log, wood, or stripped form.
   *
   * @param block Block name, with or without state.
   */
  public static boolean isLog(String block) {
    String name = unstripped(bare(block));
    return TREE_FAMILIES.contains(without(name, "_log")) || TREE_FAMILIES.contains(without(name, "_wood"))
            || NETHER_FAMILIES.contains(without(name, "_stem")) || NETHER_FAMILIES.contains(without(name, "_hyphae"));
  }

  /**
   * Whether the block is a leaf block that can decay.
   *
   * @param block Block name, with or without state.
   */
  public static boolean isLeaves(String block) {
    String family = without(bare(block), "_leaves");
    return TREE_FAMILIES.contains(family) || AZALEAS.contains(family);
  }

  /**
   * The block name without its state or {@code minecraft:} prefix.
   *
   * @param block Block name.
   */
  private static String bare(String block) {
    int bracket = block.indexOf('[');
    String name = bracket < 0 ? block : block.substring(0, bracket);
    return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
  }

  /**
   * The name without a leading {@code stripped_}.
   *
   * @param name Block name.
   */
  private static String unstripped(String name) {
    return name.startsWith("stripped_") ? name.substring("stripped_".length()) : name;
  }

  /**
   * The name without the suffix, or empty when it does not end with it.
   *
   * @param name   Block name.
   * @param suffix Ending to remove.
   */
  private static String without(String name, String suffix) {
    return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : "";
  }

  /**
   * Advanced's valid choices. A role with none falls back to oak logs, or leaves matching the log.
   *
   * @param settings Advanced mode's blocks.
   */
  static BlockSettings palette(BlockSettings settings) {
    Map<BlockRole, BlockRule> rules = new EnumMap<>(BlockRole.class);
    BlockRule logs = only(settings.rules().get(BlockRole.LOG), SurvivalSettings::isLog);
    logs = logs != null ? logs : new BlockRule.Noise(WeightedBlocks.of("oak_log"), 1);
    rules.put(BlockRole.LOG, logs);
    // Six-sided wood and cut ends follow Survival's logs until added there, so no hidden rule applies.
    BlockRule leaves = only(settings.rules().get(BlockRole.LEAVES), SurvivalSettings::isLeaves);
    rules.put(BlockRole.LEAVES, leaves != null ? leaves
            : new BlockRule.Noise(WeightedBlocks.of(leavesFor(logs.blockNames().get(0))), 1));
    return BlockSettings.ofRules(rules);
  }

  /**
   * The rule when all its blocks are allowed. Otherwise, the allowed blocks as noise, or null when none are.
   *
   * @param rule    Rule to check.
   * @param allowed Test each block must pass.
   */
  private static BlockRule only(BlockRule rule, Predicate<String> allowed) {
    if (rule == null) {
      return null;
    }
    if (!(rule instanceof BlockRule.Noise) && rule.blockNames().stream().allMatch(allowed)) {
      return rule;
    }
    List<WeightedBlocks.Entry> kept = rule.weights().entries().stream()
            .filter(entry -> entry.weight() > 0 && allowed.test(entry.block())).toList();
    int clump = rule instanceof BlockRule.Noise noise ? noise.clump() : 1;
    return kept.isEmpty() ? null : new BlockRule.Noise(new WeightedBlocks(kept), clump);
  }

  /**
   * The leaves of the same tree as a log, such as {@code spruce_leaves} for {@code stripped_spruce_log}, or oak
   * leaves when the log has no matching leaves.
   *
   * @param log Log block name.
   */
  private static String leavesFor(String log) {
    String name = unstripped(bare(log));
    String family = name.endsWith("_log") ? without(name, "_log") : without(name, "_wood");
    return (TREE_FAMILIES.contains(family) ? family : "oak") + "_leaves";
  }
}
