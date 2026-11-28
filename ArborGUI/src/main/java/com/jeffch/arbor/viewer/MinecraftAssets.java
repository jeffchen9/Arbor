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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds the newest vanilla Minecraft installed on this computer and extracts its block assets to a per-user cache,
 * outside the project, so they are never part of the source or the built jar.
 */
final class MinecraftAssets {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String MINECRAFT = "assets/minecraft/";
  private static final List<String> NEEDED = List.of(MINECRAFT + "blockstates/", MINECRAFT + "models/block/",
          MINECRAFT + "textures/block/", MINECRAFT + "textures/colormap/");
  private static final String STAMP_FILE = ".source";

  private MinecraftAssets() {
  }

  /**
   * The assets of the newest version in this system's standard Minecraft install, or empty when there is none.
   */
  static Optional<Path> installed() {
    Path versions = versionsFolder();
    return versions == null ? Optional.empty() : latest(versions.getParent(), cacheFolder());
  }

  /**
   * The {@code versions} folder of the standard Minecraft install on this system, or null when it cannot be told.
   */
  static Path versionsFolder() {
    return versionsFolder(System.getProperty("os.name", ""), System.getenv("APPDATA"),
            System.getProperty("user.home"));
  }

  /**
   * Where the Minecraft launcher installs the game on each system: {@code %APPDATA%\.minecraft} on Windows,
   * {@code ~/Library/Application Support/minecraft} on macOS and {@code ~/.minecraft} elsewhere.
   *
   * @param os      Operating system name, as in the {@code os.name} property.
   * @param appData Value of {@code APPDATA}, or null.
   * @param home    The user's home folder, or null.
   * @return The {@code versions} folder, or null when the system does not say where it is.
   */
  static Path versionsFolder(String os, String appData, String home) {
    String name = os.toLowerCase(Locale.ROOT);
    if (name.startsWith("windows")) {
      return appData == null ? null : Path.of(appData, ".minecraft", "versions");
    }
    if (home == null) {
      return null;
    }
    return name.startsWith("mac") ? Path.of(home, "Library", "Application Support", "minecraft", "versions")
            : Path.of(home, ".minecraft", "versions");
  }

  /**
   * The per-user folder that holds extracted assets on this system, outside the project.
   */
  static Path cacheFolder() {
    return cacheFolder(System.getProperty("os.name", ""), System.getenv("LOCALAPPDATA"),
            System.getProperty("user.home"));
  }

  /**
   * The usual place for a program's cache on each system: {@code %LOCALAPPDATA%} on Windows, {@code ~/Library/Caches}
   * on macOS and {@code ~/.cache} elsewhere.
   *
   * @param os           Operating system name, as in the {@code os.name} property.
   * @param localAppData Value of {@code LOCALAPPDATA}, or null.
   * @param home         The user's home folder.
   * @return The folder for extracted assets.
   */
  static Path cacheFolder(String os, String localAppData, String home) {
    String name = os.toLowerCase(Locale.ROOT);
    if (name.startsWith("windows") && localAppData != null) {
      return Path.of(localAppData, "Arbor", "minecraft-assets");
    }
    return name.startsWith("mac") ? Path.of(home, "Library", "Caches", "Arbor", "minecraft-assets")
            : Path.of(home, ".cache", "arbor", "minecraft-assets");
  }

  /**
   * Extracts a game jar the user picked by hand, which may be in any folder.
   *
   * @param jar   The chosen jar.
   * @param cache Folder that holds extracted versions.
   * @return The extracted assets folder.
   * @throws IOException When the jar cannot be read or holds no block assets.
   */
  static Path fromJar(Path jar, Path cache) throws IOException {
    String name = jar.getFileName().toString();
    String id = name.toLowerCase(Locale.ROOT).endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
    return extract(new Version(id, jar, true, null), cache);
  }

  /**
   * The assets of a jar picked in an earlier session, or empty when none was picked or it can no longer be used.
   *
   * @param jar   Path of the remembered jar, or null.
   * @param cache Folder that holds extracted versions.
   */
  static Optional<Path> remembered(String jar, Path cache) {
    if (jar == null || jar.isBlank()) {
      return Optional.empty();
    }
    try {
      Path file = Path.of(jar);
      return Files.isRegularFile(file) ? Optional.of(fromJar(file, cache)) : Optional.empty();
    } catch (IOException | RuntimeException e) {
      // A moved or damaged jar is treated as no choice, so the automatic search runs instead.
      return Optional.empty();
    }
  }

  /**
   * Extracts the newest usable version into the cache, trying older ones if a jar cannot be read.
   *
   * @param minecraft The game's install folder.
   * @param cache     Folder that holds extracted versions.
   */
  static Optional<Path> latest(Path minecraft, Path cache) {
    for (Version version : versions(minecraft)) {
      try {
        return Optional.of(extract(version, cache));
      } catch (IOException | RuntimeException e) {
        // A damaged or unusual jar should not stop the viewer; the next newest version is tried.
      }
    }
    return Optional.empty();
  }

