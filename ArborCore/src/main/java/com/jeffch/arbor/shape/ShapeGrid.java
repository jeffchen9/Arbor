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

package com.jeffch.arbor.shape;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * A sparse voxel grid of wood shapes with signed coordinates.
 */
public final class ShapeGrid {
  /**
   * The most shapes any generation step may leave in a grid.
   */
  static final int MAX_SHAPES = 4_000_000;
  private static final int OFFSET = 1 << 20;
  private static final long MASK = (1L << 21) - 1;
  private final Map<Long, Shape> shapes = new HashMap<>();

  /**
   * Packs a position into one number, 21 bits per axis with y highest, so sorting keys orders voxels by y, then z, then
   * x.
   *
   * @param x Block x.
   * @param y Block y.
   * @param z Block z.
   */
  static long key(int x, int y, int z) {
    if (outOfRange(x) || outOfRange(y) || outOfRange(z)) {
      throw new IllegalArgumentException("voxel out of range: " + x + ", " + y + ", " + z);
    }
    return ((long) (y + OFFSET) << 42) | ((long) (z + OFFSET) << 21) | (x + OFFSET);
  }

  /**
   * Whether a coordinate is beyond the roughly one million blocks a key can hold.
   *
   * @param value Coordinate to check.
   */
  private static boolean outOfRange(int value) {
    return value < -OFFSET || value >= OFFSET;
  }

  /**
   * The x coordinate stored in a key.
   *
   * @param key Packed position.
   */
  static int keyX(long key) {
    return (int) (key & MASK) - OFFSET;
  }

  /**
   * The z coordinate stored in a key.
   *
   * @param key Packed position.
   */
  static int keyZ(long key) {
    return (int) ((key >>> 21) & MASK) - OFFSET;
  }

  /**
   * The y coordinate stored in a key.
   *
   * @param key Packed position.
   */
  static int keyY(long key) {
    return (int) ((key >>> 42) & MASK) - OFFSET;
  }

  /**
   * The shape at a position, or null when empty.
   *
   * @param x Block x.
   * @param y Block y.
   * @param z Block z.
   */
  public Shape get(int x, int y, int z) {
    return shapes.get(key(x, y, z));
  }

  /**
   * Places a shape, replacing whatever was there.
   *
   * @param x     Block x.
   * @param y     Block y.
   * @param z     Block z.
   * @param shape Shape to place.
   */
  public void put(int x, int y, int z, Shape shape) {
    shapes.put(key(x, y, z), shape);
  }

  /**
   * Places a shape unless an equal or thicker class already occupies the voxel.
   *
   * @param x     Block x.
   * @param y     Block y.
   * @param z     Block z.
   * @param shape Shape to place.
   */
  public void putIfThicker(int x, int y, int z, Shape shape) {
    long key = key(x, y, z);
    Shape existing = shapes.get(key);
    if (existing == null || shape.shapeClass().compareTo(existing.shapeClass()) > 0) {
      shapes.put(key, shape);
    }
  }

  /**
   * Removes every voxel the test accepts.
   *
   * @param test Decides which voxels go.
   */
  public void removeIf(VoxelTest test) {
    shapes.keySet().removeIf(key -> test.matches(keyX(key), keyY(key), keyZ(key)));
  }

  /**
   * Whether the grid holds no shapes.
   */
  public boolean isEmpty() {
    return shapes.isEmpty();
  }

  /**
   * The number of shapes.
   */
  public int size() {
    return shapes.size();
  }

  /**
   * Visits voxels in a stable order: by y, then z, then x.
   *
   * @param visitor Called for each voxel.
   */
  public void forEachSorted(ShapeVisitor visitor) {
    for (long key : sortedKeys()) {
      visitor.visit(keyX(key), keyY(key), keyZ(key), shapes.get(key));
    }
  }

  /**
   * Every key, sorted, which orders voxels by y, then z, then x.
   */
  long[] sortedKeys() {
    long[] keys = new long[shapes.size()];
    int index = 0;
    for (long key : shapes.keySet()) {
      keys[index++] = key;
    }
    Arrays.sort(keys);
    return keys;
  }

  /**
   * The lowest x, y and z of any shape; zeros for an empty grid.
   */
  public int[] min() {
    int[] result = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
    for (long key : shapes.keySet()) {
      result[0] = Math.min(result[0], keyX(key));
      result[1] = Math.min(result[1], keyY(key));
      result[2] = Math.min(result[2], keyZ(key));
    }
    return shapes.isEmpty() ? new int[3] : result;
  }

  /**
   * The highest x, y and z of any shape; zeros for an empty grid.
   */
  public int[] max() {
    int[] result = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    for (long key : shapes.keySet()) {
      result[0] = Math.max(result[0], keyX(key));
      result[1] = Math.max(result[1], keyY(key));
      result[2] = Math.max(result[2], keyZ(key));
    }
    return shapes.isEmpty() ? new int[3] : result;
  }

  /**
   * How many shapes of each class the grid holds, including classes with none.
   */
  public Map<ShapeClass, Integer> counts() {
    Map<ShapeClass, Integer> counts = new EnumMap<>(ShapeClass.class);
    for (ShapeClass shapeClass : ShapeClass.values()) {
      counts.put(shapeClass, 0);
    }
    for (Shape shape : shapes.values()) {
      counts.merge(shape.shapeClass(), 1, Integer::sum);
    }
    return counts;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ShapeGrid grid && shapes.equals(grid.shapes);
  }

  @Override
  public int hashCode() {
    return shapes.hashCode();
  }

  /**
   * Receives one occupied voxel and its shape.
   */
  @FunctionalInterface
  public interface ShapeVisitor {
    void visit(int x, int y, int z, Shape shape);
  }

  /**
   * Decides whether a voxel should be removed.
   */
  @FunctionalInterface
  public interface VoxelTest {
    boolean matches(int x, int y, int z);
  }
}
