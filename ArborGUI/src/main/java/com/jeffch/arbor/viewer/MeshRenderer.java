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
 * Draws one mesh per shape class, so visibility toggles need no remeshing.
 */
public final class MeshRenderer implements AutoCloseable {
  private static final Map<ShapeClass, float[]> COLORS = Map.ofEntries(
          Map.entry(ShapeClass.FULL, new float[]{0.50f, 0.35f, 0.22f}),
          Map.entry(ShapeClass.WALL, new float[]{0.60f, 0.60f, 0.56f}),
          Map.entry(ShapeClass.FENCE, new float[]{0.82f, 0.60f, 0.34f}),
          Map.entry(ShapeClass.GATE, new float[]{0.93f, 0.78f, 0.28f}),
          Map.entry(ShapeClass.DEAD_BUSH, new float[]{0.74f, 0.36f, 0.20f}),
          Map.entry(ShapeClass.SAPLING, new float[]{0.55f, 0.88f, 0.32f}),
          Map.entry(ShapeClass.SLAB, new float[]{0.57f, 0.40f, 0.25f}),
          Map.entry(ShapeClass.STAIR, new float[]{0.54f, 0.38f, 0.24f}),
          Map.entry(ShapeClass.BUTTON, new float[]{0.30f, 0.20f, 0.13f}),
          Map.entry(ShapeClass.LEAVES, new float[]{0.20f, 0.46f, 0.18f}),
          Map.entry(ShapeClass.PLANT, new float[]{0.42f, 0.68f, 0.26f}));

  private final GlProgram program = GlProgram.load("shape");
  private final Map<ShapeClass, Mesh> meshes = new EnumMap<>(ShapeClass.class);

  /**
   * Sets up one mesh per shape class, so each class can be shown or hidden on its own.
   */
  public MeshRenderer() {
    for (ShapeClass shapeClass : ShapeClass.values()) {
      Mesh mesh = new Mesh();
      int stride = ShapeMesher.FLOATS_PER_VERTEX * Float.BYTES;
      glBindVertexArray(mesh.vao);
      glBindBuffer(GL_ARRAY_BUFFER, mesh.vbo);
      glEnableVertexAttribArray(0);
      glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
      glEnableVertexAttribArray(1);
      glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
      meshes.put(shapeClass, mesh);
    }
    glBindVertexArray(0);
  }

  /**
   * Replaces each shape class's mesh.
   *
   * @param data Vertex data for each shape class.
   */
  public void upload(Map<ShapeClass, float[]> data) {
    data.forEach((shapeClass, vertices) -> {
      Mesh mesh = meshes.get(shapeClass);
      glBindBuffer(GL_ARRAY_BUFFER, mesh.vbo);
      glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
      mesh.vertexCount = vertices.length / ShapeMesher.FLOATS_PER_VERTEX;
    });
  }

  /**
   * Draws the visible shapes into a shadow map.
   *
   * @param depth Shadow depth shader.
   * @param state View settings, which decide what is visible.
   */
  void drawDepth(GlProgram depth, ViewerState state) {
    depth.setInt("uCutout", 0);
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
    ShadowMap.apply(program, shadows);
    glEnable(GL_CULL_FACE);
    meshes.forEach((shapeClass, mesh) -> {
      if (mesh.vertexCount > 0 && state.isVisible(shapeClass)) {
        float[] color = COLORS.get(shapeClass);
        program.setVec3("uColor", color[0], color[1], color[2]);
        program.setInt("uTranslucent", ViewerState.FOLIAGE.contains(shapeClass) ? 1 : 0);
        glBindVertexArray(mesh.vao);
        glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
      }
    });
    glBindVertexArray(0);
    glDisable(GL_CULL_FACE);
  }

  /**
   * Frees every mesh and the shader.
   */
  @Override
  public void close() {
    meshes.values().forEach(mesh -> {
      glDeleteBuffers(mesh.vbo);
      glDeleteVertexArrays(mesh.vao);
    });
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
