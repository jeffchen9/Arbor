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

import java.util.*;

/**
 * The practical slider range and neat step for one setting. Presets and typed values may still go beyond the slider,
 * up to what the loader allows.
 *
 * @param low    Value at the left of the slider.
 * @param high   Value at the right of the slider.
 * @param step   Distance between neat values.
 * @param curved Whether the slider gives small values more travel; a range below zero curves around zero.
 */
record SliderScale(double low, double high, double step, boolean curved) {
  static final int TICKS = 1000;
  private static final double UNIT_STEP = 0.05;
  private static final double OPEN_LIMIT = 10;
  private static final double SNAP_TOLERANCE = 1e-9;
  private static final double DECIMALS = 1e6;
  private static final int MARK_PARTS = 4;
  private static final int MARK_GAP = 150;
  private static final int MAX_EVEN_PARTS = 5;
  private static final double[] EVEN_DIGITS = {1, 2, 2.5, 4, 5, 9};
  private static final double[] ROUND_DIGITS = {1, 1.5, 2, 2.5, 3, 4, 5, 6, 7.5, 8, 9, 10};

  /**
   * {@code parent.key=low high step}, with {@code ~} for a curved slider. Unit ranges not listed use 0.05 steps.
   */
  private static final String TABLE = """
          tree.sizeScale=0.25 4 0.05 ~
          tree.maxHeight=8 128 1 ~
          tree.roll=-180 180 5
          tree.trunkSpread=0 16 0.1
          tree.sizeVariance=0 0.5 0.01
          shapeControl.crownBase=0 64 0.5 ~
          shapeControl.steer=0 1 0.05
          shapeControl.lumpiness=0 1 0.01
          shapeControl.lumpSize=0.5 32 0.5
          forces.strength=-1 1 0.005 ~
          forces.falloff=0 1 0.01
          forces.directionVariance=0 90 5
          forces.gustSize=1 64 0.5 ~
          forces.heightPower=0 4 0.1
          forces.flexibility=0 4 0.1
          generation.count=0 64 1 ~
          generation.density=0 4 0.05 ~
          generation.extendParent=0 8 1
          generation.limit=0 64 1 ~
          generation.frequency=0.25 10 0.05 ~
          generation.childrenPerNode=1 12 1
          generation.internodeLength=0.25 10 0.05 ~
          generation.spiral=-180 180 0.5
          generation.rotation=-180 180 5
          generation.position=-1 1 0.05
          generation.sink=0 2 0.05
          generation.spacing=0.25 16 0.25 ~
          generation.threshold=0 90 1
          flares.radius=0 2 0.05
          flares.height=0 12 0.1
          flares.pinch=0.25 6 0.05
          flares.lobes=0 12 1
          flares.angle=-180 180 5
          jink.segments=1 16 1
          leaves.align=-1 1 0.05
          leaves.fold=-1 1 0.05
          leaves.elongation=0.25 4 0.05 ~
          leaves.envelopeEdge=0 6 0.1
          leaves.falloff=0.25 6 0.05 ~
          leaves.noiseScale=0.5 12 0.1 ~
          leaves.size=0.25 8 0.05 ~
          length.absolute=0 128 0.5 ~
          length.min=0 16 0.1 ~
          length.max=0 64 0.1 ~
          length.percentOfParent=0 1 0.01
          length.percentOfRadius=0 64 0.5 ~
          length.shapeScale=0 2 0.05
          length.crownReach=0.05 2 0.05
          levels.segmentLength=0.25 4 0.05 ~
          noise.earlyAmount=0 0.3 0.005
          noise.lateAmount=0 2 0.05
          orientation.startAngle=0 1 0.01
          orientation.startRoll=-180 180 5
          path.amount=0 8 0.1
          path.length=1 32 0.25 ~
          path.twist=-180 180 5
          plantLayers.clumpSize=0 16 0.5
          plantLayers.nearDistance=0 8 1
          pinecones.chance=0 1 0.01
          pinecones.minLevel=0 8 1
          vines.chance=0 1 0.01
          vines.minLength=1 32 1
          vines.maxLength=1 32 1
          moss.amount=0 1 0.01
          moss.patchSize=1 16 1
          radius.absolute=0 4 0.01 ~
          radius.percentOfParent=0 1 0.01
          radius.min=0 1 0.01
          radius.max=0 4 0.01 ~
          radius.lengthKillRatio=0 256 1 ~
          scale.x=0.1 3 0.05 ~
          scale.y=0.1 3 0.01 ~
          scale.z=0.1 3 0.05 ~
          shape.gravity=-1 1 0.005 ~
          shape.ancestor=-0.5 0.5 0.01
          shape.twist=-30 30 0.5
          shape.curlUp=-10 10 0.1
          shape.curlRight=-10 10 0.1
          shape.curlOut=-10 10 0.1
          shape.zigzag=0 60 1
          shape.zigzagLength=0.5 16 0.1 ~
          shapes.branchDensity=0.05 3 0.05
          shapes.fullRadius=0 1 0.01
          shapes.wallRadius=0 1 0.01
          shapes.trunkFullRadius=0 1 0.01
          shapes.impliedRadius=0 0.2 0.001
          shapes.impliedLeafSize=0 4 0.05
          shapes.deadBushDensity=1 12 1
          shapes.buttonSpacing=0 8 1
          shapes.buttonChance=0 1 0.01
          shapes.wallGapChance=0 1 0.01
          shapes.leafBlockDensity=0 1 0.01
          """;
  private static final Map<String, SliderScale> RULES = parse();
  private static final Set<String> UNIT_PARAMS = Set.of("generation.knockout", "break.spot", "leaves.strength",
          "leaves.sizePercentOfParent", "path.offset", "shape.straightness");

