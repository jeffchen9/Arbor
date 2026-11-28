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

package com.jeffch.arbor.pipeline;

import com.jeffch.arbor.skeleton.Branch;
import com.jeffch.arbor.skeleton.Node;

import java.util.List;

/**
 * Measures generated trunks the way foresters do.
 */
public final class TrunkMeasure {
  /**
   * Breast height, in blocks above the trunk base.
   */
  public static final double BREAST_HEIGHT = 1.3;

  private TrunkMeasure() {
  }

  /**
   * Diameter of the first trunk at breast height.
   *
   * @param tree the generated tree.
   * @return the diameter in blocks.
   */
  public static double breastDiameter(GeneratedTree tree) {
    Branch trunk = tree.skeleton().branches().get(0);
    List<Node> nodes = trunk.nodes();
    double target = nodes.get(0).position().y() + BREAST_HEIGHT;
    for (int i = 1; i < nodes.size(); i++) {
      Node below = nodes.get(i - 1);
      Node above = nodes.get(i);
      if (above.position().y() >= target) {
        double span = above.position().y() - below.position().y();
        double t = span <= 0 ? 0 : (target - below.position().y()) / span;
        return 2 * (below.radius() + (above.radius() - below.radius()) * t);
      }
    }
    return 2 * nodes.get(nodes.size() - 1).radius();
  }
}
