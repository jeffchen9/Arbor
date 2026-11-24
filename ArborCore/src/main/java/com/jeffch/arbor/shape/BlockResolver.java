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

import com.jeffch.arbor.preset.BlockRole;
import com.jeffch.arbor.preset.SurvivalSettings;

import java.util.*;

/**
 * Turns a generic shape into a block state string for the blocks chosen in {@link TreeBlocks}.
 */
public final class BlockResolver {
  private static final String NAMESPACE = "minecraft:";
  private static final String UNTRACKED_LEAF_DISTANCE = "7";
  private static final Set<String> LOG_SUFFIXES = Set.of("_log", "_wood", "_stem", "_hyphae", "_pillar");
  private static final Set<String> LOG_NAMES = Set.of("bamboo_block", "stripped_bamboo_block", "hay_block",
          "bone_block", "basalt", "polished_basalt", "deepslate", "infested_deepslate");

  private BlockResolver() {
  }

  /**
   * The state such as {@code minecraft:oak_fence[north=true,...]}, or null when the shape's role is off.
   *
   * @param shape  Shape to resolve.
   * @param blocks The tree's chosen blocks.
   */
  public static String state(Shape shape, TreeBlocks blocks) {
    BlockRole role = role(shape, blocks);
    return render(shape, role == null ? null : blocks.block(role));
  }

  /**
   * The block type a shape uses; null for a plant, which names its own block.
   *
   * @param shape  Shape to look up.
   * @param blocks The tree's chosen blocks.
   */
  private static BlockRole role(Shape shape, TreeBlocks blocks) {
    return switch (shape) {
      case Shape.FullBlock full -> full.cutEnd()
              ? blocks.has(BlockRole.CUT_END) ? BlockRole.CUT_END : BlockRole.LOG
              : blocks.has(BlockRole.JUNCTION) ? BlockRole.JUNCTION : BlockRole.LOG;
      case Shape.Fence f -> BlockRole.FENCE;
      case Shape.FenceGate g -> BlockRole.GATE;
      case Shape.Wall w -> BlockRole.WALL;
      case Shape.Stair s -> BlockRole.STAIRS;
      case Shape.Slab s -> BlockRole.SLAB;
      case Shape.Button b -> BlockRole.BUTTON;
      case Shape.Leaves l -> BlockRole.LEAVES;
      case Shape.Sapling s -> BlockRole.SAPLING;
      case Shape.DeadBush d -> BlockRole.DEAD_BUSH;
      case Shape.Plant p -> null;
    };
  }

  /**
   * The full block state for a shape made of this block: a plant uses its own block and half, every other shape adds
   * the properties its form needs (log axis, fence arms, stair facing and so on).
   *
   * @param shape Shape to render.
   * @param block Block it is made of.
   */
  private static String render(Shape shape, String block) {
    if (shape instanceof Shape.Plant p) {
      return merged(p.block(), switch (p.half()) {
        case SINGLE, FULL -> "";
        case LOWER -> "[half=lower]";
        case UPPER -> "[half=upper]";
      });
    }
    if (block == null) {
      return null;
    }
    return merged(block, switch (shape) {
      case Shape.FullBlock full -> isLogLike(block)
              ? "[axis=" + (full.axis() == GrainAxis.NONE ? "y" : name(full.axis())) + "]" : "";
      case Shape.Fence f -> "[north=" + f.north() + ",east=" + f.east() + ",south=" + f.south()
              + ",west=" + f.west() + "]";
      case Shape.FenceGate g -> "[facing=" + name(g.facing()) + ",in_wall=" + g.inWall()
              + ",open=" + g.open() + ",powered=false]";
      case Shape.Wall w -> "[up=" + w.post() + ",north=" + name(w.north())
              + ",east=" + name(w.east()) + ",south=" + name(w.south()) + ",west=" + name(w.west()) + "]";
      case Shape.Stair s -> "[facing=" + name(s.facing()) + ",half=" + name(s.half())
              + ",shape=" + name(s.form()) + "]";
      case Shape.Slab s -> "[type=" + name(s.half()) + "]";
      case Shape.Button b -> "[face=" + name(b.face()) + ",facing=" + name(b.facing()) + ",powered=false]";
      case Shape.Leaves l -> l.distance() > 0
              ? "[distance=" + l.distance() + ",persistent=false,waterlogged=false]" : "";
      case Shape.Sapling s -> "";
      case Shape.DeadBush d -> "";
      case Shape.Plant p -> "";
    });
  }

