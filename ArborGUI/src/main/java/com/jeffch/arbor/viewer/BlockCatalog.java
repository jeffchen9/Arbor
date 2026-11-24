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

package com.jeffch.arbor.viewer;

import com.jeffch.arbor.shape.Decorators;

import java.util.*;

/**
 * Blocks from the assets folder that suit each role, used to fill the editor's block dropdowns.
 */
final class BlockCatalog {
  private static final Set<String> TWO_BLOCK_PLANTS = Set.of("large_fern", "tall_grass", "lilac", "peony", "rose_bush",
          "sunflower", "pitcher_plant");
  private static final Set<String> GROUND_COVER = Set.of("pink_petals", "wildflowers", "leaf_litter", "moss_carpet",
          "vine", "glow_lichen", "hanging_roots", "cave_vines", "pale_hanging_moss", "spore_blossom", "sweet_berry_bush");
  private static final Set<String> CROPS = Set.of("wheat", "carrots", "potatoes", "beetroots", "nether_wart",
          "torchflower_crop", "pitcher_crop", "cocoa");
  private static final Set<String> TRUNK_BLOCKS = Set.of("composter", "hay_block", "bamboo_block",
          "stripped_bamboo_block");
  private static final Set<String> DEAD_BUSHES = Set.of("dead_bush", "short_dry_grass", "tall_dry_grass", "bush");

  private final List<String> names;
  private final Set<String> plants;

  /**
   * @param names         Every block the assets define.
   * @param crossedPlants Blocks drawn as crossed planes, which count as plants along with the known two-block plants,
   *                      ground cover and crops.
   */
  BlockCatalog(List<String> names, Set<String> crossedPlants) {
    this.names = names.stream().sorted().toList();
    HashSet<String> all = new java.util.HashSet<>(crossedPlants);
    all.addAll(TWO_BLOCK_PLANTS);
    all.addAll(GROUND_COVER);
    all.addAll(CROPS);
    this.plants = all;
  }

  /**
   * Blocks for a {@code BlockRole.jsonKey()} or {@code "plant"}, in name order, containing the search text.
   *
   * @param roleKey Block list key.
   * @param search  Text the names must contain.
   */
  List<String> suitable(String roleKey, String search) {
    String needle = search.toLowerCase(Locale.ROOT).trim();
    List<String> result = new ArrayList<>();
    for (String name : names) {
      if (fits(roleKey, name) && name.contains(needle)) {
        result.add(name);
      }
    }
    return result;
  }

  /**
   * Whether a block suits a list, mostly judged by its name's ending, such as {@code _log} for logs.
   *
   * @param roleKey Block list key.
   * @param name    Block name.
   */
  private boolean fits(String roleKey, String name) {
    return switch (roleKey) {
      case "log", "junction", "cutEnd" -> name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem")
              || name.endsWith("_hyphae") || TRUNK_BLOCKS.contains(name);
      case "planks" -> name.endsWith("_planks");
      case "stairs" -> name.endsWith("_stairs");
      case "slab" -> name.endsWith("_slab");
      case "wall" -> name.endsWith("_wall");
      case "fence" -> name.endsWith("_fence");
      case "gate" -> name.endsWith("_fence_gate");
      case "button" -> name.endsWith("_button");
      case "sapling" -> name.endsWith("_sapling") || name.equals("mangrove_propagule");
      case "deadBush" -> DEAD_BUSHES.contains(name);
      case "leaves" -> name.endsWith("_leaves");
      case "plant" -> plants.contains(name);
      case "any" -> true;
      case "solid" -> !plants.contains(name) && Decorators.isCube(name);
      default -> false;
    };
  }
}
