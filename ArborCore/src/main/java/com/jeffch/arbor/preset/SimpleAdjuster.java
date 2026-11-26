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

import com.jeffch.arbor.math.Vec3;

import java.util.*;

/**
 * Turns the Simple view's whole-tree sliders into an adjusted preset, leaving the stored settings untouched.
 */
public final class SimpleAdjuster {
  /**
   * The world's build height, which the height limit never exceeds.
   */
  private static final double MAX_TREE_HEIGHT = 384;
  /**
   * A start angle of 1 is 180 degrees from the parent.
   */
  private static final double DEGREES_PER_START_ANGLE = 180;
  /**
   * Gravity added to every branch level at full droop.
   */
  private static final double DROOP_STRENGTH = 0.06;
  /**
   * Strength of the hidden wind force at full wind lean.
   */
  private static final double WIND_STRENGTH = 0.025;
  private static final double WIND_HEADING_VARIANCE = 15;
  private static final double WIND_GUST_SIZE = 16;
  private static final double WIND_HEIGHT_POWER = 0.5;
  private static final String WIND_FORCE = SimpleSettings.RESERVED_FORCE_PREFIX + "Wind";

  private SimpleAdjuster() {
  }

  /**
   * The preset as the sliders shape it. With every slider neutral this is the same object.
   *
   * @param preset Preset to adjust.
   */
  public static SpeciesPreset apply(SpeciesPreset preset) {
    SimpleSettings simple = preset.simple();
    if (simple.neutral()) {
      return preset;
    }
    // Branch density compounds down the levels, so each level gets the matching root (the cube root with three
    // branch levels) and the total branch count changes by the slider's factor.
    long branchLevels = preset.levels().stream().filter(level -> level.parent() != null).count();
    double perLevel = branchLevels == 0 ? 1 : StrictMath.pow(simple.branchDensity(), 1.0 / branchLevels);
    String root = preset.levels().get(0).name();
    List<LevelPreset> levels = new ArrayList<>();
    for (LevelPreset level : preset.levels()) {
      levels.add(level(level, simple, perLevel, root));
    }
    List<LeafGenerator> leaves = new ArrayList<>();
    for (LeafGenerator leaf : preset.leaves()) {
      leaves.add(leaf(leaf, simple.leafFullness()));
    }
    // Wind lean adds a wind force under a reserved name, which every level responds to fully.
    List<ForceSettings> forces = new ArrayList<>(preset.forceList());
    if (simple.windLean() > 0) {
      forces.add(new ForceSettings(WIND_FORCE, ForceSettings.Type.WIND, new Vec3(1, 0, 0), Vec3.ZERO,
              simple.windLean() * WIND_STRENGTH, 0,
              new ForceSettings.Wind(WIND_HEADING_VARIANCE, 0, WIND_GUST_SIZE, WIND_HEIGHT_POWER, 0)));
    }
    return new SpeciesPreset(preset.name(), tree(preset.tree(), simple), shapeControl(preset.shapeControl(), simple),
            List.copyOf(forces), preset.shapes(), preset.blocks(), List.copyOf(levels), List.copyOf(leaves),
            preset.plantLayers(), preset.survival(), SimpleSettings.NEUTRAL, preset.decorators());
  }

  /**
   * Raises the height limit with the height slider and applies the size variety slider when it is set.
   *
   * @param tree   Whole-tree settings.
   * @param simple Slider values.
   */
  private static TreeSettings tree(TreeSettings tree, SimpleSettings simple) {
    // More size variety makes the largest seeds taller, so the height limit must not cut their tops off.
    double largestSeed = simple.sizeVariety() == null ? 1
            : Math.max(1, (1 + simple.sizeVariety()) / (1 + tree.sizeVariance()));
    return new TreeSettings(tree.sizeScale(),
            Math.min(MAX_TREE_HEIGHT, tree.maxHeight() * simple.height() * largestSeed),
            tree.sink(), tree.roll(), tree.trunkSpread(),
            simple.sizeVariety() != null ? simple.sizeVariety() : tree.sizeVariance(), tree.ground(), tree.slopeRecovery());
  }

