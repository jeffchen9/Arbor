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

package com.jeffch.arbor.shape;

import com.jeffch.arbor.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered voxel walks between two points.
 */
public final class VoxelPaths {
  private VoxelPaths() {
  }

  /**
   * A walk that changes one axis per step, for shapes without diagonal connections.
   *
   * @param a Start point.
   * @param b End point.
   */
  public static List<int[]> sixConnected(Vec3 a, Vec3 b) {
    int[] voxel = floor(a);
    int[] end = floor(b);
    double[] start = {a.x(), a.y(), a.z()};
    double[] delta = {b.x() - a.x(), b.y() - a.y(), b.z() - a.z()};
    int[] step = new int[3];
    double[] next = new double[3];
    double[] increment = new double[3];
    for (int axis = 0; axis < 3; axis++) {
      step[axis] = Integer.signum(end[axis] - voxel[axis]);
      if (step[axis] == 0 || delta[axis] == 0) {
        next[axis] = Double.POSITIVE_INFINITY;
        increment[axis] = Double.POSITIVE_INFINITY;
      } else {
        double boundary = step[axis] > 0 ? voxel[axis] + 1 : voxel[axis];
        next[axis] = (boundary - start[axis]) / delta[axis];
        increment[axis] = step[axis] / delta[axis];
      }
    }
    List<int[]> path = new ArrayList<>();
    path.add(voxel.clone());
    while (voxel[0] != end[0] || voxel[1] != end[1] || voxel[2] != end[2]) {
      int axis = -1;
      double smallest = Double.POSITIVE_INFINITY;
      for (int candidate = 0; candidate < 3; candidate++) {
        if (voxel[candidate] != end[candidate] && next[candidate] < smallest) {
          smallest = next[candidate];
          axis = candidate;
        }
      }
      voxel[axis] += step[axis];
      next[axis] += increment[axis];
      path.add(voxel.clone());
    }
    return path;
  }

  /**
   * A straight walk that may step diagonally, for shapes that are allowed to float.
   *
   * @param a Start point.
   * @param b End point.
   */
  public static List<int[]> line(Vec3 a, Vec3 b) {
    int[] from = floor(a);
    int[] to = floor(b);
    int steps = Math.max(Math.abs(to[0] - from[0]), Math.max(Math.abs(to[1] - from[1]), Math.abs(to[2] - from[2])));
    List<int[]> path = new ArrayList<>(steps + 1);
    for (int i = 0; i <= steps; i++) {
      double t = steps == 0 ? 0 : i / (double) steps;
      path.add(new int[]{
              (int) Math.floor(from[0] + (to[0] - from[0]) * t + 0.5),
              (int) Math.floor(from[1] + (to[1] - from[1]) * t + 0.5),
              (int) Math.floor(from[2] + (to[2] - from[2]) * t + 0.5),
      });
    }
    return path;
  }

  private static int[] floor(Vec3 point) {
    return new int[]{(int) Math.floor(point.x()), (int) Math.floor(point.y()), (int) Math.floor(point.z())};
  }
}
