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

/**
 * One baked quad of a block model, in block space (0..1).
 *
 * @param positions 12 floats (4 vertices), in UV corner order.
 * @param uvs       8 floats (4 vertices), from 0 to 1.
 * @param normal    3 floats.
 * @param texture   Texture reference without namespace, such as block/oak_log.
 * @param tintIndex Tint index, or -1 if none.
 * @param cull      Face that hides this quad when covered by a neighbor, or null.
 * @param shade     Whether directional shading applies.
 */
public record ModelQuad(float[] positions, float[] uvs, float[] normal, String texture,
                        int tintIndex, Direction3 cull, boolean shade) {

  /**
   * All six block directions (shape.Direction has only the horizontal four).
   */
  public enum Direction3 {DOWN, UP, NORTH, SOUTH, WEST, EAST}
}
