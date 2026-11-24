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

import org.joml.Matrix4f;

import java.util.Arrays;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws colored line segments.
 */
public final class LineRenderer implements AutoCloseable {
  private static final int FLOATS_PER_VERTEX = 6;

  private final GlProgram program = GlProgram.load("line");
  private final int vao = glGenVertexArrays();
  private final int vbo = glGenBuffers();
  private int vertexCount;

  /**
   * Sets up a vertex layout of position and color.
   */
  public LineRenderer() {
    glBindVertexArray(vao);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 3, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 3L * Float.BYTES);
    glBindVertexArray(0);
  }

  /**
   * Replaces the lines being drawn.
   *
   * @param lines Lines to draw.
   */
  public void upload(Lines lines) {
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, lines.toArray(), GL_STATIC_DRAW);
    vertexCount = lines.vertexCount();
  }

  /**
   * Draws the uploaded lines.
   *
   * @param viewProjection Combined view and projection matrix.
   */
  public void draw(Matrix4f viewProjection) {
    if (vertexCount == 0) {
      return;
    }
    program.use();
    program.setMatrix("uViewProjection", viewProjection);
    glBindVertexArray(vao);
    glDrawArrays(GL_LINES, 0, vertexCount);
    glBindVertexArray(0);
  }

  /**
   * Frees the GPU buffers and shader.
   */
  @Override
  public void close() {
    glDeleteBuffers(vbo);
    glDeleteVertexArrays(vao);
    program.close();
  }

  /**
   * A growable list of colored line segments.
   */
  public static final class Lines {
    private float[] data = new float[1024];
    private int size;

    /**
     * Adds a segment between two points in one color, growing the storage when full.
     *
     * @param x1 Start x.
     * @param y1 Start y.
     * @param z1 Start z.
     * @param x2 End x.
     * @param y2 End y.
     * @param z2 End z.
     * @param r  Red, from 0 to 1.
     * @param g  Green, from 0 to 1.
     * @param b  Blue, from 0 to 1.
     */
    public void add(float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b) {
      if (size + FLOATS_PER_VERTEX * 2 > data.length) {
        data = Arrays.copyOf(data, data.length * 2);
      }
      put(x1, y1, z1, r, g, b);
      put(x2, y2, z2, r, g, b);
    }

    /**
     * Appends one vertex.
     *
     * @param x Position x.
     * @param y Position y.
     * @param z Position z.
     * @param r Red, from 0 to 1.
     * @param g Green, from 0 to 1.
     * @param b Blue, from 0 to 1.
     */
    private void put(float x, float y, float z, float r, float g, float b) {
      data[size++] = x;
      data[size++] = y;
      data[size++] = z;
      data[size++] = r;
      data[size++] = g;
      data[size++] = b;
    }

    /**
     * The vertex data, trimmed to what was added.
     */
    float[] toArray() {
      return Arrays.copyOf(data, size);
    }

    /**
     * The number of vertices added.
     */
    int vertexCount() {
      return size / FLOATS_PER_VERTEX;
    }
  }
}
