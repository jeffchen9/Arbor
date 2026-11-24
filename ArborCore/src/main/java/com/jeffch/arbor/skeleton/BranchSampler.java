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

import com.jeffch.arbor.math.Frame;
import com.jeffch.arbor.math.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fast arc-length lookups along one branch. Positions along the branch are given as {@code t}, the fraction of its
 * length from 0 at the base to 1 at the tip, so children and leaves can be placed evenly whatever the node spacing.
 */
public final class BranchSampler {
  private final Branch branch;
  /**
   * Distance along the branch to each node; the first is 0 and the last is the full length.
   */
  private final double[] arc;
  private final double length;
  private final Vec3 initialNormal;
  /**
   * Orientation at each node, built on first use since most branches never need it.
   */
  private List<Frame> frames;

  /**
   * A sampler whose frames start with the normal along +x.
   *
   * @param branch Branch to sample.
   */
  public BranchSampler(Branch branch) {
    this(branch, new Vec3(1, 0, 0));
  }

  /**
   * @param branch        Branch to sample.
   * @param initialNormal Normal for the first frame, normally the parent's normal where this branch starts.
   */
  public BranchSampler(Branch branch, Vec3 initialNormal) {
    this.branch = branch;
    this.initialNormal = initialNormal;
    List<Node> nodes = branch.nodes();
    arc = new double[nodes.size()];
    for (int i = 1; i < arc.length; i++) {
      arc[i] = arc[i - 1] + nodes.get(i).position().sub(nodes.get(i - 1).position()).length();
    }
    length = arc[arc.length - 1];
  }

  public Branch branch() {
    return branch;
  }

  public double length() {
    return length;
  }

  /**
   * The point at fraction {@code t} of the length, between the two nearest nodes.
   *
   * @param t Fraction of the length, from 0 to 1.
   */
  public Vec3 point(double t) {
    int segment = segment(t);
    return node(segment).position().lerp(node(segment + 1).position(), fraction(t, segment));
  }

  /**
   * The branch radius at fraction {@code t}, blended between the two nearest nodes.
   *
   * @param t Fraction of the length, from 0 to 1.
   */
  public double radius(double t) {
    int segment = segment(t);
    double a = node(segment).radius();
    return a + (node(segment + 1).radius() - a) * fraction(t, segment);
  }

  /**
   * The orientation at the start of the segment holding {@code t}.
   *
   * @param t Fraction of the length, from 0 to 1.
   */
  public Frame frame(double t) {
    if (frames == null) {
      frames = Frame.parallelTransport(branch.nodes().stream().map(Node::position).toList(), initialNormal);
    }
    return frames.get(segment(t));
  }

  /**
   * Bend angles at interior nodes: how many degrees the branch turns at each, with where it is along the length.
   * Bifurcating branches use these to split at their sharpest bends.
   */
  public List<Bend> bends() {
    List<Bend> bends = new ArrayList<>();
    List<Node> nodes = branch.nodes();
    for (int i = 1; i < nodes.size() - 1; i++) {
      Vec3 before = nodes.get(i).position().sub(nodes.get(i - 1).position()).normalized();
      Vec3 after = nodes.get(i + 1).position().sub(nodes.get(i).position()).normalized();
      double cosine = Math.max(-1, Math.min(1, before.dot(after)));
      bends.add(new Bend(length <= 0 ? 0 : arc[i] / length, StrictMath.toDegrees(StrictMath.acos(cosine))));
    }
    return bends;
  }

  private Node node(int index) {
    return branch.nodes().get(index);
  }

  /**
   * The index of the node that starts the segment holding {@code t}, found by binary search on the distances.
   *
   * @param t Fraction of the length, from 0 to 1.
   */
  private int segment(double t) {
    if (arc.length < 2) {
      return 0;
    }
    double target = Math.max(0, Math.min(1, t)) * length;
    int index = Arrays.binarySearch(arc, target);
    // A miss returns minus the insertion point minus one; the node before that point starts the segment.
    int lower = index >= 0 ? index : -index - 2;
    return Math.max(0, Math.min(arc.length - 2, lower));
  }

  /**
   * How far {@code t} lies through its segment, from 0 at the start node to 1 at the end node.
   *
   * @param t       Fraction of the length, from 0 to 1.
   * @param segment Index of the segment holding {@code t}.
   */
  private double fraction(double t, int segment) {
    if (arc.length < 2) {
      return 0;
    }
    double span = arc[segment + 1] - arc[segment];
    if (span <= 0) {
      return 0;
    }
    double target = Math.max(0, Math.min(1, t)) * length;
    return Math.max(0, Math.min(1, (target - arc[segment]) / span));
  }
}
