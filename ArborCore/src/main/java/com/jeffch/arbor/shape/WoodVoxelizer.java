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
import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.preset.FlareSettings;
import com.jeffch.arbor.preset.ParamResolver;
import com.jeffch.arbor.preset.SpeciesPreset;
import com.jeffch.arbor.preset.VoxelSettings;
import com.jeffch.arbor.skeleton.*;

import java.util.*;

/**
 * Converts spines into full blocks, walls, fences and fence gates.
 */
public final class WoodVoxelizer {
  private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
  private static final Shape WALL_PLACEHOLDER = new Shape.Wall(true, WallSide.NONE, WallSide.NONE, WallSide.NONE, WallSide.NONE);
  private static final Shape.FullBlock JUNCTION = new Shape.FullBlock(GrainAxis.NONE);
  private static final double ARM_MINIMUM = 0.35;
  private static final int MAX_WOOD_VOXELS = 2_000_000;
  private static final double SURFACE_MARGIN = 1;
  private static final double CONTINUATION_COSINE = 0.95;
  private static final double PARENT_FACING_WEIGHT = 1.5;
  private static final double THICKNESS_SPREAD = 0.35;
  private static final int MAX_TIP_DROP = 2;
  private static final int WALL_ARM_REACH = 2;
  private static final int CANOPY_CLUSTER = 4;
  private static final int CANOPY_REACH = 2;

  private final Skeleton skeleton;
  private final SpeciesPreset preset;
  private final VoxelSettings settings;
  private final long seed;
  private final ShapeGrid grid;
  private final ParamResolver resolver;
  private final Map<Long, Integer> owners = new HashMap<>();
  private final Map<Long, Double> trunkClearance = new HashMap<>();
  private final Map<String, Double> thicknessFactors = new HashMap<>();
  private final GenerationBudget budget;
  private final DensityField density;
  private final ShapeAvailability availability;

  /**
   * @param skeleton     Branches to voxelize.
   * @param preset       Preset whose settings and blocks are used.
   * @param seed         Tree seed.
   * @param grid         Grid that receives the shapes.
   * @param budget       Work limit, charged for every voxel examined.
   * @param density      Leaf density, used to trim bare tips and choose saplings; may be empty.
   * @param availability Which shapes the tree blocks allow.
   */
  private WoodVoxelizer(Skeleton skeleton, SpeciesPreset preset, long seed, ShapeGrid grid, GenerationBudget budget, DensityField density, ShapeAvailability availability) {
    this.availability = availability;
    this.budget = budget;
    this.density = density;
    this.skeleton = skeleton;
    this.preset = preset;
    this.seed = seed;
    this.grid = grid;
    settings = preset.shapes();
    resolver = new ParamResolver(seed);
  }

  /**
   * Voxelizes wood, using fallback shapes for any shape the tree blocks turn off. When leaf density is given, bare
   * thin tips outside the canopy are left out.
   *
   * @param skeleton     Branches to voxelize.
   * @param preset       Preset whose settings and blocks are used.
   * @param seed         Tree seed.
   * @param grid         Grid that receives the shapes.
   * @param budget       Work limit, charged for every voxel examined.
   * @param density      Leaf density, used to trim bare tips and choose saplings; may be empty.
   * @param availability Which shapes the tree blocks allow.
   */
  public static void apply(Skeleton skeleton, SpeciesPreset preset, long seed, ShapeGrid grid, GenerationBudget budget, DensityField density, ShapeAvailability availability) {
    skeleton = woodOnly(skeleton, preset);
    WoodVoxelizer voxelizer = new WoodVoxelizer(skeleton, preset, seed, grid, budget, density, availability);
    for (int i = 0; i < skeleton.branches().size(); i++) {
      voxelizer.rasterizeFull(skeleton.branches().get(i), i);
    }
    voxelizer.connectFullBlocks();
    for (Branch branch : skeleton.branches()) {
      voxelizer.rasterizeWalls(branch);
    }
    for (Branch branch : skeleton.branches()) {
      voxelizer.rasterizeThin(branch);
    }
    for (Branch branch : skeleton.branches()) {
      voxelizer.connectWalls(branch);
    }
    voxelizer.placeSplitGates();
    voxelizer.placeDeadBushes();
    if (voxelizer.tracksTrunkSurface()) {
      TrunkSmoother.apply(grid, voxelizer.trunkClearance, preset.shapes(), seed, budget, availability);
    }
    for (Branch branch : skeleton.branches()) {
      voxelizer.markCutEnd(branch);
    }
    FenceConnections.apply(grid);
  }

  /**
   * Levels with wood turned off only guide leaves and plants.
   *
   * @param skeleton The whole tree.
   * @param preset   Preset whose levels may turn wood off.
   */
  private static Skeleton woodOnly(Skeleton skeleton, SpeciesPreset preset) {
    List<Branch> wood = skeleton.branches().stream().filter(branch -> preset.levels().get(Math.min(branch.level(), preset.levels().size() - 1)).wood()).toList();
    return wood.size() == skeleton.branches().size() ? skeleton : new Skeleton(skeleton.treeHeight(), wood);
  }

  /**
   * The compass side a mostly horizontal direction points to, or null when it points mostly up or down.
   *
   * @param direction Direction to classify.
   */
  private static Direction splitFacing(Vec3 direction) {
    if (StrictMath.hypot(direction.x(), direction.z()) < ARM_MINIMUM) {
      return null;
    }
    if (Math.abs(direction.x()) >= Math.abs(direction.z())) {
      return direction.x() > 0 ? Direction.EAST : Direction.WEST;
    }
    return direction.z() > 0 ? Direction.SOUTH : Direction.NORTH;
  }

  /**
   * The log grain for a segment: whichever world axis it runs along most.
   *
   * @param direction Direction of the segment.
   */
  private static GrainAxis axisOf(Vec3 direction) {
    double x = Math.abs(direction.x());
    double y = Math.abs(direction.y());
    double z = Math.abs(direction.z());
    if (y >= x && y >= z) {
      return GrainAxis.Y;
    }
    return x >= z ? GrainAxis.X : GrainAxis.Z;
  }

