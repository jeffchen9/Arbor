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
import com.jeffch.arbor.preset.ForceSettings;
import com.jeffch.arbor.skeleton.SpineBuilder;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Line markers showing where a tree's sun is and which way its wind blows.
 */
final class ForceMarkers {
  private static final float[] SUN = {1f, 0.85f, 0.2f};
  private static final float[] WIND = {0.35f, 0.85f, 1f};
  private static final int RING_SEGMENTS = 24;
  private static final int RAYS = 12;
  private static final int BEAM_DASHES = 5;
  private static final int WIND_COLUMNS = 3;
  private static final int WIND_ROWS = 4;
  private final LineRenderer.Lines lines = new LineRenderer.Lines();
  private final Vector3f min;
  private final Vector3f max;
  private final Vec3 center;
  private final double radius;

  /**
   * Starts from the tree's bounds; markers may grow them so the camera frames both.
   *
   * @param treeMin Tree's lower corner.
   * @param treeMax Tree's upper corner.
   */
  private ForceMarkers(Vector3f treeMin, Vector3f treeMax) {
    min = new Vector3f(treeMin);
    max = new Vector3f(treeMax);
    center = new Vec3((treeMin.x + treeMax.x) / 2.0, (treeMin.y + treeMax.y) / 2.0, (treeMin.z + treeMax.z) / 2.0);
    radius = new Vector3f(treeMax).sub(treeMin).length() / 2.0;
  }

  /**
   * Lines that show each force with strength: a sun for attractors and sun-named directions, arrows for wind.
   *
   * @param forces  The preset's forces.
   * @param seed    Tree seed, which sets each wind's heading.
   * @param treeMin Tree's lower corner.
   * @param treeMax Tree's upper corner.
   */
  static Markers build(List<ForceSettings> forces, long seed, Vector3f treeMin, Vector3f treeMax) {
    ForceMarkers markers = new ForceMarkers(treeMin, treeMax);
    List<String> shown = new ArrayList<>();
    for (ForceSettings force : forces) {
      if (force.strength() == 0) {
        continue;
      }
      if (force.type() == ForceSettings.Type.WIND) {
        Vec3 heading = SpineBuilder.windHeading(force, seed).scale(Math.signum(force.strength()));
        markers.addWind(heading, force.wind().heightPower());
        shown.add("Wind (" + force.name() + ")");
      } else if (force.strength() > 0 && isSun(force)) {
        markers.addSun(force);
        shown.add("Sun (" + force.name() + ")");
      }
    }
    return new Markers(markers.lines, markers.min, markers.max, String.join(", ", shown));
  }

  /**
   * Whether a force is shown as a sun: any attractor, or a direction force named like a sun.
   *
   * @param force Force to check.
   */
  private static boolean isSun(ForceSettings force) {
    return force.type() == ForceSettings.Type.ATTRACTOR
            || force.type() == ForceSettings.Type.DIRECTION && force.name().toLowerCase(Locale.ROOT).contains("sun");
  }

  /**
   * One point around a circle in the plane of {@code u} and {@code v}.
   *
   * @param at   Circle center.
   * @param u    First unit axis of the circle's plane.
   * @param v    Second unit axis of the circle's plane.
   * @param size Circle radius.
   * @param step Index of the point around the circle.
   */
  private static Vec3 ringPoint(Vec3 at, Vec3 u, Vec3 v, double size, int step) {
    double angle = 2 * Math.PI * step / RING_SEGMENTS;
    return at.add(u.scale(Math.cos(angle) * size)).add(v.scale(Math.sin(angle) * size));
  }

