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

import com.jeffch.arbor.math.TreeRandom;
import com.jeffch.arbor.preset.BlockRole;
import com.jeffch.arbor.preset.BlockRule;
import com.jeffch.arbor.preset.BlockSettings;
import com.jeffch.arbor.preset.WeightedBlocks;
import com.jeffch.arbor.skeleton.Skeleton;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Which block types a tree uses and how each chooses its block. A missing role is switched off.
 *
 * @param picks     One block standing for each role that is on; a missing role is off.
 * @param placement What varies the block by position, or null when each role always uses its pick.
 */
public record TreeBlocks(Map<BlockRole, String> picks, Placement placement) {
  private static final long GOLDEN = 0x9E3779B97F4A7C15L;

  public TreeBlocks {
    picks = Map.copyOf(picks);
  }

  public TreeBlocks(Map<BlockRole, String> picks) {
    this(picks, null);
  }

  /**
   * Decides which roles each tree uses. Each role rolls from its own stream, so no role affects another.
   *
   * @param settings The preset's block settings.
   * @param seed     Tree seed.
   */
  public static TreeBlocks pick(BlockSettings settings, long seed) {
    Map<BlockRole, String> picks = new EnumMap<>(BlockRole.class);
    Map<BlockRole, BlockRule> rules = new EnumMap<>(BlockRole.class);
    settings.roles().forEach((role, choices) -> {
      String block = choices.pick(TreeRandom.forPath(seed, "blocks/" + role.jsonKey()).nextDouble());
      if (!block.equals(WeightedBlocks.OFF)) {
        picks.put(role, block);
        rules.put(role, settings.rules().get(role));
      }
    });
    String log = stateless(picks.get(BlockRole.LOG));
    if (log != null && !settings.roles().containsKey(BlockRole.JUNCTION)) {
      picks.put(BlockRole.JUNCTION, junctionFor(log));
    }
    String planks = stateless(picks.get(BlockRole.PLANKS));
    if (planks != null && planks.endsWith("_planks")) {
      String family = planks.substring(0, planks.length() - "_planks".length());
      if (!settings.roles().containsKey(BlockRole.STAIRS)) {
        picks.put(BlockRole.STAIRS, family + "_stairs");
      }
      if (!settings.roles().containsKey(BlockRole.SLAB)) {
        picks.put(BlockRole.SLAB, family + "_slab");
      }
    }
    return new TreeBlocks(picks, new Placement(seed, Map.copyOf(rules), 0, 0, Map.of(), List.of()));
  }

  /**
   * The roll of the nearest jittered point, so equal rolls form irregular patches about {@code size} blocks wide.
   *
   * @param stream Random stream seed.
   * @param size   Patch size in blocks.
   * @param x      Block x.
   * @param y      Block y.
   * @param z      Block z.
   */
  private static double clumpRoll(long stream, int size, int x, int y, int z) {
    int cellX = Math.floorDiv(x, size);
    int cellY = Math.floorDiv(y, size);
    int cellZ = Math.floorDiv(z, size);
    double best = Double.MAX_VALUE;
    long nearest = 0;
    for (int i = cellX - 1; i <= cellX + 1; i++) {
      for (int j = cellY - 1; j <= cellY + 1; j++) {
        for (int k = cellZ - 1; k <= cellZ + 1; k++) {
          long hash = cell(stream, i, j, k);
          double dx = (i + fraction(mix(hash + 1))) * size - x;
          double dy = (j + fraction(mix(hash + 2))) * size - y;
          double dz = (k + fraction(mix(hash + 3))) * size - z;
          double distance = dx * dx + dy * dy + dz * dz;
          if (distance < best) {
            best = distance;
            nearest = hash;
          }
        }
      }
    }
    return fraction(nearest);
  }

  /**
   * A hash of one grid cell for one random stream.
   *
   * @param stream Random stream seed.
   * @param x      Cell x.
   * @param y      Cell y.
   * @param z      Cell z.
   */
  private static long cell(long stream, int x, int y, int z) {
    return mix(mix(mix(stream ^ (x * GOLDEN)) ^ (y * GOLDEN)) ^ (z * GOLDEN));
  }

  /**
   * A SplitMix64 step, which scrambles a number into a well-spread hash.
   *
   * @param value Number to scramble.
   */
  private static long mix(long value) {
    long z = value + GOLDEN;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }

  /**
   * A hash as a value in [0, 1).
   *
   * @param hash Hash to convert.
   */
  private static double fraction(long hash) {
    return (hash >>> 11) * 0x1.0p-53;
  }

  /**
   * The block name without any trailing {@code [...]} state; null stays null.
   *
   * @param block Block name, possibly with state, or null.
   */
  static String stateless(String block) {
    int bracket = block == null ? -1 : block.indexOf('[');
    return bracket < 0 ? block : block.substring(0, bracket);
  }

  /**
   * The six-sided wood made from a log, such as {@code oak_wood}; other blocks stand for themselves.
   *
   * @param log Log block name.
   */
  private static String junctionFor(String log) {
    if (log.endsWith("_log")) {
      return log.substring(0, log.length() - "_log".length()) + "_wood";
    }
    if (log.endsWith("crimson_stem") || log.endsWith("warped_stem")) {
      return log.substring(0, log.length() - "_stem".length()) + "_hyphae";
    }
    return log;
  }

