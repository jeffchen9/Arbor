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
 * Chances of removing whole branches of a level.
 *
 * @param interior Removal chance for branches pointing back toward the trunk, scaled by how directly they do.
 * @param up       Removal chance for branches pointing up, scaled by how steeply.
 * @param down     Removal chance for branches pointing down, scaled by how steeply.
 * @param general  Removal chance for every branch.
 * @param ground   Removal chance for branches that dip below ground level.
 */
public record PruneSettings(double interior, double up, double down, double general, double ground) {
}
