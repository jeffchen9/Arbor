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

package com.jeffch.arbor.viewer;

import com.jeffch.arbor.shape.Shape;
import com.jeffch.arbor.shape.ShapeClass;
import com.jeffch.arbor.shape.ShapeGrid;
import com.jeffch.arbor.viewer.ShapeGeometry.Box;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Builds triangle meshes per shape class, with position and normal per vertex.
 */
public final class ShapeMesher {
  public static final int FLOATS_PER_VERTEX = 6;

  private static final int[][] QUAD_ORDER = {{0, 1, 2}, {0, 2, 3}};

  private ShapeMesher() {
  }

  /**
   * Builds untextured triangles for every shape, one vertex list per shape class.
   *
   * @param grid The tree's shapes.
   */
  public static Map<ShapeClass, float[]> mesh(ShapeGrid grid) {
    Map<ShapeClass, Buffer> buffers = new EnumMap<>(ShapeClass.class);
    for (ShapeClass shapeClass : ShapeClass.values()) {
      buffers.put(shapeClass, new Buffer());
    }
    grid.forEachSorted((x, y, z, shape) -> {
      Buffer buffer = buffers.get(shape.shapeClass());
      boolean cube = shape.shapeClass() == ShapeClass.FULL || shape instanceof Shape.Leaves;
      for (Box box : ShapeGeometry.boxes(shape)) {
        addBox(buffer, grid, x, y, z, box, cube ? shape.shapeClass() : null);
      }
    });
    Map<ShapeClass, float[]> meshes = new EnumMap<>(ShapeClass.class);
    buffers.forEach((shapeClass, buffer) -> meshes.put(shapeClass, buffer.toArray()));
    return meshes;
  }

  /**
   * Hides faces between cubes of one class only, so toggling a class never reveals holes in another.
   *
   * @param out  Receives the triangles.
   * @param grid The tree's shapes.
   * @param x    Block x.
   * @param y    Block y.
   * @param z    Block z.
   * @param box  Box to add.
   * @param cube The shape's class when it is a whole cube, otherwise null.
   */
  private static void addBox(Buffer out, ShapeGrid grid, int x, int y, int z, Box box, ShapeClass cube) {
    float x0 = x + box.minX() / 16f;
    float y0 = y + box.minY() / 16f;
    float z0 = z + box.minZ() / 16f;
    float x1 = x + box.maxX() / 16f;
    float y1 = y + box.maxY() / 16f;
    float z1 = z + box.maxZ() / 16f;
    if (!covered(grid, x + 1, y, z, cube)) {
      out.face(1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
    }
    if (!covered(grid, x - 1, y, z, cube)) {
      out.face(-1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
    }
    if (!covered(grid, x, y + 1, z, cube)) {
      out.face(0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
    }
    if (!covered(grid, x, y - 1, z, cube)) {
      out.face(0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }
    if (!covered(grid, x, y, z + 1, cube)) {
      out.face(0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
    }
    if (!covered(grid, x, y, z - 1, cube)) {
      out.face(0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
    }
  }

  /**
   * Whether a neighbor of the same cube class hides this face.
   *
   * @param grid The tree's shapes.
   * @param x    Neighbor x.
   * @param y    Neighbor y.
   * @param z    Neighbor z.
   * @param cube The shape's class when it is a whole cube, otherwise null.
   */
  private static boolean covered(ShapeGrid grid, int x, int y, int z, ShapeClass cube) {
    Shape neighbor = cube == null ? null : grid.get(x, y, z);
    return neighbor != null && neighbor.shapeClass() == cube;
  }

  /**
   * A growable list of triangle vertices: position and normal.
   */
  private static final class Buffer {
    private float[] data = new float[4096];
    private int size;

    /**
     * Adds a four-cornered face as two triangles, all with the given normal.
     *
     * @param nx      Normal x.
     * @param ny      Normal y.
     * @param nz      Normal z.
     * @param corners The four corners as x, y, z triples.
     */
    void face(float nx, float ny, float nz, float... corners) {
      if (size + 6 * FLOATS_PER_VERTEX > data.length) {
        data = Arrays.copyOf(data, data.length * 2);
      }
      for (int[] triangle : QUAD_ORDER) {
        for (int corner : triangle) {
          data[size++] = corners[corner * 3];
          data[size++] = corners[corner * 3 + 1];
          data[size++] = corners[corner * 3 + 2];
          data[size++] = nx;
          data[size++] = ny;
          data[size++] = nz;
        }
      }
    }

    /**
     * The vertex data, trimmed to what was added.
     */
    float[] toArray() {
      return Arrays.copyOf(data, size);
    }
  }
}
