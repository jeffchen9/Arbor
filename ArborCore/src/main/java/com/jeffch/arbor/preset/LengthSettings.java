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
 * How long the branches of a level grow.
 *
 * @param absolute         Length in blocks.
 * @param percentOfParent  Length added as a fraction of the parent's length.
 * @param percentOfRadius  Length added as a multiple of the branch's base radius.
 * @param min              Shortest allowed length, in blocks.
 * @param max              Longest allowed length, in blocks.
 * @param shapeScale       Multiplier on the length that fits inside the crown, for the scaleLength crown action.
 * @param stopShapeControl Whether this level and its children ignore the crown shape.
 * @param crownReach       Share of the crown edge's distance from the center this level may reach; 1 is the edge.
 */
public record LengthSettings(
        Param absolute,
        Param percentOfParent,
        Param percentOfRadius,
        double min,
        double max,
        double shapeScale,
        boolean stopShapeControl,
        double crownReach) {
}