  /**
   * A fence with arms toward the previous and next blocks of its branch.
   *
   * @param back       Step to the previous block, or null.
   * @param ahead      Step to the next block, or null.
   * @param aheadUsesX For a diagonal step ahead, whether its arm points along x.
   * @param backUsesX  For a diagonal step back, whether its arm points along x.
   */
  private static Shape.Fence fenceFor(int[] back, int[] ahead, boolean aheadUsesX, boolean backUsesX) {
    boolean[] arms = new boolean[4];
    if (ahead != null) {
      addArm(arms, ahead, aheadUsesX);
    }
    if (back != null) {
      addArm(arms, back, backUsesX);
    }
    return new Shape.Fence(arms[Direction.NORTH.ordinal()], arms[Direction.EAST.ordinal()], arms[Direction.SOUTH.ordinal()], arms[Direction.WEST.ordinal()]);
  }

  /**
   * Adds the arm toward a neighboring block. A diagonal step uses one arm from each end to form a corner.
   *
   * @param arms    North, east, south and west arms, by direction order; one is set.
   * @param step    Step to the neighbor.
   * @param preferX For a diagonal step, whether to use the x arm rather than the z arm.
   */
  private static void addArm(boolean[] arms, int[] step, boolean preferX) {
    if (step[0] == 0 && step[2] == 0) {
      return;
    }
    boolean useX = step[0] != 0 && (step[2] == 0 || preferX);
    Direction side = useX ? (step[0] > 0 ? Direction.EAST : Direction.WEST) : (step[2] > 0 ? Direction.SOUTH : Direction.NORTH);
    arms[side.ordinal()] = true;
  }

  /**
   * The rail axis for a mini branch: one clear horizontal axis, no corner, and at least one level step.
   *
   * @param back  Step to the previous block, or null.
   * @param ahead Step to the next block, or null.
   */
  private static Direction railAlong(int[] back, int[] ahead) {
    Direction backAxis = back == null ? null : railAxis(back);
    Direction aheadAxis = ahead == null ? null : railAxis(ahead);
    if (backAxis == null && aheadAxis == null) {
      return null;
    }
    if (backAxis != null && aheadAxis != null && backAxis != aheadAxis) {
      return null;
    }
    boolean level = (back != null && back[1] == 0) || (ahead != null && ahead[1] == 0);
    if (!level) {
      return null;
    }
    return aheadAxis != null ? aheadAxis : backAxis;
  }

  /**
   * EAST for a step purely along x, NORTH for one purely along z, otherwise null.
   *
   * @param step Step between blocks.
   */
  private static Direction railAxis(int[] step) {
    if (step[0] != 0 && step[2] == 0) {
      return Direction.EAST;
    }
    return step[2] != 0 && step[0] == 0 ? Direction.NORTH : null;
  }

  /**
   * One of the two sides at right angles to a rail axis.
   *
   * @param rail  Rail axis.
   * @param first Whether to take the first side (north or east) rather than the second.
   */
  private static Direction perpendicular(Direction rail, boolean first) {
    if (rail.isXAxis()) {
      return first ? Direction.NORTH : Direction.SOUTH;
    }
    return first ? Direction.EAST : Direction.WEST;
  }

  /**
   * Whether two voxels are the same position.
   *
   * @param a First voxel.
   * @param b Second voxel.
   */
  private static boolean same(int[] a, int[] b) {
    return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
  }

  /**
   * A thick branch that stops with nothing past its tip shows the log's rings there.
   *
   * @param branch Branch to check.
   */
  private void markCutEnd(Branch branch) {
    List<Node> nodes = branch.nodes();
    if (nodes.size() < 2) {
      return;
    }
    double arc = 0;
    for (int i = 0; i + 2 < nodes.size(); i++) {
      arc += nodes.get(i + 1).position().sub(nodes.get(i).position()).length();
    }
    if (segmentClass(branch, nodes.size() - 2, arc) != ShapeClass.FULL) {
      return;
    }
    Vec3 tip = nodes.get(nodes.size() - 1).position();
    Vec3 direction = tip.sub(nodes.get(nodes.size() - 2).position());
    GrainAxis axis = axisOf(direction);
    int x = (int) Math.floor(tip.x());
    int y = (int) Math.floor(tip.y());
    int z = (int) Math.floor(tip.z());
    int nextX = x + (axis == GrainAxis.X ? (direction.x() > 0 ? 1 : -1) : 0);
    int nextY = y + (axis == GrainAxis.Y ? (direction.y() > 0 ? 1 : -1) : 0);
    int nextZ = z + (axis == GrainAxis.Z ? (direction.z() > 0 ? 1 : -1) : 0);
    if (grid.get(x, y, z) instanceof Shape.FullBlock full && full.axis() == axis && grid.get(nextX, nextY, nextZ) == null) {
      grid.put(x, y, z, new Shape.FullBlock(axis, true));
    }
  }

  /**
   * Whether the trunk's true surface must be measured, for smoothing or button burls.
   */
  private boolean tracksTrunkSurface() {
    return settings.smoothTrunk() || settings.buttonChance() > 0;
  }

  /**
   * Thin ends become leaves only on trees that have leaves, and leaf blocks, to imply them.
   *
   * @param branch Branch to check.
   */
  private int impliedStart(Branch branch) {
    double radius = preset.leaves().isEmpty() || !availability.leaves() ? 0 : settings.impliedRadius();
    return ImpliedTips.tailStart(branch, radius);
  }

