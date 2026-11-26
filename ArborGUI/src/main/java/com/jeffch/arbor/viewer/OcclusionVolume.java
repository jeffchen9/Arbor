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

import com.jeffch.arbor.shape.ShapeClass;
import com.jeffch.arbor.shape.ShapeGrid;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

import static org.lwjgl.opengl.GL33C.*;

/**
 * How much of each block the tree fills, as a 3D texture, so realistic lighting can darken places the sky cannot
 * reach: the inside of the crown, the trunk under it, and the ground at its foot.
 */
final class OcclusionVolume implements AutoCloseable {
  static final int TEXTURE_UNIT = 2;
  /**
   * How much sky light each kind of shape blocks, from 0 to 1.
   */
  private static final Map<ShapeClass, Double> BLOCKING = blocking();

  private final int texture = glGenTextures();
  private final int[] min = new int[3];
  private final int[] size = {1, 1, 1};
  private boolean hasVolume;

  /**
   * How much sky light each kind of shape blocks.
   *
   * @return the share blocked by each shape class.
   */
  private static Map<ShapeClass, Double> blocking() {
    Map<ShapeClass, Double> blocking = new EnumMap<>(ShapeClass.class);
    blocking.put(ShapeClass.FULL, 1.0);
    blocking.put(ShapeClass.STAIR, 0.75);
    blocking.put(ShapeClass.LEAVES, 0.6);
    blocking.put(ShapeClass.SLAB, 0.5);
    blocking.put(ShapeClass.WALL, 0.45);
    blocking.put(ShapeClass.FENCE, 0.3);
    blocking.put(ShapeClass.GATE, 0.3);
    blocking.put(ShapeClass.PLANT, 0.25);
    blocking.put(ShapeClass.DEAD_BUSH, 0.2);
    blocking.put(ShapeClass.SAPLING, 0.2);
    blocking.put(ShapeClass.BUTTON, 0.0);
    return blocking;
  }

  /**
   * Builds the occupancy of the visible shapes.
   *
   * @param shapes  the tree's shapes.
   * @param visible whether a shape class is shown; hidden ones block nothing.
   * @return the occupancy grid.
   */
  static Grid build(ShapeGrid shapes, Predicate<ShapeClass> visible) {
    if (shapes.isEmpty()) {
      return new Grid(new int[3], new int[]{1, 1, 1}, new byte[1]);
    }
    int[] low = shapes.min();
    int[] high = shapes.max();
    int[] min = {low[0] - 1, low[1] - 1, low[2] - 1};
    int[] size = {high[0] - low[0] + 3, high[1] - low[1] + 3, high[2] - low[2] + 3};
    byte[] data = new byte[size[0] * size[1] * size[2]];
    shapes.forEachSorted((x, y, z, shape) -> {
      if (visible.test(shape.shapeClass())) {
        int index = ((z - min[2]) * size[1] + (y - min[1])) * size[0] + (x - min[0]);
        data[index] = (byte) Math.round(BLOCKING.get(shape.shapeClass()) * 255);
      }
    });
    return new Grid(min, size, data);
  }

  /**
   * Gives a shader the occupancy texture, or switches its ambient occlusion off. The sampler is always set, so it
   * never shares a texture unit with a sampler of another type.
   *
   * @param target the shader to set up.
   * @param volume the occupancy, or null for none.
   */
  static void apply(GlProgram target, OcclusionVolume volume) {
    target.setInt("uOcclusion", TEXTURE_UNIT);
    boolean on = volume != null && volume.hasVolume;
    target.setInt("uAmbientOcclusion", on ? 1 : 0);
    if (on) {
      target.setVec3("uOcclusionMin", volume.min[0], volume.min[1], volume.min[2]);
      target.setVec3("uOcclusionSize", volume.size[0], volume.size[1], volume.size[2]);
      glActiveTexture(GL_TEXTURE0 + TEXTURE_UNIT);
      glBindTexture(GL_TEXTURE_3D, volume.texture);
      glActiveTexture(GL_TEXTURE0);
    }
  }

  /**
   * Replaces the texture with a new occupancy grid. Samples outside it read as empty.
   *
   * @param grid the occupancy to upload.
   */
  void upload(Grid grid) {
    System.arraycopy(grid.min(), 0, min, 0, 3);
    System.arraycopy(grid.size(), 0, size, 0, 3);
    ByteBuffer data = MemoryUtil.memAlloc(grid.data().length);
    try {
      data.put(grid.data()).flip();
      glBindTexture(GL_TEXTURE_3D, texture);
      glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
      glTexImage3D(GL_TEXTURE_3D, 0, GL_R8, size[0], size[1], size[2], 0, GL_RED, GL_UNSIGNED_BYTE, data);
      glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
      glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
      for (int wrap : new int[]{GL_TEXTURE_WRAP_S, GL_TEXTURE_WRAP_T, GL_TEXTURE_WRAP_R}) {
        glTexParameteri(GL_TEXTURE_3D, wrap, GL_CLAMP_TO_BORDER);
      }
      glTexParameterfv(GL_TEXTURE_3D, GL_TEXTURE_BORDER_COLOR, new float[]{0, 0, 0, 0});
      glBindTexture(GL_TEXTURE_3D, 0);
    } finally {
      MemoryUtil.memFree(data);
    }
    hasVolume = true;
  }

  /**
   * Frees the texture.
   */
  @Override
  public void close() {
    glDeleteTextures(texture);
  }

  /**
   * The occupancy of each block, with an empty block of border all round.
   *
   * @param min  the lowest corner, in blocks.
   * @param size the number of blocks along x, y and z.
   * @param data one byte per block from 0 (empty) to 255 (solid), x fastest, then y, then z.
   */
  record Grid(int[] min, int[] size, byte[] data) {
  }
}
