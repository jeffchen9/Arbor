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
import com.jeffch.arbor.math.ValueNoise3;
import com.jeffch.arbor.preset.DecoratorSettings;
import com.jeffch.arbor.preset.GroundPlane;
import com.jeffch.arbor.preset.WeightedBlocks;
import com.jeffch.arbor.skeleton.GenerationLimitException;

import java.util.*;
import java.util.function.Predicate;

/**
 * Finishing touches on a completed tree. Each decorator rolls from its own stream, so none reshuffles another.
 */
public final class Decorators {
  private static final String[] SIDES = {"north", "east", "south", "west"};
  private static final int[] SIDE_X = {0, 1, 0, -1};
  private static final int[] SIDE_Z = {-1, 0, 1, 0};
  private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
  private static final int ROLL_PLACE = 1;
  private static final int ROLL_BLOCK = 2;
  private static final int ROLL_CHOICE = 3;
  private static final int ROLL_FACE = 4;
  private static final Set<String> EDGE_TO_EDGE_PLANTS = Set.of("short_grass", "fern", "tall_grass", "large_fern",
          "short_dry_grass", "tall_dry_grass", "bush");
  private static final List<String> NOT_CUBES = List.of("_slab", "_stairs", "_wall", "fence", "_gate", "_button",
          "door", "_plate", "sign", "torch", "carpet", "pane", "_bars", "rail", "_sapling", "banner", "_bed", "candle",
          "_head", "_skull", "coral", "coral_fan", "lantern", "chain", "ladder", "lever", "_rod", "flower", "_bush",
          "grass", "fern", "roots", "vines", "vine", "lichen", "mushroom", "propagule", "petals", "_pot", "cake",
          "tulip", "orchid", "poppy", "dandelion", "allium", "azure_bluet", "daisy", "cornflower", "lily_of_the_valley",
          "wither_rose", "lily_pad", "pickle", "kelp", "seagrass", "dripleaf", "spore_blossom", "cobweb", "fungus",
          "sprouts", "_crop", "wheat", "carrots", "potatoes", "beetroots", "_wart", "sugar_cane", "bamboo", "cactus",
          "frogspawn", "snow", "_litter", "_cluster", "_bud", "amethyst_cluster", "pointed_dripstone", "scaffolding",
          "hopper", "anvil", "bell", "brewing_stand", "cauldron", "lectern", "grindstone", "stonecutter",
          "enchanting_table", "end_rod", "conduit", "campfire", "composter", "chest", "_shulker_box", "daylight_detector",
          "repeater", "comparator", "redstone_wire", "tripwire", "piston_head", "moss_carpet", "hanging_moss");

  private Decorators() {
  }

  /**
   * Adds the enabled decorators in the order moss, pinecones, vines. Survival trees are never decorated.
   *
   * @param grid     The tree's shapes; decorations are added to it.
   * @param settings Decorator settings.
   * @param ground   Ground the decorations stop at.
   * @param seed     Tree seed.
   */
  public static void apply(ShapeGrid grid, DecoratorSettings settings, GroundPlane ground, long seed) {
    apply(grid, settings, ground, seed, Map.of());
  }

  /**
   * @param grid     The tree's shapes; decorations are added to it.
   * @param settings Decorator settings.
   * @param ground   Ground the decorations stop at.
   * @param seed     Tree seed.
   * @param levels   Branch level by voxel key; a block without one may hold pinecones.
   */
  public static void apply(ShapeGrid grid, DecoratorSettings settings, GroundPlane ground, long seed,
                           Map<Long, Integer> levels) {
    if (!settings.anyEnabled() || grid.isEmpty()) {
      return;
    }
    int lowest = grid.min()[1];
    int highest = grid.max()[1];
    if (settings.moss().enabled()) {
      moss(grid, settings.moss(), seed, lowest, highest);
    }
    if (settings.pinecones().enabled()) {
      pinecones(grid, settings.pinecones(), ground, seed, lowest, highest, levels);
    }
    if (settings.vines().enabled()) {
      vines(grid, settings.vines(), ground, seed);
    }
  }

