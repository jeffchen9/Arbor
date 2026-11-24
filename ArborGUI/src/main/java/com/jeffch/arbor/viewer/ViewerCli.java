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

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Reads the editor's command line.
 */
public final class ViewerCli {
  public static final String USAGE =
          "usage: arbor [view] [<preset.json>] [--seed <n>] [--assets <folder>] [--lighting minecraft|realistic] | "
                  + "arbor view <preset.json> --screenshot <file.png> [--seed <n>] [--assets <folder>] "
                  + "[--lighting minecraft|realistic] | "
                  + com.jeffch.arbor.export.ExportOptions.USAGE;

  private ViewerCli() {
  }

  /**
   * Reads an optional preset path plus the named options, in any order. A screenshot needs a preset.
   *
   * @param args Command line arguments.
   */
  public static ViewerOptions parse(String[] args) {
    Path preset = null;
    Path screenshot = null;
    Path assets = null;
    long seed = 1;
    boolean realistic = false;
    try {
      for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
          case "--seed" -> seed = Long.parseLong(value(args, ++i));
          case "--screenshot" -> screenshot = Path.of(value(args, ++i));
          case "--assets" -> assets = Path.of(value(args, ++i));
          case "--lighting" -> {
            String lighting = value(args, ++i);
            if (!lighting.equals("minecraft") && !lighting.equals("realistic")) {
              throw new IllegalArgumentException(USAGE);
            }
            realistic = lighting.equals("realistic");
          }
          default -> {
            if (args[i].startsWith("--") || preset != null) {
              throw new IllegalArgumentException(USAGE);
            }
            preset = Path.of(args[i]);
          }
        }
      }
    } catch (NumberFormatException | InvalidPathException e) {
      throw new IllegalArgumentException(USAGE);
    }
    if (screenshot != null && preset == null) {
      throw new IllegalArgumentException(USAGE);
    }
    return new ViewerOptions(preset, seed, screenshot, assets, realistic);
  }

  /**
   * The value after an option, or a usage error when the option is last.
   *
   * @param args  Command line arguments.
   * @param index Position of the option.
   */
  private static String value(String[] args, int index) {
    if (index >= args.length) {
      throw new IllegalArgumentException(USAGE);
    }
    return args[index];
  }
}