  /**
   * Replaces fences and closed gates where a visible child splits from its parent.
   */
  private void placeSplitGates() {
    if (settings.openGateChance() <= 0 && settings.fineBranchChance() <= 0) {
      return;
    }
    Map<String, BranchSampler> samplers = new HashMap<>();
    for (Branch branch : skeleton.branches()) {
      samplers.put(branch.pathId(), new BranchSampler(branch));
    }
    for (Branch child : skeleton.branches()) {
      BranchSampler parent = child.parentPathId() == null ? null : samplers.get(child.parentPathId());
      if (parent == null || child.nodes().size() < 2) {
        continue;
      }
      Vec3 start = child.nodes().get(0).position();
      int x = (int) Math.floor(start.x());
      int y = (int) Math.floor(start.y());
      int z = (int) Math.floor(start.z());
      Shape existing = grid.get(x, y, z);
      if (!(existing instanceof Shape.Fence) && !(existing instanceof Shape.FenceGate)) {
        continue;
      }
      Vec3 parentTangent = parent.frame(child.parentT()).tangent();
      Vec3 childTangent = child.nodes().get(1).position().sub(start).normalized();
      if (impliedStart(child) == 0 || !continuesVisibly(child, x, y, z)) {
        placeFineBranch(child, parentTangent, childTangent, x, y, z, !keepsTrunkWhole(parent.branch()));
        continue;
      }
      if (keepsTrunkWhole(parent.branch()) || parentTangent.dot(childTangent) > CONTINUATION_COSINE) {
        continue;
      }
      Direction facing = splitFacing(parentTangent.scale(PARENT_FACING_WEIGHT).add(childTangent));
      if (facing == null) {
        continue;
      }
      TreeRandom random = TreeRandom.forPath(seed, "split/" + x + "," + y + "," + z);
      if (random.chance(settings.openGateChance())) {
        boolean inWall = random.chance(settings.gateInWallChance());
        if (availability.gate()) {
          grid.put(x, y, z, new Shape.FenceGate(facing, true, inWall));
        }
      }
    }
  }

  /**
   * Hints at a branch too fine for its own blocks with an open gate or a fence arm on its parent.
   *
   * @param child         The fine branch.
   * @param parentTangent Parent's direction at the split.
   * @param childTangent  Child's starting direction.
   * @param x             X of the parent's block at the split.
   * @param y             Y of the parent's block at the split.
   * @param z             Z of the parent's block at the split.
   * @param allowGate     Whether an open gate may be used.
   */
  private void placeFineBranch(Branch child, Vec3 parentTangent, Vec3 childTangent, int x, int y, int z, boolean allowGate) {
    Direction side = splitFacing(childTangent);
    if (side == null || !(grid.get(x, y, z) instanceof Shape.Fence fence)) {
      return;
    }
    int nx = x + side.dx();
    int nz = z + side.dz();
    if (grid.get(nx, y, nz) != null || !inCanopy(nx, y, nz)) {
      return;
    }
    TreeRandom random = TreeRandom.forPath(seed, child.pathId() + "#fine");
    if (!random.chance(settings.fineBranchChance())) {
      return;
    }
    Direction facing = splitFacing(parentTangent.scale(PARENT_FACING_WEIGHT).add(childTangent));
    if (allowGate && facing != null && random.chance(settings.fineGateChance()) && availability.gate()) {
      grid.put(x, y, z, new Shape.FenceGate(facing, true, random.chance(settings.gateInWallChance())));
    } else {
      grid.put(x, y, z, FenceConnections.withArm(fence, side, true));
    }
  }

  /**
   * Whether a voxel is leafy enough to hide wood. Always true when the tree has no leaves or the setting is off.
   *
   * @param x Voxel x.
   * @param y Voxel y.
   * @param z Voxel z.
   */
  private boolean inCanopy(int x, int y, int z) {
    return !availability.leaves() || density == null || density.isEmpty() || settings.tipCanopyDensity() <= 0 || density.get(x, y, z) >= settings.tipCanopyDensity();
  }

  /**
   * True when the child's first block after the split holds a shape.
   *
   * @param child The child branch.
   * @param x     X of the block at the split.
   * @param y     Y of the block at the split.
   * @param z     Z of the block at the split.
   */
  private boolean continuesVisibly(Branch child, int x, int y, int z) {
    int[] start = {x, y, z};
    for (int i = 1; i < child.nodes().size(); i++) {
      for (int[] voxel : VoxelPaths.line(child.nodes().get(i - 1).position(), child.nodes().get(i).position())) {
        if (!same(start, voxel)) {
          return grid.get(voxel[0], voxel[1], voxel[2]) != null;
        }
      }
    }
    return false;
  }

  /**
   * Marks dense tangles of thin branches, generally with a bush resting on a fence.
   */
  private void placeDeadBushes() {
    if (settings.deadBushChance() <= 0) {
      return;
    }
    List<Long> crowded = new ArrayList<>();
    for (long key : grid.sortedKeys()) {
      int x = ShapeGrid.keyX(key);
      int y = ShapeGrid.keyY(key);
      int z = ShapeGrid.keyZ(key);
      if (grid.get(x, y, z) instanceof Shape.Fence) {
        budget.charge(27);
        if (thinNeighbors(x, y, z) >= settings.deadBushDensity()) {
          crowded.add(key);
        }
      }
    }
    for (long key : crowded) {
      int x = ShapeGrid.keyX(key);
      int y = ShapeGrid.keyY(key);
      int z = ShapeGrid.keyZ(key);
      TreeRandom random = TreeRandom.forPath(seed, "bush/" + x + "," + y + "," + z);
      if (!random.chance(settings.deadBushChance())) {
        continue;
      }
      Shape plant;
      if (random.chance(settings.deadBushOffFenceChance())) {
        plant = clusterPlant(x, y, z);
        if (plant != null) {
          grid.put(x, y, z, plant);
        }
      } else if (grid.get(x, y + 1, z) == null && (plant = clusterPlant(x, y + 1, z)) != null) {
        grid.put(x, y + 1, z, plant);
      }
    }
    checkVoxelLimit();
  }

  /**
   * A branch cluster reads as a sapling among leaves and a dead bush when bare. A sapling that is off becomes
   * a dead bush, and a dead bush that is off leaves the cluster as it was, so this can return null.
   *
   * @param x Voxel x.
   * @param y Voxel y.
   * @param z Voxel z.
   */
  private Shape clusterPlant(int x, int y, int z) {
    boolean leafy = availability.leaves() && density != null && !density.isEmpty() && settings.saplingLeafDensity() > 0 && density.get(x, y, z) >= settings.saplingLeafDensity();
    if (leafy && availability.sapling()) {
      return new Shape.Sapling();
    }
    return availability.deadBush() ? new Shape.DeadBush() : null;
  }

