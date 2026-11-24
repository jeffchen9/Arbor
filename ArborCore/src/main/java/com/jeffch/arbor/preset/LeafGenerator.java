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
 * One group of leaf clusters placed on the branches of a level.
 *
 * @param parent              Name of the branch level that carries these clusters.
 * @param generation          Where clusters are placed on each parent branch.
 * @param size                Cluster radius in blocks.
 * @param sizePercentOfParent Extra radius, as a fraction of the parent branch's length.
 * @param scaleX              Radius multiplier across the cluster, on its normal axis.
 * @param scaleY              Radius multiplier along the cluster's stretch axis.
 * @param scaleZ              Radius multiplier across the cluster, on its binormal axis.
 * @param elongation          Further multiplier on the stretch axis only.
 * @param skyInfluence        How far cluster stretch and offset turn toward the sky, from 0 to 1.
 * @param fold                Moves clusters above (positive) or below (negative) the branch, from -1 to 1.
 * @param align               Moves clusters toward the branch tip (positive) or base (negative), from -1 to 1.
 * @param reach               How far clusters sit off the branch, as a fraction of cluster size, from 0 to 1.
 * @param strength            Density at each cluster's center, from 0 to 1.
 * @param falloff             Exponent of the fade from center to edge; higher values make softer, smaller clusters.
 * @param noiseScale          Size in blocks of the noise that breaks up the density.
 * @param noiseStrength       How much the noise can remove, from 0 to 1.
 * @param envelopeEdge        Distance in blocks over which density fades out at the crown boundary.
 * @param shadePruning        Optional removal of inner or outer foliage.
 */
public record LeafGenerator(
        String parent,
        GenerationSettings generation,
        Param size,
        Param sizePercentOfParent,
        double scaleX,
        double scaleY,
        double scaleZ,
        double elongation,
        double skyInfluence,
        double fold,
        double align,
        double reach,
        Param strength,
        double falloff,
        double noiseScale,
        double noiseStrength,
        double envelopeEdge,
        LeafGenerator.ShadePruning shadePruning) {

  /**
   * Which foliage shade pruning removes: none, the shaded interior, or the outer shell.
   */
  public enum ShadeStyle {NONE, INTERIOR, EXTERIOR}

  /**
   * Removes foliage by how far it is from the tree's center at each height.
   *
   * @param style     Which foliage is removed: none, the interior, or the exterior.
   * @param threshold Normalized distance from the center, from 0 to 1, where removal changes over.
   */
  public record ShadePruning(ShadeStyle style, double threshold) {
  }
}
