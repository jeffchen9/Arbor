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

package com.jeffch.arbor.preset;

import com.jeffch.arbor.math.Vec3;

/**
 * A radial crown profile around the vertical line through the trunk base.
 *
 * @param crownBase Height in blocks, before size scale, where the crown starts.
 * @param radius    Crown radius in blocks by normalized height, from the crown base (0) to the tree top (1).
 */
public record CrownEnvelope(double crownBase, Curve radius) {
  /**
   * The crown's center line runs through the middle of the origin block.
   */
  private static final double AXIS = 0.5;

  /**
   * Horizontal distance from the crown's center line.
   *
   * @param point Point to measure.
   */
  public static double radialDistance(Vec3 point) {
    double dx = point.x() - AXIS;
    double dz = point.z() - AXIS;
    return StrictMath.sqrt(dx * dx + dz * dz);
  }

  /**
   * Whether a point is no higher than the tree and within the crown radius at its height. Below the crown base the
   * curve's first value applies, so branches there are limited by the lowest radius.
   *
   * @param point      Point to check.
   * @param treeHeight Height of the tree.
   * @param sizeScale  The tree's overall size factor.
   */
  public boolean contains(Vec3 point, double treeHeight, double sizeScale) {
    if (point.y() > treeHeight) {
      return false;
    }
    return radialDistance(point) <= radiusAt(point.y(), treeHeight, sizeScale);
  }

  /**
   * Leaf density multiplier: zero outside the crown, fading to one over the edge distance.
   *
   * @param point      Point to check.
   * @param treeHeight Height of the tree.
   * @param edge       Distance over which density fades in.
   * @param sizeScale  The tree's overall size factor.
   */
  public double softFactor(Vec3 point, double treeHeight, double edge, double sizeScale) {
    double base = crownBase * sizeScale;
    if (point.y() < base || point.y() > treeHeight) {
      return 0;
    }
    // Distance to the nearest surface: the side, the crown base or the top.
    double inside = radiusAt(point.y(), treeHeight, sizeScale) - radialDistance(point);
    inside = Math.min(inside, Math.min(point.y() - base, treeHeight - point.y()));
    if (inside <= 0) {
      return 0;
    }
    double scaledEdge = edge * sizeScale;
    if (scaledEdge <= 0) {
      return 1;
    }
    double t = Math.min(1, inside / scaledEdge);
    return t * t * (3 - 2 * t);
  }

  /**
   * The crown radius in blocks at height {@code y}, read from the curve by how far up the crown that height is.
   *
   * @param y          Height in blocks.
   * @param treeHeight Height of the tree.
   * @param sizeScale  The tree's overall size factor.
   */
  public double radiusAt(double y, double treeHeight, double sizeScale) {
    double base = crownBase * sizeScale;
    double span = treeHeight - base;
    double t = span <= 0 ? 0 : (y - base) / span;
    return radius.eval(t) * sizeScale;
  }
}
