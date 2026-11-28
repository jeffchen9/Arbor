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

/**
 * Grass and foliage tints, sampled from the plains point of the colormaps.
 */
final class TintColors {
  /**
   * Colormap column for plains temperature 0.8: (1 - 0.8) * 255, written out because the double product truncates to 50.
   */
  private static final int X = 51;
  /**
   * Colormap row for plains downfall 0.4: (1 - 0.4 * 0.8) * 255.
   */
  private static final int Y = 173;
  private static final float[] GRASS_FALLBACK = rgb(0x91BD59);
  private static final float[] FOLIAGE_FALLBACK = rgb(0x77AB2F);
  private static final float[] DRY_FALLBACK = rgb(0x8A7A3A);

  private final float[] grass, foliage, dry;

  /**
   * Reads the biome color maps, or uses typical colors when a map is missing.
   *
   * @param folder Assets folder holding the color maps.
   */
  TintColors(Path folder) {
    grass = sample(folder, "grass.png", GRASS_FALLBACK);
    foliage = sample(folder, "foliage.png", FOLIAGE_FALLBACK);
    dry = sample(folder, "dry_foliage.png", DRY_FALLBACK);
  }

  /**
   * One color from a color map, at the spot Minecraft uses for a temperate biome.
   *
   * @param folder   Assets folder holding the color maps.
   * @param name     Color map name.
   * @param fallback Color used when the map cannot be read.
   */
  private static float[] sample(Path folder, String name, float[] fallback) {
    if (folder == null) return fallback;
    PngFile png = PngFile.read(folder.resolve(name));
    if (png == null) return fallback;
    int x = X * png.width() / 256, y = Y * png.height() / 256;
    return rgb(png.argb()[y * png.width() + x]);
  }

  private static float[] rgb(int hex) {
    return new float[]{((hex >> 16) & 255) / 255f, ((hex >> 8) & 255) / 255f, (hex & 255) / 255f};
  }

  /**
   * RGB 0..1 for a block; tintIndex -1 means no tint.
   *
   * @param blockName Block name.
   * @param tintIndex Tint index from the model face.
   */
  float[] color(String blockName, int tintIndex) {
    if (tintIndex < 0) return new float[]{1f, 1f, 1f};
    String n = blockName.substring(blockName.lastIndexOf(':') + 1);
    if (n.equals("spruce_leaves")) return rgb(0x619961);
    if (n.equals("birch_leaves")) return rgb(0x80A755);
    if (n.equals("mangrove_leaves")) return rgb(0x92C648);
    // The game draws these with their own colors, though their models ask for a tint.
    if (n.equals("cherry_leaves") || n.equals("pale_oak_leaves")) return new float[]{1f, 1f, 1f};
    if (n.equals("leaf_litter")) return dry.clone();
    if (n.endsWith("_leaves") || n.equals("vine")) return foliage.clone();
    return grass.clone();
  }
}