  /**
   * Vanilla versions that have a jar: releases first, then snapshots, each newest first.
   *
   * @param minecraft The game's install folder.
   */
  private static List<Version> versions(Path minecraft) {
    List<Version> found = new ArrayList<>();
    Path folder = minecraft.resolve("versions");
    if (!Files.isDirectory(folder)) {
      return found;
    }
    try (Stream<Path> entries = Files.list(folder)) {
      entries.forEach(entry -> read(entry).ifPresent(found::add));
    } catch (IOException e) {
      return found;
    }
    found.sort(Comparator.comparing(Version::release).reversed()
            .thenComparing(Version::released, Comparator.reverseOrder()));
    return found;
  }

  /**
   * Modded profiles inherit from a vanilla version and carry later dates, so they are skipped.
   *
   * @param folder A version's folder.
   */
  private static Optional<Version> read(Path folder) {
    String id = folder.getFileName().toString();
    Path jar = folder.resolve(id + ".jar");
    Path description = folder.resolve(id + ".json");
    if (!Files.isRegularFile(jar) || !Files.isRegularFile(description)) {
      return Optional.empty();
    }
    try {
      JsonNode json = JSON.readTree(description.toFile());
      if (json.hasNonNull("inheritsFrom")) {
        return Optional.empty();
      }
      String type = json.path("type").asString();
      if (!type.equals("release") && !type.equals("snapshot")) {
        return Optional.empty();
      }
      OffsetDateTime released = OffsetDateTime.parse(json.path("releaseTime").asString());
      return Optional.of(new Version(id, jar, type.equals("release"), released));
    } catch (tools.jackson.core.JacksonException | DateTimeParseException e) {
      // Jackson 3 reports unreadable and broken files alike as JacksonException.
      return Optional.empty();
    }
  }

  /**
   * Copies a version's block models, block states and textures from its jar into the cache. A folder already stamped
   * with this jar is reused; a stale one is replaced. The copy is made in a temporary folder and moved into place in
   * one step, so a half-finished extraction is never used.
   *
   * @param version Version to extract.
   * @param cache   Folder that holds extracted versions.
   */
  private static Path extract(Version version, Path cache) throws IOException {
    Path target = cache.resolve(version.id());
    String stamp = Files.size(version.jar()) + ":" + Files.getLastModifiedTime(version.jar()).toMillis();
    if (current(target, stamp)) {
      return target;
    }
    Files.createDirectories(cache);
    Path temporary = Files.createTempDirectory(cache, version.id() + ".");
    try {
      copyNeeded(version.jar(), temporary);
      Files.writeString(temporary.resolve(STAMP_FILE), stamp);
      deleteTree(target);
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      return target;
    } catch (IOException e) {
      // Another copy of the program may have finished the same extraction first.
      if (current(target, stamp)) {
        return target;
      }
      throw e;
    } finally {
      deleteTree(temporary);
    }
  }

  /**
   * Whether a cache folder was extracted from this exact jar.
   *
   * @param target Cache folder to check.
   * @param stamp  Size and date of the jar.
   */
  private static boolean current(Path target, String stamp) throws IOException {
    Path file = target.resolve(STAMP_FILE);
    return Files.isRegularFile(file) && Files.readString(file).equals(stamp);
  }

  /**
   * Copies only the asset folders the viewer reads out of the jar, skipping any entry whose path would escape the
   * destination. Fails when the jar has no block states, since it is then not a usable game jar.
   *
   * @param jar         Game jar.
   * @param destination Folder to copy into.
   */
  private static void copyNeeded(Path jar, Path destination) throws IOException {
    Path root = destination.toAbsolutePath().normalize();
    boolean hasBlockStates = false;
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (entry.isDirectory() || NEEDED.stream().noneMatch(name::startsWith)) {
          continue;
        }
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root.resolve(MINECRAFT))) {
          continue;
        }
        Files.createDirectories(file.getParent());
        try (InputStream in = zip.getInputStream(entry)) {
          Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
        hasBlockStates |= name.startsWith(NEEDED.get(0));
      }
    }
    if (!hasBlockStates) {
      throw new IOException(jar + " has no block assets");
    }
  }

  /**
   * Deletes a folder and everything in it.
   *
   * @param folder Folder to delete.
   */
  private static void deleteTree(Path folder) throws IOException {
    if (!Files.exists(folder)) {
      return;
    }
    try (Stream<Path> files = Files.walk(folder)) {
      for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
  }

  /**
   * An installed game version with a client jar.
   *
   * @param id       Version name, which is also its folder name.
   * @param jar      The client jar.
   * @param release  Whether this is a full release rather than a snapshot.
   * @param released When Mojang released it, or null for a jar picked by hand.
   */
  private record Version(String id, Path jar, boolean release, OffsetDateTime released) {
  }
}