  /**
   * The block with generated properties added to any state it already carries, in one bracket group.
   *
   * @param block     Block name, possibly with its own state.
   * @param generated Properties to add, as a {@code [...]} group.
   */
  private static String merged(String block, String generated) {
    String name = TreeBlocks.stateless(block);
    String qualified = name.startsWith(NAMESPACE) ? name : NAMESPACE + name;
    Map<String, String> properties = new LinkedHashMap<>();
    addProperties(properties, block.substring(name.length()));
    addProperties(properties, generated);
    // Only survival's tracked leaves may decay. Any other leaf block is placed as if by a player.
    if (SurvivalSettings.isLeaves(name) && !generated.contains("persistent=false")) {
      String distance = properties.getOrDefault("distance", UNTRACKED_LEAF_DISTANCE);
      String waterlogged = properties.getOrDefault("waterlogged", "false");
      properties.clear();
      properties.put("distance", distance);
      properties.put("persistent", "true");
      properties.put("waterlogged", waterlogged);
    }
    if (properties.isEmpty()) {
      return qualified;
    }
    StringBuilder text = new StringBuilder(qualified).append('[');
    properties.forEach((key, value) -> text.append(text.charAt(text.length() - 1) == '[' ? "" : ",").append(key).append('=').append(value));
    return text.append(']').toString();
  }

  /**
   * Adds each {@code key=value} pair from a {@code [...]} group, replacing any earlier value for the same key.
   *
   * @param properties Receives the pairs.
   * @param group      A {@code [...]} group.
   */
  private static void addProperties(Map<String, String> properties, String group) {
    if (group.length() < 3) {
      return;
    }
    for (String pair : group.substring(1, group.length() - 1).split(",")) {
      int equals = pair.indexOf('=');
      properties.put(pair.substring(0, equals), pair.substring(equals + 1));
    }
  }

  /**
   * Whether a block has an {@code axis} property, like logs, wood, stems, pillars and a few others.
   *
   * @param block Block name.
   */
  private static boolean isLogLike(String block) {
    String stateless = TreeBlocks.stateless(block);
    String bare = stateless.startsWith(NAMESPACE) ? stateless.substring(NAMESPACE.length()) : stateless;
    return LOG_NAMES.contains(bare) || LOG_SUFFIXES.stream().anyMatch(bare::endsWith);
  }

  /**
   * An enum value as the lowercase text block states use.
   *
   * @param value Enum value.
   */
  private static String name(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }

  /**
   * Resolves shapes at positions for one tree, reusing states that repeat.
   */
  public static final class Cached {
    private final TreeBlocks blocks;
    private final Map<Shape, Map<String, String>> states = new HashMap<>();
    private final Map<Shape, String> plants = new HashMap<>();

    public Cached(TreeBlocks blocks) {
      this.blocks = blocks;
    }

    /**
     * The state of a shape at a position, or null when its role is off.
     *
     * @param shape Shape to resolve.
     * @param x     Block x.
     * @param y     Block y.
     * @param z     Block z.
     */
    public String at(Shape shape, int x, int y, int z) {
      BlockRole role = role(shape, blocks);
      if (role == null) {
        return plants.computeIfAbsent(shape, plant -> render(plant, null));
      }
      String block = blocks.blockAt(role, x, y, z);
      return block == null ? null
              : states.computeIfAbsent(shape, key -> new HashMap<>()).computeIfAbsent(block, name -> render(shape, name));
    }
  }
}
