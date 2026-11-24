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

package com.jeffch.arbor.preset;

import java.util.List;

/**
 * One block or a weighted list of blocks for a role. An entry named {@link #OFF} switches the role off.
 *
 * @param entries The choices, in order.
 */
public record WeightedBlocks(List<Entry> entries) {
  public static final String OFF = "off";

  public WeightedBlocks {
    entries = List.copyOf(entries);
  }

  public static WeightedBlocks of(String block) {
    return new WeightedBlocks(List.of(new Entry(block, 1)));
  }

  /**
   * The block a roll in [0, 1) lands on, ignoring {@link #OFF}. Null when no block has weight.
   *
   * @param roll Random value from 0 to 1.
   */
  public String pickBlock(double roll) {
    double total = 0;
    for (Entry entry : entries) {
      if (entry.weight() > 0 && !entry.block().equals(OFF)) {
        total += entry.weight();
      }
    }
    double target = roll * total;
    double running = 0;
    String last = null;
    for (Entry entry : entries) {
      if (entry.weight() > 0 && !entry.block().equals(OFF)) {
        last = entry.block();
        running += entry.weight();
        if (target < running) {
          return last;
        }
      }
    }
    return last;
  }

  /**
   * The entry a roll in [0, 1) lands on. Entries with no weight are never chosen.
   *
   * @param roll Random value from 0 to 1.
   */
  public String pick(double roll) {
    double total = 0;
    for (Entry entry : entries) {
      total += entry.weight();
    }
    double target = roll * total;
    double running = 0;
    String last = entries.get(0).block();
    for (Entry entry : entries) {
      if (entry.weight() <= 0) {
        continue;
      }
      last = entry.block();
      running += entry.weight();
      if (target < running) {
        return entry.block();
      }
    }
    return last;
  }

  /**
   * One weighted block choice.
   *
   * @param block  Block name, optionally with a state such as wheat[age=7], or off.
   * @param weight Relative likelihood; 0 or less is never chosen.
   */
  public record Entry(String block, double weight) {
  }
}
