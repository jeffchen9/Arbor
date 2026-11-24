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

package com.jeffch.arbor.math;

import java.util.ArrayList;
import java.util.List;

/**
 * An orthonormal frame on a spine, carried along it without sudden roll.
 *
 * @param tangent  Unit direction the spine travels at this point.
 * @param normal   Unit direction perpendicular to the tangent, used as the zero angle for roll.
 * @param binormal Unit direction perpendicular to both the tangent and the normal.
 */
public record Frame(Vec3 tangent, Vec3 normal, Vec3 binormal) {

  /**
   * One frame per point. Each normal is the previous one turned by exactly the bend between neighboring tangents,
   * so the frame never spins around the spine; this is what keeps child branches from twisting at random.
   *
   * @param points        Points along a curve.
   * @param initialNormal Preferred normal for the first point; only its part perpendicular to the tangent is used.
   */
  public static List<Frame> parallelTransport(List<Vec3> points, Vec3 initialNormal) {
    List<Frame> frames = new ArrayList<>(points.size());
    Vec3 previousTangent = null;
    Vec3 normal = null;
    for (int i = 0; i < points.size(); i++) {
      Vec3 tangent = tangentAt(points, i, previousTangent);
      if (normal == null) {
        normal = orthogonalNormal(tangent, initialNormal);
      } else {
        normal = orthogonalNormal(tangent, transport(normal, previousTangent, tangent));
      }
      frames.add(new Frame(tangent, normal, tangent.cross(normal).normalized()));
      previousTangent = tangent;
    }
    return frames;
  }

  /**
   * The direction from a point to the next one; the last point uses the segment before it. Where two points
   * coincide, the previous tangent (or straight up at the start) stands in.
   *
   * @param points   Points along a curve.
   * @param index    Position of the point.
   * @param previous Tangent of the point before, used where two points coincide; null at the start.
   */
  private static Vec3 tangentAt(List<Vec3> points, int index, Vec3 previous) {
    int from = index == points.size() - 1 ? Math.max(0, index - 1) : index;
    int to = Math.min(points.size() - 1, from + 1);
    Vec3 tangent = points.get(to).sub(points.get(from)).normalized();
    if (tangent.equals(Vec3.ZERO)) {
      return previous == null ? Vec3.UP : previous;
    }
    return tangent;
  }

  /**
   * Rotates {@code normal} by the rotation that turns direction {@code from} into {@code to} (Rodrigues' formula).
   * Parallel directions leave it unchanged.
   *
   * @param normal Normal to rotate.
   * @param from   Direction before.
   * @param to     Direction after.
   */
  private static Vec3 transport(Vec3 normal, Vec3 from, Vec3 to) {
    Vec3 axis = from.cross(to);
    double sine = axis.length();
    if (sine < 1e-12) {
      return normal;
    }
    Vec3 unit = axis.scale(1 / sine);
    double cosine = from.dot(to);
    return normal.scale(cosine)
            .add(unit.cross(normal).scale(sine))
            .add(unit.scale(unit.dot(normal) * (1 - cosine)));
  }

  /**
   * The candidate with its component along the tangent removed, as a unit vector. When the candidate points along
   * the tangent, any world axis that is not nearly parallel to it is used instead.
   *
   * @param tangent   Unit tangent.
   * @param candidate Vector to straighten.
   */
  private static Vec3 orthogonalNormal(Vec3 tangent, Vec3 candidate) {
    Vec3 normal = candidate.sub(tangent.scale(candidate.dot(tangent)));
    if (normal.length() < 1e-6) {
      Vec3 axis = Math.abs(tangent.x()) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
      normal = axis.sub(tangent.scale(axis.dot(tangent)));
    }
    return normal.normalized();
  }
}
