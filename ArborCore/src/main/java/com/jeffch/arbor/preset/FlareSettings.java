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
 * Root-like widening around the base of a branch, mostly used on trunks.
 *
 * @param radius Extra radius at the base, as a multiple of the branch radius.
 * @param height Distance along the branch, in blocks, over which the flare fades out.
 * @param pinch  Exponent of the fade; higher values keep the flare closer to the base.
 * @param lobes  Number of root-like bulges around the branch; 0 flares evenly.
 * @param noise  Irregularity of the flare around the branch, from 0 to 1.
 * @param angle  Rotation of the lobes around the branch, in degrees.
 */
public record FlareSettings(double radius, double height, double pinch, int lobes, double noise, Param angle) {
}
