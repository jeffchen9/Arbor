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

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * A color and depth render target, so frames can be composited and saved reliably.
 */
public final class OffscreenTarget implements AutoCloseable {
  private final int framebuffer = glGenFramebuffers();
  private final int color = glGenTextures();
  private final int depth = glGenTextures();
  private int width;
  private int height;

  /**
   * Sharp, unrepeated sampling for the bound texture.
   */
  private static void configureTexture() {
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
  }

  /**
   * Resizes the color and depth textures when the view size changes.
   *
   * @param newWidth  View width in pixels.
   * @param newHeight View height in pixels.
   */
  public void ensureSize(int newWidth, int newHeight) {
    if (newWidth == width && newHeight == height) {
      return;
    }
    width = newWidth;
    height = newHeight;
    glBindTexture(GL_TEXTURE_2D, color);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
    configureTexture();
    glBindTexture(GL_TEXTURE_2D, depth);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, width, height, 0, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT,
            (ByteBuffer) null);
    configureTexture();
    glBindTexture(GL_TEXTURE_2D, 0);
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
      throw new IllegalStateException("offscreen framebuffer is incomplete");
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
  }

  /**
   * Draws into this target from now on, at its full size.
   */
  public void bind() {
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    glViewport(0, 0, width, height);
  }

  /**
   * Removes the depth attachment so the depth texture can be sampled while drawing color.
   */
  public void detachDepth() {
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
  }

  /**
   * Puts the depth texture back after {@link #detachDepth}.
   */
  public void attachDepth() {
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
  }

  /**
   * The rendered image as bottom-up RGBA, in a new buffer.
   */
  public ByteBuffer readPixels() {
    return readPixels(BufferUtils.createByteBuffer(width * height * 4));
  }

  /**
   * Reads bottom-up RGBA into a buffer of at least width * height * 4 bytes.
   *
   * @param pixels Receives the pixels.
   */
  public ByteBuffer readPixels(ByteBuffer pixels) {
    glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
    return pixels;
  }

  /**
   * The depth texture, for effects that need to know where geometry is.
   */
  public int depthTexture() {
    return depth;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  /**
   * Frees the framebuffer and its textures.
   */
  @Override
  public void close() {
    glDeleteFramebuffers(framebuffer);
    glDeleteTextures(color);
    glDeleteTextures(depth);
  }
}