  /**
   * How many of the 26 surrounding voxels hold a fence or fence gate.
   *
   * @param x Voxel x.
   * @param y Voxel y.
   * @param z Voxel z.
   */
  private int thinNeighbors(int x, int y, int z) {
    int count = 0;
    for (int dy = -1; dy <= 1; dy++) {
      for (int dz = -1; dz <= 1; dz++) {
        for (int dx = -1; dx <= 1; dx++) {
          if (dx == 0 && dy == 0 && dz == 0) {
            continue;
          }
          Shape shape = grid.get(x + dx, y + dy, z + dz);
          if (shape instanceof Shape.Fence || shape instanceof Shape.FenceGate) {
            count++;
          }
        }
      }
    }
    return count;
  }

  /**
   * The shape class of a segment after fallbacks, or null when the segment is left out.
   *
   * @param branch  Branch the segment belongs to.
   * @param segment Index of the segment.
   * @param arc     Distance along the branch to the segment's start.
   */
  private ShapeClass segmentClass(Branch branch, int segment, double arc) {
    return availability.woodClass(wantedClass(branch, segment, arc), branch.level() == 0);
  }

  /**
   * The shape a segment's radius calls for before fallbacks: a full block, a wall or a fence. The radius is varied per
   * branch and widened by root flares near the base.
   *
   * @param branch  Branch the segment belongs to.
   * @param segment Index of the segment.
   * @param arc     Distance along the branch to the segment's start.
   */
  private ShapeClass wantedClass(Branch branch, int segment, double arc) {
    double radius = Math.max(branch.nodes().get(segment).radius(), branch.nodes().get(segment + 1).radius());
    if (keepsTrunkWhole(branch) && radius < settings.trunkFullRadius()) {
      return radius >= settings.wallRadius() ? ShapeClass.WALL : ShapeClass.FENCE;
    }
    radius *= thicknessFactor(branch);
    FlareSettings flares = flares(branch);
    if (flares.radius() > 0 && flares.height() > 0 && arc < flares.height() * scale()) {
      radius *= 1 + flares.radius() * StrictMath.pow(1 - arc / (flares.height() * scale()), flares.pinch());
    }
    if (radius >= settings.fullRadius()) {
      return ShapeClass.FULL;
    }
    return radius >= settings.wallRadius() ? ShapeClass.WALL : ShapeClass.FENCE;
  }

  /**
   * Shifts where each side branch changes shape class, so equal branches do not all change at one radius.
   *
   * @param branch Branch to look up.
   */
  private double thicknessFactor(Branch branch) {
    double randomness = settings.voxelRandomness();
    if (randomness <= 0 || branch.level() == 0) {
      return 1;
    }
    return thicknessFactors.computeIfAbsent(branch.pathId(), path -> 1 + randomness * THICKNESS_SPREAD * (2 * TreeRandom.forPath(seed, path + "#thickness").nextDouble() - 1));
  }

  /**
   * A trunk drawn in walls and fences must read as one column, so it gets no gaps or gates.
   *
   * @param branch Branch to check.
   */
  private boolean keepsTrunkWhole(Branch branch) {
    return branch.level() == 0 && settings.trunkFullRadius() > 0;
  }

  /**
   * The tree's size scale.
   */
  private double scale() {
    return preset.tree().sizeScale();
  }

  /**
   * The root flare settings of a branch's level.
   *
   * @param branch Branch to look up.
   */
  private FlareSettings flares(Branch branch) {
    return preset.levels().get(Math.min(branch.level(), preset.levels().size() - 1)).flares();
  }

  /**
   * Fills every full-block segment of a branch: a line of blocks along the center, then the solid tapered cone around
   * it.
   *
   * @param branch      Branch to fill.
   * @param branchIndex Branch's position in the skeleton, recorded as each block's owner.
   */
  private void rasterizeFull(Branch branch, int branchIndex) {
    List<Node> nodes = branch.nodes();
    FlareField flare = new FlareField(branch);
    double arc = 0;
    for (int i = 0; i + 1 < nodes.size(); i++) {
      Node a = nodes.get(i);
      Node b = nodes.get(i + 1);
      double length = b.position().sub(a.position()).length();
      if (segmentClass(branch, i, arc) == ShapeClass.FULL) {
        GrainAxis axis = axisOf(b.position().sub(a.position()));
        for (int[] voxel : VoxelPaths.sixConnected(a.position(), b.position())) {
          markFull(voxel[0], voxel[1], voxel[2], axis, branchIndex);
        }
        fillCone(a, b, length, arc, axis, branchIndex, flare, branch.level() == 0 && i == 0, branch.level() == 0 && tracksTrunkSurface());
      }
      arc += length;
    }
  }

