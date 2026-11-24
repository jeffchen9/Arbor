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

import java.util.List;

/**
 * The trunks' horizontal offset by height, so the crown can follow leaning, curved or multiple trunks.
 */
public final class TrunkAxis {
  /**
   * Trunks start at the middle of a block, so the axis is measured from there.
   */
  private static final double BASE = 0.5;

  private final int minY;
  /**
   * Sideways offset of the trunks' middle at each whole height from {@code minY} up.
   */
  private final double[] offsetX;
  private final double[] offsetZ;

  /**
   * The axis of a single trunk.
   *
   * @param root The trunk.
   */
  public TrunkAxis(Branch root) {
    this(List.of(root));
  }

  /**
   * The axis through the middle of several trunks.
   *
   * @param roots The trunks.
   */
  public TrunkAxis(List<Branch> roots) {
    double low = Double.POSITIVE_INFINITY;
    double high = Double.NEGATIVE_INFINITY;
    for (Branch root : roots) {
      for (Node node : root.nodes()) {
        low = Math.min(low, node.position().y());
        high = Math.max(high, node.position().y());
      }
    }
    minY = (int) Math.floor(low);
    int size = (int) Math.ceil(high) - minY + 1;
    offsetX = new double[size];
    offsetZ = new double[size];
    for (int i = 0; i < size; i++) {
      double sumX = 0;
      double sumZ = 0;
      for (Branch root : roots) {
        Vec3 point = pointAtHeight(root.nodes(), minY + i);
        sumX += point.x();
        sumZ += point.z();
      }
      offsetX[i] = sumX / roots.size() - BASE;
      offsetZ[i] = sumZ / roots.size() - BASE;
    }
  }

  /**
   * The axis of a skeleton's trunks, or of its first branch when it has no root level.
   *
   * @param branches All branches of the skeleton.
   */
  public static TrunkAxis ofTrunks(List<Branch> branches) {
    List<Branch> trunks = branches.stream().filter(branch -> branch.level() == 0).toList();
    return new TrunkAxis(trunks.isEmpty() ? List.of(branches.get(0)) : trunks);
  }

  /**
   * Where the trunk crosses height {@code y}. Above or below the trunk, the node nearest that height stands in.
   *
   * @param nodes The trunk's nodes.
   * @param y     Height.
   */
  private static Vec3 pointAtHeight(List<Node> nodes, double y) {
    Vec3 nearest = nodes.get(0).position();
    for (int i = 1; i < nodes.size(); i++) {
      Vec3 a = nodes.get(i - 1).position();
      Vec3 b = nodes.get(i).position();
      if ((a.y() <= y && b.y() >= y) || (a.y() >= y && b.y() <= y)) {
        double span = b.y() - a.y();
        return span == 0 ? a : a.lerp(b, (y - a.y()) / span);
      }
      if (Math.abs(b.y() - y) < Math.abs(nearest.y() - y)) {
        nearest = b;
      }
    }
    return nearest;
  }

  /**
   * Moves a point so the trunk at that height lies on the crown's central axis.
   *
   * @param point Point to move.
   */
  public Vec3 recenter(Vec3 point) {
    double position = Math.max(0, Math.min(offsetX.length - 1, point.y() - minY));
    int lower = Math.min(offsetX.length - 1, (int) position);
    int upper = Math.min(offsetX.length - 1, lower + 1);
    double fraction = position - lower;
    double x = offsetX[lower] + (offsetX[upper] - offsetX[lower]) * fraction;
    double z = offsetZ[lower] + (offsetZ[upper] - offsetZ[lower]) * fraction;
    return new Vec3(point.x() - x, point.y(), point.z() - z);
  }
}
