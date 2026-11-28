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

package com.jeffch.arbor.foliage;

import com.jeffch.arbor.math.Frame;
import com.jeffch.arbor.math.TreeRandom;
import com.jeffch.arbor.math.ValueNoise3;
import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.preset.*;
import com.jeffch.arbor.skeleton.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the 3D leaf density field from clusters placed on branches.
 *
 * <p>Each leaf generator places soft ellipsoids ("clusters") along the branches of its level. Overlapping clusters
 * combine like stacked translucent layers: density is one minus the product of what each lets through, so it rises
 * toward 1 but never past it. The voxelizer later turns this field into leaf blocks.
 */
public final class DensityGenerator {
  /**
   * Largest field along any axis, in voxels.
   */
  private static final int MAX_AXIS = 512;
  /**
   * Largest field in total, in voxels.
   */
  private static final long MAX_VOXELS = 32_000_000L;
  /**
   * Half-width of the soft edge where shade pruning fades leaves in or out.
   */
  private static final double SHADE_BAND = 0.1;
  /**
   * Density below this counts as empty when measuring how wide each layer of the crown is.
   */
  private static final double VISIBLE = 0.001;
  private final Skeleton skeleton;
  private final SpeciesPreset preset;
  private final long seed;
  private final double scale;
  private final ParamResolver resolver;
  private final GenerationBudget budget;
  private final TrunkAxis axis;

  private DensityGenerator(Skeleton skeleton, SpeciesPreset preset, long seed, GenerationBudget budget) {
    this.budget = budget;
    this.skeleton = skeleton;
    this.preset = preset;
    this.seed = seed;
    scale = preset.tree().sizeScale();
    resolver = new ParamResolver(seed);
    axis = TrunkAxis.ofTrunks(skeleton.branches());
  }

  /**
   * The leaf density for a skeleton, with a fresh work budget.
   *
   * @param skeleton The tree's branches.
   * @param preset   Preset whose leaf generators are used.
   * @param seed     Tree seed.
   */
  public static DensityField generate(Skeleton skeleton, SpeciesPreset preset, long seed) {
    return generate(skeleton, preset, seed, new GenerationBudget());
  }

  /**
   * The leaf density for a skeleton, or an empty field when the preset has no leaves or the tree no branches.
   *
   * @param skeleton The tree's branches.
   * @param preset   Preset whose leaf generators are used.
   * @param seed     Tree seed.
   * @param budget   Shared work limit, so an extreme preset stops with an error instead of hanging.
   */
  public static DensityField generate(Skeleton skeleton, SpeciesPreset preset, long seed, GenerationBudget budget) {
    if (preset.leaves().isEmpty() || skeleton.branches().isEmpty()) {
      return DensityField.empty();
    }
    return new DensityGenerator(skeleton, preset, seed, budget).build();
  }

  /**
   * Grows a bounding box along one axis to hold a cluster's reach.
   *
   * @param low    Lower corner; changed in place.
   * @param high   Upper corner; changed in place.
   * @param center Cluster center along the axis.
   * @param reach  Cluster radius along the axis.
   * @param axis   0 for x, 1 for y, 2 for z.
   */
  private static void expand(int[] low, int[] high, double center, double reach, int axis) {
    low[axis] = Math.min(low[axis], (int) Math.floor(center - reach));
    high[axis] = Math.max(high[axis], (int) Math.floor(center + reach));
  }

  /**
   * Moves a cluster off its branch along the leaf direction set by sky influence, fold and align.
   *
   * @param leaf    The leaf generator.
   * @param radial  Direction straight out from the branch.
   * @param tangent Branch direction at the cluster.
   * @param size    The cluster's size factor.
   */
  private static Vec3 leafOffset(LeafGenerator leaf, Vec3 radial, Vec3 tangent, double size) {
    Vec3 direction = radial.lerp(Vec3.UP, leaf.skyInfluence());
    Vec3 aboveBranch = Vec3.UP.sub(tangent.scale(tangent.y())).normalized();
    if (!aboveBranch.equals(Vec3.ZERO)) {
      direction = direction.lerp(aboveBranch.scale(Math.signum(leaf.fold())), Math.abs(leaf.fold()));
    }
    direction = direction.lerp(tangent.scale(Math.signum(leaf.align())), Math.abs(leaf.align()));
    return direction.scale(leaf.reach() * size);
  }

