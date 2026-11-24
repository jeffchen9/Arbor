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

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Reports preset saves, watching the folder so editors that replace the file are still seen.
 */
public final class PresetWatcher implements AutoCloseable {
  private static final long DEBOUNCE_NANOS = TimeUnit.MILLISECONDS.toNanos(150);
  private static final long POLL_MILLIS = 25;

  private final Path fileName;
  private final Runnable onChange;
  private final WatchService service;

  /**
   * Watches the file's folder on a background thread and calls {@code onChange} when the file is created, changed or
   * deleted.
   *
   * @param file     File to watch.
   * @param onChange Runs when the file changes.
   */
  public PresetWatcher(Path file, Runnable onChange) throws IOException {
    Path absolute = file.toAbsolutePath().normalize();
    fileName = absolute.getFileName();
    this.onChange = onChange;
    service = FileSystems.getDefault().newWatchService();
    absolute.getParent().register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
    Thread thread = new Thread(this::run, "preset-watcher");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Waits for folder events and reports the ones about the watched file, until closed.
   */
  private void run() {
    long lastChange = -1;
    try {
      while (true) {
        WatchKey key = service.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
        if (key != null) {
          for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == OVERFLOW || fileName.equals(event.context())) {
              lastChange = System.nanoTime();
            }
          }
          if (!key.reset()) {
            return;
          }
        }
        if (lastChange >= 0 && System.nanoTime() - lastChange >= DEBOUNCE_NANOS) {
          lastChange = -1;
          onChange.run();
        }
      }
    } catch (InterruptedException | ClosedWatchServiceException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Stops watching.
   */
  @Override
  public void close() throws IOException {
    service.close();
  }
}