  /**
   * Covers the requested share of exposed logs, taking the highest-scoring spots so they form patches.
   *
   * @param grid    The tree's shapes.
   * @param moss    Moss settings.
   * @param seed    Tree seed.
   * @param lowest  Lowest y of the tree.
   * @param highest Highest y of the tree.
   */
  private static void moss(ShapeGrid grid, DecoratorSettings.Moss moss, long seed, int lowest, int highest) {
    long place = stream(seed, "moss/place");
    ValueNoise3 clump = new ValueNoise3(stream(seed, "moss/clump"));
    double patch = moss.patchSize();
    List<MossSpot> spots = new ArrayList<>();
    grid.forEachSorted((x, y, z, shape) -> {
      if (!(shape instanceof Shape.FullBlock log) || log.cutEnd()
              || !inHeight(y, lowest, highest, moss.minHeight(), moss.maxHeight())
              || !touches(grid, x, y, z, java.util.Objects::isNull)) {
        return;
      }
      double lift = grid.get(x, y + 1, z) == null ? moss.upward() : 0;
      spots.add(new MossSpot(x, y, z, clump.at(x / patch, y / patch, z / patch) + lift));
    });
    spots.sort(Comparator.comparingDouble(MossSpot::score).reversed());
    int count = (int) Math.round(moss.amount() * spots.size());
    for (MossSpot spot : spots.subList(0, count)) {
      String block = moss.blocks().pickBlock(PlantLayerPlacer.roll(place, spot.x(), spot.y(), spot.z(), ROLL_BLOCK));
      if (block != null) {
        grid.put(spot.x(), spot.y(), spot.z(), new Shape.Plant(block, PlantHalf.FULL));
      }
    }
  }

  /**
   * Hangs pinecones under or beside the blocks that can hold one. Every spot is decided before any is placed.
   *
   * @param grid    The tree's shapes.
   * @param cones   Pinecone settings.
   * @param ground  Ground the pinecones stop at.
   * @param seed    Tree seed.
   * @param lowest  Lowest y of the tree.
   * @param highest Highest y of the tree.
   * @param levels  Branch level by voxel key; a block without one may hold pinecones.
   */
  private static void pinecones(ShapeGrid grid, DecoratorSettings.Pinecones cones, GroundPlane ground, long seed,
                                int lowest, int highest, Map<Long, Integer> levels) {
    long place = stream(seed, "pinecones/place");
    Holds high = (x, y, z) -> levels.getOrDefault(ShapeGrid.key(x, y, z), Integer.MAX_VALUE) >= cones.minLevel();
    Holds below = (x, y, z) -> holdsPineconeBelow(grid.get(x, y, z)) && high.at(x, y, z);
    Holds beside = (x, y, z) -> holdsPineconeBeside(grid.get(x, y, z)) && high.at(x, y, z);
    // Everything is decided before anything is placed, so a pinecone never hangs from another pinecone.
    List<Object[]> placed = new ArrayList<>();
    for (int[] spot : openSpots(grid, ground, below, beside)) {
      int x = spot[0];
      int y = spot[1];
      int z = spot[2];
      if (!inHeight(y, lowest, highest, cones.minHeight(), cones.maxHeight())
              || PlantLayerPlacer.roll(place, x, y, z, ROLL_PLACE) >= cones.chance()) {
        continue;
      }
      List<Integer> sides = sidesWith(x, y, z, beside);
      // The share splits pinecones strictly, so a spot that offers only the other kind stays empty.
      boolean useUnder = PlantLayerPlacer.roll(place, x, y, z, ROLL_CHOICE) < cones.undersideShare();
      if (useUnder ? !below.at(x, y + 1, z) : sides.isEmpty()) {
        continue;
      }
      String block = cones.blocks().pickBlock(PlantLayerPlacer.roll(place, x, y, z, ROLL_BLOCK));
      if (block == null) {
        continue;
      }
      String state = "";
      if (isNamed(block, "_button")) {
        state = useUnder ? "[face=ceiling,facing=north,powered=false]"
                : "[face=wall,facing=" + SIDES[(rolledSide(sides, place, x, y, z) + 2) % 4] + ",powered=false]";
      }
      placed.add(new Object[]{spot, new Shape.Plant(withState(block, state), PlantHalf.SINGLE)});
    }
    for (Object[] pinecone : placed) {
      int[] at = (int[]) pinecone[0];
      put(grid, at[0], at[1], at[2], (Shape) pinecone[1]);
    }
  }

