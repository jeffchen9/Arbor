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
 * A generic wood shape and its exact configuration, independent of any material.
 */
public sealed interface Shape permits Shape.FullBlock, Shape.Fence, Shape.FenceGate, Shape.Wall, Shape.DeadBush,
        Shape.Sapling, Shape.Slab, Shape.Stair, Shape.Button, Shape.Leaves, Shape.Plant {
  ShapeClass shapeClass();

  /**
   * A half-height block.
   *
   * @param half Whether the slab fills the top or bottom half.
   */
  record Slab(Half half) implements Shape {
    @Override
    public ShapeClass shapeClass() {
      return ShapeClass.SLAB;
    }
  }

  /**
   * A stair whose raised step is on its {@code facing} side.
   *
   * @param facing Side the raised step is on.
   * @param half   Whether the stair is upright (bottom) or upside down (top).
   * @param form   Straight, or one of the inner and outer corner forms.
   */
  record Stair(Direction facing, Half half, StairForm form) implements Shape {
    @Override
    public ShapeClass shapeClass() {
      return ShapeClass.STAIR;
    }
  }

  /**
   * A small knob, used for burls and broken stubs. {@code facing} points away from its support.
   *
   * @param face   Whether the button is on a floor, wall or ceiling.
   * @param facing Direction pointing away from its support.
   */
  record Button(ButtonFace face, Direction facing) implements Shape {
    @Override
    public ShapeClass shapeClass() {
      return ShapeClass.BUTTON;
    }
  }

  /**
   * A dense tangle of fine branches.
   */
  record DeadBush() implements Shape {
    @Override
    public ShapeClass shapeClass() {
      return ShapeClass.DEAD_BUSH;
    }
  }

  /**
   * A dense tangle of fine branches inside the leaves.
   */
  record Sapling() implements Shape {
    @Override
    public ShapeClass shapeClass() {
      return ShapeClass.SAPLING;
    }
  }

  /**
   * A full leaf block, for dense foliage.
   *
   * @param distance Steps to the nearest log, from 1 to 6, for a leaf that decays naturally; 0 when not tracked.
   */
  record Leaves(int distance) implements Shape {
    public Leaves() {
      this(0);
    }

    @Override
    public ShapeClass shapeClass() {
      return ShapeClass.LEAVES;
    }
  }

  /**
   * A plant block, or one half of a two-block plant. The block may carry a state such as {@code [age=3]}.
   *
   * @param block Block name, optionally with a state.
   * @param half  Whether this is a one-block plant, or the lower or upper half of a two-block plant.
   */
  record Plant(String block, PlantHalf half) implements Shape {
    @Override
    public ShapeClass shapeClass() {
      return half == PlantHalf.FULL ? ShapeClass.FULL : ShapeClass.PLANT;
    }
  }

  /**
   * A full block, six-sided bark by default. {@code cutEnd} shows the log's rings on a cut-off branch end.
   *
   * @param axis   Direction of the wood grain; NONE marks a block where different grains meet.
   * @param cutEnd Whether this block uses the ringed log to show a cut-off branch end.
   */
  record FullBlock(GrainAxis axis, boolean cutEnd) implements Shape {
    public FullBlock(GrainAxis axis) {
      this(axis, false);
    }

    @Override
    public ShapeClass shapeClass() {
      return ShapeClass.FULL;
    }
  }

  /**
   * A fence post with up to four arms.
   *
   * @param north Whether it has an arm to the north.
   * @param east  Whether it has an arm to the east.
   * @param south Whether it has an arm to the south.
   * @param west  Whether it has an arm to the west.
   */
  record Fence(boolean north, boolean east, boolean south, boolean west) implements Shape {
    @Override
    public ShapeClass shapeClass() {
      return ShapeClass.FENCE;
    }

    public Fence merged(Fence other) {
      return new Fence(north || other.north, east || other.east, south || other.south, west || other.west);
    }
  }

  /**
   * A fence gate.
   *
   * @param facing Direction the gate faces; its rails run perpendicular to this.
   * @param open   Whether the gate is open, which represents a split branch.
   * @param inWall Whether the gate uses the lowered in-wall state.
   */
  record FenceGate(Direction facing, boolean open, boolean inWall) implements Shape {
    @Override
    public ShapeClass shapeClass() {
      return ShapeClass.GATE;
    }
  }

  /**
   * A wall with a post and up to four arms.
   *
   * @param post  Whether the tall center post is shown.
   * @param north Arm height to the north, or NONE.
   * @param east  Arm height to the east, or NONE.
   * @param south Arm height to the south, or NONE.
   * @param west  Arm height to the west, or NONE.
   */
  record Wall(boolean post, WallSide north, WallSide east, WallSide south, WallSide west) implements Shape {
    @Override
    public ShapeClass shapeClass() {
      return ShapeClass.WALL;
    }
  }
}
