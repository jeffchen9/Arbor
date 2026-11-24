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

import com.jeffch.arbor.shape.*;

import java.util.ArrayList;
import java.util.List;

/**
 * True block-space geometry for each shape, in sixteenths of a block.
 */
public final class ShapeGeometry {
  private static final int LOW_WALL = 14;
  private static final int TALL_WALL = 16;
  private static final int IN_WALL_DROP = 3;
  private static final int DEAD_BUSH_HEIGHT = 13;
  private static final int SAPLING_HEIGHT = 15;
  private static final int SINGLE_PLANT_HEIGHT = 13;
  private static final int LOWER_PLANT_HEIGHT = 16;
  private static final int UPPER_PLANT_HEIGHT = 12;

  private ShapeGeometry() {
  }

  /**
   * The boxes that draw a shape, matching Minecraft's block models closely enough to judge the tree's look.
   *
   * @param shape Shape to draw.
   */
  public static List<Box> boxes(Shape shape) {
    return switch (shape) {
      case Shape.DeadBush bush -> crossedPlant(DEAD_BUSH_HEIGHT);
      case Shape.Sapling sapling -> crossedPlant(SAPLING_HEIGHT);
      case Shape.Leaves leaves -> List.of(new Box(0, 0, 0, 16, 16, 16));
      case Shape.Plant plant -> plant.half() == PlantHalf.FULL ? List.of(new Box(0, 0, 0, 16, 16, 16))
              : tuft(switch (plant.half()) {
        case LOWER -> LOWER_PLANT_HEIGHT;
        case UPPER -> UPPER_PLANT_HEIGHT;
        default -> SINGLE_PLANT_HEIGHT;
      });
      case Shape.Slab slab -> List.of(slab.half() == Half.BOTTOM
              ? new Box(0, 0, 0, 16, 8, 16) : new Box(0, 8, 0, 16, 16, 16));
      case Shape.Stair stair -> stair(stair);
      case Shape.Button button -> List.of(button(button));
      case Shape.FullBlock full -> List.of(new Box(0, 0, 0, 16, 16, 16));
      case Shape.Fence fence -> fence(fence);
      case Shape.Wall wall -> wall(wall);
      case Shape.FenceGate gate -> gate(gate);
    };
  }

  /**
   * A half-block base plus one to three raised quarter columns.
   *
   * @param stair The stair.
   */
  private static List<Box> stair(Shape.Stair stair) {
    boolean bottom = stair.half() == Half.BOTTOM;
    List<Box> boxes = new ArrayList<>(4);
    boxes.add(bottom ? new Box(0, 0, 0, 16, 8, 16) : new Box(0, 8, 0, 16, 16, 16));
    int stepMin = bottom ? 8 : 0;
    int stepMax = stepMin + 8;
    // Quarters for a north-facing stair, as {x, z}; the back row is at z = 0.
    int[][] quarters = switch (stair.form()) {
      case STRAIGHT -> new int[][]{{0, 0}, {8, 0}};
      case OUTER_LEFT -> new int[][]{{0, 0}};
      case OUTER_RIGHT -> new int[][]{{8, 0}};
      case INNER_LEFT -> new int[][]{{0, 0}, {8, 0}, {0, 8}};
      case INNER_RIGHT -> new int[][]{{0, 0}, {8, 0}, {8, 8}};
    };
    int turns = stair.facing().ordinal();
    for (int[] quarter : quarters) {
      int x = quarter[0];
      int z = quarter[1];
      for (int i = 0; i < turns; i++) {
        int rotated = 8 - z;
        z = x;
        x = rotated;
      }
      boxes.add(new Box(x, stepMin, z, x + 8, stepMax, z + 8));
    }
    return boxes;
  }

  /**
   * A button on the floor, wall or ceiling, turned to face away from its support.
   *
   * @param button The button.
   */
  private static Box button(Shape.Button button) {
    Box south = switch (button.face()) {
      case WALL -> new Box(5, 6, 0, 11, 10, 2);
      case FLOOR -> new Box(5, 0, 6, 11, 2, 10);
      case CEILING -> new Box(5, 14, 6, 11, 16, 10);
    };
    return rotate(south, button.facing());
  }

  /**
   * An X of thin stepped posts, approximating the crossed-plane plant model.
   *
   * @param height Height in sixteenths.
   */
  private static List<Box> crossedPlant(int height) {
    List<Box> boxes = new ArrayList<>(16);
    for (int i = 0; i < 8; i++) {
      int low = i * 2;
      boxes.add(new Box(low, 0, low, low + 2, height, low + 2));
      boxes.add(new Box(14 - low, 0, low, 16 - low, height, low + 2));
    }
    return boxes;
  }

  /**
   * A lighter X of eight posts, because a tree can hold thousands of these plants.
   *
   * @param height Height in sixteenths.
   */
  private static List<Box> tuft(int height) {
    List<Box> boxes = new ArrayList<>(8);
    for (int low = 1; low < 16; low += 4) {
      boxes.add(new Box(low, 0, low, low + 2, height, low + 2));
      boxes.add(new Box(14 - low, 0, low, 16 - low, height, low + 2));
    }
    return boxes;
  }

