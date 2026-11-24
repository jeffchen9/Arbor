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

package com.jeffch.arbor.skeleton;

/**
 * Where one child starts on its parent.
 *
 * @param index         The child's number among its siblings, used in its path.
 * @param t             Position on the parent, from 0 to 1.
 * @param rollDeg       Roll around the parent, in degrees.
 * @param sink          Distance in blocks the child starts behind its attachment point.
 * @param extendsParent Whether the child continues the parent's direction from its tip.
 */
public record Placement(int index, double t, double rollDeg, double sink, boolean extendsParent) {
}
