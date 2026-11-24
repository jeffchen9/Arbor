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

import java.util.List;

/**
 * One generated branch.
 *
 * @param level        Index of this branch's level in the preset.
 * @param levelName    Name of this branch's level.
 * @param pathId       Unique path through the hierarchy, such as trunk.0/bough.3.
 * @param parentPathId Path of the parent branch, or null for a trunk.
 * @param parentT      Position on the parent where this branch starts, from 0 to 1.
 * @param nodes        Points along the branch's center line, from base to tip.
 */
public record Branch(int level, String levelName, String pathId, String parentPathId, double parentT,
                     List<Node> nodes) {
}
