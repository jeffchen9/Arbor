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
 * The direction a branch starts in.
 *
 * @param startAngle Angle away from the parent's direction, as a fraction of 180 degrees.
 * @param startRoll  Degrees added to the branch's roll around the parent.
 * @param alignment  How far the roll's zero direction turns toward the sky, from 0 to 1.
 */
public record OrientationSettings(Param startAngle, Param startRoll, double alignment) {
}
