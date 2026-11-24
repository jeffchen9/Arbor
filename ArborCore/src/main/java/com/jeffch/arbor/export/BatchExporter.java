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

import com.jeffch.arbor.pipeline.GeneratedTree;
import com.jeffch.arbor.pipeline.TreeGenerator;
import com.jeffch.arbor.preset.SpeciesPreset;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.stream.LongStream;

/**
 * Generates trees from random seeds, on all CPU cores, and saves each as a new .schem file.
 */
public final class BatchExporter {
  private BatchExporter() {
  }

  /**
   * Where a preset's trees go unless told otherwise.
   *
   * @param preset Preset being exported.
   */
  public static Path defaultFolder(SpeciesPreset preset) {
    return Path.of("exports", defaultName(preset));
  }

  /**
   * Exports up to {@code count} trees and returns how many were written. Existing files are never replaced.
   *
   * @param preset      Preset to export.
   * @param count       Number of trees wanted.
   * @param folder      Folder to write into.
   * @param dataVersion Minecraft data version written into each file.
   * @param progress    Receives the number of trees finished so far; may be called from several threads.
   * @param cancelled   Checked before each tree; when true, no further trees are started.
   */
  public static int export(SpeciesPreset preset, int count, Path folder, int dataVersion, IntConsumer progress,
                           BooleanSupplier cancelled) throws IOException {
    return export(preset, count, folder, defaultName(preset), dataVersion, progress, cancelled);
  }

  /**
   * Exports as {@code name_0001.schem} and so on, skipping numbers already used in the folder.
   *
   * @param preset      Preset to export.
   * @param count       Number of trees wanted.
   * @param folder      Folder to write into.
   * @param fileName    Base file name, cleaned with {@link #safeFileName}; it must not clean to nothing.
   * @param dataVersion Minecraft data version written into each file.
   * @param progress    Receives the number of trees finished so far; may be called from several threads.
   * @param cancelled   Checked before each tree; when true, no further trees are started.
   */
  public static int export(SpeciesPreset preset, int count, Path folder, String fileName, int dataVersion,
                           IntConsumer progress, BooleanSupplier cancelled) throws IOException {
    String name = safeFileName(fileName);
    if (name.isEmpty()) {
      throw new IllegalArgumentException("export name is empty");
    }
    Files.createDirectories(folder);
    // Distinct random seeds, so no two files in one batch hold the same tree.
    Set<Long> seeds = new LinkedHashSet<>();
    while (seeds.size() < count) {
      seeds.add(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE));
    }
    AtomicInteger number = new AtomicInteger();
    AtomicInteger written = new AtomicInteger();
    AtomicBoolean failed = new AtomicBoolean();
    try {
      // Each seed is generated and saved on whichever core is free. After a failure or a cancel, the trees still
      // queued are skipped, while ones already running finish normally.
      LongStream.of(seeds.stream().mapToLong(Long::longValue).toArray()).parallel().forEach(seed -> {
        if (failed.get() || cancelled.getAsBoolean()) {
          return;
        }
        try {
          GeneratedTree tree = TreeGenerator.generate(preset, seed);
          save(tree, name, folder, number, dataVersion);
          progress.accept(written.incrementAndGet());
        } catch (IOException e) {
          failed.set(true);
          throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
          failed.set(true);
          throw e;
        }
      });
    } catch (UncheckedIOException e) {
      // A stream cannot throw checked exceptions, so the file error was wrapped above; hand back the original.
      throw e.getCause();
    }
    return written.get();
  }

  /**
   * Writes one tree under the next free number. Creating the file only when it does not exist yet means two threads,
   * or a file left by an earlier export, can never be overwritten. A half-written file is deleted on failure.
   *
   * @param tree        Tree to write.
   * @param name        File name before the number.
   * @param folder      Folder to write into.
   * @param number      Next number to try, shared between threads.
   * @param dataVersion Minecraft data version written into the file.
   */
  private static void save(GeneratedTree tree, String name, Path folder, AtomicInteger number,
                           int dataVersion) throws IOException {
    while (true) {
      String label = String.format(Locale.ROOT, "%s_%04d", name, number.incrementAndGet());
      Path file = folder.resolve(label + ".schem");
      OutputStream raw;
      try {
        raw = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      } catch (FileAlreadyExistsException e) {
        // That number is taken; try the next one.
        continue;
      }
      try (OutputStream out = new BufferedOutputStream(raw)) {
        SchematicWriter.write(out, tree, label, dataVersion);
      } catch (IOException | RuntimeException e) {
        Files.deleteIfExists(file);
        throw e;
      }
      return;
    }
  }

  /**
   * Where a folder dialog opens for typed text: that path, or the working folder when it is blank or invalid.
   *
   * @param typed Folder path as typed.
   */
  public static Path browseStart(String typed) {
    try {
      return Path.of(typed.isBlank() ? "." : typed.trim()).toAbsolutePath();
    } catch (InvalidPathException e) {
      return Path.of(".").toAbsolutePath();
    }
  }

  /**
   * The file name a preset's trees use unless told otherwise.
   *
   * @param preset Preset being exported.
   */
  public static String defaultName(SpeciesPreset preset) {
    String cleaned = safeFileName(preset.name());
    return cleaned.isEmpty() ? "tree" : cleaned;
  }

  /**
   * A name that is safe in any folder: other characters become underscores and edge dots and underscores go.
   * The result is empty when nothing usable remains.
   *
   * @param name Name to clean up.
   */
  public static String safeFileName(String name) {
    return name.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^[._]+|[._]+$", "");
  }
}
