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

import com.jeffch.arbor.foliage.DensityField;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Raymarches the leaf density volume over the opaque scene.
 */
public final class DensityRenderer implements AutoCloseable {
  private static final float STEP_BLOCKS = 0.4f;

  private final GlProgram program = GlProgram.load("density");
  private final int vao = glGenVertexArrays();
  private final int texture = glGenTextures();
  private final Vector3f boxMin = new Vector3f();
  private final Vector3f boxSize = new Vector3f();
  private boolean hasVolume;

  /**
   * Copies the leaf density into a 3D texture, smoothly blended between voxels. An empty field draws nothing.
   *
   * @param field Leaf density to show.
   */
  public void upload(DensityField field) {
    hasVolume = !field.isEmpty();
    if (!hasVolume) {
      return;
    }
    int[] min = field.min();
    int[] size = field.dimensions();
    boxMin.set(min[0], min[1], min[2]);
    boxSize.set(size[0], size[1], size[2]);
    FloatBuffer data = MemoryUtil.memAllocFloat(field.data().length);
    try {
      data.put(field.data()).flip();
      glBindTexture(GL_TEXTURE_3D, texture);
      glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
      glTexImage3D(GL_TEXTURE_3D, 0, GL_R32F, size[0], size[1], size[2], 0, GL_RED, GL_FLOAT, data);
      glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
      glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
      glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
      glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
      glBindTexture(GL_TEXTURE_3D, 0);
    } finally {
      MemoryUtil.memFree(data);
    }
  }

  /**
   * Whether there is any density to draw.
   */
  public boolean hasVolume() {
    return hasVolume;
  }

  /**
   * Ray-marches the density as a translucent cloud over the finished scene: one full-screen triangle whose shader steps
   * through the box, stopping at solid geometry from the depth texture.
   *
   * @param view         View matrix.
   * @param projection   Projection matrix.
   * @param depthTexture Depth of the scene already drawn.
   * @param threshold    Density below which nothing shows.
   * @param opacity      How opaque the cloud is.
   */
  public void render(Matrix4f view, Matrix4f projection, int depthTexture, float threshold, float opacity) {
    if (!hasVolume) {
      return;
    }
    Matrix4f inverse = new Matrix4f(projection).mul(view).invert();
    Vector3f eye = new Matrix4f(view).invert().getTranslation(new Vector3f());
    program.use();
    program.setMatrix("uInverseViewProjection", inverse);
    program.setVec3("uEye", eye.x, eye.y, eye.z);
    program.setVec3("uBoxMin", boxMin.x, boxMin.y, boxMin.z);
    program.setVec3("uBoxSize", boxSize.x, boxSize.y, boxSize.z);
    program.setFloat("uThreshold", threshold);
    program.setFloat("uOpacity", opacity);
    program.setFloat("uStep", STEP_BLOCKS);
    program.setInt("uDensity", 0);
    program.setInt("uDepth", 1);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_3D, texture);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, depthTexture);
    glDisable(GL_DEPTH_TEST);
    glDepthMask(false);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glBindVertexArray(vao);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindVertexArray(0);
    glDisable(GL_BLEND);
    glDepthMask(true);
    glEnable(GL_DEPTH_TEST);
    glBindTexture(GL_TEXTURE_2D, 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_3D, 0);
  }

  /**
   * Frees the GPU texture, vertex array and shader.
   */
  @Override
  public void close() {
    glDeleteTextures(texture);
    glDeleteVertexArrays(vao);
    program.close();
  }
}
