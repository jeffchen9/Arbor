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

import com.jeffch.arbor.math.TreeRandom;

/**
 * Samples parameter offsets from independent streams, so settings cannot disturb each other.
 */
public final class ParamResolver {
  private final long seed;

  public ParamResolver(long seed) {
    this.seed = seed;
  }

  /**
   * The setting's value for one branch: its base, plus a per-tree offset shared by every branch, plus a per-branch
   * offset. Each offset is drawn uniformly within plus or minus its variance.
   *
   * @param param      The setting.
   * @param settingKey Full name of the setting, such as {@code levels.bough.length.absolute}.
   * @param ownerPath  Path id of the branch the value is for.
   */
  public BoundParam bind(Param param, String settingKey, String ownerPath) {
    double value = param.base();
    if (param.treeVariance() != 0) {
      value += TreeRandom.forPath(seed, "tree/" + settingKey)
              .range(-param.treeVariance(), param.treeVariance());
    }
    if (param.branchVariance() != 0) {
      value += TreeRandom.forPath(seed, ownerPath + "#" + settingKey)
              .range(-param.branchVariance(), param.branchVariance());
    }
    return new BoundParam(value, param.profile());
  }
}
