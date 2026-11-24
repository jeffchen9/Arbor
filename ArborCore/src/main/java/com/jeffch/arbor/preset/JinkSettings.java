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
 * Straightens a branch into angular pieces.
 *
 * @param amount     How far the branch moves toward straight pieces, from 0 to 1.
 * @param segments   Number of straight pieces.
 * @param uniformity How evenly the pieces are spaced, from 0 (irregular) to 1 (even).
 */
public record JinkSettings(double amount, Param segments, double uniformity) {
}
