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

/**
 * Immutable 3D vector in block units.
 *
 * @param x East-west coordinate, in blocks; positive is east.
 * @param y Vertical coordinate, in blocks; positive is up.
 * @param z North-south coordinate, in blocks; positive is south.
 */
public record Vec3(double x, double y, double z) {
  public static final Vec3 ZERO = new Vec3(0, 0, 0);
  public static final Vec3 UP = new Vec3(0, 1, 0);

  /**
   * Add vectors.
   *
   * @param other the other vector.
   * @return the sum vector.
   */
  public Vec3 add(Vec3 other) {
    return new Vec3(x + other.x, y + other.y, z + other.z);
  }

  /**
   * Subtract vectors.
   *
   * @param other the other vector.
   * @return the difference vector.
   */
  public Vec3 sub(Vec3 other) {
    return new Vec3(x - other.x, y - other.y, z - other.z);
  }

  /**
   * Multiply vector by a scalar.
   *
   * @param a the scalar.
   * @return the scaled vector.
   */
  public Vec3 scale(double a) {
    return new Vec3(x * a, y * a, z * a);
  }

  public double dot(Vec3 other) {
    return x * other.x + y * other.y + z * other.z;
  }

  /**
   * Get the cross product.
   *
   * @param other the other vector.
   * @return the cross product.
   */
  public Vec3 cross(Vec3 other) {
    return new Vec3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x);
  }

  /**
   * Get the norm of the vector.
   *
   * @return the norm.
   */
  public double length() {
    return StrictMath.sqrt(dot(this));
  }

  /**
   * Get the unit vector, or zero when this vector has no usable length.
   *
   * @return the unit vector.
   */
  public Vec3 normalized() {
    double length = length();
    return length < 1e-12 ? ZERO : scale(1 / length);
  }

  public Vec3 lerp(Vec3 other, double t) {
    return add(other.sub(this).scale(t));
  }
}
