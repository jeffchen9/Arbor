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
 * Finds branch ends too thin for a fence, which leaves imply instead of wood.
 */
public final class ImpliedTips {
  private ImpliedTips() {
  }

  /**
   * Returns the node where the implied tail begins. Segments from this node onward are not
   * drawn as wood. A result of the last node index means nothing is implied.
   *
   * @param branch     Branch to check.
   * @param thinRadius Radius below which the tip is implied by leaves.
   */
  public static int tailStart(Branch branch, double thinRadius) {
    List<Node> nodes = branch.nodes();
    int start = nodes.size() - 1;
    if (nodes.get(start).radius() >= thinRadius) {
      return start;
    }
    while (start > 0 && nodes.get(start - 1).radius() < thinRadius) {
      start--;
    }
    return start;
  }
}
