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

import com.jeffch.arbor.math.ValueNoise3;
import com.jeffch.arbor.math.Vec3;

/**
 * A radial crown profile around the vertical line through the trunk base, optionally lumpy.
 *
 * @param crownBase Height in blocks, before size scale, where the crown starts.
 * @param radius    Crown radius by normalized height, from the crown base (0) to the tree top (1): in blocks before
 *                  size scale, or as a share of the tree's height when relative.
 * @param relative  Whether the radius is a share of the tree's height instead of blocks.
 * @param lumpiness How far the edge bulges out and dents in, as a share of the radius, from 0 (smooth) to 1.
 * @param lumpSize  Typical width of one bulge, in blocks before size scale.
 * @param seed      Seed of this tree's lumps.
 */
public record CrownEnvelope(double crownBase, Curve radius, boolean relative, double lumpiness, double lumpSize,
                            long seed) {
  /**
   * The crown's center line runs through the middle of the origin block.
   */
  private static final double AXIS = 0.5;
  /**
   * Two octaves of value noise rarely stray far from the middle, so their spread is widened to reach the full
   * lumpiness at the strongest bulges and dents.
   */
  private static final double SPREAD = 2.5;

  /**
   * A smooth envelope.
   *
   * @param crownBase Height in blocks, before size scale, where the crown starts.
   * @param radius    Crown radius by normalized height.
   * @param relative  Whether the radius is a share of the tree's height instead of blocks.
   */
  public CrownEnvelope(double crownBase, Curve radius, boolean relative) {
    this(crownBase, radius, relative, 0, 1, 0);
  }

  /**
   * A smooth envelope whose radius is in blocks.
   *
   * @param crownBase Height in blocks, before size scale, where the crown starts.
   * @param radius    Crown radius in blocks by normalized height.
   */
  public CrownEnvelope(double crownBase, Curve radius) {
    this(crownBase, radius, false);
  }

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
   * Whether a point is no higher than the tree and within the crown's edge at its height and direction.
   *
   * @param point      Point to check.
   * @param treeHeight Height of the tree.
   * @param sizeScale  The tree's overall size factor.
   */
  public boolean contains(Vec3 point, double treeHeight, double sizeScale) {
    return contains(point, treeHeight, sizeScale, 1);
  }

  /**
   * Whether a point is no higher than the tree and within a share of the crown's edge at its height and direction.
   * Below the crown base the curve's first value applies, so branches there are limited by the lowest radius.
   *
   * @param point      Point to check.
   * @param treeHeight Height of the tree.
   * @param sizeScale  The tree's overall size factor.
   * @param reach      Share of the edge's distance from the center line the point may reach; 1 is the edge itself.
   */
  public boolean contains(Vec3 point, double treeHeight, double sizeScale, double reach) {
    if (point.y() > treeHeight) {
      return false;
    }
    return radialDistance(point) <= edgeAt(point, treeHeight, sizeScale) * reach;
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
    double inside = edgeAt(point, treeHeight, sizeScale) - radialDistance(point);
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
   * The crown radius in blocks at height {@code y}, read from the curve by how far up the crown that height is. A
   * relative curve is multiplied by the tree's height, which already includes the size scale. This is the smooth
   * radius, before any lumps.
   *
   * @param y          Height in blocks.
   * @param treeHeight Height of the tree.
   * @param sizeScale  The tree's overall size factor.
   */
  public double radiusAt(double y, double treeHeight, double sizeScale) {
    double base = crownBase * sizeScale;
    double span = treeHeight - base;
    double t = span <= 0 ? 0 : (y - base) / span;
    return radius.eval(t) * (relative ? treeHeight : sizeScale);
  }

  /**
   * The crown edge's distance from the center line in the direction of {@code point}, at its height: the smooth
   * radius, pushed out or pulled in by this tree's lumps. The lumps are read on the smooth surface itself, so every
   * point in one direction shares one edge.
   *
   * @param point      Point giving the direction and height.
   * @param treeHeight Height of the tree.
   * @param sizeScale  The tree's overall size factor.
   */
  public double edgeAt(Vec3 point, double treeHeight, double sizeScale) {
    double smooth = radiusAt(point.y(), treeHeight, sizeScale);
    if (lumpiness <= 0) {
      return smooth;
    }
    double angle = StrictMath.atan2(point.z() - AXIS, point.x() - AXIS);
    double size = lumpSize * sizeScale;
    double noise = new ValueNoise3(seed).at(StrictMath.cos(angle) * smooth / size, point.y() / size,
            StrictMath.sin(angle) * smooth / size);
    double bulge = Math.clamp((2 * noise - 1) * SPREAD, -1, 1);
    return smooth * (1 + lumpiness * bulge);
  }
}
