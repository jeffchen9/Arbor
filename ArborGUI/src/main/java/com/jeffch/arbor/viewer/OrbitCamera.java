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
 * A mouse-driven camera that orbits, pans and zooms around a target.
 */
public final class OrbitCamera {
  public static final float MIN_DISTANCE = 2;
  public static final float MAX_DISTANCE = 2000;
  public static final float MAX_PITCH = 85;

  private static final float FOV_RADIANS = (float) Math.toRadians(45);
  private static final float ORBIT_DEGREES_PER_PIXEL = 0.3f;
  private static final float ZOOM_FACTOR = 0.9f;

  private final Vector3f target = new Vector3f(0.5f, 10, 0.5f);
  private float yaw = 35;
  private float pitch = 18;
  private float distance = 60;

  /**
   * The value limited to a range.
   *
   * @param value Value to limit.
   * @param min   Lowest allowed.
   * @param max   Highest allowed.
   */
  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  /**
   * Turns around the target by a mouse drag, keeping the pitch short of straight up or down.
   *
   * @param deltaX Horizontal drag in pixels.
   * @param deltaY Vertical drag in pixels.
   */
  public void orbit(double deltaX, double deltaY) {
    yaw -= (float) deltaX * ORBIT_DEGREES_PER_PIXEL;
    pitch = clamp(pitch + (float) deltaY * ORBIT_DEGREES_PER_PIXEL, -MAX_PITCH, MAX_PITCH);
  }

  /**
   * Slides the target across the screen so it follows the mouse at the target's distance.
   *
   * @param deltaX         Horizontal drag in pixels.
   * @param deltaY         Vertical drag in pixels.
   * @param viewportHeight View height in pixels.
   */
  public void pan(double deltaX, double deltaY, int viewportHeight) {
    float unitsPerPixel = 2 * distance * (float) Math.tan(FOV_RADIANS / 2) / Math.max(1, viewportHeight);
    Vector3f forward = new Vector3f(target).sub(eye()).normalize();
    Vector3f right = new Vector3f(forward).cross(0, 1, 0).normalize();
    Vector3f up = new Vector3f(right).cross(forward).normalize();
    target.add(right.mul((float) -deltaX * unitsPerPixel));
    target.add(up.mul((float) deltaY * unitsPerPixel));
  }

  /**
   * Moves closer or farther by mouse wheel ticks.
   *
   * @param ticks Wheel ticks; positive moves closer.
   */
  public void zoom(double ticks) {
    distance = clamp(distance * (float) Math.pow(ZOOM_FACTOR, ticks), MIN_DISTANCE, MAX_DISTANCE);
  }

  /**
   * Centers the camera so the given bounds fit in view.
   *
   * @param min Lower corner of the bounds.
   * @param max Upper corner of the bounds.
   */
  public void frame(Vector3f min, Vector3f max) {
    target.set(min).add(max).mul(0.5f);
    float radius = new Vector3f(max).sub(min).length() / 2;
    distance = clamp(radius / (float) Math.sin(FOV_RADIANS / 2) * 1.05f, MIN_DISTANCE, MAX_DISTANCE);
  }

  /**
   * Where the camera is, from its target, angles and distance.
   */
  public Vector3f eye() {
    double yawRadians = Math.toRadians(yaw);
    double pitchRadians = Math.toRadians(pitch);
    double horizontal = Math.cos(pitchRadians) * distance;
    return new Vector3f(
            target.x + (float) (Math.sin(yawRadians) * horizontal),
            target.y + (float) (Math.sin(pitchRadians) * distance),
            target.z + (float) (Math.cos(yawRadians) * horizontal));
  }

  /**
   * The view matrix looking from the eye at the target.
   *
   * @param destination Receives the matrix.
   */
  public Matrix4f view(Matrix4f destination) {
    return destination.setLookAt(eye(), target, new Vector3f(0, 1, 0));
  }

  /**
   * The perspective matrix, with near and far planes set from the distance so close and distant views both stay sharp.
   *
   * @param destination Receives the matrix.
   * @param aspect      View width divided by height.
   */
  public Matrix4f projection(Matrix4f destination, float aspect) {
    float near = Math.max(0.05f, distance * 0.005f);
    return destination.setPerspective(FOV_RADIANS, aspect, near, distance * 4 + 512);
  }

  public Vector3f target() {
    return target;
  }

  public float distance() {
    return distance;
  }

  public float pitch() {
    return pitch;
  }
}