  /**
   * Adds one cluster to the field. {@code remaining} holds, per voxel, how much is still let through; each voxel
   * inside the ellipsoid lets through less, most at the center and fading to nothing at the edge.
   *
   * @param cluster   Cluster to add.
   * @param falloff   Higher values make the cluster denser at its core and thinner toward the edge.
   * @param remaining How much each voxel still lets through; changed in place.
   * @param min       Field's lower corner in world voxels.
   * @param size      Field's size in voxels along x, y and z.
   */
  private static void splat(Cluster cluster, double falloff, float[] remaining, int[] min, int[] size) {
    Vec3 center = cluster.center();
    double reach = cluster.reach();
    // Only voxels in the cluster's bounding box, clipped to the field, can be touched.
    int fromX = Math.max(min[0], (int) Math.floor(center.x() - reach));
    int toX = Math.min(min[0] + size[0] - 1, (int) Math.floor(center.x() + reach));
    int fromY = Math.max(min[1], (int) Math.floor(center.y() - reach));
    int toY = Math.min(min[1] + size[1] - 1, (int) Math.floor(center.y() + reach));
    int fromZ = Math.max(min[2], (int) Math.floor(center.z() - reach));
    int toZ = Math.min(min[2] + size[2] - 1, (int) Math.floor(center.z() + reach));
    for (int z = fromZ; z <= toZ; z++) {
      for (int y = fromY; y <= toY; y++) {
        for (int x = fromX; x <= toX; x++) {
          double dx = x + 0.5 - center.x();
          double dy = y + 0.5 - center.y();
          double dz = z + 0.5 - center.z();
          // Offset in the cluster's own axes, each divided by that axis's radius: inside when the sum of squares < 1.
          double along = axis(dx, dy, dz, cluster.stretch()) / cluster.stretchRadius();
          double side = axis(dx, dy, dz, cluster.normal()) / cluster.normalRadius();
          double other = axis(dx, dy, dz, cluster.binormal()) / cluster.binormalRadius();
          double squared = along * along + side * side + other * other;
          if (squared >= 1) {
            continue;
          }
          double value = cluster.strength() * StrictMath.pow(1 - StrictMath.sqrt(squared), falloff);
          int index = (x - min[0]) + size[0] * ((y - min[1]) + size[1] * (z - min[2]));
          remaining[index] *= (float) (1 - value);
        }
      }
    }
  }

  /**
   * The length of an offset along a unit axis.
   *
   * @param x    Offset x.
   * @param y    Offset y.
   * @param z    Offset z.
   * @param axis Unit axis.
   */
  private static double axis(double x, double y, double z, Vec3 axis) {
    return x * axis.x() + y * axis.y() + z * axis.z();
  }

  /**
   * Visits every voxel of a field in storage order, with its local position and array index.
   *
   * @param size    Field's size in voxels along x, y and z.
   * @param visitor Called for each voxel.
   */
  private static void forEachVoxel(int[] size, VoxelVisitor visitor) {
    int index = 0;
    for (int z = 0; z < size[2]; z++) {
      for (int y = 0; y < size[1]; y++) {
        for (int x = 0; x < size[0]; x++, index++) {
          visitor.visit(x, y, z, index);
        }
      }
    }
  }

  /**
   * 0 below {@code from}, 1 above {@code to}, and an S-shaped ramp between.
   *
   * @param from  Where the ramp starts.
   * @param to    Where the ramp ends.
   * @param value Input value.
   */
  private static double smoothstep(double from, double to, double value) {
    double t = Math.clamp((value - from) / (to - from), 0, 1);
    return t * t * (3 - 2 * t);
  }