  /**
   * A center post with two rails toward each connected side.
   *
   * @param fence The fence.
   */
  private static List<Box> fence(Shape.Fence fence) {
    List<Box> boxes = new ArrayList<>();
    boxes.add(new Box(6, 0, 6, 10, 16, 10));
    for (int[] rail : new int[][]{{6, 9}, {12, 15}}) {
      if (fence.north()) {
        boxes.add(new Box(7, rail[0], 0, 9, rail[1], 6));
      }
      if (fence.south()) {
        boxes.add(new Box(7, rail[0], 10, 9, rail[1], 16));
      }
      if (fence.west()) {
        boxes.add(new Box(0, rail[0], 7, 6, rail[1], 9));
      }
      if (fence.east()) {
        boxes.add(new Box(10, rail[0], 7, 16, rail[1], 9));
      }
    }
    return boxes;
  }

  /**
   * An optional post with a low or tall arm toward each connected side.
   *
   * @param wall The wall.
   */
  private static List<Box> wall(Shape.Wall wall) {
    List<Box> boxes = new ArrayList<>();
    if (wall.post()) {
      boxes.add(new Box(4, 0, 4, 12, 16, 12));
    }
    if (wall.north() != WallSide.NONE) {
      boxes.add(new Box(5, 0, 0, 11, height(wall.north()), 8));
    }
    if (wall.south() != WallSide.NONE) {
      boxes.add(new Box(5, 0, 8, 11, height(wall.south()), 16));
    }
    if (wall.west() != WallSide.NONE) {
      boxes.add(new Box(0, 0, 5, 8, height(wall.west()), 11));
    }
    if (wall.east() != WallSide.NONE) {
      boxes.add(new Box(8, 0, 5, 16, height(wall.east()), 11));
    }
    return boxes;
  }

  /**
   * The height in sixteenths of a low or tall wall arm.
   *
   * @param side The wall arm.
   */
  private static int height(WallSide side) {
    return side == WallSide.TALL ? TALL_WALL : LOW_WALL;
  }

  /**
   * A gate drawn facing south, lowered when in a wall, then turned to its facing.
   *
   * @param gate The gate.
   */
  private static List<Box> gate(Shape.FenceGate gate) {
    List<Box> south = gate.open() ? openGateFacingSouth() : closedGate();
    int drop = gate.inWall() ? IN_WALL_DROP : 0;
    List<Box> boxes = new ArrayList<>(south.size());
    for (Box box : south) {
      boxes.add(rotate(new Box(box.minX(), box.minY() - drop, box.minZ(), box.maxX(), box.maxY() - drop, box.maxZ()),
              gate.facing()));
    }
    return boxes;
  }

  /**
   * A closed gate facing south: two end posts and the rails between them.
   */
  private static List<Box> closedGate() {
    return List.of(
            new Box(0, 5, 7, 2, 16, 9),
            new Box(14, 5, 7, 16, 16, 9),
            new Box(6, 6, 7, 8, 15, 9),
            new Box(8, 6, 7, 10, 15, 9),
            new Box(2, 6, 7, 6, 9, 9),
            new Box(2, 12, 7, 6, 15, 9),
            new Box(10, 6, 7, 14, 9, 9),
            new Box(10, 12, 7, 14, 15, 9));
  }

  /**
   * An open gate facing south: two end posts with the doors swung back against them.
   */
  private static List<Box> openGateFacingSouth() {
    return List.of(
            new Box(0, 5, 7, 2, 16, 9),
            new Box(14, 5, 7, 16, 16, 9),
            new Box(0, 6, 13, 2, 15, 15),
            new Box(14, 6, 13, 16, 15, 15),
            new Box(0, 6, 9, 2, 9, 13),
            new Box(0, 12, 9, 2, 15, 13),
            new Box(14, 6, 9, 16, 9, 13),
            new Box(14, 12, 9, 16, 15, 13));
  }

  /**
   * A box turned from facing south to another facing, around the block's center.
   *
   * @param box    Box facing south.
   * @param facing Facing to turn to.
   */
  private static Box rotate(Box box, Direction facing) {
    return switch (facing) {
      case SOUTH -> box;
      case NORTH -> new Box(16 - box.maxX(), box.minY(), 16 - box.maxZ(), 16 - box.minX(), box.maxY(), 16 - box.minZ());
      case EAST -> new Box(box.minZ(), box.minY(), 16 - box.maxX(), box.maxZ(), box.maxY(), 16 - box.minX());
      case WEST -> new Box(16 - box.maxZ(), box.minY(), box.minX(), 16 - box.minZ(), box.maxY(), box.maxX());
    };
  }

  /**
   * An axis-aligned box inside one block, in sixteenths of a block.
   *
   * @param minX Low x edge.
   * @param minY Low y edge.
   * @param minZ Low z edge.
   * @param maxX High x edge.
   * @param maxY High y edge.
   * @param maxZ High z edge.
   */
  public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
  }
}
