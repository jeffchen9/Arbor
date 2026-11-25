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
 * A color and depth render target, so frames can be composited and saved reliably. Drawing goes first into a
 * multisampled copy, which smooths edges, and is resolved into plain textures before it is read or its depth sampled.
 */
public final class OffscreenTarget implements AutoCloseable {
  private static final int MAX_SAMPLES = 4;

  private final int framebuffer = glGenFramebuffers();
  private final int color = glGenTextures();
  private final int depth = glGenTextures();
  private final int samples = Math.min(MAX_SAMPLES, glGetInteger(GL_MAX_SAMPLES));
  private final int multisampled = samples > 1 ? glGenFramebuffers() : 0;
  private final int multisampledColor = samples > 1 ? glGenRenderbuffers() : 0;
  private final int multisampledDepth = samples > 1 ? glGenRenderbuffers() : 0;
  private boolean resolved;
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
    if (multisampled != 0) {
      glBindRenderbuffer(GL_RENDERBUFFER, multisampledColor);
      glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA8, width, height);
      glBindRenderbuffer(GL_RENDERBUFFER, multisampledDepth);
      glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT24, width, height);
      glBindRenderbuffer(GL_RENDERBUFFER, 0);
      glBindFramebuffer(GL_FRAMEBUFFER, multisampled);
      glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, multisampledColor);
      glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, multisampledDepth);
      if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        throw new IllegalStateException("multisampled framebuffer is incomplete");
      }
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
  }

  /**
   * Draws into this target from now on, at its full size, starting a new frame in the multisampled copy.
   */
  public void bind() {
    resolved = multisampled == 0;
    glBindFramebuffer(GL_FRAMEBUFFER, resolved ? framebuffer : multisampled);
    glViewport(0, 0, width, height);
  }

  /**
   * Averages the multisampled frame into the plain textures, and draws there from now on, without smoothing. Called
   * before sampling the depth texture and before reading; does nothing when already resolved.
   */
  public void resolve() {
    if (resolved) {
      return;
    }
    resolved = true;
    int[] viewport = new int[4];
    glGetIntegerv(GL_VIEWPORT, viewport);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, multisampled);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
    glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT,
            GL_NEAREST);
    glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
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
    resolve();
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
    if (multisampled != 0) {
      glDeleteFramebuffers(multisampled);
      glDeleteRenderbuffers(multisampledColor);
      glDeleteRenderbuffers(multisampledDepth);
    }
  }
}
