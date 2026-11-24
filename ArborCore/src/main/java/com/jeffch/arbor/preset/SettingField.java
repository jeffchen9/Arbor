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

import java.util.List;

/**
 * One editable setting as the loader reads it. A null fallback means the setting is required.
 *
 * @param path     JSON path, such as levels[1].radius.min.
 * @param kind     Editor control and value type for this setting.
 * @param fallback Value used when the JSON leaves it out; null means the setting is required.
 * @param min      Smallest allowed value, for numeric settings.
 * @param max      Largest allowed value, for numeric settings.
 * @param options  Allowed values, for choice settings.
 */
public record SettingField(String path, Kind kind, Object fallback, double min, double max, List<String> options) {
  public String key() {
    return path.substring(path.lastIndexOf('.') + 1);
  }

  public boolean required() {
    return fallback == null;
  }

  /**
   * The type of value, which picks the editor control: a number, whole number, checkbox, list choice, text, x/y/z
   * vector, number with variance, curve, weighted block list, block rule, or min/max range.
   */
  public enum Kind {NUMBER, INTEGER, BOOLEAN, CHOICE, TEXT, VECTOR, PARAM, CURVE, WEIGHTS, BLOCK_RULE, RANGE}
}
