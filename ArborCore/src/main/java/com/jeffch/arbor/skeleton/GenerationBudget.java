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

package com.jeffch.arbor.skeleton;

/**
 * Limits total generation work and stops promptly when the running thread is cancelled.
 */
public final class GenerationBudget {
  /**
   * Total work allowed for one tree, in rough units of voxels or samples visited.
   */
  public static final long MAX_WORK = 400_000_000L;
  /**
   * Farthest a branch may reach from the origin, in blocks.
   */
  public static final double WORLD_LIMIT = 4096;

  private long used;

  /**
   * Records work about to be done. Called inside long loops, so it is also where a replaced generation notices it
   * should stop.
   *
   * @param units Amount of work.
   * @throws CancelledException       When the thread was interrupted.
   * @throws GenerationLimitException When the work would exceed {@link #MAX_WORK}.
   */
  public void charge(double units) {
    if (Thread.currentThread().isInterrupted()) {
      throw new CancelledException();
    }
    // Written as "not within" so a NaN charge also fails.
    if (!(units <= MAX_WORK - used)) {
      throw new GenerationLimitException("tree needs too much generation work; reduce counts, lengths or sizes");
    }
    used += (long) Math.ceil(units);
  }

  /**
   * Thrown inside a generation that a newer request has replaced.
   */
  public static final class CancelledException extends RuntimeException {
    public CancelledException() {
      super("generation was replaced by a newer request");
    }
  }
}