  /**
   * Places every generator's clusters, sizes the field to hold them all, then combines the generators.
   */
  private DensityField build() {
    // First pass: place clusters and find the box they cover. The first generator also carries the implied tips.
    List<List<Cluster>> clusters = new ArrayList<>();
    int[] low = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
    int[] high = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    boolean any = false;
    for (int g = 0; g < preset.leaves().size(); g++) {
      List<Cluster> group = place(preset.leaves().get(g), g);
      if (g == 0) {
        group.addAll(impliedTipClusters());
      }
      clusters.add(group);
      for (Cluster cluster : group) {
        any = true;
        expand(low, high, cluster.center().x(), cluster.reach(), 0);
        expand(low, high, cluster.center().y(), cluster.reach(), 1);
        expand(low, high, cluster.center().z(), cluster.reach(), 2);
      }
    }
    if (!any) {
      return DensityField.empty();
    }
    // One empty voxel of border on every side, so neighbor lookups at the edge always read zero.
    int[] min = {low[0] - 1, low[1] - 1, low[2] - 1};
    int[] size = {high[0] - low[0] + 3, high[1] - low[1] + 3, high[2] - low[2] + 3};
    long total = (long) size[0] * size[1] * size[2];
    if (size[0] > MAX_AXIS || size[1] > MAX_AXIS || size[2] > MAX_AXIS || total > MAX_VOXELS) {
      throw new GenerationLimitException("leaf density exceeds the limit of " + MAX_AXIS + " voxels per axis or "
              + MAX_VOXELS + " total voxels");
    }
    // Second pass: each generator builds its own field (with its own envelope, noise and pruning), and the fields
    // combine the same way clusters do.
    float[] remaining = new float[(int) total];
    Arrays.fill(remaining, 1);
    for (int g = 0; g < clusters.size(); g++) {
      if (!clusters.get(g).isEmpty()) {
        float[] density = generatorField(preset.leaves().get(g), g, clusters.get(g), min, size);
        for (int i = 0; i < remaining.length; i++) {
          remaining[i] *= 1 - density[i];
        }
      }
    }
    float[] data = new float[remaining.length];
    for (int i = 0; i < data.length; i++) {
      data[i] = Math.clamp(1 - remaining[i], 0, 1);
    }
    return new DensityField(min, size, data);
  }

  /**
   * Places leaves along branch ends too thin for wood, so foliage implies the branch.
   */
  private List<Cluster> impliedTipClusters() {
    VoxelSettings shapes = preset.shapes();
    double radius = shapes.impliedLeafSize() * scale;
    List<Cluster> clusters = new ArrayList<>();
    if (shapes.impliedRadius() <= 0 || radius <= 0 || shapes.impliedLeafStrength() <= 0) {
      return clusters;
    }
    Vec3 side = new Vec3(1, 0, 0);
    Vec3 other = Vec3.UP.cross(side);
    for (Branch branch : skeleton.branches()) {
      if (!preset.levels().get(Math.min(branch.level(), preset.levels().size() - 1)).wood()) {
        continue;
      }
      // From where the branch gets too thin for wood, drop a round cluster about once per scaled block.
      int start = ImpliedTips.tailStart(branch, shapes.impliedRadius());
      List<Node> nodes = branch.nodes();
      double sinceLast = Double.POSITIVE_INFINITY;
      for (int i = start; i < nodes.size() && start < nodes.size() - 1; i++) {
        if (i > start) {
          sinceLast += nodes.get(i).position().sub(nodes.get(i - 1).position()).length();
        }
        if (sinceLast >= scale) {
          Vec3 tangent = nodes.get(Math.min(i + 1, nodes.size() - 1)).position()
                  .sub(nodes.get(Math.max(i - 1, 0)).position()).normalized();
          Vec3 center = nodes.get(i).position().add(leafOffset(preset.leaves().get(0), Vec3.ZERO, tangent, radius));
          clusters.add(new Cluster(center, Vec3.UP, side, other, radius, radius, radius,
                  shapes.impliedLeafStrength()));
          sinceLast = 0;
        }
      }
    }
    return clusters;
  }