  /**
   * Moves the crown base with the height and widens the crown shape with the crown width.
   *
   * @param control Crown shape settings.
   * @param simple  Slider values.
   */
  private static ShapeControl shapeControl(ShapeControl control, SimpleSettings simple) {
    return new ShapeControl(control.style(), control.crownBase() * simple.height(),
            control.radius().scaled(simple.crownWidth()), control.action(), control.steer(), control.relativeRadius());
  }

  /**
   * One level with every slider applied. The trunk takes height and thickness; branch levels take crown width,
   * density, angle and droop; every level takes twist and wind.
   *
   * @param level    Level to adjust.
   * @param simple   Slider values.
   * @param perLevel Share of the branch density slider each branch level takes, so the levels together multiply to the
   *                 slider.
   * @param root     Name of the root level.
   */
  private static LevelPreset level(LevelPreset level, SimpleSettings simple, double perLevel, String root) {
    boolean isRoot = level.parent() == null;
    double lengthFactor = isRoot ? simple.height() : simple.crownWidth();
    // Lengths sized from the parent already inherit its change, except that the trunk's height must not widen the crown.
    double parentFactor = root.equals(level.parent()) ? simple.crownWidth() / simple.height() : 1;
    double radiusFactor = isRoot ? simple.trunkThickness() : 1;
    return new LevelPreset(level.name(), level.parent(), level.segmentLength(),
            isRoot ? level.generation() : generation(level.generation(), perLevel),
            length(level.length(), lengthFactor, parentFactor),
            isRoot ? level.orientation() : orientation(level.orientation(), simple.branchAngle()),
            isRoot ? level.shape() : shape(level.shape(), simple.droop()), noise(level.noise(), simple.twist()), level.jink(),
            level.path(), level.breakage(), level.prune(), forces(level.forces(), simple.windLean()), radius(level.radius(), radiusFactor, lengthFactor), level.flares(), level.wood());
  }

  /**
   * Multiplies the number of children by {@code factor}, whatever the placement mode: counts and densities grow,
   * while spacings shrink.
   *
   * @param g      Generation settings.
   * @param factor Multiplier for the number of children.
   */
  private static GenerationSettings generation(GenerationSettings g, double factor) {
    boolean byCount = g.mode() == GenerationSettings.Mode.FLOOD || g.mode() == GenerationSettings.Mode.ABSOLUTE;
    return new GenerationSettings(g.mode(), g.first(), g.last(), g.clip(), g.rotation(), g.position(), g.sink(),
            g.pruning(), g.knockout(), g.extendParent(), byCount ? g.count().scaled(factor) : g.count(),
            g.mode() == GenerationSettings.Mode.PROPORTIONAL ? g.density().scaled(factor) : g.density(),
            g.mode() == GenerationSettings.Mode.INTERVAL ? g.frequency().scaled(1 / factor) : g.frequency(),
            g.phyllotaxy(), g.childrenPerNode(),
            g.mode() == GenerationSettings.Mode.PHYLLOTAXY ? g.internodeLength().scaled(1 / factor) : g.internodeLength(),
            g.spread(), g.spiral(), g.limit(), g.align(), g.balance(), g.threshold(),
            g.mode() == GenerationSettings.Mode.BIFURCATION ? g.spacing() / factor : g.spacing(), g.locality());
  }

  /**
   * Scales every way of setting length, and its limits, by {@code factor}; the share of the parent's length by
   * {@code parentFactor}.
   *
   * @param length       Length settings.
   * @param factor       Multiplier for the length.
   * @param parentFactor Multiplier for the share of the parent's length.
   */
  private static LengthSettings length(LengthSettings length, double factor, double parentFactor) {
    return new LengthSettings(length.absolute().scaled(factor), length.percentOfParent().scaled(parentFactor),
            length.percentOfRadius().scaled(factor), length.min() * factor, length.max() * factor, length.shapeScale(),
            length.stopShapeControl());
  }