  /**
   * Fills every voxel whose center lies within the segment's radius (widened by flares), measured from the nearest
   * point on the segment. For the trunk it also records how far each nearby voxel lies inside the surface, for
   * smoothing. The first trunk segment gets a flat base so nothing pokes below the ground.
   *
   * @param a            Segment start.
   * @param b            Segment end.
   * @param length       Segment length.
   * @param arc          Distance along the branch to the segment's start.
   * @param axis         Log grain for the segment.
   * @param branchIndex  Branch's position in the skeleton, recorded as each block's owner.
   * @param flare        Root flares that widen the radius near the base.
   * @param flatBase     Whether to cut the cone off flat at its start.
   * @param trackSurface Whether to record how far voxels lie inside the surface, for smoothing.
   */
  private void fillCone(Node a, Node b, double length, double arc, GrainAxis axis, int branchIndex, FlareField flare, boolean flatBase, boolean trackSurface) {
    Vec3 p = a.position();
    Vec3 q = b.position();
    double reach = Math.max(a.radius(), b.radius()) * flare.maximumScale(arc);
    if (!(reach <= GenerationBudget.WORLD_LIMIT)) {
      throw new GenerationLimitException("wood radius is too large");
    }
    if (trackSurface) {
      reach += SURFACE_MARGIN;
    }
    budget.charge((Math.abs(q.x() - p.x()) + 2 * reach + 2) * (Math.abs(q.y() - p.y()) + 2 * reach + 2) * (Math.abs(q.z() - p.z()) + 2 * reach + 2));
    int minX = (int) Math.floor(Math.min(p.x(), q.x()) - reach);
    int maxX = (int) Math.floor(Math.max(p.x(), q.x()) + reach);
    int minY = (int) Math.floor(Math.min(p.y(), q.y()) - reach);
    int maxY = (int) Math.floor(Math.max(p.y(), q.y()) + reach);
    int minZ = (int) Math.floor(Math.min(p.z(), q.z()) - reach);
    int maxZ = (int) Math.floor(Math.max(p.z(), q.z()) + reach);
    double abX = q.x() - p.x();
    double abY = q.y() - p.y();
    double abZ = q.z() - p.z();
    double denominator = abX * abX + abY * abY + abZ * abZ;
    for (int y = minY; y <= maxY; y++) {
      for (int z = minZ; z <= maxZ; z++) {
        for (int x = minX; x <= maxX; x++) {
          double cx = x + 0.5 - p.x();
          double cy = y + 0.5 - p.y();
          double cz = z + 0.5 - p.z();
          double raw = denominator <= 0 ? 0 : (cx * abX + cy * abY + cz * abZ) / denominator;
          if (flatBase && raw < 0) {
            continue;
          }
          double u = Math.clamp(raw, 0, 1);
          double dx = cx - abX * u;
          double dy = cy - abY * u;
          double dz = cz - abZ * u;
          double radius = a.radius() + (b.radius() - a.radius()) * u;
          radius *= flare.scale(arc + u * length, dx, dz);
          double squared = dx * dx + dy * dy + dz * dz;
          if (trackSurface) {
            double inside = radius - Math.sqrt(squared);
            if (inside > -SURFACE_MARGIN) {
              trunkClearance.merge(ShapeGrid.key(x, y, z), inside, Math::max);
            }
          }
          if (squared <= radius * radius) {
            markFull(x, y, z, axis, branchIndex);
          }
        }
      }
    }
  }

  /**
   * Places a log, or turns it into six-sided wood where a different branch crosses with another grain.
   *
   * @param x           Voxel x.
   * @param y           Voxel y.
   * @param z           Voxel z.
   * @param axis        Log grain.
   * @param branchIndex Branch's position in the skeleton.
   */
  private void markFull(int x, int y, int z, GrainAxis axis, int branchIndex) {
    long key = ShapeGrid.key(x, y, z);
    Shape existing = grid.get(x, y, z);
    if (existing instanceof Shape.FullBlock full) {
      Integer owner = owners.get(key);
      if (full.axis() != axis && (owner == null || owner != branchIndex)) {
        grid.put(x, y, z, JUNCTION);
      }
      return;
    }
    grid.put(x, y, z, new Shape.FullBlock(axis));
    owners.put(key, branchIndex);
    checkVoxelLimit();
  }

  /**
   * Stops generation when there is too much wood.
   */
  private void checkVoxelLimit() {
    if (grid.size() > MAX_WOOD_VOXELS) {
      throw new GenerationLimitException("tree exceeds the limit of " + MAX_WOOD_VOXELS + " wood voxels");
    }
  }

  /**
   * Joins full-block wood that came apart, such as a thick bough that reaches the trunk only through thinner wood.
   * Pieces are joined only within the same trunk, so separate trunks stay separate.
   */
  private void connectFullBlocks() {
    Set<Long> unvisited = new HashSet<>();
    List<Long> ordered = new ArrayList<>();
    for (long key : grid.sortedKeys()) {
      if (grid.get(ShapeGrid.keyX(key), ShapeGrid.keyY(key), ShapeGrid.keyZ(key)) instanceof Shape.FullBlock) {
        unvisited.add(key);
        ordered.add(key);
      }
    }
    List<List<Long>> components = new ArrayList<>();
    for (long start : ordered) {
      if (unvisited.remove(start)) {
        components.add(flood(start, unvisited));
      }
    }
    Map<String, List<List<Long>>> byTrunk = new LinkedHashMap<>();
    for (List<Long> component : components) {
      byTrunk.computeIfAbsent(trunkOf(component.get(0)), trunk -> new ArrayList<>()).add(component);
    }
    byTrunk.forEach(this::connectComponents);
  }

  /**
   * The path of the separate trunk that owns a block, or an empty string when it has none.
   *
   * @param key Packed voxel position.
   */
  private String trunkOf(long key) {
    Integer owner = owners.get(key);
    if (owner == null) {
      return "";
    }
    String path = skeleton.branches().get(owner).pathId();
    String first = path.contains("/") ? path.substring(0, path.indexOf('/')) : path;
    return skeleton.branches().stream().anyMatch(branch -> branch.level() == 0 && branch.pathId().equals(first)) ? first : "";
  }

  /**
   * Bridges every separate piece of one trunk's full blocks to the main piece, each by the shortest path.
   *
   * @param trunk      Path of the trunk.
   * @param components Separate pieces of the trunk's full blocks, as packed positions.
   */
  private void connectComponents(String trunk, List<List<Long>> components) {
    if (components.size() < 2) {
      return;
    }
    int mainIndex = mainComponent(components, trunk);
    List<Long> main = new ArrayList<>(surface(components.get(mainIndex)));
    for (int i = 0; i < components.size(); i++) {
      if (i != mainIndex) {
        List<Long> outer = surface(components.get(i));
        main.addAll(bridge(outer, main));
        main.addAll(outer);
      }
    }
  }

  /**
   * Every full block connected to {@code start} through shared faces, taking each from {@code unvisited}.
   *
   * @param start     Packed position to start from.
   * @param unvisited Blocks not yet assigned to a piece; those reached are removed.
   */
  private List<Long> flood(long start, Set<Long> unvisited) {
    List<Long> component = new ArrayList<>();
    ArrayDeque<Long> queue = new ArrayDeque<Long>();
    queue.add(start);
    while (!queue.isEmpty()) {
      long key = queue.poll();
      component.add(key);
      int x = ShapeGrid.keyX(key);
      int y = ShapeGrid.keyY(key);
      int z = ShapeGrid.keyZ(key);
      for (int[] step : NEIGHBORS) {
        long next = ShapeGrid.key(x + step[0], y + step[1], z + step[2]);
        if (unvisited.remove(next)) {
          queue.add(next);
        }
      }
    }
    return component;
  }