  /**
   * A distant sun is drawn just past the crown, in its true direction, so it stays in view.
   *
   * @param force The sun force.
   */
  private void addSun(ForceSettings force) {
    Vec3 offset = force.type() == ForceSettings.Type.ATTRACTOR ? force.position().sub(center) : force.vector();
    Vec3 direction = offset.length() < 1e-6 ? Vec3.UP : offset.normalized();
    double reach = force.type() == ForceSettings.Type.ATTRACTOR
            ? Math.min(offset.length(), radius * 1.05) : radius * 1.05;
    Vec3 sun = center.add(direction.scale(reach));
    double size = Math.max(1, radius * 0.09);
    ring(sun, new Vec3(1, 0, 0), new Vec3(0, 1, 0), size);
    ring(sun, new Vec3(1, 0, 0), new Vec3(0, 0, 1), size);
    ring(sun, new Vec3(0, 1, 0), new Vec3(0, 0, 1), size);
    for (int i = 0; i < RAYS; i++) {
      double angle = 2 * Math.PI * i / RAYS;
      Vec3 ray = new Vec3(Math.cos(angle), 0.45 * (i % 2 == 0 ? 1 : -1), Math.sin(angle)).normalized();
      segment(sun.add(ray.scale(size * 1.3)), sun.add(ray.scale(size * 2)), SUN);
    }
    for (int i = 0; i < BEAM_DASHES; i++) {
      double start = size * 2.4 + i * reach * 0.09;
      segment(sun.sub(direction.scale(start)), sun.sub(direction.scale(start + reach * 0.05)), SUN);
    }
  }

  /**
   * A circle of line segments.
   *
   * @param at   Circle center.
   * @param u    First unit axis of the circle's plane.
   * @param v    Second unit axis of the circle's plane.
   * @param size Circle radius.
   */
  private void ring(Vec3 at, Vec3 u, Vec3 v, double size) {
    for (int i = 0; i < RING_SEGMENTS; i++) {
      segment(ringPoint(at, u, v, size, i), ringPoint(at, u, v, size, i + 1), SUN);
    }
  }

  /**
   * Arrows on the upwind side, longer where the wind is stronger with height.
   *
   * @param heading     Direction the wind blows.
   * @param heightPower How quickly the wind strengthens with height.
   */
  private void addWind(Vec3 heading, double heightPower) {
    Vec3 side = heading.cross(Vec3.UP);
    side = side.length() < 1e-6 ? new Vec3(1, 0, 0) : side.normalized();
    Vec3 lift = side.cross(heading).normalized();
    Vec3 plane = center.sub(heading.scale(radius * 0.8));
    double height = max.y - min.y;
    double width = Math.max(max.x - min.x, max.z - min.z);
    for (int row = 0; row < WIND_ROWS; row++) {
      double t = (row + 0.5) / WIND_ROWS;
      double length = radius * (0.12 + 0.26 * Math.pow(t, heightPower));
      for (int column = 0; column < WIND_COLUMNS; column++) {
        double across = (column / (WIND_COLUMNS - 1.0) - 0.5) * width;
        Vec3 tail = plane.add(side.scale(across)).add(lift.scale((t - 0.5) * height));
        Vec3 tip = tail.add(heading.scale(length));
        segment(tail, tip, WIND);
        Vec3 back = tip.sub(heading.scale(length * 0.25));
        segment(tip, back.add(side.scale(length * 0.13)), WIND);
        segment(tip, back.sub(side.scale(length * 0.13)), WIND);
      }
    }
  }

  /**
   * Adds one line and grows the bounds to hold it.
   *
   * @param a     Start point.
   * @param b     End point.
   * @param color Line color as red, green and blue.
   */
  private void segment(Vec3 a, Vec3 b, float[] color) {
    lines.add((float) a.x(), (float) a.y(), (float) a.z(), (float) b.x(), (float) b.y(), (float) b.z(),
            color[0], color[1], color[2]);
    include(a);
    include(b);
  }

  /**
   * Grows the bounds to hold a point.
   *
   * @param point Point to include.
   */
  private void include(Vec3 point) {
    Vector3f p = new Vector3f((float) point.x(), (float) point.y(), (float) point.z());
    min.min(p);
    max.max(p);
  }

  /**
   * Marker geometry for one tree.
   *
   * @param lines   Line segments to draw.
   * @param min     Lowest corner of the tree and its markers.
   * @param max     Highest corner of the tree and its markers.
   * @param summary Which markers are shown, or empty when none are.
   */
  record Markers(LineRenderer.Lines lines, Vector3f min, Vector3f max, String summary) {
  }
}
