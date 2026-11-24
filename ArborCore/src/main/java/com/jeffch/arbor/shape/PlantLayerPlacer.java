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

import com.jeffch.arbor.foliage.DensityField;
import com.jeffch.arbor.math.TreeRandom;
import com.jeffch.arbor.math.ValueNoise3;
import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.preset.PlantLayer;
import com.jeffch.arbor.preset.SpeciesPreset;
import com.jeffch.arbor.preset.WeightedBlocks;
import com.jeffch.arbor.skeleton.*;

import java.util.*;

/**
 * Fills empty voxels with the plants of each active layer, in order, so an earlier layer has priority. A layer's
 * random choices depend only on the seed, the layer's name and the voxel, so editing one layer never reshuffles
 * another.
 */
public final class PlantLayerPlacer {
  private static final Set<String> TWO_BLOCK = Set.of("large_fern", "tall_grass", "lilac", "peony", "rose_bush",
          "sunflower", "pitcher_plant");
  private static final double CLUMP_THRESHOLD = 0.55;
  private static final double OUTER_FRACTION = 0.7;
  private static final double INNER_FRACTION = 0.4;
  private static final int TALL_PLANT_REACH = 2;
  private static final String NAMESPACE = "minecraft:";

  private PlantLayerPlacer() {
  }

  /**
   * Places every active layer and returns the names of the layers that are active on this tree.
   *
   * @param skeleton The tree's branches.
   * @param preset   Preset whose plant layers are used.
   * @param density  Leaf density.
   * @param seed     Tree seed.
   * @param grid     The tree's shapes; plants are added to it.
   * @param budget   Work limit.
   */
  public static List<String> apply(Skeleton skeleton, SpeciesPreset preset, DensityField density, long seed,
                                   ShapeGrid grid, GenerationBudget budget) {
    return apply(skeleton, preset, density, seed, grid, budget, ShapeGrid.MAX_SHAPES);
  }

  /**
   * The same, with a custom shape limit for tests.
   *
   * @param skeleton  The tree's branches.
   * @param preset    Preset whose plant layers are used.
   * @param density   Leaf density.
   * @param seed      Tree seed.
   * @param grid      The tree's shapes; plants are added to it.
   * @param budget    Work limit.
   * @param maxShapes Most shapes the tree may hold.
   */
  static List<String> apply(Skeleton skeleton, SpeciesPreset preset, DensityField density, long seed,
                            ShapeGrid grid, GenerationBudget budget, int maxShapes) {
    List<PlantLayer> active = new ArrayList<>();
    for (PlantLayer layer : preset.plantLayers()) {
      double roll = TreeRandom.forPath(seed, "layer/" + layer.name() + "/tree").nextDouble();
      if (layer.enabled() && roll < layer.treeChance()) {
        active.add(layer);
      }
    }
    if (active.isEmpty() || skeleton.branches().isEmpty()) {
      return active.stream().map(PlantLayer::name).toList();
    }
    Scene scene = new Scene(skeleton, preset, density, grid, active.stream().mapToInt(PlantLayer::nearDistance).max().orElse(0));
    budget.charge((double) scene.volume() * active.size());
    scene.measure();
    Map<String, BitSet> nearSets = new HashMap<>();
    for (PlantLayer layer : active) {
      place(layer, scene, nearSets, seed, grid, budget, maxShapes);
    }
    return active.stream().map(PlantLayer::name).toList();
  }