  /**
   * Reads the {@code key=low high step} lines of the table; a fourth word marks a curved slider.
   */
  private static Map<String, SliderScale> parse() {
    Map<String, SliderScale> rules = new HashMap<>();
    TABLE.lines().forEach(line -> {
      int equals = line.indexOf('=');
      String[] parts = line.substring(equals + 1).split(" ");
      rules.put(line.substring(0, equals), new SliderScale(Double.parseDouble(parts[0]),
              Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), parts.length > 3));
    });
    return rules;
  }

  /**
   * The listed range for a setting, or null when it has none. A level's response to a named force gets a range from -1
   * to 2.
   *
   * @param path Setting path.
   */
  private static SliderScale rule(String path) {
    String key = SettingNames.lookupKey(path);
    SliderScale rule = RULES.get(key);
    // Under a level, forces.<name> is how strongly that level responds to the named force.
    return rule == null && key.startsWith("forces.") ? new SliderScale(-1, 2, UNIT_STEP, false) : rule;
  }

  static boolean hasRule(String path, double min, double max) {
    return rule(path) != null || min >= 0 && max <= 1 || isUnitParam(path);
  }

  /**
   * Parameters with no listed rule are fractions from 0 to 1, such as chances and positions along a branch.
   *
   * @param path Setting path.
   */
  private static boolean isUnitParam(String path) {
    return UNIT_PARAMS.contains(SettingNames.lookupKey(path));
  }

  /**
   * The scale for a setting, kept inside what the preset loader allows.
   *
   * @param path  Setting path.
   * @param min   Lowest value the loader allows.
   * @param max   Highest value the loader allows.
   * @param whole Whether only whole numbers are allowed.
   */
  static SliderScale of(String path, double min, double max, boolean whole) {
    SliderScale rule = rule(path);
    if (rule == null) {
      boolean unit = min >= 0 && max <= 1 || isUnitParam(path);
      rule = unit ? new SliderScale(0, 1, UNIT_STEP, false)
              : new SliderScale(Double.isFinite(min) ? min : -OPEN_LIMIT, Double.isFinite(max) ? max : OPEN_LIMIT,
              0.1, false);
    }
    double low = Math.max(rule.low, min);
    double high = Math.min(rule.high, max);
    double step = whole ? Math.max(1, Math.rint(rule.step)) : rule.step;
    return new SliderScale(low, high, step, rule.curved);
  }

  /**
   * Removes floating point noise such as 0.30000000000000004.
   *
   * @param value Value to clean up.
   */
  private static double tidy(double value) {
    return Math.round(value * DECIMALS) / DECIMALS;
  }

  /**
   * The scale for how much this setting varies: from zero up to half its range.
   */
  SliderScale forVariance() {
    return new SliderScale(0, (high - low) / 2, step, false);
  }

  /**
   * The neat value at a slider position, from 0 to {@link #TICKS}.
   *
   * @param tick Slider position.
   */
  double valueAt(int tick) {
    if (tick <= 0) {
      return low;
    }
    if (tick >= TICKS) {
      return high;
    }
    double t = tick / (double) TICKS;
    double value;
    if (curved && low < 0) {
      double around = 2 * t - 1;
      value = Math.signum(around) * around * around * Math.max(-low, high);
    } else {
      value = low + (high - low) * (curved ? t * t : t);
    }
    return Math.max(low, Math.min(high, tidy(Math.round(value / step) * step)));
  }

  /**
   * The slider position closest to a value. Values beyond the range rest at its ends.
   *
   * @param value Value to find.
   */
  int tickOf(double value) {
    double clamped = Math.max(low, Math.min(high, value));
    double t;
    if (curved && low < 0) {
      t = (Math.signum(clamped) * Math.sqrt(Math.abs(clamped) / Math.max(-low, high)) + 1) / 2;
    } else {
      double linear = (clamped - low) / (high - low);
      t = curved ? Math.sqrt(linear) : linear;
    }
    return (int) Math.round(t * TICKS);
  }

  /**
   * The values to label: both ends, plus round values spread evenly along the slider's travel.
   */
  List<Double> marks() {
    List<Double> marks = new ArrayList<>(List.of(low));
    if (high <= low) {
      return marks;
    }
    for (double mark : curved ? curvedCandidates() : evenCandidates()) {
      int tick = tickOf(mark);
      if (mark > low && mark < high && tick - tickOf(marks.getLast()) >= MARK_GAP && TICKS - tick >= MARK_GAP) {
        marks.add(mark);
      }
    }
    marks.add(high);
    return marks;
  }

  /**
   * Label values for a curved slider: rounded values at evenly spaced slider positions.
   */
  private List<Double> curvedCandidates() {
    List<Double> candidates = new ArrayList<>();
    for (int part = 1; part < MARK_PARTS; part++) {
      candidates.add(roundForLabel(valueAt(TICKS * part / MARK_PARTS)));
    }
    return candidates;
  }

  /**
   * Multiples of one round interval, so a straight slider's labels are evenly spaced and include zero.
   */
  private List<Double> evenCandidates() {
    double wanted = (high - low) / MAX_EVEN_PARTS;
    double size = Math.pow(10, Math.floor(Math.log10(wanted)));
    double interval = 10 * size;
    for (double digits : EVEN_DIGITS) {
      if (digits * size >= wanted - SNAP_TOLERANCE) {
        interval = digits * size;
        break;
      }
    }
    interval = Math.ceil(interval / step - SNAP_TOLERANCE) * step;
    List<Double> candidates = new ArrayList<>();
    for (long k = (long) Math.ceil(low / interval); k * interval < high; k++) {
      candidates.add(tidy(k * interval));
    }
    return candidates;
  }

  /**
   * The value rounded to a short, friendly number with the same size, such as 2.5 or 40.
   *
   * @param value Value to round.
   */
  private double roundForLabel(double value) {
    if (value == 0) {
      return 0;
    }
    double size = Math.pow(10, Math.floor(Math.log10(Math.abs(value))));
    double closest = ROUND_DIGITS[0];
    for (double digits : ROUND_DIGITS) {
      if (Math.abs(digits * size - Math.abs(value)) < Math.abs(closest * size - Math.abs(value))) {
        closest = digits;
      }
    }
    return tidy(Math.round(Math.signum(value) * closest * size / step) * step);
  }

  /**
   * The next neat value above, so stepping from 0.012 by 0.005 gives 0.015.
   *
   * @param value Current value.
   */
  double stepUp(double value) {
    return tidy((Math.floor(value / step + SNAP_TOLERANCE) + 1) * step);
  }

  /**
   * The next neat value below.
   *
   * @param value Current value.
   */
  double stepDown(double value) {
    return tidy((Math.ceil(value / step - SNAP_TOLERANCE) - 1) * step);
  }
}
