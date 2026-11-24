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

import java.util.ArrayList;
import java.util.List;

/**
 * Every shape configuration the generator can produce.
 */
public final class ShapeCatalog {
  private static final List<Shape> ALL = build();

  private ShapeCatalog() {
  }

  public static List<Shape> all() {
    return ALL;
  }

  private static List<Shape> build() {
    List<Shape> shapes = new ArrayList<>(261);
    shapes.add(new Shape.DeadBush());
    shapes.add(new Shape.Sapling());
    shapes.add(new Shape.Leaves());
    shapes.add(new Shape.Plant("short_grass", PlantHalf.SINGLE));
    shapes.add(new Shape.Plant("large_fern", PlantHalf.LOWER));
    shapes.add(new Shape.Plant("large_fern", PlantHalf.UPPER));
    for (Half half : Half.values()) {
      shapes.add(new Shape.Slab(half));
      for (Direction facing : Direction.values()) {
        for (StairForm form : StairForm.values()) {
          shapes.add(new Shape.Stair(facing, half, form));
        }
      }
    }
    for (ButtonFace face : ButtonFace.values()) {
      for (Direction facing : Direction.values()) {
        shapes.add(new Shape.Button(face, facing));
      }
    }
    for (GrainAxis axis : GrainAxis.values()) {
      shapes.add(new Shape.FullBlock(axis));
      if (axis != GrainAxis.NONE) {
        shapes.add(new Shape.FullBlock(axis, true));
      }
    }
    for (int mask = 0; mask < 16; mask++) {
      shapes.add(new Shape.Fence((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0));
    }
    for (Direction facing : Direction.values()) {
      for (boolean open : new boolean[]{false, true}) {
        for (boolean inWall : new boolean[]{false, true}) {
          shapes.add(new Shape.FenceGate(facing, open, inWall));
        }
      }
    }
    for (boolean post : new boolean[]{true, false}) {
      for (WallSide north : WallSide.values()) {
        for (WallSide east : WallSide.values()) {
          for (WallSide south : WallSide.values()) {
            for (WallSide west : WallSide.values()) {
              shapes.add(new Shape.Wall(post, north, east, south, west));
            }
          }
        }
      }
    }
    return List.copyOf(shapes);
  }
}