  /**
   * Fills one layer: every empty voxel in its height and leaf density range, close enough to the chosen branches, on
   * the allowed side and inside a clump, gets a plant by chance. Two-block plants need room above, or use a one-block
   * plant from the list instead.
   *
   * @param layer     Layer to place.
   * @param scene     The tree's bounds, crown and branches.
   * @param nearSets  Voxels near each branch level, shared between layers; filled in as needed.
   * @param seed      Tree seed.
   * @param grid      The tree's shapes; plants are added to it.
   * @param budget    Work limit.
   * @param maxShapes Most shapes the tree may hold.
   */
  private static void place(PlantLayer layer, Scene scene, Map<String, BitSet> nearSets, long seed, ShapeGrid grid,
                            GenerationBudget budget, int maxShapes) {
    String nearKey = layer.nearBranch() + "/" + layer.nearDistance();
    BitSet near = nearSets.get(nearKey);
    if (near == null) {
      near = scene.nearBranches(layer.nearBranch(), layer.nearDistance(), budget);
      nearSets.put(nearKey, near);
    }
    long placeSeed = Double.doubleToRawLongBits(TreeRandom.forPath(seed, "layer/" + layer.name() + "/place").nextDouble());
    ValueNoise3 clump = new ValueNoise3(Double.doubleToRawLongBits(
            TreeRandom.forPath(seed, "layer/" + layer.name() + "/clump").nextDouble()));
    WeightedBlocks singles = singlesOf(layer.blocks());
    // A plant on a tall plant can sit above the crown's top row, as far as the scanned region allows.
    int lastRow = layer.side() == PlantLayer.Side.ON_TALL_PLANT
            ? Math.min(scene.max[1], scene.top() + TALL_PLANT_REACH) : scene.top();
    for (int y = scene.min[1]; y <= lastRow; y++) {
      budget.charge(0);
      if (y < scene.lowestY) {
        continue;
      }
      double height = scene.heightFraction(y);
      if (height < layer.minHeight() || height > layer.maxHeight()) {
        continue;
      }
      for (int z = scene.min[2]; z <= scene.max[2]; z++) {
        for (int x = scene.min[0]; x <= scene.max[0]; x++) {
          if (grid.get(x, y, z) != null) {
            continue;
          }
          double d = scene.density.get(x, y, z);
          if (d < layer.minDensity() || d > layer.maxDensity()) {
            continue;
          }
          boolean close = near.get(scene.index(x, y, z));
          if (layer.nearBranch() != null ? !close : !(d > 0 || close)) {
            continue;
          }
          if (!sideAllows(layer.side(), scene, grid, x, y, z)) {
            continue;
          }
          if (layer.clumpSize() > 0 && clump.at(x / layer.clumpSize(), y / layer.clumpSize(),
                  z / layer.clumpSize()) < CLUMP_THRESHOLD) {
            continue;
          }
          if (roll(placeSeed, x, y, z, 1) >= layer.chance()) {
            continue;
          }
          double blockRoll = roll(placeSeed, x, y, z, 2);
          String block = layer.blocks().pick(blockRoll);
          if (isTwoBlock(block) && layer.doubleHalf() != PlantLayer.DoubleHalf.BOTH) {
            grid.put(x, y, z, new Shape.Plant(block,
                    layer.doubleHalf() == PlantLayer.DoubleHalf.TOP ? PlantHalf.UPPER : PlantHalf.LOWER));
            checkLimit(grid, maxShapes);
            continue;
          }
          if (isTwoBlock(block)) {
            if (grid.get(x, y + 1, z) == null) {
              grid.put(x, y, z, new Shape.Plant(block, PlantHalf.LOWER));
              grid.put(x, y + 1, z, new Shape.Plant(block, PlantHalf.UPPER));
              checkLimit(grid, maxShapes);
              continue;
            }
            block = singles == null ? null : singles.pick(blockRoll);
          }
          if (block != null) {
            grid.put(x, y, z, new Shape.Plant(block, PlantHalf.SINGLE));
            checkLimit(grid, maxShapes);
          }
        }
      }
    }
  }

  /**
   * Stops generation when the tree passes the shape limit.
   *
   * @param grid      The tree's shapes.
   * @param maxShapes Most shapes the tree may hold.
   */
  private static void checkLimit(ShapeGrid grid, int maxShapes) {
    if (grid.size() > maxShapes) {
      throw new GenerationLimitException("preset exceeds the limit of " + maxShapes + " shapes");
    }
  }

  /**
   * Whether a voxel suits a layer's side setting.
   *
   * @param side  The layer's side setting.
   * @param scene The tree's bounds, crown and branches.
   * @param grid  The tree's shapes.
   * @param x     Voxel x.
   * @param y     Voxel y.
   * @param z     Voxel z.
   */
  private static boolean sideAllows(PlantLayer.Side side, Scene scene, ShapeGrid grid, int x, int y, int z) {
    return switch (side) {
      case ANY -> true;
      case TOP -> solid(grid.get(x, y - 1, z)) && grid.get(x, y + 1, z) == null;
      case UNDERSIDE -> solid(grid.get(x, y + 1, z)) && grid.get(x, y - 1, z) == null;
      case OUTER -> scene.distance(x, y, z) >= OUTER_FRACTION * scene.width(y);
      case INNER -> scene.distance(x, y, z) <= INNER_FRACTION * scene.width(y);
      case ON_TALL_PLANT -> grid.get(x, y - 1, z) instanceof Shape.Plant below && below.half() == PlantHalf.UPPER;
    };
  }

