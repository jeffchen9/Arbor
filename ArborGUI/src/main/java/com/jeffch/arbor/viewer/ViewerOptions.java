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
 * @param views             Whether the screenshot shows front, side, three-quarter and top views in one image.
 * @param variations        How many seeds, from {@code seed} up, the screenshot shows side by side, or 0 for one tree.
 * @param highDetail        Whether a views, variations or closeup image is drawn at twice the width and height.
 * @param closeup           The part of the tree a closeup shows, as {bottom, top, left, right} fractions of the tree
 *                          in that view, or null for no closeup.
 * @param view              Which of the {@link #VIEWS} a closeup looks from.
 * @param wireframe         Whether a screenshot shows only the branch skeleton's lines instead of blocks.
 * @param compare           Other designs drawn after this one, side by side from the front on the same seed and
 *                          labelled with their names, or empty for none.
 */
public record ViewerOptions(Path preset, long seed, Path screenshot, Path assets, boolean realisticLighting,
                            boolean views, int variations, boolean highDetail, double[] closeup, int view,
                            boolean wireframe, java.util.List<Path> compare) {
  public static final int MAX_VARIATIONS = 9;
  public static final java.util.List<String> VIEWS = java.util.List.of("front", "side", "three-quarter", "top");
}
