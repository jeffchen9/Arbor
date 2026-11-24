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

package com.jeffch.arbor.shape;

/**
 * Horizontal directions using Minecraft axes: north is -z and east is +x.
 */
public enum Direction {
  NORTH(0, -1),
  EAST(1, 0),
  SOUTH(0, 1),
  WEST(-1, 0);

  private final int dx;
  private final int dz;

  /**
   * @param dx Step in x toward this side.
   * @param dz Step in z toward this side; north is negative z.
   */
  Direction(int dx, int dz) {
    this.dx = dx;
    this.dz = dz;
  }

  public int dx() {
    return dx;
  }

  public int dz() {
    return dz;
  }

  public Direction opposite() {
    return values()[(ordinal() + 2) % 4];
  }

  public Direction clockwise() {
    return values()[(ordinal() + 1) % 4];
  }

  public Direction counterClockwise() {
    return values()[(ordinal() + 3) % 4];
  }

  public boolean isXAxis() {
    return dx != 0;
  }
}