  /**
   * Hangs strands down from open spots beside or under leaves, logs and full moss, each a rolled length and stopping at
   * any block or the ground.
   *
   * @param grid   The tree's shapes.
   * @param vines  Vine settings.
   * @param ground Ground the vines stop at.
   * @param seed   Tree seed.
   */
  private static void vines(ShapeGrid grid, DecoratorSettings.Vines vines, GroundPlane ground, long seed) {
    WeightedBlocks choices = vines.blocks();
    long place = stream(seed, "vines/place");
    Holds support = (x, y, z) -> holdsVine(grid.get(x, y, z));
    for (int[] spot : openSpots(grid, ground, support, support)) {
      int x = spot[0];
      int y = spot[1];
      int z = spot[2];
      if (grid.get(x, y, z) != null || PlantLayerPlacer.roll(place, x, y, z, ROLL_PLACE) >= vines.chance()) {
        continue;
      }
      String block = choices.pickBlock(PlantLayerPlacer.roll(place, x, y, z, ROLL_BLOCK));
      if (block == null) {
        continue;
      }
      List<Integer> sides = sidesWith(x, y, z, support);
      boolean underside = sides.isEmpty();
      int span = vines.maxLength() - vines.minLength() + 1;
      int wanted = vines.minLength() + (int) (PlantLayerPlacer.roll(place, x, y, z, ROLL_CHOICE) * span);
      int length = freeBelow(grid, ground, x, y, z, wanted);
      boolean realVine = TreeBlocks.stateless(block).replace("minecraft:", "").equals("vine");
      PlantHalf half = PlantLayerPlacer.isTwoBlock(block) ? PlantHalf.LOWER : PlantHalf.SINGLE;
      String face = underside ? SIDES[(int) (PlantLayerPlacer.roll(place, x, y, z, ROLL_FACE) * 4)]
              : SIDES[rolledSide(sides, place, x, y, z)];
      for (int i = 0; i < length; i++) {
        String state = "";
        if (realVine) {
          state = !underside ? "[" + face + "=true]"
                  : length == 1 ? "[up=true]"
                  : i == 0 ? "[" + face + "=true,up=true]" : "[" + face + "=true]";
        }
        put(grid, x, y - i, z, new Shape.Plant(withState(block, state), half));
      }
    }
  }

  /**
   * How many empty, unburied blocks there are from a spot downward, up to {@code wanted}.
   *
   * @param grid   The tree's shapes.
   * @param ground Ground that stops the count.
   * @param x      Spot x.
   * @param y      Spot y.
   * @param z      Spot z.
   * @param wanted Most blocks to count.
   */
  private static int freeBelow(ShapeGrid grid, GroundPlane ground, int x, int y, int z, int wanted) {
    int length = 0;
    while (length < wanted && grid.get(x, y - length, z) == null && !ground.buries(x, y - length, z)) {
      length++;
    }
    return length;
  }

  /**
   * Empty, unburied blocks directly below or beside a support, in a repeatable order.
   *
   * @param grid   The tree's shapes.
   * @param ground Ground below which spots are skipped.
   * @param below  Which blocks can hold a decoration underneath.
   * @param beside Which blocks can hold a decoration on their side.
   */
  private static List<int[]> openSpots(ShapeGrid grid, GroundPlane ground, Holds below, Holds beside) {
    Map<Long, int[]> spots = new LinkedHashMap<>();
    grid.forEachSorted((x, y, z, shape) -> {
      if (below.at(x, y, z)) {
        addOpen(grid, ground, spots, x, y - 1, z);
      }
      if (!beside.at(x, y, z)) {
        return;
      }
      for (int side = 0; side < SIDES.length; side++) {
        addOpen(grid, ground, spots, x + SIDE_X[side], y, z + SIDE_Z[side]);
      }
    });
    return new ArrayList<>(spots.values());
  }

  /**
   * Records a spot when it is empty and above the ground.
   *
   * @param grid   The tree's shapes.
   * @param ground Ground below which spots are skipped.
   * @param spots  Receives the spot by voxel key.
   * @param x      Spot x.
   * @param y      Spot y.
   * @param z      Spot z.
   */
  private static void addOpen(ShapeGrid grid, GroundPlane ground, Map<Long, int[]> spots, int x, int y, int z) {
    if (grid.get(x, y, z) == null && !ground.buries(x, y, z)) {
      spots.putIfAbsent(ShapeGrid.key(x, y, z), new int[]{x, y, z});
    }
  }

  /**
   * The sides (0 north, 1 east, 2 south, 3 west) of a spot whose neighbor holds a decoration.
   *
   * @param x       Spot x.
   * @param y       Spot y.
   * @param z       Spot z.
   * @param support Which neighbors can hold the decoration.
   */
  private static List<Integer> sidesWith(int x, int y, int z, Holds support) {
    List<Integer> sides = new ArrayList<>(SIDES.length);
    for (int side = 0; side < SIDES.length; side++) {
      if (support.at(x + SIDE_X[side], y, z + SIDE_Z[side])) {
        sides.add(side);
      }
    }
    return sides;
  }

  /**
   * One of the given sides, picked at random for this spot.
   *
   * @param sides Sides to pick from.
   * @param place Random stream seed.
   * @param x     Spot x.
   * @param y     Spot y.
   * @param z     Spot z.
   */
  private static int rolledSide(List<Integer> sides, long place, int x, int y, int z) {
    return sides.get((int) (PlantLayerPlacer.roll(place, x, y, z, ROLL_FACE) * sides.size()));
  }

