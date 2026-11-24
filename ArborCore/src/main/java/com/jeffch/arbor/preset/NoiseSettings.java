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
 * Random wander along a branch.
 *
 * @param earlyAmount     Direction noise added per block while growing, which builds into broad curves.
 * @param earlyTurbulence How often the direction noise changes along the branch, from 0 to 1.
 * @param earlyThreshold  Direction noise below this size is ignored, from 0 to 1.
 * @param lateAmount      Largest position offset in blocks added after growth, without changing the overall path.
 * @param lateTurbulence  How often the position offset changes along the branch, from 0 to 1.
 * @param lateSmooth      How much the position offset is smoothed by using fewer changes, from 0 to 1.
 */
public record NoiseSettings(
        Param earlyAmount,
        double earlyTurbulence,
        double earlyThreshold,
        Param lateAmount,
        double lateTurbulence,
        double lateSmooth) {
}
