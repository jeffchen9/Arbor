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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes OpenGL pixels to a PNG file.
 */
final class Screenshot {
  private Screenshot() {
  }

  /**
   * Saves pixels read from OpenGL as a PNG, flipping them since OpenGL's rows start at the bottom.
   *
   * @param file         PNG file to write.
   * @param bottomUpRgba Pixels as read from OpenGL.
   * @param width        Image width.
   * @param height       Image height.
   */
  static void write(Path file, ByteBuffer bottomUpRgba, int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int source = ((height - 1 - y) * width + x) * 4;
        image.setRGB(x, y, (bottomUpRgba.get(source) & 255) << 16 | (bottomUpRgba.get(source + 1) & 255) << 8
                | bottomUpRgba.get(source + 2) & 255);
      }
    }
    Path absolute = file.toAbsolutePath();
    try {
      if (absolute.getParent() != null) {
        Files.createDirectories(absolute.getParent());
      }
      if (!ImageIO.write(image, "png", absolute.toFile())) {
        throw new IOException("no PNG writer");
      }
    } catch (IOException e) {
      throw new IllegalStateException("cannot write screenshot " + file + ": " + e.getMessage());
    }
  }
}
