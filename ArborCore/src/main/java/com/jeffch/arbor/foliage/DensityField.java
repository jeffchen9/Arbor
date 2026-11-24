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

package com.jeffch.arbor.foliage;

import java.util.Arrays;

/**
 * Leaf density per voxel, stored with x fastest, then y, then z, and a one-voxel zero border.
 */
public final class DensityField {
  private static final DensityField EMPTY = new DensityField(new int[3], new int[3], new float[0]);

  private final int[] min;
  private final int[] size;
  private final float[] data;

  /**
   * @param min  World position of the first voxel.
   * @param size Voxels along x, y and z.
   * @param data Density of each voxel, from 0 to 1.
   */
  DensityField(int[] min, int[] size, float[] data) {
    this.min = min;
    this.size = size;
    this.data = data;
  }

  public static DensityField empty() {
    return EMPTY;
  }

  /**
   * The density at a world voxel, or 0 outside the field.
   *
   * @param x World x.
   * @param y World y.
   * @param z World z.
   */
  public double get(int x, int y, int z) {
    int dx = x - min[0];
    int dy = y - min[1];
    int dz = z - min[2];
    if (data.length == 0 || dx < 0 || dy < 0 || dz < 0 || dx >= size[0] || dy >= size[1] || dz >= size[2]) {
      return 0;
    }
    return data[dx + size[0] * (dy + size[1] * dz)];
  }

  public int[] min() {
    return min.clone();
  }

  public int[] dimensions() {
    return size.clone();
  }

  public float[] data() {
    return data;
  }

  public boolean isEmpty() {
    return data.length == 0;
  }

  /**
   * Equal when position, size and every voxel match, which lets tests check that generation is repeatable.
   *
   * @param other Object to compare with.
   */
  @Override
  public boolean equals(Object other) {
    return other instanceof DensityField field
            && Arrays.equals(min, field.min)
            && Arrays.equals(size, field.size)
            && Arrays.equals(data, field.data);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * Arrays.hashCode(min) + Arrays.hashCode(size)) + Arrays.hashCode(data);
  }
}
