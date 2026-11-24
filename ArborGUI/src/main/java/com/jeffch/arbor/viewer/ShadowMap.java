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
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * A depth image of the tree as seen from the sun.
 */
final class ShadowMap implements AutoCloseable {
  private static final int SIZE = 4096;
  private static final int TEXTURE_UNIT = 1;

  private final GlProgram program = GlProgram.load("depth");
  private final int framebuffer = glGenFramebuffers();
  private final int depth = glGenTextures();
  private Matrix4f matrix = new Matrix4f();

  /**
   * Sets up a depth texture and framebuffer for the sun's view.
   */
  ShadowMap() {
    glBindTexture(GL_TEXTURE_2D, depth);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SIZE, SIZE, 0, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT,
            (ByteBuffer) null);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
    glBindTexture(GL_TEXTURE_2D, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
    glDrawBuffer(GL_NONE);
    glReadBuffer(GL_NONE);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
      throw new IllegalStateException("shadow framebuffer is incomplete");
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
  }

  /**
   * Sets a lit shader's uniforms. A null map selects the Minecraft face shading.
   *
   * @param target Shader to set up.
   * @param map    Shadow map, or null for Minecraft face shading.
   */
  static void apply(GlProgram target, ShadowMap map) {
    target.setInt("uShadowMap", TEXTURE_UNIT);
    target.setInt("uRealistic", map == null ? 0 : 1);
    if (map != null) {
      target.setVec3("uSunDirection", SunLight.DIRECTION.x, SunLight.DIRECTION.y, SunLight.DIRECTION.z);
      target.setMatrix("uLightMatrix", map.matrix);
      glActiveTexture(GL_TEXTURE0 + TEXTURE_UNIT);
      glBindTexture(GL_TEXTURE_2D, map.depth);
      glActiveTexture(GL_TEXTURE0);
    }
  }

  /**
   * Redraws the shadow casters inside the given bounds. The caller rebinds its own framebuffer afterward.
   *
   * @param min     Lower corner of the scene.
   * @param max     Upper corner of the scene.
   * @param casters Draws the shadow casters with the depth shader.
   */
  void render(Vector3f min, Vector3f max, Consumer<GlProgram> casters) {
    matrix = SunLight.matrix(min, max);
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    glViewport(0, 0, SIZE, SIZE);
    glEnable(GL_DEPTH_TEST);
    glClear(GL_DEPTH_BUFFER_BIT);
    glEnable(GL_POLYGON_OFFSET_FILL);
    glPolygonOffset(1.5f, 2f);
    program.use();
    program.setMatrix("uLightMatrix", matrix);
    casters.accept(program);
    glDisable(GL_POLYGON_OFFSET_FILL);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
  }

  /**
   * Frees the framebuffer, depth texture and shader.
   */
  @Override
  public void close() {
    glDeleteFramebuffers(framebuffer);
    glDeleteTextures(depth);
    program.close();
  }
}
