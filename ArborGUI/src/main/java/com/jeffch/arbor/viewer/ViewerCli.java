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
          "usage: arbor [view] [<design.json>] [--seed <n>] [--assets <folder>] [--lighting minecraft|realistic] | "
                  + "arbor view <design.json> --screenshot <file.png> [--views | --variations <2-9> | --compare <other.json>[" + java.io.File.pathSeparator + "<other.json>...] | --closeup <bottom,top,left,right> [--view front|side|three-quarter|top]] [--high-detail] [--wireframe] [--seed <n>] [--assets <folder>] "
                  + "[--lighting minecraft|realistic] | "
                  + com.jeffch.arbor.export.ExportOptions.USAGE + " | arbor mcp";

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
    boolean views = false;
    int variations = 0;
    boolean highDetail = false;
    double[] closeup = null;
    String view = null;
    boolean wireframe = false;
    java.util.List<Path> compare = java.util.List.of();
    try {
      for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
          case "--seed" -> seed = Long.parseLong(value(args, ++i));
          case "--screenshot" -> screenshot = Path.of(value(args, ++i));
          case "--assets" -> assets = Path.of(value(args, ++i));
          case "--views" -> views = true;
          case "--variations" -> variations = Integer.parseInt(value(args, ++i));
          case "--high-detail" -> highDetail = true;
          case "--closeup" -> closeup = closeup(value(args, ++i));
          case "--view" -> view = value(args, ++i);
          case "--wireframe" -> wireframe = true;
          case "--compare" -> compare = java.util.Arrays.stream(value(args, ++i).split(java.util.regex.Pattern.quote(
                  java.io.File.pathSeparator))).map(Path::of).toList();
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
    boolean badVariations = variations != 0 && (screenshot == null || views || variations < 2
            || variations > ViewerOptions.MAX_VARIATIONS);
    boolean badCompare = !compare.isEmpty() && (screenshot == null || views || variations != 0 || closeup != null
            || compare.size() + 1 > ViewerOptions.MAX_VARIATIONS);
    boolean badDetail = highDetail && !views && variations == 0 && closeup == null && compare.isEmpty();
    boolean badCloseup = closeup != null && (screenshot == null || views || variations != 0)
            || view != null && (closeup == null || !ViewerOptions.VIEWS.contains(view));
    if (screenshot != null && preset == null || views && screenshot == null || badVariations || badDetail
            || badCloseup || badCompare || wireframe && screenshot == null) {
      throw new IllegalArgumentException(USAGE);
    }
    return new ViewerOptions(preset, seed, screenshot, assets, realistic, views, variations, highDetail, closeup,
            view == null ? 0 : ViewerOptions.VIEWS.indexOf(view), wireframe, compare);
  }

  /**
   * The four closeup fractions, each from 0 to 1, with bottom below top and left before right.
   *
   * @param text {@code bottom,top,left,right}.
   */
  private static double[] closeup(String text) {
    String[] parts = text.split(",", -1);
    if (parts.length != 4) {
      throw new IllegalArgumentException(USAGE);
    }
    double[] region = new double[4];
    for (int i = 0; i < 4; i++) {
      region[i] = Double.parseDouble(parts[i].trim());
      if (!(region[i] >= 0 && region[i] <= 1)) {
        throw new IllegalArgumentException(USAGE);
      }
    }
    if (region[0] >= region[1] || region[2] >= region[3]) {
      throw new IllegalArgumentException(USAGE);
    }
    return region;
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
