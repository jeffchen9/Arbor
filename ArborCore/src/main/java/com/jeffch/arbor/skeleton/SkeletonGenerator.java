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

package com.jeffch.arbor.skeleton;

import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.preset.LevelPreset;
import com.jeffch.arbor.preset.ParamResolver;
import com.jeffch.arbor.preset.SpeciesPreset;

import java.util.*;

/**
 * Builds the branch hierarchy for one preset and seed.
 */
public final class SkeletonGenerator {
  private SkeletonGenerator() {
  }

  /**
   * The skeleton for a preset and seed, with a fresh work budget.
   *
   * @param preset Preset to grow.
   * @param seed   Tree seed.
   */
  public static Skeleton generate(SpeciesPreset preset, long seed) {
    return generate(preset, seed, new GenerationBudget());
  }

  /**
   * The skeleton for a preset and seed, charging its work to {@code budget}.
   *
   * @param preset Preset to grow.
   * @param seed   Tree seed.
   * @param budget Work limit.
   */
  public static Skeleton generate(SpeciesPreset preset, long seed, GenerationBudget budget) {
    return new Run(preset, seed, budget).generate();
  }

  /**
   * The state of one generation: every branch made so far and the limits being tracked.
   */
  private static final class Run {
    private final SpeciesPreset preset;
    private final long seed;
    private final ParamResolver resolver;
    private final List<Branch> all = new ArrayList<>();
    private final GenerationBudget budget;
    private long nodeCount;
    private TrunkAxis axis;
    private int rootCount = 1;

    Run(SpeciesPreset preset, long seed, GenerationBudget budget) {
      this.budget = budget;
      this.preset = preset;
      this.seed = seed;
      resolver = new ParamResolver(seed);
    }

    /**
     * Grows the tree one level at a time: the trunks first, then each level's branches on every branch of its
     * parent level. Every branch's random choices come from its own path, so one branch never shifts another.
     */
    Skeleton generate() {
      // The tree's height is the tallest trunk, measured before any branch grows, because levels use it for
      // height-based settings and the crown envelope.
      LevelPreset root = preset.levels().get(0);
      List<Placement> rootPlacements = NodePlacer.place(root.generation(), resolver,
              "levels." + root.name() + ".generation", 0, List.of(), "root", seed, budget);
      rootCount = rootPlacements.size();
      double height = 0;
      for (Placement placement : rootPlacements) {
        height = Math.max(height, SpineBuilder.sampledLength(
                request(0, null, placement, 0, root.name() + "." + placement.index())));
      }
      double treeHeight = Math.min(height, preset.tree().maxHeight());

      Map<String, List<BranchSampler>> byLevel = new HashMap<>();
      Vec3 rootNormal = SpineBuilder.rootFrame(preset, resolver).normal();
      for (int index = 0; index < preset.levels().size(); index++) {
        LevelPreset level = preset.levels().get(index);
        List<BranchSampler> created = new ArrayList<>();
        if (index == 0) {
          for (Placement placement : rootPlacements) {
            addBranch(created, index, level, null, placement, treeHeight, rootNormal);
          }
          if (!created.isEmpty()) {
            axis = new TrunkAxis(created.stream().map(BranchSampler::branch).toList());
          }
        } else {
          for (BranchSampler parent : byLevel.getOrDefault(level.parent(), List.of())) {
            List<Placement> placements = NodePlacer.place(level.generation(), resolver,
                    "levels." + level.name() + ".generation", parent.length(), parent.bends(),
                    parent.branch().pathId(), seed, budget);
            for (Placement placement : placements) {
              addBranch(created, index, level, parent, placement, treeHeight,
                      parent.frame(placement.t()).normal());
            }
          }
        }
        byLevel.put(level.name(), created);
      }
      return new Skeleton(all.isEmpty() ? 0 : treeHeight, List.copyOf(all));
    }

    /**
     * Everything the spine builder needs to grow one branch.
     *
     * @param index      Level index.
     * @param parent     Parent branch, or null for a trunk.
     * @param placement  Where on the parent the branch starts.
     * @param treeHeight Height of the tree.
     * @param path       Path id of the branch.
     */
    private SpineBuilder.SpineRequest request(int index, BranchSampler parent, Placement placement,
                                              double treeHeight, String path) {
      return new SpineBuilder.SpineRequest(preset, index, parent, placement, treeHeight, path, resolver, seed, budget,
              axis, rootCount);
    }

    /**
     * Grows one branch and records it, unless it was removed (pruned, broken or cut by the envelope). The path id,
     * such as {@code trunk.0/bough.3}, names the branch for its random streams and its children.
     *
     * @param created       Receives the new branch.
     * @param index         Level index.
     * @param level         The branch's level.
     * @param parent        Parent branch, or null for a trunk.
     * @param placement     Where on the parent the branch starts.
     * @param treeHeight    Height of the tree.
     * @param initialNormal Starting normal for the branch's frames.
     */
    private void addBranch(List<BranchSampler> created, int index, LevelPreset level, BranchSampler parent,
                           Placement placement, double treeHeight, Vec3 initialNormal) {
      String own = level.name() + "." + placement.index();
      String path = parent == null ? own : parent.branch().pathId() + "/" + own;
      Optional<List<Node>> nodes = SpineBuilder.build(request(index, parent, placement, treeHeight, path));
      if (nodes.isEmpty()) {
        return;
      }
      if (all.size() >= NodePlacer.MAX_BRANCHES) {
        throw new GenerationLimitException("preset exceeds the limit of " + NodePlacer.MAX_BRANCHES + " branches");
      }
      nodeCount += nodes.get().size();
      if (nodeCount > SpineBuilder.MAX_NODES) {
        throw new GenerationLimitException("preset exceeds the limit of " + SpineBuilder.MAX_NODES + " spine nodes");
      }
      Branch branch = new Branch(index, level.name(), path, parent == null ? null : parent.branch().pathId(),
              placement.t(), nodes.get());
      all.add(branch);
      created.add(new BranchSampler(branch, initialNormal));
    }
  }
}
