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
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws textured meshes, one per shape class, with the block atlas bound.
 */
final class TexturedRenderer implements AutoCloseable {
  private static final int STRIDE = TexturedMesher.FLOATS_PER_VERTEX * Float.BYTES;

  private final GlProgram program = GlProgram.load("textured");
  private final Map<ShapeClass, Mesh> meshes = new EnumMap<>(ShapeClass.class);
  private final int atlasTexture;

  /**
   * Uploads the atlas as a sharp texture and sets up one mesh per shape class.
   *
   * @param atlas Packed block textures.
   */
  TexturedRenderer(TextureAtlas atlas) {
    atlasTexture = glGenTextures();
    glBindTexture(GL_TEXTURE_2D, atlasTexture);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    // The atlas has no tile gutters, so mipmaps or linear filtering would bleed between tiles.
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, atlas.size(), atlas.size(), 0, GL_RGBA, GL_UNSIGNED_BYTE, atlas.pixels());
    for (ShapeClass shapeClass : ShapeClass.values()) {
      Mesh mesh = new Mesh();
      glBindVertexArray(mesh.vao);
      glBindBuffer(GL_ARRAY_BUFFER, mesh.vbo);
      attribute(0, 3, 0);
      attribute(1, 3, 3);
      attribute(2, 2, 6);
      attribute(3, 3, 8);
      meshes.put(shapeClass, mesh);
    }
    glBindVertexArray(0);
  }

  /**
   * Declares one vertex attribute in the shared vertex layout.
   *
   * @param index        Attribute location.
   * @param size         Number of floats.
   * @param offsetFloats Offset in the vertex, in floats.
   */
  private static void attribute(int index, int size, int offsetFloats) {
    glEnableVertexAttribArray(index);
    glVertexAttribPointer(index, size, GL_FLOAT, false, STRIDE, (long) offsetFloats * Float.BYTES);
  }

  /**
   * Replaces each shape class's mesh.
   *
   * @param data Vertex data for each shape class.
   */
  void upload(Map<ShapeClass, float[]> data) {
    data.forEach((shapeClass, vertices) -> {
      Mesh mesh = meshes.get(shapeClass);
      glBindBuffer(GL_ARRAY_BUFFER, mesh.vbo);
      glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
      mesh.vertexCount = vertices.length / TexturedMesher.FLOATS_PER_VERTEX;
    });
  }

  /**
   * Draws the visible shapes into a shadow map, with transparent texels left out.
   *
   * @param depth Shadow depth shader.
   * @param state View settings, which decide what is visible.
   */
  void drawDepth(GlProgram depth, ViewerState state) {
    depth.setInt("uCutout", 1);
    depth.setInt("uAtlas", 0);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, atlasTexture);
    glDisable(GL_CULL_FACE);
    meshes.forEach((shapeClass, mesh) -> {
      if (mesh.vertexCount > 0 && state.isVisible(shapeClass)) {
        glBindVertexArray(mesh.vao);
        glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
      }
    });
    glBindVertexArray(0);
  }

  /**
   * Draws with realistic lighting when given a shadow map, otherwise with Minecraft face shading.
   *
   * @param viewProjection Combined view and projection matrix.
   * @param state          View settings, which decide what is visible.
   * @param shadows        Shadow map, or null for Minecraft face shading.
   */
  void draw(Matrix4f viewProjection, ViewerState state, ShadowMap shadows) {
    program.use();
    program.setMatrix("uViewProjection", viewProjection);
    program.setInt("uAtlas", 0);
    ShadowMap.apply(program, shadows);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, atlasTexture);
    glDisable(GL_CULL_FACE);
    meshes.forEach((shapeClass, mesh) -> {
      if (mesh.vertexCount > 0 && state.isVisible(shapeClass)) {
        glBindVertexArray(mesh.vao);
        glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
      }
    });
    glBindVertexArray(0);
  }

  /**
   * Frees every mesh, the atlas texture and the shader.
   */
  @Override
  public void close() {
    meshes.values().forEach(mesh -> {
      glDeleteBuffers(mesh.vbo);
      glDeleteVertexArrays(mesh.vao);
    });
    glDeleteTextures(atlasTexture);
    program.close();
  }

  /**
   * One shape class's vertex array, buffer and vertex count.
   */
  private static final class Mesh {
    final int vao = glGenVertexArrays();
    final int vbo = glGenBuffers();
    int vertexCount;
  }
}
