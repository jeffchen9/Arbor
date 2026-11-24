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

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Packs every block texture into one square RGBA atlas of 16x16 tiles. Tile 0 is magenta and stands in for
 * unknown or unreadable textures. Row 0 is the top of the atlas and v=0 is the top.
 */
final class TextureAtlas {
  static final int TILE = 16;

  private final int cols;
  private final int size;
  private final ByteBuffer pixels;
  private final Map<String, Integer> tiles = new HashMap<>();

  /**
   * Packs the tiles into one square image, with a magenta "missing" tile first.
   *
   * @param names    Texture ids, in tile order.
   * @param tileData One 16x16 RGBA tile per name.
   */
  private TextureAtlas(List<String> names, List<ByteBuffer> tileData) {
    int count = names.size() + 1;
    cols = (int) Math.ceil(Math.sqrt(count));
    size = cols * TILE;
    pixels = MemoryUtil.memCalloc(size * size * 4);
    byte[] magenta = {(byte) 255, 0, (byte) 255, (byte) 255};
    for (int i = 0; i < TILE * TILE; i++) blit(0, i % TILE, i / TILE, magenta);
    byte[] px = new byte[4];
    for (int t = 0; t < names.size(); t++) {
      tiles.put(names.get(t), t + 1);
      ByteBuffer d = tileData.get(t);
      for (int i = 0; i < TILE * TILE; i++) {
        d.get(i * 4, px);
        blit(t + 1, i % TILE, i / TILE, px);
      }
    }
  }

  /**
   * Loads every block texture in a folder into one atlas; textures that cannot be read are left out.
   *
   * @param folder Folder holding the block textures.
   */
  static TextureAtlas load(Path folder) {
    List<Path> files = new ArrayList<>();
    if (folder != null && Files.isDirectory(folder)) {
      try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder, "*.png")) {
        for (Path p : ds) if (Files.isRegularFile(p)) files.add(p);
      } catch (IOException ignored) {
        // an unreadable folder just leaves the magenta tile
      }
    }
    Collections.sort(files);
    List<String> names = new ArrayList<>();
    List<ByteBuffer> data = new ArrayList<>();
    for (Path p : files) {
      ByteBuffer tile = decodeTile(p);
      if (tile == null) continue;
      String file = p.getFileName().toString();
      names.add(file.substring(0, file.length() - 4).toLowerCase(Locale.ROOT));
      data.add(tile);
    }
    try {
      return new TextureAtlas(names, data);
    } finally {
      data.forEach(MemoryUtil::memFree);
    }
  }

  /**
   * Decodes a PNG to one 16x16 RGBA tile (top square frame, nearest neighbour), or null if unreadable.
   *
   * @param file PNG file.
   */
  private static ByteBuffer decodeTile(Path file) {
    PngFile png = PngFile.read(file);
    if (png == null) return null;
    int frame = Math.min(png.width(), png.height());
    ByteBuffer tile = MemoryUtil.memAlloc(TILE * TILE * 4).order(ByteOrder.BIG_ENDIAN);
    for (int y = 0; y < TILE; y++) {
      for (int x = 0; x < TILE; x++) {
        int argb = png.argb()[(y * frame / TILE) * png.width() + x * frame / TILE];
        tile.putInt((y * TILE + x) * 4, argb << 8 | argb >>> 24);
      }
    }
    return tile;
  }

  /**
   * Writes one pixel into a tile.
   *
   * @param tile Tile index.
   * @param x    Pixel x within the tile.
   * @param y    Pixel y within the tile.
   * @param rgba Pixel color.
   */
  private void blit(int tile, int x, int y, byte[] rgba) {
    int px = (tile % cols) * TILE + x;
    int py = (tile / cols) * TILE + y;
    pixels.put((py * size + px) * 4, rgba);
  }

  /**
   * The atlas's width and height in pixels.
   */
  int size() {
    return size;
  }

  /**
   * Direct RGBA, row 0 on top. Owned by the atlas.
   */
  ByteBuffer pixels() {
    return pixels;
  }

  /**
   * {u0, v0, u1, v1} for a texture id such as {@code block/oak_log}; magenta tile when unknown.
   *
   * @param texture Texture id.
   */
  float[] uvBounds(String texture) {
    int tile = 0;
    if (texture != null) {
      String key = texture.substring(Math.max(texture.lastIndexOf('/'), texture.lastIndexOf(':')) + 1)
              .toLowerCase(Locale.ROOT);
      tile = tiles.getOrDefault(key, 0);
    }
    float u0 = (tile % cols) * TILE / (float) size, v0 = (tile / cols) * TILE / (float) size;
    float d = TILE / (float) size;
    return new float[]{u0, v0, u0 + d, v0 + d};
  }
}
