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

import com.jeffch.arbor.math.Vec3;

/**
 * A named force that bends growing branches.
 *
 * @param name     Name that levels use to scale their response to this force.
 * @param type     How the force pushes: in one direction, toward a point, around an axis, or as wind.
 * @param vector   Push direction for a direction or wind force, or the spin axis for a curl force.
 * @param position World point an attractor force pulls toward.
 * @param strength Bend added per block grown; negative values reverse the force.
 * @param falloff  Weakening with distance; strength is divided by 1 + falloff times distance.
 * @param wind     Wind behavior, or null for any other force type.
 */
public record ForceSettings(String name, Type type, Vec3 vector, Vec3 position, double strength, double falloff,
                            Wind wind) {
  /**
   * How a force pushes: one fixed way, toward a point, around an axis, or as height-dependent gusty wind.
   */
  public enum Type {DIRECTION, ATTRACTOR, CURL, WIND}

  /**
   * How a wind force varies across a tree.
   *
   * @param directionVariance Largest random turn of the wind's heading per tree, in degrees.
   * @param gust              How much gusts raise and lower the wind through the tree, from 0 to 1.
   * @param gustSize          Typical size of a gust, in blocks.
   * @param heightPower       How fast wind grows from ground to tree top; 0 is even, 1 is linear.
   * @param flexibility       Extra bend on thin branches; a branch 1 block in radius or thicker gets none.
   */
  public record Wind(double directionVariance, double gust, double gustSize, double heightPower, double flexibility) {
  }
}
