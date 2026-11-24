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

package com.jeffch.arbor.export;

import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.pipeline.GeneratedTree;
import com.jeffch.arbor.shape.BlockResolver;
import com.jeffch.arbor.skeleton.Branch;
import org.jnbt.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes one tree as a Sponge Schematic version 3 file, with the paste point at the bottom of the trunk.
 */
public final class SchematicWriter {
  public static final int DEFAULT_DATA_VERSION = 5023;

  private static final int FORMAT_VERSION = 3;
  private static final int MAX_SIDE = 0xFFFF;
  private static final long MAX_VOLUME = 1L << 30;
  private static final String AIR = "minecraft:air";

  private SchematicWriter() {
  }

  /**
   * Writes gzip-compressed NBT. The stream is finished but left open for the caller to close.
   *
   * @param out         Stream to write to.
   * @param tree        Tree to write.
   * @param name        Name stored in the file.
   * @param dataVersion Minecraft data version stored in the file.
   */
  public static void write(OutputStream out, GeneratedTree tree, String name, int dataVersion) throws IOException {
    BlockResolver.Cached states = new BlockResolver.Cached(tree.blocks());
    // The bounding box of every shape that becomes a real block; switched-off roles leave no block.
    int[] low = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
    int[] high = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    tree.shapes().forEachSorted((x, y, z, shape) -> {
      if (states.at(shape, x, y, z) != null) {
        int[] position = {x, y, z};
        for (int axis = 0; axis < 3; axis++) {
          low[axis] = Math.min(low[axis], position[axis]);
          high[axis] = Math.max(high[axis], position[axis]);
        }
      }
    });
    if (low[0] > high[0]) {
      throw new IOException("seed " + tree.seed() + " produced no blocks");
    }
    int width = high[0] - low[0] + 1;
    int height = high[1] - low[1] + 1;
    int length = high[2] - low[2] + 1;
    if (width > MAX_SIDE || height > MAX_SIDE || length > MAX_SIDE
            || (long) width * height * length > MAX_VOLUME) {
      throw new IOException("tree is too large for a schematic: " + width + " x " + height + " x " + length);
    }

    // Each distinct block state gets a palette number in order of first use; air is always 0.
    Map<String, Integer> palette = new LinkedHashMap<>();
    palette.put(AIR, 0);
    ByteArrayOutputStream data = new ByteArrayOutputStream(width * height * length);
    long volume = (long) width * height * length;
    long[] next = {0};
    // Shapes are visited by y, then z, then x, which is the order of the data array.
    tree.shapes().forEachSorted((x, y, z, shape) -> {
      String state = states.at(shape, x, y, z);
      if (state == null) {
        return;
      }
      long index = (x - low[0]) + (long) (z - low[2]) * width + (long) (y - low[1]) * width * length;
      // Fill the gap since the previous block with air.
      for (; next[0] < index; next[0]++) {
        data.write(0);
      }
      writeVarInt(data, palette.computeIfAbsent(state, key -> palette.size()));
      next[0]++;
    });
    for (; next[0] < volume; next[0]++) {
      data.write(0);
    }

    // Assemble the NBT layout the format expects: Schematic > Metadata, size, Offset and Blocks > Palette, Data.
    // Offset places the box relative to the paste point, so pasting puts the trunk base where the player stands.
    int[] origin = origin(tree);
    Map<String, Tag> blocks = new HashMap<>();
    put(blocks, paletteTag(palette));
    put(blocks, new ByteArrayTag("Data", data.toByteArray()));

    Map<String, Tag> metadata = new HashMap<>();
    put(metadata, new StringTag("Name", name));
    put(metadata, new LongTag("Date", System.currentTimeMillis()));
    put(metadata, new LongTag("Seed", tree.seed()));

    Map<String, Tag> schematic = new HashMap<>();
    put(schematic, new IntTag("Version", FORMAT_VERSION));
    put(schematic, new IntTag("DataVersion", dataVersion));
    put(schematic, new CompoundTag("Metadata", metadata));
    put(schematic, new ShortTag("Width", (short) width));
    put(schematic, new ShortTag("Height", (short) height));
    put(schematic, new ShortTag("Length", (short) length));
    put(schematic, new IntArrayTag("Offset", new int[]{low[0] - origin[0], low[1] - origin[1], low[2] - origin[2]}));
    put(schematic, new CompoundTag("Blocks", blocks));

    Map<String, Tag> root = new HashMap<>();
    put(root, new CompoundTag("Schematic", schematic));
    GZIPOutputStream gzip = new GZIPOutputStream(out);
    new NBTOutputStream(gzip).writeTag(new CompoundTag("", root));
    gzip.finish();
  }

  /**
   * The block at the bottom of the trunk, which becomes the paste point. A tree with several trunks uses the middle
   * of their bases, at the height of the lowest one. On sloped ground it is the ground under that middle point.
   *
   * @param tree Tree to look at.
   */
  public static int[] origin(GeneratedTree tree) {
    List<Branch> trunks = tree.skeleton().branches().stream().filter(branch -> branch.level() == 0).toList();
    if (trunks.isEmpty()) {
      return new int[3];
    }
    double x = 0;
    double z = 0;
    double y = Double.POSITIVE_INFINITY;
    for (Branch trunk : trunks) {
      Vec3 base = tree.ground().alongNormalToGround(trunk.nodes().get(0).position());
      x += base.x();
      z += base.z();
      y = Math.min(y, base.y());
    }
    int blockX = (int) Math.floor(x / trunks.size());
    int blockZ = (int) Math.floor(z / trunks.size());
    if (!tree.ground().flat()) {
      y = tree.ground().heightAt(blockX + 0.5, blockZ + 0.5);
    }
    return new int[]{blockX, (int) Math.floor(y), blockZ};
  }

  /**
   * The palette as an NBT compound mapping each block state to its number.
   *
   * @param palette Block states and their numbers.
   */
  private static CompoundTag paletteTag(Map<String, Integer> palette) {
    Map<String, Tag> entries = new HashMap<>();
    palette.forEach((state, id) -> entries.put(state, new IntTag(state, id)));
    return new CompoundTag("Palette", entries);
  }

  private static void put(Map<String, Tag> compound, Tag tag) {
    compound.put(tag.getName(), tag);
  }

  /**
   * Writes a palette number as the format's variable-length integer: seven bits per byte, lowest first, with the
   * top bit set on every byte except the last.
   *
   * @param out   Stream to write to.
   * @param value Palette number.
   */
  static void writeVarInt(ByteArrayOutputStream out, int value) {
    int remaining = value;
    while ((remaining & ~0x7F) != 0) {
      out.write((remaining & 0x7F) | 0x80);
      remaining >>>= 7;
    }
    out.write(remaining);
  }
}