  /**
   * The clusters of one leaf generator: positions along each branch of its parent level, then size, strength,
   * orientation and offset for each.
   *
   * @param leaf  The leaf generator.
   * @param index The generator's position in the preset, which keeps its random choices separate from the others.
   */
  private List<Cluster> place(LeafGenerator leaf, int index) {
    List<Cluster> clusters = new ArrayList<>();
    String prefix = "leaves." + index;
    for (Branch branch : skeleton.branches()) {
      if (!branch.levelName().equals(leaf.parent())) {
        continue;
      }
      BranchSampler sampler = new BranchSampler(branch);
      List<Placement> placements = NodePlacer.place(leaf.generation(), resolver, prefix + ".generation", sampler.length(),
              sampler.bends(), branch.pathId(), seed, budget);
      for (Placement placement : placements) {
        String owner = branch.pathId() + "/leaf" + index + "." + placement.index();
        double t = placement.t();
        double size = Math.max(0, resolver.bind(leaf.size(), prefix + ".size", owner).at(t)) * scale
                + Math.max(0, resolver.bind(leaf.sizePercentOfParent(), prefix + ".sizePercentOfParent", owner).at(t))
                * sampler.length();
        double strength = Math.clamp(resolver.bind(leaf.strength(), prefix + ".strength", owner).at(t), 0, 1);
        if (size <= 0 || strength <= 0) {
          continue;
        }
        // The long axis follows the branch, tilted toward the sky by sky influence. The other two axes are built
        // perpendicular to it from the branch frame, with fallbacks for when the branch points straight up.
        Frame frame = sampler.frame(t);
        Vec3 stretch = frame.tangent().lerp(Vec3.UP, leaf.skyInfluence()).normalized();
        if (stretch.equals(Vec3.ZERO)) {
          stretch = Vec3.UP;
        }
        Vec3 normal = frame.normal().sub(stretch.scale(frame.normal().dot(stretch))).normalized();
        if (normal.equals(Vec3.ZERO)) {
          normal = new Vec3(1, 0, 0).sub(stretch.scale(stretch.x())).normalized();
          if (normal.equals(Vec3.ZERO)) {
            normal = new Vec3(0, 0, 1);
          }
        }
        // The side of the branch this cluster sits on, set by the placement's roll around the branch.
        double roll = StrictMath.toRadians(placement.rollDeg());
        Vec3 radial = frame.normal().scale(StrictMath.cos(roll)).add(frame.binormal().scale(StrictMath.sin(roll)));
        Vec3 center = sampler.point(t).add(leafOffset(leaf, radial, frame.tangent(), size));
        Cluster cluster = new Cluster(center, stretch, normal, stretch.cross(normal).normalized(),
                size * leaf.scaleY() * leaf.elongation(), size * leaf.scaleX(), size * leaf.scaleZ(), strength);
        if (!(cluster.reach() <= MAX_AXIS)) {
          throw new GenerationLimitException("leaf generator " + index + ": cluster exceeds "
                  + MAX_AXIS + " blocks");
        }
        clusters.add(cluster);
      }
    }
    return clusters;
  }

  /**
   * One generator's density: its clusters combined, softened at the crown envelope, broken up by noise, then shade
   * pruned.
   *
   * @param leaf     The leaf generator.
   * @param index    Position of the generator, used to name its random streams.
   * @param clusters The generator's clusters.
   * @param min      Field's lower corner in world voxels.
   * @param size     Field's size in voxels along x, y and z.
   */
  private float[] generatorField(LeafGenerator leaf, int index, List<Cluster> clusters, int[] min, int[] size) {
    int count = size[0] * size[1] * size[2];
    budget.charge(count);
    float[] remaining = new float[count];
    Arrays.fill(remaining, 1);
    for (Cluster cluster : clusters) {
      double span = 2 * cluster.reach() + 2;
      budget.charge(span * span * span);
      splat(cluster, leaf.falloff(), remaining, min, size);
    }
    CrownEnvelope envelope = preset.shapeControl().enabled() ? preset.shapeControl().envelope(seed) : null;
    ValueNoise3 noise = new ValueNoise3(TreeRandomSeed.of(seed, "leaves." + index + "#noise"));
    double noiseScale = leaf.noiseScale() * scale;
    float[] density = new float[count];
    for (int z = 0, i = 0; z < size[2]; z++) {
      double worldZ = min[2] + z + 0.5;
      for (int y = 0; y < size[1]; y++) {
        double worldY = min[1] + y + 0.5;
        for (int x = 0; x < size[0]; x++, i++) {
          double value = 1 - remaining[i];
          if (value <= 0) {
            continue;
          }
          double worldX = min[0] + x + 0.5;
          if (envelope != null) {
            value *= envelope.softFactor(axis.recenter(new Vec3(worldX, worldY, worldZ)), skeleton.treeHeight(),
                    leaf.envelopeEdge(), scale);
          }
          if (value > 0 && leaf.noiseStrength() > 0) {
            value *= 1 - leaf.noiseStrength() * noise.at(worldX / noiseScale, worldY / noiseScale, worldZ / noiseScale);
          }
          density[i] = (float) Math.clamp(value, 0, 1);
        }
      }
    }
    applyShadePruning(leaf, density, min, size);
    return density;
  }