  /**
   * Whether any of the six neighbors passes the test.
   *
   * @param grid The tree's shapes.
   * @param x    Block x.
   * @param y    Block y.
   * @param z    Block z.
   * @param test Test for each neighbor.
   */
  private static boolean touches(ShapeGrid grid, int x, int y, int z, Predicate<Shape> test) {
    for (int[] face : FACES) {
      if (test.test(grid.get(x + face[0], y + face[1], z + face[2]))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Anything but a button can hold a pinecone underneath, even a fence post or a plant.
   *
   * @param shape Shape to check.
   */
  private static boolean holdsPineconeBelow(Shape shape) {
    return shape != null && shape.shapeClass() != ShapeClass.BUTTON;
  }

  /**
   * Only a block whose side is one full face can hold a pinecone there, including grass that reaches the block's
   * edges, but not narrow plants such as flowers.
   *
   * @param shape Shape to check.
   */
  private static boolean holdsPineconeBeside(Shape shape) {
    return shape != null && (shape.shapeClass() == ShapeClass.FULL || shape.shapeClass() == ShapeClass.LEAVES
            || shape instanceof Shape.Plant plant
            && EDGE_TO_EDGE_PLANTS.contains(TreeBlocks.stateless(plant.block()).replace("minecraft:", "")));
  }

  /**
   * Leaves, logs and full-cube moss can hold a vine.
   *
   * @param shape Shape to check.
   */
  private static boolean holdsVine(Shape shape) {
    return shape instanceof Shape.Leaves || shape instanceof Shape.FullBlock
            || shape instanceof Shape.Plant plant && plant.half() == PlantHalf.FULL && isCube(plant.block());
  }

  /**
   * Whether a block is a whole cube that vines can hang from, judged by its name.
   *
   * @param block Block name, possibly with state.
   */
  // ponytail: name heuristic, no block models in the generator; read model bounds if odd blocks slip through.
  public static boolean isCube(String block) {
    String name = TreeBlocks.stateless(block).replace("minecraft:", "");
    return !PlantLayerPlacer.isTwoBlock(name) && NOT_CUBES.stream().noneMatch(name::endsWith);
  }

  /**
   * Whether a height lies in the min to max fraction of the tree's height.
   *
   * @param y       Height to check.
   * @param lowest  Lowest y of the tree.
   * @param highest Highest y of the tree.
   * @param min     Lowest fraction of the tree's height.
   * @param max     Highest fraction of the tree's height.
   */
  private static boolean inHeight(int y, int lowest, int highest, double min, double max) {
    double along = highest == lowest ? 0 : (double) (y - lowest) / (highest - lowest);
    return along >= min && along <= max;
  }

  /**
   * Whether a block's name ends with the suffix, ignoring any state.
   *
   * @param block  Block name, possibly with state.
   * @param suffix Ending to look for.
   */
  private static boolean isNamed(String block, String suffix) {
    return TreeBlocks.stateless(block).endsWith(suffix);
  }

  /**
   * Adds generated properties to a block that may already carry its own state.
   *
   * @param block Block name, possibly with its own state.
   * @param state Properties to add, as a {@code [...]} group.
   */
  private static String withState(String block, String state) {
    if (state.isEmpty()) {
      return block;
    }
    return block.endsWith("]") ? block.substring(0, block.length() - 1) + "," + state.substring(1) : block + state;
  }

  /**
   * Places a decoration, stopping generation if the tree passes the shape limit.
   *
   * @param grid  The tree's shapes.
   * @param x     Block x.
   * @param y     Block y.
   * @param z     Block z.
   * @param shape Decoration to place.
   */
  private static void put(ShapeGrid grid, int x, int y, int z, Shape shape) {
    grid.put(x, y, z, shape);
    if (grid.size() > ShapeGrid.MAX_SHAPES) {
      throw new GenerationLimitException("tree exceeds the limit of " + ShapeGrid.MAX_SHAPES + " shapes");
    }
  }

  /**
   * A seed for one decorator's random rolls.
   *
   * @param seed Tree seed.
   * @param path Name of the decorator's rolls.
   */
  private static long stream(long seed, String path) {
    return Double.doubleToRawLongBits(TreeRandom.forPath(seed, "decorator/" + path).nextDouble());
  }

  /**
   * Whether the block at a position can hold a decoration.
   */
  private interface Holds {
    boolean at(int x, int y, int z);
  }

  /**
   * An exposed log that moss may cover, with its score: patch noise plus a bonus for an open top.
   *
   * @param x     Block x.
   * @param y     Block y.
   * @param z     Block z.
   * @param score Patch noise plus the open-top bonus; higher is covered first.
   */
  private record MossSpot(int x, int y, int z, double score) {
  }
}
