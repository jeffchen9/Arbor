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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Parsed batch export command line. Seeds are always random, so there is deliberately no seed option.
 *
 * @param preset      Preset file to generate from.
 * @param count       Number of trees to export.
 * @param folder      Output folder, or null for the preset's default folder.
 * @param dataVersion Minecraft data version written into each file.
 */
public record ExportOptions(Path preset, int count, Path folder, int dataVersion) {
  public static final String USAGE = "arbor export <design.json> --count <n> [--out <folder>] [--data-version <n>]";
  private static final int MAX_COUNT = 1_000_000;

  /**
   * Reads the arguments after {@code export}: one preset path plus the named options, in any order.
   *
   * @param args Command line arguments after {@code export}.
   * @throws IllegalArgumentException With the usage text, for anything missing, unknown or out of range.
   */
  public static ExportOptions parse(String[] args) {
    Path preset = null;
    Path folder = null;
    int count = 0;
    int dataVersion = SchematicWriter.DEFAULT_DATA_VERSION;
    try {
      for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
          case "--count" -> count = Integer.parseInt(value(args, ++i));
          case "--out" -> folder = Path.of(value(args, ++i));
          case "--data-version" -> dataVersion = Integer.parseInt(value(args, ++i));
          default -> {
            // Anything else is the preset path; an unknown option or a second path is an error.
            if (args[i].startsWith("--") || preset != null) {
              throw usage();
            }
            preset = Path.of(args[i]);
          }
        }
      }
    } catch (NumberFormatException | InvalidPathException e) {
      throw usage();
    }
    if (preset == null || count < 1 || count > MAX_COUNT || dataVersion < 1) {
      throw usage();
    }
    return new ExportOptions(preset, count, folder, dataVersion);
  }

  /**
   * The value that follows an option, or a usage error when the option is last.
   *
   * @param args  Command line arguments.
   * @param index Position of the option.
   */
  private static String value(String[] args, int index) {
    if (index >= args.length) {
      throw usage();
    }
    return args[index];
  }

  private static IllegalArgumentException usage() {
    return new IllegalArgumentException("usage: " + USAGE);
  }
}
