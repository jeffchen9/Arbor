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
 * The sloped ground a tree stands on, passing through the center of the block at the origin.
 *
 * @param normal Direction the ground faces; any length, with a positive y.
 */
public record GroundPlane(Vec3 normal) {
  public static final GroundPlane FLAT = new GroundPlane(Vec3.UP);
  /**
   * Steepest slope a tree can stand on, in degrees.
   */
  public static final double MAX_DEGREES = 80;

  private static final double CENTER = 0.5;

  public boolean flat() {
    return normal.x() == 0 && normal.z() == 0;
  }

  /**
   * Ground height at a horizontal position.
   *
   * @param x World x.
   * @param z World z.
   */
  public double heightAt(double x, double z) {
    return flat() ? 0 : -(normal.x() * (x - CENTER) + normal.z() * (z - CENTER)) / normal.y();
  }

  /**
   * Where a line through this point along the normal meets the ground. Flat ground leaves the point unchanged.
   *
   * @param point Point the line passes through.
   */
  public Vec3 alongNormalToGround(Vec3 point) {
    if (flat()) {
      return point;
    }
    Vec3 unit = normal.normalized();
    return point.sub(unit.scale(unit.dot(point.sub(new Vec3(CENTER, 0, CENTER)))));
  }

  /**
   * How far the ground drops per block along its steepest direction.
   */
  public double steepness() {
    return StrictMath.hypot(normal.x(), normal.z()) / normal.y();
  }

  /**
   * Angle of the slope from horizontal.
   */
  public double degrees() {
    return StrictMath.toDegrees(StrictMath.atan(steepness()));
  }

  /**
   * Whether the whole block at these coordinates is under the ground.
   *
   * @param x Block x.
   * @param y Block y.
   * @param z Block z.
   */
  public boolean buries(int x, int y, int z) {
    double lowestCorner = heightAt(x + CENTER, z + CENTER)
            - CENTER * (Math.abs(normal.x()) + Math.abs(normal.z())) / normal.y();
    return y + 1 <= lowestCorner;
  }
}
