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
 * Deterministic smooth 3D value noise in the range 0 to 1.
 */
public final class ValueNoise3 {
  private final long seed;

  public ValueNoise3(long seed) {
    this.seed = seed;
  }

  /**
   * Smoothstep easing, so the noise has no visible creases at lattice lines.
   *
   * @param t Fraction from 0 to 1.
   */
  private static double smooth(double t) {
    return t * t * (3 - 2 * t);
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  /**
   * Two octaves of value noise: the base at double weight plus finer detail at about twice the frequency. The
   * detail octave is shifted so its lattice does not line up with the base one.
   *
   * @param x Position x.
   * @param y Position y.
   * @param z Position z.
   */
  public double at(double x, double y, double z) {
    return (octave(x, y, z) * 2 + octave(x * 2.03 + 17.1, y * 2.03 - 5.7, z * 2.03 + 9.3)) / 3;
  }

  /**
   * One octave: random values at the eight surrounding lattice corners, blended smoothly along x, then y, then z.
   *
   * @param x Position x.
   * @param y Position y.
   * @param z Position z.
   */
  private double octave(double x, double y, double z) {
    int ix = (int) Math.floor(x);
    int iy = (int) Math.floor(y);
    int iz = (int) Math.floor(z);
    double fx = smooth(x - ix);
    double fy = smooth(y - iy);
    double fz = smooth(z - iz);
    double x00 = lerp(lattice(ix, iy, iz), lattice(ix + 1, iy, iz), fx);
    double x10 = lerp(lattice(ix, iy + 1, iz), lattice(ix + 1, iy + 1, iz), fx);
    double x01 = lerp(lattice(ix, iy, iz + 1), lattice(ix + 1, iy, iz + 1), fx);
    double x11 = lerp(lattice(ix, iy + 1, iz + 1), lattice(ix + 1, iy + 1, iz + 1), fx);
    return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
  }

  /**
   * A repeatable random value in [0, 1) for one lattice corner, from a SplitMix64-style hash of the seed and corner.
   *
   * @param x Corner x.
   * @param y Corner y.
   * @param z Corner z.
   */
  private double lattice(int x, int y, int z) {
    long hash = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
    hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
    hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
    hash ^= hash >>> 31;
    return (hash >>> 11) * 0x1.0p-53;
  }
}
