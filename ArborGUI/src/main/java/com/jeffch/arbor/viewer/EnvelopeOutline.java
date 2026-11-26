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

import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.preset.CrownEnvelope;
import com.jeffch.arbor.skeleton.Skeleton;
import com.jeffch.arbor.skeleton.TrunkAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.DoubleFunction;

/**
 * The crown envelope as a wire cage: rings from the crown base to the top and at each point of the radius curve, joined
 * by lines up its sides. Each ring is centred on the trunk at its height, as the generator measures it.
 */
final class EnvelopeOutline {
  /**
   * Rings at every tenth of the crown's height, matching the editor's steps, besides one at each curve point.
   */
  private static final int RINGS = 10;
  private static final int RING_SEGMENTS = 48;
  private static final int SIDE_LINES = 8;
  private static final double CENTER = 0.5;

  private EnvelopeOutline() {
  }

  /**
   * The cage around a generated tree, each ring centred on its trunk; none for a tree with no branches, such as one
   * whose trunk length is 0.
   *
   * @param envelope  The crown envelope.
   * @param skeleton  The generated tree.
   * @param sizeScale The tree's overall size factor.
   */
  static List<double[]> segments(CrownEnvelope envelope, Skeleton skeleton, double sizeScale) {
    if (skeleton.branches().isEmpty()) {
      return List.of();
    }
    TrunkAxis axis = TrunkAxis.ofTrunks(skeleton.branches());
    return segments(envelope, skeleton.treeHeight(), sizeScale, y -> {
      Vec3 centred = axis.recenter(new Vec3(0, y, 0));
      return new double[]{-centred.x(), -centred.z()};
    });
  }

  /**
   * Line segments as {x1, y1, z1, x2, y2, z2}.
   *
   * @param envelope   The crown envelope.
   * @param treeHeight Height of the tree in blocks.
   * @param sizeScale  The tree's overall size factor.
   * @param trunkShift Sideways x and z offset of the trunk's middle at a height, from the middle of the origin block.
   *                   The generator measures the crown radius from there, so each ring is centred on it.
   */
  static List<double[]> segments(CrownEnvelope envelope, double treeHeight, double sizeScale,
                                 DoubleFunction<double[]> trunkShift) {
    double base = envelope.crownBase() * sizeScale;
    TreeSet<Double> heights = new TreeSet<>(List.of(base, Math.max(base, treeHeight)));
    for (int step = 1; step < RINGS; step++) {
      heights.add(base + step * (treeHeight - base) / RINGS);
    }
    for (double[] point : envelope.radius().points()) {
      if (point[0] > 0 && point[0] < 1) {
        heights.add(base + point[0] * (treeHeight - base));
      }
    }
    List<double[]> segments = new ArrayList<>();
    for (double y : heights) {
      double radius = envelope.radiusAt(y, treeHeight, sizeScale);
      double[] shift = trunkShift.apply(y);
      for (int i = 0; i < RING_SEGMENTS && radius > 0; i++) {
        double a = 2 * Math.PI * i / RING_SEGMENTS;
        double b = 2 * Math.PI * (i + 1) / RING_SEGMENTS;
        segments.add(new double[]{CENTER + shift[0] + Math.cos(a) * radius, y, CENTER + shift[1] + Math.sin(a) * radius,
                CENTER + shift[0] + Math.cos(b) * radius, y, CENTER + shift[1] + Math.sin(b) * radius});
      }
    }
    Double previous = null;
    for (double y : heights) {
      if (previous != null) {
        double low = envelope.radiusAt(previous, treeHeight, sizeScale);
        double high = envelope.radiusAt(y, treeHeight, sizeScale);
        double[] lowShift = trunkShift.apply(previous);
        double[] highShift = trunkShift.apply(y);
        for (int i = 0; i < SIDE_LINES; i++) {
          double a = 2 * Math.PI * i / SIDE_LINES;
          segments.add(new double[]{CENTER + lowShift[0] + Math.cos(a) * low, previous,
                  CENTER + lowShift[1] + Math.sin(a) * low,
                  CENTER + highShift[0] + Math.cos(a) * high, y, CENTER + highShift[1] + Math.sin(a) * high});
        }
      }
      previous = y;
    }
    return segments;
  }
}
