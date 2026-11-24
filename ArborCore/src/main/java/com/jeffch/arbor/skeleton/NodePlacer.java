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

import com.jeffch.arbor.math.TreeRandom;
import com.jeffch.arbor.preset.BoundParam;
import com.jeffch.arbor.preset.GenerationSettings;
import com.jeffch.arbor.preset.GenerationSettings.Phyllotaxy;
import com.jeffch.arbor.preset.Param;
import com.jeffch.arbor.preset.ParamResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides where children grow on a parent spine.
 *
 * <p>Works in two steps. A placement mode first proposes candidate "slots" (a position along the parent and a roll
 * around it). {@link #finish} then nudges, filters and randomly removes them to give the final placements.
 */
public final class NodePlacer {
  /**
   * Most branches one tree may have.
   */
  public static final int MAX_BRANCHES = 200_000;

  /**
   * Budget units per placement, which hashes several random-stream paths.
   */
  private static final int PLACEMENT_WORK = 256;
  /**
   * Closest spacing between groups along the parent, in blocks, so a tiny setting cannot create millions of groups.
   */
  private static final double MIN_SPACING = 0.25;
  /**
   * Tolerance for comparing positions, so a child exactly at the first or last position is not lost to rounding.
   */
  private static final double EPSILON = 1e-9;

  private final GenerationSettings settings;
  private final ParamResolver resolver;
  private final String prefix;
  private final double length;
  private final String owner;
  private final long seed;
  private final GenerationBudget budget;

  private NodePlacer(GenerationSettings settings, ParamResolver resolver, String prefix, double length,
                     String owner, long seed, GenerationBudget budget) {
    this.budget = budget;
    this.settings = settings;
    this.resolver = resolver;
    this.prefix = prefix;
    this.length = length;
    this.owner = owner;
    this.seed = seed;
  }

  /**
   * The children of one parent, with a fresh work budget.
   *
   * @param settings      The level's placement settings.
   * @param resolver      Rolls each setting's variation.
   * @param settingPrefix Path of the settings, such as {@code levels.bough.generation}.
   * @param parentLength  Length of the parent branch.
   * @param bends         Where the parent changes direction.
   * @param ownerPath     Path id of the parent branch.
   * @param seed          Tree seed.
   */
  public static List<Placement> place(GenerationSettings settings, ParamResolver resolver, String settingPrefix,
                                      double parentLength, List<Bend> bends, String ownerPath, long seed) {
    return place(settings, resolver, settingPrefix, parentLength, bends, ownerPath, seed, new GenerationBudget());
  }

  /**
   * The children of one parent.
   *
   * @param settings      The level's placement settings.
   * @param resolver      Rolls each setting's variation.
   * @param settingPrefix Name of the generation settings, such as {@code levels.bough.generation}; it keys the random
   *                      streams.
   * @param parentLength  Parent length in blocks; 0 for the trunks, which have no parent.
   * @param bends         The parent's bends, used only by bifurcation.
   * @param ownerPath     The parent's path id, so each parent rolls its own children.
   * @param seed          Tree seed.
   * @param budget        Work limit.
   */
  public static List<Placement> place(GenerationSettings settings, ParamResolver resolver, String settingPrefix,
                                      double parentLength, List<Bend> bends, String ownerPath, long seed,
                                      GenerationBudget budget) {
    NodePlacer placer = new NodePlacer(settings, resolver, settingPrefix, parentLength, ownerPath, seed, budget);
    return placer.finish(placer.slots(bends));
  }

  private static GenerationLimitException limitError() {
    return new GenerationLimitException("preset exceeds the limit of " + MAX_BRANCHES + " branches");
  }

  /**
   * Candidate positions from the level's placement mode.
   *
   * @param bends Where the parent changes direction.
   */
  private List<Slot> slots(List<Bend> bends) {
    return switch (settings.mode()) {
      case FLOOD -> flood();
      case ABSOLUTE -> even(count(settings.count(), "count", 0.5, 0));
      case PROPORTIONAL ->
              even(count(settings.density(), "density", 0.5, 0, length * (settings.last() - settings.first())));
      case INTERVAL -> interval();
      case PHYLLOTAXY -> phyllotaxy();
      case BIFURCATION -> bifurcation(bends);
      case PARENT -> atOrigin();
    };
  }

  /**
   * A count of children at random positions and random rolls.
   */
  private List<Slot> flood() {
    int total = count(settings.count(), "count", 0.5, 0);
    List<Slot> slots = new ArrayList<>(total);
    for (int i = 0; i < total; i++) {
      TreeRandom random = TreeRandom.forPath(seed, child(i) + "#flood");
      slots.add(new Slot(random.range(settings.first(), settings.last()), random.range(0, 360)));
    }
    return slots;
  }

  /**
   * {@code total} children spaced evenly between first and last, each turned by the spiral angle from the one
   * before.
   *
   * @param total Number of children.
   */
  private List<Slot> even(int total) {
    List<Slot> slots = new ArrayList<>(total);
    double span = settings.last() - settings.first();
    for (int i = 0; i < total; i++) {
      slots.add(new Slot(settings.first() + span * (i + 0.5) / total, i * settings.spiral()));
    }
    return slots;
  }

  /**
   * All children at one position, spread evenly around it, as for several trunks from one base.
   */
  private List<Slot> atOrigin() {
    int total = count(settings.count(), "count", 0, 0);
    List<Slot> slots = new ArrayList<>(total);
    for (int i = 0; i < total; i++) {
      slots.add(new Slot(settings.first(), total == 0 ? 0 : i * 360.0 / total));
    }
    return slots;
  }

  /**
   * Groups of children at a set spacing. Balance turns each group so its members fall between the previous
   * group's.
   */
  private List<Slot> interval() {
    BoundParam frequency = bind(settings.frequency(), "frequency", owner);
    List<Slot> slots = new ArrayList<>();
    walkGroups(frequency, (group, t, spacing) -> {
      int members = count(settings.count(), "count", t, group);
      double base = group * settings.spiral();
      if (members > 0) {
        base += group * settings.balance() * 180.0 / members;
      }
      addGroup(slots, t, spacing, members, base);
    });
    return slots;
  }

  /**
   * Children at nodes one internode apart, arranged like real plants: pairs facing one way (distichous) or turning
   * 90 degrees each node (decussate), single children alternating sides, or rings (whorls) of several.
   */
  private List<Slot> phyllotaxy() {
    BoundParam internode = bind(settings.internodeLength(), "internodeLength", owner);
    Phyllotaxy style = settings.phyllotaxy();
    int members = switch (style) {
      case OPPOSITE_DISTICHOUS, OPPOSITE_DECUSSATE, OPPOSITE_RANDOM -> 2;
      case ALTERNATE_DISTICHOUS, ALTERNATE_RANDOM -> 1;
      case WHORLED -> settings.childrenPerNode();
    };
    List<Slot> slots = new ArrayList<>();
    walkGroups(internode, (group, t, spacing) -> {
      double base = group * settings.spiral() + switch (style) {
        case OPPOSITE_DISTICHOUS -> 0;
        case OPPOSITE_DECUSSATE -> group * 90.0;
        case ALTERNATE_DISTICHOUS -> group * 180.0;
        case OPPOSITE_RANDOM, ALTERNATE_RANDOM ->
                TreeRandom.forPath(seed, owner + "#" + prefix + "/roll/" + group).range(0, 360);
        case WHORLED -> group * 180.0 / members;
      };
      addGroup(slots, t, spacing, members, base);
    });
    return slots;
  }

  /**
   * Splits the parent at its bends, as a branch forks where it changes direction. Bends sharper than the threshold
   * are taken sharpest first (or gentlest first when locality is high), skipping any closer than the spacing to
   * one already chosen.
   *
   * @param bends Where the parent changes direction.
   */
  private List<Slot> bifurcation(List<Bend> bends) {
    // +1 prefers sharp bends, -1 prefers gentle ones.
    double direction = 1 - 2 * settings.locality();
    List<Bend> candidates = bends.stream()
            .filter(bend -> bend.angleDeg() >= settings.threshold()
                    && bend.t() >= settings.first() - EPSILON
                    && bend.t() <= settings.last() + EPSILON)
            .sorted(Comparator.<Bend>comparingDouble(bend -> -bend.angleDeg() * direction)
                    .thenComparingDouble(Bend::t))
            .toList();
    double minimumT = length <= 0 ? 0 : settings.spacing() / length;
    List<Bend> chosen = new ArrayList<>();
    for (Bend candidate : candidates) {
      if (settings.limit() > 0 && chosen.size() >= settings.limit()) {
        break;
      }
      budget.charge(chosen.size() + 1);
      boolean clear = chosen.stream().noneMatch(bend -> Math.abs(bend.t() - candidate.t()) < minimumT - EPSILON);
      if (clear) {
        chosen.add(candidate);
      }
    }
    chosen.sort(Comparator.comparingDouble(Bend::t));
    List<Slot> slots = new ArrayList<>(chosen.size());
    for (int i = 0; i < chosen.size(); i++) {
      double roll = i * settings.spiral() + i * settings.balance() * 180.0;
      slots.add(new Slot(chosen.get(i).t(), roll));
    }
    return slots;
  }

  /**
   * Steps from the last position down to the first, one spacing at a time, visiting each group. Walking down from
   * the tip keeps the youngest growth in the same place when the parent's length changes.
   *
   * @param spacingParam Distance between groups.
   * @param visitor      Called for each group.
   */
  private void walkGroups(BoundParam spacingParam, GroupVisitor visitor) {
    if (length <= 0) {
      return;
    }
    double position = settings.last() * length;
    double end = settings.first() * length - EPSILON;
    for (int group = 0; position >= end; group++) {
      if (settings.limit() > 0 && group >= settings.limit()) {
        break;
      }
      budget.charge(1);
      double t = Math.max(0, position / length);
      double spacing = Math.max(MIN_SPACING, spacingParam.at(t));
      visitor.visit(group, t, spacing);
      position -= spacing;
    }
  }

  /**
   * Adds one group's members evenly around the parent, each nudged along it by up to half a spacing when spread is
   * set.
   *
   * @param slots    Receives the members.
   * @param t        Group position along the parent, from 0 to 1.
   * @param spacing  Distance between groups.
   * @param members  Number of children in the group.
   * @param baseRoll Rotation of the group's first member, in degrees.
   */
  private void addGroup(List<Slot> slots, double t, double spacing, int members, double baseRoll) {
    for (int i = 0; i < members; i++) {
      double value = t;
      if (settings.spread() > 0 && length > 0) {
        TreeRandom random = TreeRandom.forPath(seed, child(slots.size()) + "#spread");
        value += random.range(-1, 1) * settings.spread() * spacing / 2 / length;
      }
      slots.add(new Slot(value, baseRoll + i * 360.0 / members));
      if (slots.size() > MAX_BRANCHES) {
        throw limitError();
      }
    }
  }

  /**
   * Turns candidates into placements: shifts each by the position offset, drops ones outside the range (when clip
   * is on), pruned by the pruning curve or randomly knocked out, and adds rotation and sink. Extra tip children
   * that continue the parent are added at the end.
   *
   * @param slots Candidate positions.
   */
  private List<Placement> finish(List<Slot> slots) {
    List<Placement> placements = new ArrayList<>(slots.size() + settings.extendParent());
    for (int i = 0; i < slots.size(); i++) {
      Slot slot = slots.get(i);
      // Each candidate keeps its index even when earlier ones are dropped, so its random choices never shift.
      String childOwner = child(i);
      double t = slot.t() + bind(settings.position(), "position", childOwner).at(slot.t());
      if (settings.clip() && (t < settings.first() - EPSILON || t > settings.last() + EPSILON)) {
        continue;
      }
      t = Math.max(0, Math.min(1, t));
      if (settings.pruning().eval(t) < 0.5) {
        continue;
      }
      double knockout = bind(settings.knockout(), "knockout", childOwner).at(t);
      if (knockout > 0 && TreeRandom.forPath(seed, childOwner + "#knockout").chance(knockout)) {
        continue;
      }
      double roll = slot.rollDeg() + bind(settings.rotation(), "rotation", childOwner).at(t);
      double sink = bind(settings.sink(), "sink", childOwner).at(t);
      placements.add(new Placement(i, t, roll, sink, false));
    }
    for (int i = 0; i < settings.extendParent(); i++) {
      placements.add(new Placement(slots.size() + i, 1, i * 360.0 / settings.extendParent(), 0, true));
    }
    return placements;
  }

  /**
   * A whole number of children from a setting.
   *
   * @param param The count setting.
   * @param key   Setting name, such as {@code count}.
   * @param t     Position along the parent, from 0 to 1.
   * @param group Index of the group, which names its random stream.
   */
  private int count(Param param, String key, double t, int group) {
    return count(param, key, t, group, 1);
  }

  /**
   * A whole number of children from a setting times {@code multiplier}. A fraction becomes one more child with
   * that chance, so 2.3 gives 3 children about 30% of the time and 2 otherwise.
   *
   * @param param      The count setting.
   * @param key        Setting name, such as {@code count}.
   * @param t          Position along the parent, from 0 to 1.
   * @param group      Index of the group, which names its random stream.
   * @param multiplier Factor applied to the count.
   */
  private int count(Param param, String key, double t, int group, double multiplier) {
    double value = Math.max(0, bind(param, key, owner).at(t)) * Math.max(0, multiplier);
    if (value > MAX_BRANCHES) {
      throw limitError();
    }
    budget.charge(value * PLACEMENT_WORK);
    int whole = (int) value;
    double fraction = value - whole;
    if (fraction > EPSILON
            && TreeRandom.forPath(seed, owner + "#" + prefix + "/" + key + "/" + group).chance(fraction)) {
      whole++;
    }
    return whole;
  }

  /**
   * A setting with its per-tree and per-branch variation rolled for {@code ownerPath}.
   *
   * @param param     The setting.
   * @param key       Setting name.
   * @param ownerPath Path id of the branch it applies to.
   */
  private BoundParam bind(Param param, String key, String ownerPath) {
    return resolver.bind(param, prefix + "." + key, ownerPath);
  }

  /**
   * The path id of candidate {@code index}, which names its random streams.
   *
   * @param index Candidate index.
   */
  private String child(int index) {
    return owner + "/" + prefix + "." + index;
  }

  /**
   * Receives one group: its number counting from the tip, its position and the spacing to the next group.
   */
  @FunctionalInterface
  private interface GroupVisitor {
    void visit(int group, double t, double spacing);
  }

  /**
   * A candidate child position, before filtering.
   *
   * @param t       Position on the parent, from 0 to 1.
   * @param rollDeg Roll around the parent, in degrees.
   */
  private record Slot(double t, double rollDeg) {
  }
}