  /**
   * Thins leaves by how far out they are in their horizontal layer. INTERIOR keeps the outside and clears the shaded
   * middle; the other style keeps the middle. The threshold is a fraction of that layer's widest leaf.
   *
   * @param leaf    The leaf generator.
   * @param density The generator's density; changed in place.
   * @param min     Field's lower corner in world voxels.
   * @param size    Field's size in voxels along x, y and z.
   */
  private void applyShadePruning(LeafGenerator leaf, float[] density, int[] min, int[] size) {
    LeafGenerator.ShadeStyle style = leaf.shadePruning().style();
    if (style == LeafGenerator.ShadeStyle.NONE) {
      return;
    }
    double threshold = leaf.shadePruning().threshold();
    // The widest visible leaf in each horizontal layer, measured from the trunk axis.
    double[] layerRadius = new double[size[1]];
    forEachVoxel(size, (x, y, z, i) -> {
      if (density[i] > VISIBLE) {
        layerRadius[y] = Math.max(layerRadius[y], radial(min, x, y, z));
      }
    });
    forEachVoxel(size, (x, y, z, i) -> {
      if (density[i] > 0 && layerRadius[y] > 0) {
        double normalized = radial(min, x, y, z) / layerRadius[y];
        double edge = smoothstep(threshold - SHADE_BAND, threshold + SHADE_BAND, normalized);
        density[i] *= (float) (style == LeafGenerator.ShadeStyle.INTERIOR ? edge : 1 - edge);
      }
    });
  }

  /**
   * Horizontal distance of a local voxel's center from the trunk axis.
   *
   * @param min Field's lower corner in world voxels.
   * @param x   Local x.
   * @param y   Local y.
   * @param z   Local z.
   */
  private double radial(int[] min, int x, int y, int z) {
    return CrownEnvelope.radialDistance(axis.recenter(new Vec3(min[0] + x + 0.5, min[1] + y + 0.5, min[2] + z + 0.5)));
  }

  /**
   * Receives one voxel's local position and array index.
   */
  @FunctionalInterface
  private interface VoxelVisitor {
    void visit(int x, int y, int z, int index);
  }

  /**
   * One soft ellipsoid of leaf density.
   *
   * @param center         World position of the middle of the ellipsoid.
   * @param stretch        Unit axis the cluster is elongated along.
   * @param normal         Unit axis across the cluster, perpendicular to stretch.
   * @param binormal       Unit axis perpendicular to both stretch and normal.
   * @param stretchRadius  Radius in blocks along the stretch axis.
   * @param normalRadius   Radius in blocks along the normal axis.
   * @param binormalRadius Radius in blocks along the binormal axis.
   * @param strength       Density at the center, from 0 to 1.
   */
  private record Cluster(Vec3 center, Vec3 stretch, Vec3 normal, Vec3 binormal,
                         double stretchRadius, double normalRadius, double binormalRadius, double strength) {
    /**
     * The largest radius, which bounds the cluster in every direction.
     */
    double reach() {
      return Math.max(stretchRadius, Math.max(normalRadius, binormalRadius));
    }
  }

  /**
   * Derives a stable noise seed from the tree seed and a purpose.
   */
  private static final class TreeRandomSeed {
    private TreeRandomSeed() {
    }

    /**
     * A seed for the noise named by {@code path}.
     *
     * @param seed Tree seed.
     * @param path Name of the noise.
     */
    static long of(long seed, String path) {
      TreeRandom random = TreeRandom.forPath(seed, path);
      return (long) (random.nextDouble() * Long.MAX_VALUE);
    }
  }
  /**
   * How many leaf clusters each leaf generator places on this skeleton, in preset order, without building the field.
   * Shows whether sparse foliage comes from too few clusters or from clusters too weak to make leaf blocks.
   *
   * @param skeleton The tree's branches.
   * @param preset   Preset exactly as the tree was grown from it.
   * @param seed     Tree seed.
   */
  public static List<Integer> clusterCounts(Skeleton skeleton, SpeciesPreset preset, long seed) {
    DensityGenerator generator = new DensityGenerator(skeleton, preset, seed, new GenerationBudget());
    List<Integer> counts = new ArrayList<>();
    for (int g = 0; g < preset.leaves().size(); g++) {
      counts.add(skeleton.branches().isEmpty() ? 0 : generator.place(preset.leaves().get(g), g).size());
    }
    return counts;
  }
}
