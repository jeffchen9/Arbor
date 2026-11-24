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
 * How branches and leaf density become Minecraft shapes.
 *
 * @param fullRadius             Branches at least this thick, in blocks, become full blocks.
 * @param wallRadius             Branches at least this thick, but below fullRadius, become walls; thinner ones become fences.
 * @param wallGapChance          Probability from 0 to 1 of skipping a wall block along a side branch.
 * @param thinCoverage           Probability from 0 to 1 that each fence position is placed.
 * @param gateChance             Probability from 0 to 1 that a straight fence position becomes a closed gate.
 * @param gateInWallChance       Probability from 0 to 1 that a gate uses the lowered in-wall state.
 * @param openGateChance         Probability from 0 to 1 that a branch split becomes an open gate.
 * @param impliedRadius          Branch ends thinner than this are drawn as leaves instead of wood.
 * @param impliedLeafSize        Radius in blocks of the leaf clusters that replace those ends.
 * @param impliedLeafStrength    Density from 0 to 1 of those replacement clusters.
 * @param deadBushChance         Probability from 0 to 1 that a crowded fence gets a dead bush or sapling.
 * @param deadBushDensity        Nearby fences and gates needed for a fence to count as crowded.
 * @param deadBushOffFenceChance Probability from 0 to 1 that the plant replaces the fence instead of sitting on it.
 * @param smoothTrunk            Whether the trunk surface is softened with partial blocks.
 * @param smoothStairDepth       Surface blocks at most this deep inside the trunk can become stairs.
 * @param smoothSlabDepth        Surface blocks at most this deep inside the trunk can become slabs.
 * @param smoothWallDepth        Surface blocks at most this deep inside the trunk can become walls.
 * @param smoothFenceDepth       Surface blocks at most this deep inside the trunk can become fences.
 * @param buttonChance           Probability from 0 to 1 of a button on each eligible trunk face.
 * @param buttonSpacing          Minimum distance in blocks between trunk buttons.
 * @param tipCanopyDensity       Leaf density a thin branch tip needs to stay; 0 keeps bare tips.
 * @param fineBranchChance       Probability from 0 to 1 of hinting a too-fine branch with a fence arm or gate.
 * @param fineGateChance         Probability from 0 to 1 that such a hint is an open gate instead of a fence arm.
 * @param trunkFullRadius        A trunk thinner than this uses only walls and fences; 0 turns this off.
 * @param saplingLeafDensity     Leaf density where a branch cluster becomes a sapling instead of a dead bush.
 * @param voxelRandomness        Overall irregularity of shape thresholds and branch tips, from 0 to 1.
 * @param leafBlockDensity       Leaf density needed for a full leaf block; 0 places no leaf blocks.
 * @param trimToCanopy           Whether walls and fences stop at the last point with leaves at or above them.
 * @param removeDetached         Whether blocks with no chain of touching blocks back to a trunk are removed.
 * @param survival               Whether the tree uses only logs, wood and naturally decaying leaves.
 */
public record VoxelSettings(
        double fullRadius,
        double wallRadius,
        double wallGapChance,
        double thinCoverage,
        double gateChance,
        double gateInWallChance,
        double openGateChance,
        double impliedRadius,
        double impliedLeafSize,
        double impliedLeafStrength,
        double deadBushChance,
        int deadBushDensity,
        double deadBushOffFenceChance,
        boolean smoothTrunk,
        double smoothStairDepth,
        double smoothSlabDepth,
        double smoothWallDepth,
        double smoothFenceDepth,
        double buttonChance,
        int buttonSpacing,
        double tipCanopyDensity,
        double fineBranchChance,
        double fineGateChance,
        double trunkFullRadius,
        double saplingLeafDensity,
        double voxelRandomness,
        double leafBlockDensity,
        boolean trimToCanopy,
        boolean removeDetached,
        boolean survival) {

  /**
   * Survival's own values, with every shape survival cannot use switched off.
   *
   * @param full         Radius above which branches are full blocks.
   * @param wall         Radius above which branches are walls.
   * @param trunkFull    A trunk thinner than this uses only walls and fences; 0 turns this off.
   * @param implied      Branch ends thinner than this are drawn as leaves instead of wood.
   * @param leafSize     Radius in blocks of the leaf clusters that replace those ends.
   * @param leafStrength Density from 0 to 1 of those replacement clusters.
   * @param tipDensity   Leaf density a thin branch tip needs to stay; 0 keeps bare tips.
   * @param trim         Whether walls and fences stop at the last point with leaves at or above them.
   * @param randomness   Overall irregularity of shape thresholds and branch tips, from 0 to 1.
   * @param leafDensity  Leaf density needed for a full leaf block.
   */
  VoxelSettings forSurvival(double full, double wall, double trunkFull, double implied, double leafSize,
                            double leafStrength, double tipDensity, boolean trim, double randomness,
                            double leafDensity) {
    return new VoxelSettings(full, wall, 0, 1, 0, 0, 0, implied, leafSize, leafStrength, 0, deadBushDensity, 0,
            false, smoothStairDepth, smoothSlabDepth, smoothWallDepth, smoothFenceDepth, 0, buttonSpacing, tipDensity,
            0, 0, trunkFull, saplingLeafDensity, randomness, leafDensity, trim, true, true);
  }
}
