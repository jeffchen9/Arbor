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

package com.jeffch.arbor.math;

import java.nio.charset.StandardCharsets;
import java.util.SplittableRandom;

/**
 * A deterministic random stream identified by a tree seed and a stable path.
 */
public final class TreeRandom {
  private static final long FNV_OFFSET = 0xcbf29ce484222325L;
  private static final long FNV_PRIME = 0x100000001b3L;

  private final SplittableRandom random;

  private TreeRandom(long streamSeed) {
    random = new SplittableRandom(streamSeed);
  }

  /**
   * A stream for one named decision, such as {@code "blocks/log"}. The path is hashed (FNV-1a) and mixed with the
   * seed, so each decision draws from its own sequence and adding a new one never shifts the others.
   *
   * @param seed Tree seed.
   * @param path Name of the decision.
   */
  public static TreeRandom forPath(long seed, String path) {
    long hash = FNV_OFFSET;
    for (byte value : path.getBytes(StandardCharsets.UTF_8)) {
      hash ^= value & 0xff;
      hash *= FNV_PRIME;
    }
    return new TreeRandom(hash ^ seed);
  }

  /**
   * Generate a uniform value in [0, 1).
   *
   * @return the uniform value.
   */
  public double nextDouble() {
    return random.nextDouble();
  }

  /**
   * Generate a uniform value in [min, max).
   *
   * @param min the minimum value.
   * @param max the maximum value.
   * @return the uniform value.
   */
  public double range(double min, double max) {
    return min + (max - min) * random.nextDouble();
  }

  /**
   * Generate a boolean with the given probability.
   *
   * @param probability the probability from 0 to 1.
   * @return {@code true} or {@code false}.
   */
  public boolean chance(double probability) {
    return random.nextDouble() < probability;
  }
}
