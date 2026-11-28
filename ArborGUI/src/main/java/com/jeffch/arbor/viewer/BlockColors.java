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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The colors blocks really show, averaged from their textures and biome tints, so a block can be chosen by its color
 * instead of by its name.
 */
final class BlockColors {
  /**
   * Faces closer than this in red, green and blue (0 to 255 each) count as one color.
   */
  private static final double SAME_COLOR = 24;
  private final BlockModels models;
  private final Path textures;
  private final TintColors tints;
  private final Map<String, int[]> averages = new HashMap<>();

  /**
   * @param assets Folder holding {@code assets/minecraft}.
   */
  BlockColors(Path assets) {
    models = new BlockModels(assets);
    textures = assets.resolve("assets").resolve("minecraft").resolve("textures");
    tints = new TintColors(textures.resolve("colormap"));
  }

  /**
   * A block's colors in words and hex: its sides, and its top and bottom when they differ, such as a log's bark and cut
   * end. Null when the block has no model to read.
   *
   * @param block Block name.
   */
  String describe(String block) {
    List<ModelQuad> quads = models.quads(block);
    if (quads.isEmpty()) {
      return null;
    }
    int[] sides = average(block, quads, false);
    int[] ends = average(block, quads, true);
    if (sides == null || ends == null || distance(sides, ends) < SAME_COLOR) {
      int[] color = sides != null ? sides : ends;
      return color == null ? null : text(color);
    }
    return "sides " + text(sides) + "; top and bottom " + text(ends);
  }

  /**
   * The average color of a block's side faces, or of its top and bottom faces, tinted as in a plains biome. Null when
   * it has no such faces.
   *
   * @param block Block name.
   * @param quads The block's faces.
   * @param ends  Whether to average the faces pointing up or down instead of the rest.
   */
  private int[] average(String block, List<ModelQuad> quads, boolean ends) {
    double[] sum = new double[3];
    int faces = 0;
    for (ModelQuad quad : quads) {
      boolean vertical = Math.abs(quad.normal()[1]) > 0.9;
      int[] texture = averages.computeIfAbsent(quad.texture(), this::textureAverage);
      if (vertical != ends || texture == null) {
        continue;
      }
      float[] tint = tints.color(block, quad.tintIndex());
      for (int i = 0; i < 3; i++) {
        sum[i] += texture[i] * tint[i];
      }
      faces++;
    }
    if (faces == 0) {
      return null;
    }
    return new int[]{(int) Math.round(sum[0] / faces), (int) Math.round(sum[1] / faces),
            (int) Math.round(sum[2] / faces)};
  }

  /**
   * The average of a texture's visible pixels, or null when it cannot be read or has none.
   *
   * @param texture Texture reference, such as {@code block/oak_log}.
   */
  private int[] textureAverage(String texture) {
    PngFile png = PngFile.read(textures.resolve(texture + ".png"));
    if (png == null) {
      return null;
    }
    long[] sum = new long[3];
    long count = 0;
    for (int argb : png.argb()) {
      if ((argb >>> 24) >= 128) {
        sum[0] += (argb >> 16) & 255;
        sum[1] += (argb >> 8) & 255;
        sum[2] += argb & 255;
        count++;
      }
    }
    return count == 0 ? null : new int[]{(int) (sum[0] / count), (int) (sum[1] / count), (int) (sum[2] / count)};
  }

  private static double distance(int[] a, int[] b) {
    return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
  }

  private static String text(int[] rgb) {
    return String.format(Locale.ROOT, "#%02X%02X%02X %s", rgb[0], rgb[1], rgb[2], words(rgb));
  }

  /**
   * A color in plain words, such as dark brown, pale gray or light green.
   *
   * @param rgb Red, green and blue, 0 to 255 each.
   */
  static String words(int[] rgb) {
    double r = rgb[0] / 255.0;
    double g = rgb[1] / 255.0;
    double b = rgb[2] / 255.0;
    double max = Math.max(r, Math.max(g, b));
    double min = Math.min(r, Math.min(g, b));
    double lightness = (max + min) / 2;
    double chroma = max - min;
    String shade = lightness < 0.18 ? "very dark" : lightness < 0.35 ? "dark" : lightness < 0.55 ? "medium"
            : lightness < 0.72 ? "light" : "pale";
    if (chroma < 0.08) {
      return lightness < 0.12 ? "black" : lightness > 0.88 ? "white" : shade + " gray";
    }
    double hue = max == r ? 60 * (((g - b) / chroma) % 6) : max == g ? 60 * ((b - r) / chroma + 2)
            : 60 * ((r - g) / chroma + 4);
    hue = (hue + 360) % 360;
    String name;
    if (hue < 15 || hue >= 330) {
      name = lightness < 0.35 ? "maroon" : lightness >= 0.7 ? "pink" : "red";
    } else if (hue < 50) {
      name = lightness < 0.5 ? "brown" : chroma < 0.45 ? "tan" : "orange";
    } else if (hue < 70) {
      name = lightness < 0.4 ? "olive" : "yellow";
    } else if (hue < 165) {
      name = "green";
    } else if (hue < 200) {
      name = "teal";
    } else if (hue < 255) {
      name = "blue";
    } else if (hue < 290) {
      name = "purple";
    } else {
      name = "pink";
    }
    // A weak color reads as a grayish one, as pale oak's bark does.
    return shade + (chroma < 0.16 ? " grayish " : " ") + name;
  }
}
