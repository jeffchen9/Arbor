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

/**
 * Limits branches to a crown shape.
 *
 * @param style          Whether a crown envelope is used.
 * @param crownBase      Height in blocks, before size scale, where the crown starts.
 * @param radius         Crown radius in blocks by normalized height, from the crown base (0) to the tree top (1).
 * @param action         What happens at the boundary: cut the branch, or shorten it to fit before it grows.
 * @param steer          How strongly branches near the boundary turn along it instead of outward.
 * @param relativeRadius Whether the radius is a share of the tree's height instead of blocks.
 */
public record ShapeControl(Style style, double crownBase, Curve radius, Action action, double steer,
                           boolean relativeRadius) {
  public boolean enabled() {
    return style == Style.ENVELOPE;
  }

  public CrownEnvelope envelope() {
    return new CrownEnvelope(crownBase, radius, relativeRadius);
  }

  /**
   * Whether a crown shape limits the branches.
   */
  public enum Style {NONE, ENVELOPE}

  /**
   * What a branch does at the crown shape: stop where it crosses it, or shrink to fit inside before growing.
   */
  public enum Action {STOP, SCALE_LENGTH}
}