  /**
   * Turns the start angle by {@code degrees}, staying between along the parent and straight back.
   *
   * @param orientation Orientation settings.
   * @param degrees     Angle to add.
   */
  private static OrientationSettings orientation(OrientationSettings orientation, double degrees) {
    Param angle = orientation.startAngle();
    double base = Math.clamp(angle.base() + degrees / DEGREES_PER_START_ANGLE, 0, 1);
    return new OrientationSettings(new Param(base, angle.treeVariance(), angle.branchVariance(), angle.profile()),
            orientation.startRoll(), orientation.alignment());
  }

  /**
   * Adds droop as gravity: positive droop pulls branches down, negative lifts them.
   *
   * @param shape Shape settings.
   * @param droop Droop amount.
   */
  private static ShapeSettings shape(ShapeSettings shape, double droop) {
    Param gravity = shape.gravity();
    return new ShapeSettings(new Param(gravity.base() + droop * DROOP_STRENGTH, gravity.treeVariance(),
            gravity.branchVariance(), gravity.profile()), shape.ancestor(), shape.straightness(), shape.twist(),
            shape.curlUp(), shape.curlRight(), shape.curlOut(), shape.zigzag(), shape.zigzagLength());
  }

  /**
   * The level's force responses, plus full response to the hidden wind when wind lean is on.
   *
   * @param responses The level's force responses.
   * @param windLean  Wind lean amount; 0 is off.
   */
  private static Map<String, Param> forces(Map<String, Param> responses, double windLean) {
    if (windLean <= 0) {
      return responses;
    }
    Map<String, Param> withWind = new LinkedHashMap<>(responses);
    withWind.put(WIND_FORCE, Param.of(1));
    return Collections.unmodifiableMap(withWind);
  }

  /**
   * Scales both noise amounts by the twist slider, so branches wander more or less.
   *
   * @param noise  Noise settings.
   * @param factor Multiplier for both noise amounts.
   */
  private static NoiseSettings noise(NoiseSettings noise, double factor) {
    return new NoiseSettings(noise.earlyAmount().scaled(factor), noise.earlyTurbulence(), noise.earlyThreshold(),
            noise.lateAmount().scaled(factor), noise.lateTurbulence(), noise.lateSmooth());
  }

  /**
   * Keeps a branch's length-to-radius limit in step, so resizing never removes a branch that fit before.
   *
   * @param radius       Radius settings.
   * @param factor       Multiplier for the radius.
   * @param lengthFactor Multiplier for the length.
   */
  private static RadiusSettings radius(RadiusSettings radius, double factor, double lengthFactor) {
    return new RadiusSettings(radius.absolute().scaled(factor), radius.percentOfParent(), radius.taper(),
            radius.clampToParent(), radius.min() * factor, radius.max() * factor, radius.lengthCompensation(),
            radius.lengthKillRatio() * lengthFactor / factor);
  }

  /**
   * Scales cluster size by leaf fullness and raises or lowers their strength, never above full density.
   *
   * @param leaf   The leaf generator.
   * @param factor Leaf fullness multiplier.
   */
  private static LeafGenerator leaf(LeafGenerator leaf, double factor) {
    Param strength = leaf.strength();
    Param fuller = new Param(Math.min(1, strength.base() * factor), strength.treeVariance(), strength.branchVariance(),
            strength.profile());
    return new LeafGenerator(leaf.parent(), leaf.generation(), leaf.size().scaled(factor),
            leaf.sizePercentOfParent().scaled(factor), leaf.scaleX(), leaf.scaleY(), leaf.scaleZ(), leaf.elongation(),
            leaf.skyInfluence(), leaf.fold(), leaf.align(), leaf.reach(), fuller, leaf.falloff(), leaf.noiseScale(),
            leaf.noiseStrength(), leaf.envelopeEdge(), leaf.shadePruning());
  }
}
