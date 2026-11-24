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
 * A numeric setting with tree-level variance, branch-level variance and a profile curve.
 *
 * @param base           Base value.
 * @param treeVariance   Largest random change, fixed once per tree.
 * @param branchVariance Largest random change, rolled for each branch.
 * @param profile        Multiplier curve over normalized position along the parent or branch.
 */
public record Param(double base, double treeVariance, double branchVariance, Curve profile) {
  public static Param of(double base) {
    return new Param(base, 0, 0, Curve.constant(1));
  }

  /**
   * This setting with its value and random variation multiplied, so a small value cannot roll below zero.
   *
   * @param factor Multiplier.
   */
  public Param scaled(double factor) {
    return new Param(base * factor, treeVariance * factor, branchVariance * factor, profile);
  }
}