  /**
   * The piece holding the trunk's base, or the largest piece when none does.
   *
   * @param components Separate pieces, as packed positions.
   * @param trunk      Path of the trunk.
   */
  private int mainComponent(List<List<Long>> components, String trunk) {
    Branch root = skeleton.branches().stream().filter(branch -> branch.pathId().equals(trunk)).findFirst().orElse(skeleton.branches().get(0));
    Vec3 base = root.nodes().get(0).position();
    long rootKey = ShapeGrid.key((int) Math.floor(base.x()), (int) Math.floor(base.y()), (int) Math.floor(base.z()));
    int largest = 0;
    for (int i = 0; i < components.size(); i++) {
      if (components.get(i).contains(rootKey)) {
        return i;
      }
      if (components.get(i).size() > components.get(largest).size()) {
        largest = i;
      }
    }
    return largest;
  }

  /**
   * The blocks of a piece with at least one face not against a full block; only these can be nearest another piece.
   *
   * @param component A piece, as packed positions.
   */
  private List<Long> surface(List<Long> component) {
    List<Long> result = new ArrayList<>();
    for (long key : component) {
      int x = ShapeGrid.keyX(key);
      int y = ShapeGrid.keyY(key);
      int z = ShapeGrid.keyZ(key);
      for (int[] step : NEIGHBORS) {
        if (!(grid.get(x + step[0], y + step[1], z + step[2]) instanceof Shape.FullBlock)) {
          result.add(key);
          break;
        }
      }
    }
    return result;
  }

  /**
   * Finds the closest pair of blocks between two pieces and fills a stepped line of wood between them, moving along x,
   * then y, then z.
   *
   * @param from Piece to bridge from.
   * @param to   Piece to bridge to.
   */
  private List<Long> bridge(List<Long> from, List<Long> to) {
    budget.charge((double) from.size() * to.size());
    long bestFrom = from.get(0);
    long bestTo = to.get(0);
    int best = Integer.MAX_VALUE;
    for (long a : from) {
      int ax = ShapeGrid.keyX(a);
      int ay = ShapeGrid.keyY(a);
      int az = ShapeGrid.keyZ(a);
      for (long b : to) {
        int distance = Math.abs(ax - ShapeGrid.keyX(b)) + Math.abs(ay - ShapeGrid.keyY(b)) + Math.abs(az - ShapeGrid.keyZ(b));
        if (distance < best) {
          best = distance;
          bestFrom = a;
          bestTo = b;
        }
      }
    }
    List<Long> added = new ArrayList<>();
    int x = ShapeGrid.keyX(bestFrom);
    int y = ShapeGrid.keyY(bestFrom);
    int z = ShapeGrid.keyZ(bestFrom);
    int targetX = ShapeGrid.keyX(bestTo);
    int targetY = ShapeGrid.keyY(bestTo);
    int targetZ = ShapeGrid.keyZ(bestTo);
    while (x != targetX || y != targetY || z != targetZ) {
      if (x != targetX) {
        x += Integer.signum(targetX - x);
      } else if (y != targetY) {
        y += Integer.signum(targetY - y);
      } else {
        z += Integer.signum(targetZ - z);
      }
      if (!(grid.get(x, y, z) instanceof Shape.FullBlock)) {
        grid.put(x, y, z, JUNCTION);
        added.add(ShapeGrid.key(x, y, z));
      }
    }
    return added;
  }

  /**
   * Places a wall post on every voxel of a branch's wall-thick segments, now and then leaving a single gap so a long
   * wall branch looks less solid. A trunk that must read as one column never gets gaps.
   *
   * @param branch Branch to place.
   */
  private void rasterizeWalls(Branch branch) {
    TreeRandom random = TreeRandom.forPath(seed, branch.pathId() + "#walls");
    List<Node> nodes = branch.nodes();
    double arc = 0;
    int[] previous = null;
    boolean skippedPrevious = false;
    boolean runStarted = false;
    int impliedStart = impliedStart(branch);
    int canopyEnd = canopyEnd(branch);
    for (int i = 0; i + 1 < nodes.size(); i++) {
      Vec3 a = nodes.get(i).position();
      Vec3 b = nodes.get(i + 1).position();
      boolean impliedFence = i >= impliedStart && wantedClass(branch, i, arc) == ShapeClass.FENCE;
      if (i < canopyEnd && segmentClass(branch, i, arc) == ShapeClass.WALL && !impliedFence) {
        budget.charge(3 * (b.sub(a).length() + 1));
        checkVoxelLimit();
        for (int[] voxel : VoxelPaths.sixConnected(a, b)) {
          if (previous != null && same(previous, voxel)) {
            continue;
          }
          previous = voxel;
          double roll = random.nextDouble();
          boolean gap = !keepsTrunkWhole(branch) && runStarted && !skippedPrevious && roll < settings.wallGapChance();
          runStarted = true;
          skippedPrevious = gap;
          if (!gap) {
            grid.putIfThicker(voxel[0], voxel[1], voxel[2], WALL_PLACEHOLDER);
          }
        }
      } else {
        runStarted = false;
        skippedPrevious = false;
        previous = null;
      }
      arc += b.sub(a).length();
    }
  }

  /**
   * Points each wall toward where its branch goes next, like a fence arm. The arm may reach across a wall gap
   * or a one-block rise. Walls beside each other stay separate unless a branch links them.
   *
   * @param branch Branch whose walls to connect.
   */
  private void connectWalls(Branch branch) {
    List<Node> nodes = branch.nodes();
    List<int[]> path = new ArrayList<>();
    List<Boolean> isWall = new ArrayList<>();
    int impliedStart = impliedStart(branch);
    double arc = 0;
    for (int i = 0; i + 1 < nodes.size(); i++) {
      Vec3 a = nodes.get(i).position();
      Vec3 b = nodes.get(i + 1).position();
      boolean impliedFence = i >= impliedStart && wantedClass(branch, i, arc) == ShapeClass.FENCE;
      boolean wall = segmentClass(branch, i, arc) == ShapeClass.WALL && !impliedFence;
      budget.charge(3 * (b.sub(a).length() + 1));
      for (int[] voxel : VoxelPaths.sixConnected(a, b)) {
        int last = path.size() - 1;
        if (last < 0 || !same(path.get(last), voxel)) {
          path.add(voxel);
          isWall.add(wall);
        } else if (wall) {
          isWall.set(last, true);
        }
      }
      arc += b.sub(a).length();
    }
    for (int i = 0; i < path.size(); i++) {
      if (isWall.get(i) && grid.get(path.get(i)[0], path.get(i)[1], path.get(i)[2]) instanceof Shape.Wall) {
        pointWall(path, i, 1);
        pointWall(path, i, -1);
      }
    }
  }

