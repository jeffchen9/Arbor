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

package com.jeffch.arbor;

import com.jeffch.arbor.export.BatchExporter;
import com.jeffch.arbor.export.ExportOptions;
import com.jeffch.arbor.preset.PresetException;
import com.jeffch.arbor.preset.PresetLoader;
import com.jeffch.arbor.preset.SpeciesPreset;
import com.jeffch.arbor.skeleton.GenerationLimitException;
import com.jeffch.arbor.viewer.ViewerApp;
import com.jeffch.arbor.viewer.ViewerCli;
import com.jeffch.arbor.viewer.ViewerOptions;

import java.awt.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Entry point to program.
 */
public final class Main {
  /**
   * Exit code for bad arguments, an invalid preset, or a tree that could not be generated.
   */
  private static final int BAD_INPUT = 2;

  /**
   * Prevent instantiation
   */
  private Main() {
  }

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  /**
   * Runs a command and returns its exit code instead of exiting, so tests can call it. Problems are printed as one
   * readable line, never a stack trace.
   *
   * @param args command line arguments.
   * @param out  output stream.
   * @param err  error stream.
   */
  public static int run(String[] args, PrintStream out, PrintStream err) {
    if (args.length > 0 && args[0].equals("export")) {
      return export(Arrays.copyOfRange(args, 1, args.length), out, err);
    }
    if (args.length == 1 && args[0].equals("mcp")) {
      // Only MCP messages may reach the client, so anything else printed goes to the error stream.
      System.setOut(err);
      com.jeffch.arbor.viewer.ArborMcp.serve(System.in, out);
      return 0;
    }
    // "view" is optional before a preset or an option; any other first word is an unknown command.
    boolean namesView = args.length > 0 && args[0].equals("view");
    if (args.length > 0 && !namesView && !args[0].endsWith(".json") && !args[0].startsWith("--")) {
      err.println(ViewerCli.USAGE);
      return BAD_INPUT;
    }
    ViewerOptions options;
    try {
      options = ViewerCli.parse(Arrays.copyOfRange(args, namesView ? 1 : 0, args.length));
    } catch (IllegalArgumentException e) {
      err.println(e.getMessage());
      return BAD_INPUT;
    }
    if (options.screenshot() == null && GraphicsEnvironment.isHeadless()) {
      err.println("no display for the editor; use --screenshot or export. " + ViewerCli.USAGE);
      return BAD_INPUT;
    }
    try {
      return ViewerApp.run(options, out, err);
    } catch (PresetException | GenerationLimitException e) {
      err.println(e.getMessage());
      return BAD_INPUT;
    } catch (OutOfMemoryError e) {
      err.println(options.preset() + ": tree needs too much memory; reduce counts, lengths or sizes");
      return BAD_INPUT;
    } catch (RuntimeException e) {
      err.println(options.preset() + ": cannot generate tree: " + e.getMessage());
      return BAD_INPUT;
    }
  }

  /**
   * Exports a batch of trees, printing progress about every tenth of the way.
   *
   * @param args Command line arguments after {@code export}.
   * @param out  Receives progress.
   * @param err  Receives errors.
   */
  private static int export(String[] args, PrintStream out, PrintStream err) {
    ExportOptions options;
    try {
      options = ExportOptions.parse(args);
    } catch (IllegalArgumentException e) {
      err.println(e.getMessage());
      return BAD_INPUT;
    }
    try {
      SpeciesPreset preset = PresetLoader.load(options.preset());
      Path folder = options.folder() != null ? options.folder() : BatchExporter.defaultFolder(preset);
      int step = Math.max(1, options.count() / 10);
      int written = BatchExporter.export(preset, options.count(), folder, options.dataVersion(), done -> {
        if (done % step == 0 && done < options.count()) {
          out.println(done + " of " + options.count());
        }
      }, () -> false);
      out.println("wrote " + written + " trees to " + folder.toAbsolutePath());
      return 0;
    } catch (PresetException | GenerationLimitException e) {
      err.println(e.getMessage());
    } catch (IOException e) {
      err.println(options.preset() + ": cannot export: " + e.getMessage());
    } catch (OutOfMemoryError e) {
      err.println(options.preset() + ": not enough memory; export fewer trees or give Java more memory");
    } catch (RuntimeException e) {
      err.println(options.preset() + ": cannot generate tree: " + e.getMessage());
    }
    return BAD_INPUT;
  }
}
