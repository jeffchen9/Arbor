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

import java.util.Arrays;

/**
 * A piecewise-linear curve with strictly increasing t values, clamped at both ends.
 */
public final class Curve {
  /**
   * Positions of the control points, increasing.
   */
  private final double[] ts;
  /**
   * The curve's value at each control point.
   */
  private final double[] values;

  private Curve(double[] ts, double[] values) {
    this.ts = ts;
    this.values = values;
  }

  /**
   * A curve with the same value everywhere.
   *
   * @param value The value.
   */
  public static Curve constant(double value) {
    return new Curve(new double[]{0}, new double[]{value});
  }

  /**
   * A curve through the given points, written as t1, value1, t2, value2 and so on.
   *
   * @param tValuePairs Alternating t and value, with t increasing.
   * @throws IllegalArgumentException When a pair is incomplete, a number is not finite, or t does not increase.
   */
  public static Curve of(double... tValuePairs) {
    if (tValuePairs.length == 0 || tValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException("curve needs t/value pairs");
    }
    int count = tValuePairs.length / 2;
    double[] ts = new double[count];
    double[] values = new double[count];
    for (int i = 0; i < count; i++) {
      ts[i] = tValuePairs[i * 2];
      values[i] = tValuePairs[i * 2 + 1];
      if (!Double.isFinite(ts[i]) || !Double.isFinite(values[i])) {
        throw new IllegalArgumentException("curve values must be finite");
      }
      if (i > 0 && ts[i] <= ts[i - 1]) {
        throw new IllegalArgumentException("curve t values must increase");
      }
    }
    return new Curve(ts, values);
  }

  /**
   * The value at {@code t}: straight lines between control points, and the end values beyond either end.
   *
   * @param t Position along the curve.
   */
  public double eval(double t) {
    if (t <= ts[0]) {
      return values[0];
    }
    int last = ts.length - 1;
    if (t >= ts[last]) {
      return values[last];
    }
    int index = Arrays.binarySearch(ts, t);
    if (index >= 0) {
      return values[index];
    }
    int upper = -index - 1;
    int lower = upper - 1;
    double fraction = (t - ts[lower]) / (ts[upper] - ts[lower]);
    return values[lower] + (values[upper] - values[lower]) * fraction;
  }

  /**
   * This curve with every value multiplied.
   *
   * @param factor Multiplier.
   */
  public Curve scaled(double factor) {
    return new Curve(ts, Arrays.stream(values).map(value -> value * factor).toArray());
  }

  /**
   * Largest value at any control point.
   */
  public double max() {
    return Arrays.stream(values).max().orElse(0);
  }

  /**
   * Equal when every control point matches.
   *
   * @param other Object to compare with.
   */
  @Override
  public boolean equals(Object other) {
    return other instanceof Curve curve
            && Arrays.equals(ts, curve.ts)
            && Arrays.equals(values, curve.values);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(ts) + Arrays.hashCode(values);
  }
}