  /**
   * Gives a wall an arm toward the next horizontal step of its path, in one direction, looking up to two blocks ahead.
   *
   * @param path The branch's voxels in order.
   * @param from Index of the wall.
   * @param step 1 to look forward along the path, -1 to look back.
   */
  private void pointWall(List<int[]> path, int from, int step) {
    int[] voxel = path.get(from);
    for (int j = from + step; Math.abs(j - from) <= WALL_ARM_REACH && j >= 0 && j < path.size(); j += step) {
      int[] target = path.get(j);
      if (target[0] == voxel[0] && target[2] == voxel[2]) {
        continue;
      }
      if (continuesFrom(path, j, step)) {
        for (Direction direction : Direction.values()) {
          if (voxel[0] + direction.dx() == target[0] && voxel[2] + direction.dz() == target[2]) {
            addWallArm(voxel, direction);
          }
        }
      }
      return;
    }
  }

  /**
   * True when a block exists at or just past this path index, so an arm never points off a bare tip.
   *
   * @param path  The branch's voxels in order.
   * @param index Index to check from.
   * @param step  1 to look forward along the path, -1 to look back.
   */
  private boolean continuesFrom(List<int[]> path, int index, int step) {
    for (int k = 0; k <= WALL_ARM_REACH; k++) {
      int j = index + k * step;
      if (j >= 0 && j < path.size() && grid.get(path.get(j)[0], path.get(j)[1], path.get(j)[2]) != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Adds a low arm on one side of a wall.
   *
   * @param voxel     Position of the wall.
   * @param direction Side of the arm.
   */
  private void addWallArm(int[] voxel, Direction direction) {
    if (grid.get(voxel[0], voxel[1], voxel[2]) instanceof Shape.Wall w) {
      grid.put(voxel[0], voxel[1], voxel[2], new Shape.Wall(w.post(), direction == Direction.NORTH ? WallSide.LOW : w.north(), direction == Direction.EAST ? WallSide.LOW : w.east(), direction == Direction.SOUTH ? WallSide.LOW : w.south(), direction == Direction.WEST ? WallSide.LOW : w.west()));
    }
  }

  /**
   * Places fences along a branch's fence-thin segments, linked to their neighbors, with occasional closed gates on
   * straight level runs. Some voxels are skipped, by the coverage setting, to thin out busy twigs.
   *
   * @param branch Branch to place.
   */
  private void rasterizeThin(Branch branch) {
    TreeRandom random = TreeRandom.forPath(seed, branch.pathId() + "#thin");
    List<int[]> path = thinPath(branch);
    int count = path.size();
    boolean[] placed = new boolean[count];
    double[] gateRolls = new double[count];
    double[] facingRolls = new double[count];
    double[] inWallRolls = new double[count];
    boolean[] flippedCorner = new boolean[count];
    TreeRandom corners = TreeRandom.forPath(seed, branch.pathId() + "#corners");
    for (int i = 0; i < count; i++) {
      placed[i] = random.nextDouble() < settings.thinCoverage() || keepsTrunkWhole(branch);
      gateRolls[i] = random.nextDouble();
      facingRolls[i] = random.nextDouble();
      inWallRolls[i] = random.nextDouble();
      flippedCorner[i] = corners.nextDouble() < settings.voxelRandomness() * 0.5;
    }
    for (int i = 0; i < count; i++) {
      if (!placed[i]) {
        continue;
      }
      int[] back = stepTo(path, placed, i, i - 1);
      int[] ahead = stepTo(path, placed, i, i + 1);
      Direction rail = railAlong(back, ahead);
      if (rail != null && !keepsTrunkWhole(branch) && gateRolls[i] < settings.gateChance() && availability.gate()) {
        Direction facing = perpendicular(rail, facingRolls[i] < 0.5);
        placeThin(path.get(i), new Shape.FenceGate(facing, false, inWallRolls[i] < settings.gateInWallChance()));
      } else {
        placeThin(path.get(i), fenceFor(back, ahead, !flippedCorner[i], i > 0 && flippedCorner[i - 1]));
      }
    }
  }

  /**
   * The block step to a path neighbor that will actually hold a shape, otherwise null.
   *
   * @param path   The branch's voxels in order.
   * @param placed Which path voxels will receive a shape.
   * @param from   Index of the block.
   * @param to     Index of the neighbor.
   */
  private int[] stepTo(List<int[]> path, boolean[] placed, int from, int to) {
    if (to < 0 || to >= path.size()) {
      return null;
    }
    int[] a = path.get(from);
    int[] b = path.get(to);
    int[] step = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
    boolean adjacent = Math.max(Math.abs(step[0]), Math.max(Math.abs(step[1]), Math.abs(step[2]))) == 1;
    return adjacent && (placed[to] || grid.get(b[0], b[1], b[2]) != null) ? step : null;
  }

  /**
   * The voxels a branch's fence segments pass through, stopping where the tip becomes leaves, trimmed back to the
   * canopy, and with a random few blocks dropped from the tip.
   *
   * @param branch Branch to trace.
   */
  private List<int[]> thinPath(Branch branch) {
    List<Node> nodes = branch.nodes();
    List<int[]> path = new ArrayList<>();
    double arc = 0;
    int impliedStart = Math.min(impliedStart(branch), canopyEnd(branch));
    for (int i = 0; i + 1 < nodes.size() && i < impliedStart; i++) {
      Vec3 a = nodes.get(i).position();
      Vec3 b = nodes.get(i + 1).position();
      if (segmentClass(branch, i, arc) == ShapeClass.FENCE && !b.sub(a).normalized().equals(Vec3.ZERO)) {
        budget.charge(b.sub(a).length() + 1);
        checkVoxelLimit();
        for (int[] voxel : VoxelPaths.line(a, b)) {
          if (path.isEmpty() || !same(path.get(path.size() - 1), voxel)) {
            path.add(voxel);
          }
        }
      }
      arc += b.sub(a).length();
    }
    if (!keepsTrunkWhole(branch) || path.stream().anyMatch(v -> inCanopy(v[0], v[1], v[2]))) {
      trimOutsideCanopy(path);
    }
    if (settings.voxelRandomness() > 0 && branch.level() > 0) {
      double roll = TreeRandom.forPath(seed, branch.pathId() + "#tip").nextDouble();
      int drop = (int) (roll * settings.voxelRandomness() * (MAX_TIP_DROP + 1));
      for (int i = 0; i < drop && path.size() > 1; i++) {
        path.remove(path.size() - 1);
      }
    }
    return path;
  }

  /**
   * With {@code trimToCanopy}, the wall and fence segments to draw: up to the last node with leaves at or above it,
   * so nothing pokes out of the crown. A trunk that never reaches the leaves stays whole, and a side branch that
   * never does is left out.
   *
   * @param branch Branch to check.
   */
  private int canopyEnd(Branch branch) {
    List<Node> nodes = branch.nodes();
    if (!settings.trimToCanopy() || !availability.leaves() || density == null || density.isEmpty() || settings.tipCanopyDensity() <= 0) {
      return nodes.size() - 1;
    }
    // The implied tail makes its own leaf puff, so it cannot count as reaching the canopy.
    for (int i = Math.min(nodes.size() - 1, impliedStart(branch)); i >= 0; i--) {
      Vec3 p = nodes.get(i).position();
      if (touchesCanopy((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()))) {
        return i;
      }
    }
    return branch.level() == 0 ? nodes.size() - 1 : 0;
  }

  /**
   * A slim trunk can pass between leaves without its own blocks having any density, so neighbors count. It takes
   * a small cluster at or above this block: a lone speck is not canopy, and leaves only below mean it has poked out.
   *
   * @param x Voxel x.
   * @param y Voxel y.
   * @param z Voxel z.
   */
  private boolean touchesCanopy(int x, int y, int z) {
    int found = 0;
    for (int dy = 0; dy <= CANOPY_REACH; dy++) {
      for (int dz = -CANOPY_REACH; dz <= CANOPY_REACH; dz++) {
        for (int dx = -CANOPY_REACH; dx <= CANOPY_REACH; dx++) {
          if (inCanopy(x + dx, y + dy, z + dz) && ++found >= CANOPY_CLUSTER) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Removes the bare end of a thin branch, so wood does not poke out past the leaves.
   *
   * @param path The branch's voxels in order; trimmed in place.
   */
  private void trimOutsideCanopy(List<int[]> path) {
    if (density == null || density.isEmpty() || settings.tipCanopyDensity() <= 0) {
      return;
    }
    while (!path.isEmpty()) {
      int[] tip = path.get(path.size() - 1);
      if (inCanopy(tip[0], tip[1], tip[2])) {
        return;
      }
      path.remove(path.size() - 1);
    }
  }

  /**
   * Places a fence or gate; a fence landing on another fence merges their arms instead.
   *
   * @param voxel Position to place at.
   * @param shape Fence or gate to place.
   */
  private void placeThin(int[] voxel, Shape shape) {
    Shape existing = grid.get(voxel[0], voxel[1], voxel[2]);
    if (existing instanceof Shape.Fence fence && shape instanceof Shape.Fence added) {
      grid.put(voxel[0], voxel[1], voxel[2], fence.merged(added));
    } else {
      grid.putIfThicker(voxel[0], voxel[1], voxel[2], shape);
    }
  }

  /**
   * Radius multiplier for root-like spread around the base of a branch.
   */
  private final class FlareField {
    private final FlareSettings settings;
    private final double height;
    private final double angle;
    private final double phaseA;
    private final double phaseB;

    /**
     * Rolls this branch's flare angle and the two noise phases that make the lobes uneven.
     *
     * @param branch Branch the flares belong to.
     */
    FlareField(Branch branch) {
      settings = flares(branch);
      height = settings.height() * preset.tree().sizeScale();
      String key = "levels." + branch.levelName() + ".flares.angle";
      angle = StrictMath.toRadians(resolver.bind(settings.angle(), key, branch.pathId()).at(0));
      TreeRandom random = TreeRandom.forPath(seed, branch.pathId() + "#flare");
      phaseA = random.range(0, 2 * Math.PI);
      phaseB = random.range(0, 2 * Math.PI);
    }

    /**
     * Whether a distance up the branch is still within the flared base.
     *
     * @param arc Distance up the branch.
     */
    private boolean active(double arc) {
      return settings.radius() > 0 && height > 0 && arc < height;
    }

    /**
     * The largest the flare can make the radius at this height, used to size the area to fill.
     *
     * @param arc Distance up the branch.
     */
    double maximumScale(double arc) {
      return active(arc) ? 1 + settings.radius() * (1 + settings.noise()) : 1;
    }

    /**
     * The radius multiplier at a height and compass direction: largest at the base and shrinking with height, highest
     * on each lobe, varied by noise.
     *
     * @param arc Distance up the branch.
     * @param dx  X offset from the branch's center.
     * @param dz  Z offset from the branch's center.
     */
    double scale(double arc, double dx, double dz) {
      if (!active(arc)) {
        return 1;
      }
      double lobe = 1;
      if (settings.lobes() > 0 || settings.noise() > 0) {
        double theta = StrictMath.atan2(dz, dx);
        if (settings.lobes() > 0) {
          double wave = StrictMath.cos(settings.lobes() * (theta - angle) / 2);
          lobe = wave * wave;
        }
        lobe *= 1 + settings.noise() * 0.5 * (StrictMath.sin(3 * theta + phaseA) + StrictMath.sin(5 * theta + phaseB));
      }
      return 1 + settings.radius() * StrictMath.pow(1 - arc / height, settings.pinch()) * Math.max(0, lobe);
    }
  }
}
