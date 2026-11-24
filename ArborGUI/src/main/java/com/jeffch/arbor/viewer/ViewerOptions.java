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

package com.jeffch.arbor.viewer;

import java.nio.file.Path;

/**
 * Parsed viewer command line.
 *
 * @param preset            Preset file, or null to start with a new tree.
 * @param seed              Tree seed.
 * @param screenshot        File to save and exit, or null for an interactive window.
 * @param assets            Folder from --assets, or null if it was not given.
 * @param realisticLighting Whether to start with sun lighting and shadows instead of Minecraft shading.
 */
public record ViewerOptions(Path preset, long seed, Path screenshot, Path assets, boolean realisticLighting) {
}
