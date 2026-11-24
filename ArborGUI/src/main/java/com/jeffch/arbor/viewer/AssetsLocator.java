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

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Finds a folder holding the Minecraft assets: the explicit choice, then the environment, then the installed game,
 * then a sibling folder.
 */
final class AssetsLocator {
  static final String ENVIRONMENT_VARIABLE = "ARBOR_ASSETS";
  static final String SIBLING = "Minecraft textures";

  private AssetsLocator() {
  }

  /**
   * {@code installed} is only called when no explicit or environment folder is usable.
   *
   * @param explicit         Folder given on the command line, or null.
   * @param environmentValue Value of the {@code ARBOR_ASSETS} environment variable, or null.
   * @param workingDirectory Folder whose sibling assets folder is tried last, or null.
   * @param installed        Finds the assets of the installed game.
   */
  static Optional<Path> find(Path explicit, String environmentValue, Path workingDirectory,
                             Supplier<Optional<Path>> installed) {
    if (valid(explicit)) {
      return Optional.of(explicit);
    }
    if (environmentValue != null && !environmentValue.isBlank()) {
      try {
        Path fromEnvironment = Path.of(environmentValue.strip());
        if (valid(fromEnvironment)) {
          return Optional.of(fromEnvironment);
        }
      } catch (InvalidPathException ignored) {
        // an unusable environment value is the same as none
      }
    }
    Optional<Path> game = installed.get().filter(AssetsLocator::valid);
    if (game.isPresent()) {
      return game;
    }
    if (workingDirectory != null) {
      Path sibling = workingDirectory.resolve("..").resolve(SIBLING).normalize();
      if (valid(sibling)) {
        return Optional.of(sibling);
      }
    }
    return Optional.empty();
  }

  private static boolean valid(Path folder) {
    return folder != null && Files.isDirectory(folder.resolve("assets").resolve("minecraft").resolve("blockstates"));
  }
}
