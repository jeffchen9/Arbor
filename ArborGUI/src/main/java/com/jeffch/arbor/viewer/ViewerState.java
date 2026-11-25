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

import com.jeffch.arbor.shape.ShapeClass;

import java.util.EnumSet;
import java.util.Set;

/**
 * User-controlled viewer settings.
 */
public final class ViewerState {
  /**
   * Leaves and plants: thin enough to let light through, and hidden together by "Wood only".
   */
  public static final Set<ShapeClass> FOLIAGE = EnumSet.of(ShapeClass.LEAVES, ShapeClass.PLANT,
          ShapeClass.DEAD_BUSH, ShapeClass.SAPLING);
  private final Set<ShapeClass> visible = EnumSet.allOf(ShapeClass.class);
  public long seed;
  public boolean showDensity;
  public boolean showSkeleton;
  public boolean showVoxelGrid;
  public boolean branchView;
  public boolean useTextures = true;
  public boolean showForces = true;
  public boolean realisticLighting;
  public boolean splitView;
  public float densityThreshold = 0.05f;
  public float densityOpacity = 1.2f;

  public ViewerState(long seed) {
    this.seed = seed;
  }

  /**
   * Split view always has a blocks half, whichever single view is selected.
   */
  public boolean drawsShapes() {
    return splitView || !branchView;
  }

  public boolean drawsVoxelGrid() {
    return drawsShapes() && showVoxelGrid;
  }

  public boolean drawsSkeleton() {
    return branchView || showSkeleton;
  }

  public boolean isVisible(ShapeClass shapeClass) {
    return visible.contains(shapeClass);
  }

  /**
   * Shows or hides one shape class.
   *
   * @param shapeClass Shape class.
   * @param show       Whether to show it.
   */
  public void setVisible(ShapeClass shapeClass, boolean show) {
    if (show) {
      visible.add(shapeClass);
    } else {
      visible.remove(shapeClass);
    }
  }
}
