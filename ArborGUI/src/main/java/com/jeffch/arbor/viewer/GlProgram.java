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
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL33C.*;

/**
 * A linked shader program loaded from the shaders resource folder.
 */
public final class GlProgram implements AutoCloseable {
  /**
   * Marks where a shader takes the shared lighting code.
   */
  private static final String LIGHTING = "// @lighting";

  private final int id;

  private GlProgram(int id) {
    this.id = id;
  }

  /**
   * Compiles and links {@code name.vert} and {@code name.frag} from the shaders folder.
   *
   * @param name Shader name without extension.
   */
  public static GlProgram load(String name) {
    int vertex = compile(GL_VERTEX_SHADER, name + ".vert");
    int fragment = compile(GL_FRAGMENT_SHADER, name + ".frag");
    int program = glCreateProgram();
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glLinkProgram(program);
    glDeleteShader(vertex);
    glDeleteShader(fragment);
    if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
      String log = glGetProgramInfoLog(program);
      glDeleteProgram(program);
      throw new IllegalStateException("cannot link shader " + name + ": " + log.strip());
    }
    return new GlProgram(program);
  }

  /**
   * Compiles one shader, first pasting the shared lighting code where the source asks for it.
   *
   * @param type     OpenGL shader type, such as {@code GL_VERTEX_SHADER}.
   * @param resource Shader file name.
   */
  private static int compile(int type, String resource) {
    int shader = glCreateShader(type);
    String source = read(resource);
    glShaderSource(shader, source.contains(LIGHTING) ? source.replace(LIGHTING, read("lighting.glsl")) : source);
    glCompileShader(shader);
    if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
      String log = glGetShaderInfoLog(shader);
      glDeleteShader(shader);
      throw new IllegalStateException("cannot compile " + resource + ": " + log.strip());
    }
    return shader;
  }

  /**
   * A shader's source text from the shaders resource folder.
   *
   * @param resource Shader file name.
   */
  private static String read(String resource) {
    try (InputStream stream = GlProgram.class.getResourceAsStream("/shaders/" + resource)) {
      if (stream == null) {
        throw new IllegalStateException("missing shader " + resource);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("cannot read shader " + resource, e);
    }
  }

  /**
   * Makes this the active program.
   */
  public void use() {
    glUseProgram(id);
  }

  /**
   * Sets a 4x4 matrix uniform.
   *
   * @param name   Uniform name.
   * @param matrix Value to set.
   */
  public void setMatrix(String name, Matrix4f matrix) {
    try (MemoryStack stack = MemoryStack.stackPush()) {
      FloatBuffer buffer = stack.mallocFloat(16);
      matrix.get(buffer);
      glUniformMatrix4fv(glGetUniformLocation(id, name), false, buffer);
    }
  }

  /**
   * Sets a float uniform.
   *
   * @param name  Uniform name.
   * @param value Value to set.
   */
  public void setFloat(String name, float value) {
    glUniform1f(glGetUniformLocation(id, name), value);
  }

  /**
   * Sets an int uniform, also used to pick texture units.
   *
   * @param name  Uniform name.
   * @param value Value to set.
   */
  public void setInt(String name, int value) {
    glUniform1i(glGetUniformLocation(id, name), value);
  }

  /**
   * Sets a three-component vector uniform.
   *
   * @param name Uniform name.
   * @param x    First component.
   * @param y    Second component.
   * @param z    Third component.
   */
  public void setVec3(String name, float x, float y, float z) {
    glUniform3f(glGetUniformLocation(id, name), x, y, z);
  }

  /**
   * Deletes the program.
   */
  @Override
  public void close() {
    glDeleteProgram(id);
  }
}
