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
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes OpenGL pixels to a PNG file.
 */
final class Screenshot {
  /**
   * Height in pixels of the strip above each tile of a sheet that holds its name.
   */
  static final int LABEL_HEIGHT = 18;
  private static final Color LABEL_STRIP = new Color(45, 48, 54);
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
    write(file, image(bottomUpRgba, width, height));
  }

  /**
   * Pixels read from OpenGL as an image, flipped since OpenGL's rows start at the bottom.
   *
   * @param bottomUpRgba Pixels as read from OpenGL.
   * @param width        Image width.
   * @param height       Image height.
   */
  static BufferedImage image(ByteBuffer bottomUpRgba, int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int source = ((height - 1 - y) * width + x) * 4;
        image.setRGB(x, y, (bottomUpRgba.get(source) & 255) << 16 | (bottomUpRgba.get(source + 1) & 255) << 8
                | bottomUpRgba.get(source + 2) & 255);
      }
    }
    return image;
  }

  /**
   * Cuts every tile down to one box around everything drawn in any of them, so no tile keeps empty background and a
   * block stays the same size from tile to tile. The background is the colour of each tile's top left corner.
   *
   * @param tiles Same-sized images.
   * @param pad   Pixels of background kept around the content.
   */
  static List<BufferedImage> cropToContent(List<BufferedImage> tiles, int pad) {
    int width = tiles.get(0).getWidth();
    int height = tiles.get(0).getHeight();
    int left = width;
    int top = height;
    int right = -1;
    int bottom = -1;
    for (BufferedImage tile : tiles) {
      int background = tile.getRGB(0, 0) & 0xFFFFFF;
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          if ((tile.getRGB(x, y) & 0xFFFFFF) != background) {
            left = Math.min(left, x);
            right = Math.max(right, x);
            top = Math.min(top, y);
            bottom = Math.max(bottom, y);
          }
        }
      }
    }
    if (right < 0) {
      return tiles;
    }
    int x = Math.max(0, left - pad);
    int y = Math.max(0, top - pad);
    int w = Math.min(width, right + pad + 1) - x;
    int h = Math.min(height, bottom + pad + 1) - y;
    return tiles.stream().map(tile -> tile.getSubimage(x, y, w, h)).toList();
  }

  /**
   * Same-sized images in a near-square grid (2×2 for four, 3×3 for nine), each named in its top left corner.
   *
   * @param tiles  Images, left to right then top to bottom.
   * @param labels Name of each image.
   */
  static BufferedImage sheet(List<BufferedImage> tiles, List<String> labels) {
    int width = tiles.get(0).getWidth();
    int height = tiles.get(0).getHeight();
    int columns = (int) Math.ceil(Math.sqrt(tiles.size()));
    int rows = (tiles.size() + columns - 1) / columns;
    int row = height + LABEL_HEIGHT;
    BufferedImage sheet = new BufferedImage(width * columns, row * rows, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = sheet.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, LABEL_HEIGHT - 5));
    FontMetrics metrics = g.getFontMetrics();
    for (int i = 0; i < tiles.size(); i++) {
      int x = i % columns * width;
      int y = i / columns * row;
      // The label gets its own strip above the tile, so it never hides a tightly cropped tree.
      g.setColor(LABEL_STRIP);
      g.fillRect(x, y, width, LABEL_HEIGHT);
      g.setColor(Color.WHITE);
      g.drawString(labels.get(i), x + 4, y + (LABEL_HEIGHT - metrics.getHeight()) / 2 + metrics.getAscent());
      g.drawImage(tiles.get(i), x, y + LABEL_HEIGHT, null);
    }
    g.dispose();
    return sheet;
  }

  /**
   * One entry of an image's key: a colored square and its text.
   *
   * @param text  What the color stands for.
   * @param color Red, green and blue from 0 to 1.
   */
  record Key(String text, float[] color) {
  }

  /**
   * The image with a key under it, its entries in rows that wrap to the image's width.
   *
   * @param image Image to label.
   * @param keys  Entries, in order.
   */
  static BufferedImage withKey(BufferedImage image, List<Key> keys) {
    Font font = new Font(Font.SANS_SERIF, Font.BOLD, LABEL_HEIGHT - 5);
    BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    FontMetrics metrics = probe.createGraphics().getFontMetrics(font);
    int swatch = LABEL_HEIGHT - 8;
    List<int[]> places = new ArrayList<>();
    int x = 4;
    int row = 0;
    for (Key key : keys) {
      int width = swatch + 4 + metrics.stringWidth(key.text()) + 12;
      if (x > 4 && x + width > image.getWidth()) {
        x = 4;
        row++;
      }
      places.add(new int[]{x, row});
      x += width;
    }
    BufferedImage keyed = new BufferedImage(image.getWidth(), image.getHeight() + (row + 1) * LABEL_HEIGHT,
            BufferedImage.TYPE_INT_RGB);
    Graphics2D g = keyed.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.setColor(LABEL_STRIP);
    g.fillRect(0, image.getHeight(), image.getWidth(), (row + 1) * LABEL_HEIGHT);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(font);
    for (int i = 0; i < keys.size(); i++) {
      float[] c = keys.get(i).color();
      int top = image.getHeight() + places.get(i)[1] * LABEL_HEIGHT;
      g.setColor(new Color(c[0], c[1], c[2]));
      g.fillRect(places.get(i)[0], top + 4, swatch, swatch);
      g.setColor(Color.WHITE);
      g.drawString(keys.get(i).text(), places.get(i)[0] + swatch + 4,
              top + (LABEL_HEIGHT - metrics.getHeight()) / 2 + metrics.getAscent());
    }
    g.dispose();
    return keyed;
  }

  /**
   * Saves an image as a PNG.
   *
   * @param file  PNG file to write.
   * @param image Image to save.
   */
  static void write(Path file, BufferedImage image) {
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
