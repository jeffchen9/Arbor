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
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A decoded PNG with its stored color values.
 *
 * @param width  Width in pixels.
 * @param height Height in pixels.
 * @param argb   Pixels as 0xAARRGGBB, row by row from the top.
 */
record PngFile(int width, int height, int[] argb) {
  /**
   * Reads a PNG, or returns null if it cannot be read.
   *
   * @param file PNG file.
   */
  static PngFile read(Path file) {
    try {
      BufferedImage image = ImageIO.read(file.toFile());
      if (image == null) {
        return null;
      }
      int width = image.getWidth();
      int height = image.getHeight();
      ColorModel model = image.getColorModel();
      boolean gray = model.getColorSpace().getType() == ColorSpace.TYPE_GRAY && !(model instanceof IndexColorModel);
      int[] pixels = gray ? storedGray(image.getRaster(), model) : image.getRGB(0, 0, width, height, null, 0, width);
      return new PngFile(width, height, pixels);
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /**
   * Java treats gray as linear and would brighten it through getRGB, so the stored values are read directly.
   *
   * @param raster The image's pixels.
   * @param model  The image's color model.
   */
  private static int[] storedGray(Raster raster, ColorModel model) {
    int width = raster.getWidth();
    int height = raster.getHeight();
    int shift = Math.max(0, model.getComponentSize(0) - 8);
    boolean hasAlpha = raster.getNumBands() > 1;
    int[] pixels = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int value = raster.getSample(x, y, 0) >> shift;
        int alpha = hasAlpha ? raster.getSample(x, y, 1) >> shift : 255;
        pixels[y * width + x] = alpha << 24 | value << 16 | value << 8 | value;
      }
    }
    return pixels;
  }
}