  /**
   * Every type as plain oak, for tests and for drawing a tree before its blocks are known.
   */
  public static TreeBlocks oakDefaults() {
    return new TreeBlocks(Map.ofEntries(
            Map.entry(BlockRole.LOG, "oak_log"),
            Map.entry(BlockRole.JUNCTION, "oak_wood"),
            Map.entry(BlockRole.PLANKS, "oak_planks"),
            Map.entry(BlockRole.STAIRS, "oak_stairs"),
            Map.entry(BlockRole.SLAB, "oak_slab"),
            Map.entry(BlockRole.WALL, "mud_brick_wall"),
            Map.entry(BlockRole.FENCE, "oak_fence"),
            Map.entry(BlockRole.GATE, "oak_fence_gate"),
            Map.entry(BlockRole.BUTTON, "oak_button"),
            Map.entry(BlockRole.SAPLING, "oak_sapling"),
            Map.entry(BlockRole.DEAD_BUSH, "dead_bush"),
            Map.entry(BlockRole.LEAVES, "oak_leaves")));
  }

  /**
   * The block standing for a role across the tree, or null when the role is off.
   *
   * @param role Block type.
   */
  public String block(BlockRole role) {
    return picks.get(role);
  }

  public boolean has(BlockRole role) {
    return picks.containsKey(role);
  }

  /**
   * These blocks with the finished tree's height range and, when a rule needs them, branch levels.
   *
   * @param grid       The finished tree's shapes.
   * @param skeleton   The tree's branches.
   * @param levelNames Names of the preset's levels, in order.
   */
  public TreeBlocks placed(ShapeGrid grid, Skeleton skeleton, List<String> levelNames) {
    boolean needsLevels = placement.rules().values().stream().anyMatch(BlockRule.ByLevel.class::isInstance);
    return new TreeBlocks(picks, new Placement(placement.seed(), placement.rules(), grid.min()[1], grid.max()[1],
            needsLevels ? BranchLevels.of(grid, skeleton) : Map.of(), List.copyOf(levelNames)));
  }

  /**
   * The block for a role at a position, or null when the role is off.
   *
   * @param role Block type.
   * @param x    Block x.
   * @param y    Block y.
   * @param z    Block z.
   */
  public String blockAt(BlockRole role, int x, int y, int z) {
    String pick = picks.get(role);
    if (pick == null || placement == null) {
      return pick;
    }
    BlockRule rule = placement.rules().get(role);
    if (rule != null) {
      return switch (rule) {
        case BlockRule.Noise noise -> noiseBlock(role, noise, x, y, z);
        case BlockRule.Height height -> heightBlock(height, y);
        case BlockRule.ByLevel level -> levelBlock(level, x, y, z);
      };
    }
    return switch (role) {
      case JUNCTION -> junctionFor(stateless(blockAt(BlockRole.LOG, x, y, z)));
      case STAIRS -> plankFamily(x, y, z, "_stairs", pick);
      case SLAB -> plankFamily(x, y, z, "_slab", pick);
      default -> pick;
    };
  }

  /**
   * The stairs or slab matching the planks used at this position, such as {@code spruce_stairs}.
   *
   * @param x        Block x.
   * @param y        Block y.
   * @param z        Block z.
   * @param suffix   {@code _stairs} or {@code _slab}.
   * @param fallback Block used when the planks have no matching stairs or slab.
   */
  private String plankFamily(int x, int y, int z, String suffix, String fallback) {
    String planks = stateless(blockAt(BlockRole.PLANKS, x, y, z));
    return planks != null && planks.endsWith("_planks")
            ? planks.substring(0, planks.length() - "_planks".length()) + suffix : fallback;
  }

  /**
   * The block a noise rule picks here: an independent roll per block, or one roll per patch when clumped.
   *
   * @param role  Block type.
   * @param noise The type's noise rule.
   * @param x     Block x.
   * @param y     Block y.
   * @param z     Block z.
   */
  private String noiseBlock(BlockRole role, BlockRule.Noise noise, int x, int y, int z) {
    long stream = mix(placement.seed() ^ (role.jsonKey().hashCode() * GOLDEN));
    double roll = noise.clump() <= 1 ? fraction(cell(stream, x, y, z)) : clumpRoll(stream, noise.clump(), x, y, z);
    return noise.blocks().pickBlock(roll);
  }

  /**
   * The band a height falls in, measured from the lowest to the highest block of the tree.
   *
   * @param rule The type's height rule.
   * @param y    Block y.
   */
  private String heightBlock(BlockRule.Height rule, int y) {
    double along = (y - placement.minY()) / (double) Math.max(1, placement.maxY() - placement.minY());
    for (BlockRule.Band band : rule.bands()) {
      if (along < band.below()) {
        return band.block();
      }
    }
    return rule.bands().get(rule.bands().size() - 1).block();
  }

  /**
   * The block for this position's branch level, or the rule's other block when the level is not named.
   *
   * @param rule The type's level rule.
   * @param x    Block x.
   * @param y    Block y.
   * @param z    Block z.
   */
  private String levelBlock(BlockRule.ByLevel rule, int x, int y, int z) {
    Integer level = placement.levels().get(ShapeGrid.key(x, y, z));
    List<String> names = placement.levelNames();
    if (level == null || names.isEmpty()) {
      return rule.other();
    }
    return rule.levels().getOrDefault(names.get(Math.min(level, names.size() - 1)), rule.other());
  }

  /**
   * The tree facts that block rules read.
   *
   * @param seed       Tree seed.
   * @param rules      Rule of each role the preset set and that is on.
   * @param minY       Lowest placed block.
   * @param maxY       Highest placed block.
   * @param levels     Branch level by voxel key, filled only when a level rule needs it.
   * @param levelNames Level names by index.
   */
  public record Placement(long seed, Map<BlockRole, BlockRule> rules, int minY, int maxY, Map<Long, Integer> levels,
                          List<String> levelNames) {
  }
}