  /**
   * Whether a shape is something a plant can sit on or hang from, rather than another plant or small piece.
   *
   * @param shape Shape to check.
   */
  private static boolean solid(Shape shape) {
    return shape != null && switch (shape.shapeClass()) {
      case PLANT, DEAD_BUSH, SAPLING, BUTTON -> false;
      default -> true;
    };
  }

  /**
   * Whether a block is a plant two blocks tall, such as tall grass or a sunflower.
   *
   * @param block Block name.
   */
  static boolean isTwoBlock(String block) {
    String name = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
    return TWO_BLOCK.contains(name.startsWith(NAMESPACE) ? name.substring(NAMESPACE.length()) : name);
  }

  /**
   * The layer's one-block plants, or null when it has none with weight.
   *
   * @param blocks The layer's blocks.
   */
  private static WeightedBlocks singlesOf(WeightedBlocks blocks) {
    List<WeightedBlocks.Entry> singles = blocks.entries().stream()
            .filter(entry -> entry.weight() > 0 && !isTwoBlock(entry.block())).toList();
    return singles.isEmpty() ? null : new WeightedBlocks(singles);
  }

  /**
   * A repeatable roll in [0, 1) for one voxel of one layer.
   *
   * @param layerSeed The layer's random seed.
   * @param x         Voxel x.
   * @param y         Voxel y.
   * @param z         Voxel z.
   * @param salt      Tells apart several rolls for the same voxel.
   */
  static double roll(long layerSeed, int x, int y, int z, int salt) {
    long hash = layerSeed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L)
            ^ (salt * 0xD6E8FEB86659FD93L);
    hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
    hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
    hash ^= hash >>> 31;
    return (hash >>> 11) * 0x1.0p-53;
  }

  /**
   * The region plants may use, plus what the sides and heights are measured against.
   */
  private static final class Scene {
    private final Skeleton skeleton;
    private final SpeciesPreset preset;
    private final DensityField density;
    private final ShapeGrid grid;
    private final int[] min = new int[3];
    private final int[] max = new int[3];
    private final int[] size = new int[3];
    private int lowestY;
    private int highestY;
    private boolean hasSolid;
    private double[] widths;
    private TrunkAxis axis;

    /**
     * A box around the wood, the leaf density and every branch, widened by {@code margin} so plants near a branch fit.
     *
     * @param skeleton The tree's branches.
     * @param preset   The tree's preset.
     * @param density  Leaf density.
     * @param grid     The tree's shapes.
     * @param margin   Blocks to widen the box by on every side.
     */
    Scene(Skeleton skeleton, SpeciesPreset preset, DensityField density, ShapeGrid grid, int margin) {
      this.preset = preset;
      this.skeleton = skeleton;
      this.density = density;
      this.grid = grid;
      int[] low = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
      int[] high = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
      if (!grid.isEmpty()) {
        widen(low, high, grid.min());
        widen(low, high, grid.max());
      }
      if (!density.isEmpty()) {
        int[] densityMin = density.min();
        int[] dimensions = density.dimensions();
        widen(low, high, densityMin);
        widen(low, high, new int[]{densityMin[0] + dimensions[0] - 1, densityMin[1] + dimensions[1] - 1,
                densityMin[2] + dimensions[2] - 1});
      }
      for (Branch branch : skeleton.branches()) {
        for (Node node : branch.nodes()) {
          Vec3 p = node.position();
          widen(low, high, new int[]{(int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z())});
        }
      }
      for (int axisIndex = 0; axisIndex < 3; axisIndex++) {
        min[axisIndex] = low[axisIndex] - margin;
        max[axisIndex] = high[axisIndex] + margin;
        size[axisIndex] = max[axisIndex] - min[axisIndex] + 1;
      }
    }

    /**
     * Grows a box to hold a point.
     *
     * @param low   Lower corner; changed in place.
     * @param high  Upper corner; changed in place.
     * @param point Point to include.
     */
    private static void widen(int[] low, int[] high, int[] point) {
      for (int i = 0; i < 3; i++) {
        low[i] = Math.min(low[i], point[i]);
        high[i] = Math.max(high[i], point[i]);
      }
    }

    double volume() {
      return (double) size[0] * size[1] * size[2];
    }

    int index(int x, int y, int z) {
      return (x - min[0]) + size[0] * ((y - min[1]) + size[1] * (z - min[2]));
    }

    /**
     * Records the wood and leaf extent: the lowest and highest block, and the widest reach at each height.
     */
    void measure() {
      lowestY = Integer.MAX_VALUE;
      highestY = Integer.MIN_VALUE;
      widths = new double[size[1]];
      axis = TrunkAxis.ofTrunks(skeleton.branches());
      grid.forEachSorted((x, y, z, shape) -> {
        if (!solid(shape)) {
          return;
        }
        lowestY = Math.min(lowestY, y);
        highestY = Math.max(highestY, y);
        if (y >= min[1] && y <= max[1]) {
          widths[y - min[1]] = Math.max(widths[y - min[1]], distance(x, y, z));
        }
      });
      for (Branch branch : skeleton.branches()) {
        if (preset.levels().get(Math.min(branch.level(), preset.levels().size() - 1)).wood()) {
          continue;
        }
        for (Node node : branch.nodes()) {
          Vec3 p = node.position();
          int y = (int) Math.floor(p.y());
          lowestY = Math.min(lowestY, y);
          highestY = Math.max(highestY, y);
          widths[y - min[1]] = Math.max(widths[y - min[1]],
                  distance((int) Math.floor(p.x()), y, (int) Math.floor(p.z())));
        }
      }
      hasSolid = lowestY <= highestY;
      if (!hasSolid) {
        lowestY = min[1];
        highestY = min[1];
      }
    }

    /**
     * The highest row plants may use: one above the crown, or the whole region when the tree has no blocks.
     */
    int top() {
      return hasSolid ? Math.min(max[1], highestY + 1) : max[1];
    }

    double heightFraction(int y) {
      return y > highestY ? 1 : highestY == lowestY ? 0 : (double) (y - lowestY) / (highestY - lowestY);
    }

    /**
     * The crown's reach at a row; the row above the crown uses the highest row's reach.
     *
     * @param y Row index.
     */
    double width(int y) {
      return widths[(hasSolid ? Math.min(y, highestY) : y) - min[1]];
    }

    /**
     * Horizontal distance from the crown's central axis, which follows the trunk.
     *
     * @param x Voxel x.
     * @param y Voxel y.
     * @param z Voxel z.
     */
    double distance(int x, int y, int z) {
      Vec3 centered = axis.recenter(new Vec3(x + 0.5, y + 0.5, z + 0.5));
      return StrictMath.hypot(centered.x() - 0.5, centered.z() - 0.5);
    }

    /**
     * Voxels within {@code distance} of a branch of {@code level}, or of any branch when level is null.
     *
     * @param level    Branch level name, or null for any.
     * @param distance Greatest distance in blocks.
     * @param budget   Work limit.
     */
    BitSet nearBranches(String level, int distance, GenerationBudget budget) {
      List<int[]> offsets = new ArrayList<>();
      for (int dx = -distance; dx <= distance; dx++) {
        for (int dy = -distance; dy <= distance; dy++) {
          for (int dz = -distance; dz <= distance; dz++) {
            if (dx * dx + dy * dy + dz * dz <= distance * distance) {
              offsets.add(new int[]{dx, dy, dz});
            }
          }
        }
      }
      BitSet near = new BitSet();
      BitSet stamped = new BitSet();
      for (Branch branch : skeleton.branches()) {
        if (level != null && !level.equals(branch.levelName())) {
          continue;
        }
        List<Node> nodes = branch.nodes();
        for (int i = 0; i < nodes.size(); i++) {
          List<int[]> path = VoxelPaths.line(nodes.get(Math.max(0, i - 1)).position(), nodes.get(i).position());
          budget.charge((double) path.size() * offsets.size());
          for (int[] voxel : path) {
            int center = index(voxel[0], voxel[1], voxel[2]);
            if (stamped.get(center)) {
              continue;
            }
            stamped.set(center);
            for (int[] offset : offsets) {
              int x = voxel[0] + offset[0];
              int y = voxel[1] + offset[1];
              int z = voxel[2] + offset[2];
              if (x >= min[0] && x <= max[0] && y >= min[1] && y <= max[1] && z >= min[2] && z <= max[2]) {
                near.set(index(x, y, z));
              }
            }
          }
        }
      }
      return near;
    }
  }
}
