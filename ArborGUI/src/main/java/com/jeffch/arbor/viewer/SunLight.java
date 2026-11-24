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

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The directional sun used by realistic lighting. It is fixed at an angle so shadows fall beside the tree.
 */
final class SunLight {
  /**
   * Unit vector pointing toward the sun.
   */
  static final Vector3f DIRECTION = new Vector3f(0.55f, 0.72f, 0.42f).normalize();

  private SunLight() {
  }

  /**
   * World to shadow-map space, fitted around everything inside the given bounds.
   *
   * @param min Lower corner of the scene.
   * @param max Upper corner of the scene.
   */
  static Matrix4f matrix(Vector3f min, Vector3f max) {
    Vector3f center = new Vector3f(min).add(max).mul(0.5f);
    float radius = Math.max(1, new Vector3f(max).sub(min).length() / 2 + 0.5f);
    Vector3f eye = new Vector3f(DIRECTION).mul(2 * radius).add(center);
    return new Matrix4f().ortho(-radius, radius, -radius, radius, radius, 3 * radius)
            .lookAt(eye, center, new Vector3f(0, 1, 0));
  }
}
