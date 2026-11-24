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

import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.preset.GroundPlane;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL33C.*;

/**
 * The white ground that receives shadows in realistic lighting, following the preset's slope.
 */
final class GroundRenderer implements AutoCloseable {
  private static final int[][] CORNERS = {{-1, -1}, {-1, 1}, {1, 1}, {-1, -1}, {1, 1}, {1, -1}};

  private final GlProgram program = GlProgram.load("ground");
  private final int vao = glGenVertexArrays();
  private final int vbo = glGenBuffers();
  private Vec3 normal = Vec3.UP;

  GroundRenderer() {
    glBindVertexArray(vao);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
    glBindVertexArray(0);
  }

  /**
   * Builds a square centered on the trunk, reaching this far in each direction.
   *
   * @param ground Ground plane to draw.
   * @param reach  Distance from the center to each edge.
   */
  void upload(GroundPlane ground, float reach) {
    normal = ground.normal().normalized();
    float[] vertices = new float[CORNERS.length * 3];
    for (int i = 0; i < CORNERS.length; i++) {
      float x = 0.5f + CORNERS[i][0] * reach;
      float z = 0.5f + CORNERS[i][1] * reach;
      vertices[i * 3] = x;
      vertices[i * 3 + 1] = (float) ground.heightAt(x, z);
      vertices[i * 3 + 2] = z;
    }
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
  }

  /**
   * Draws the ground, with tree shadows when a shadow map is given.
   *
   * @param viewProjection Combined view and projection matrix.
   * @param shadows        Shadow map, or null for none.
   */
  void draw(Matrix4f viewProjection, ShadowMap shadows) {
    program.use();
    program.setMatrix("uViewProjection", viewProjection);
    program.setVec3("uNormal", (float) normal.x(), (float) normal.y(), (float) normal.z());
    ShadowMap.apply(program, shadows);
    glBindVertexArray(vao);
    glDrawArrays(GL_TRIANGLES, 0, CORNERS.length);
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
}
