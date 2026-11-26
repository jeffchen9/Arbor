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

import com.jeffch.arbor.pipeline.GeneratedTree;
import com.jeffch.arbor.preset.SpeciesPreset;
import com.jeffch.arbor.shape.ShapeClass;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * Generates and meshes trees off the render thread, keeping only the newest request.
 */
public final class GenerationWorker implements AutoCloseable {
  private final BiFunction<SpeciesPreset, Long, GeneratedTree> builder;
  private final Object lock = new Object();
  private final AtomicReference<GenerationResult> ready = new AtomicReference<>();
  private final Thread thread;
  private volatile TexturedMesher texturedMesher;
  private Request pending;
  private long newestId;
  private boolean closed;
  private boolean running;

  /**
   * {@code texturedMesher} may be null, in which case results carry no textured mesh.
   *
   * @param builder        Makes a tree from a preset and seed.
   * @param texturedMesher Builds textured meshes, or null for none.
   */
  public GenerationWorker(BiFunction<SpeciesPreset, Long, GeneratedTree> builder, TexturedMesher texturedMesher) {
    this.builder = builder;
    this.texturedMesher = texturedMesher;
    thread = new Thread(this::run, "tree-generation");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Asks for a tree. A newer request replaces any that has not started, and interrupts one that is running. Returns the
   * request's id.
   *
   * @param preset Preset to generate.
   * @param seed   Tree seed.
   */
  public long request(SpeciesPreset preset, long seed) {
    synchronized (lock) {
      newestId++;
      pending = new Request(newestId, preset, seed);
      if (running) {
        thread.interrupt();
      }
      lock.notifyAll();
      return newestId;
    }
  }

  /**
   * Meshes later trees with new textures, such as after the user picks a Minecraft jar.
   *
   * @param mesher Builds textured meshes, or null for none.
   */
  public void useTexturedMesher(TexturedMesher mesher) {
    texturedMesher = mesher;
  }

  /**
   * The newest finished result, taken so it is returned only once.
   */
  public Optional<GenerationResult> pollLatest() {
    return Optional.ofNullable(ready.getAndSet(null));
  }

  /**
   * The background thread: waits for a request, generates it, and keeps only results for the newest request.
   */
  private void run() {
    while (true) {
      Request request;
      synchronized (lock) {
        while (pending == null && !closed) {
          try {
            lock.wait();
          } catch (InterruptedException e) {
            return;
          }
        }
        if (closed) {
          return;
        }
        request = pending;
        pending = null;
        running = true;
      }
      GenerationResult result = generate(request);
      synchronized (lock) {
        running = false;
        Thread.interrupted();
        if (request.id() == newestId) {
          ready.set(result);
        }
      }
    }
  }

  /**
   * Generates and meshes one tree, turning any failure into a readable error.
   *
   * @param request Request to generate.
   */
  private GenerationResult generate(Request request) {
    try {
      GeneratedTree tree = builder.apply(request.preset(), request.seed());
      TexturedMesher mesher = texturedMesher;
      TexturedMesher.Result textured = mesher == null ? null : mesher.mesh(tree.shapes(), tree.blocks());
      return new GenerationResult(request.id(), tree, ShapeMesher.mesh(tree.shapes()), textured, null,
              request.preset());
    } catch (OutOfMemoryError e) {
      return new GenerationResult(request.id(), null, null, null,
              "preset needs too much memory; reduce counts, lengths or sizes", null);
    } catch (RuntimeException e) {
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      return new GenerationResult(request.id(), null, null, null, message, null);
    }
  }

  /**
   * Stops the background thread.
   */
  @Override
  public void close() {
    synchronized (lock) {
      closed = true;
      thread.interrupt();
      lock.notifyAll();
    }
  }

  /**
   * A finished request: a tree and its mesh, or an error message.
   *
   * @param requestId Id of the request this answers.
   * @param tree      The generated tree, or null on error.
   * @param mesh      Flat-color mesh for each shape class.
   * @param textured  Textured meshes, or null without assets.
   * @param error     Error message, or null on success.
   * @param preset    Preset the tree was generated from, or null on error.
   */
  public record GenerationResult(long requestId, GeneratedTree tree, Map<ShapeClass, float[]> mesh,
                                 TexturedMesher.Result textured, String error, SpeciesPreset preset) {
    /**
     * Whether a tree was made.
     */
    public boolean isSuccess() {
      return error == null;
    }
  }

  /**
   * One tree to generate.
   *
   * @param id     Increasing id, so stale results can be ignored.
   * @param preset Preset to generate from.
   * @param seed   Seed to generate.
   */
  private record Request(long id, SpeciesPreset preset, long seed) {
  }
}
