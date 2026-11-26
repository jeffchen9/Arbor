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

package com.jeffch.arbor.skeleton;

import com.jeffch.arbor.math.Frame;
import com.jeffch.arbor.math.TreeRandom;
import com.jeffch.arbor.math.ValueNoise3;
import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.preset.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Grows one branch spine: length, orientation, shaping, limits, radius and removal.
 *
 * <p>A spine is a list of nodes (points with a radius) about one segment length apart. It is built in stages:
 * {@link #grow} walks step by step, turning the heading by gravity, forces, curl, twist and early noise;
 * {@link #deform} then offsets the finished points by path waves, zigzag and late noise, and straightens them in
 * pieces (jink); {@link #applyLimits} cuts the spine at breaks, the height limit and the crown envelope; finally
 * {@link #applyRadius} tapers it and {@link #removed} may prune the whole branch.
 */
public final class SpineBuilder {
  /**
   * Most spine nodes one tree may have.
   */
  public static final int MAX_NODES = 5_000_000;

  /**
   * The middle of the origin block, where the tree's center line starts.
   */
  private static final Vec3 BASE = new Vec3(0.5, 0, 0.5);
  /**
   * The response a level has to a force it does not list.
   */
  private static final Param FULL_RESPONSE = Param.of(1);
  /**
   * Steering begins once a branch is this far out toward the envelope, as a fraction of the radius there.
   */
  private static final double STEER_START = 0.6;
  private final SpineRequest request;
  private final SpeciesPreset preset;
  private final LevelPreset level;
  private final double scale;
  private final double parentT;
  private final Frame parentFrame;
  /**
   * Where the branch meets its parent, or the trunk base on the ground.
   */
  private final Vec3 attach;
  /**
   * Whether the crown envelope limits this branch. Trunks and exempt levels ignore it.
   */
  private final boolean usesEnvelope;
  private final CrownEnvelope envelope;
  /**
   * The heading at the base, set by {@link #orient}.
   */
  private Vec3 startDirection;
  /**
   * The direction away from the parent at the base, which curl out bends toward.
   */
  private Vec3 startRadial;
  /**
   * The sideways direction at the base, which curl right bends toward.
   */
  private Vec3 startSide;

  /**
   * Works out where the branch attaches: on its parent, or for a trunk at the base (sunk, spread out from the other
   * trunks, and set on sloped ground), and whether the crown envelope limits it.
   *
   * @param request The branch to build and everything it needs.
   */
  private SpineBuilder(SpineRequest request) {
    this.request = request;
    preset = request.preset();
    level = preset.levels().get(request.levelIndex());
    scale = preset.tree().sizeScale();
    parentT = request.placement().t();
    parentFrame = request.parent() == null
            ? rootFrame(preset, request.resolver())
            : request.parent().frame(parentT);
    attach = request.parent() == null
            ? onGround(BASE.add(new Vec3(0, -preset.tree().sink() * scale, 0)).add(trunkOffset()))
            : request.parent().point(parentT);
    ShapeControl control = preset.shapeControl();
    usesEnvelope = control.enabled() && request.levelIndex() != 0 && !exemptFromEnvelope();
    envelope = control.envelope();
  }

  /**
   * The nodes of one branch, or empty when it has no length or is removed.
   *
   * @param request The branch to build and everything it needs.
   */
  public static Optional<List<Node>> build(SpineRequest request) {
    return new SpineBuilder(request).run();
  }

  /**
   * The root's sampled length, which sets the tree height before any spine is shaped.
   *
   * @param request The root branch's request.
   */
  public static double sampledLength(SpineRequest request) {
    SpineBuilder builder = new SpineBuilder(request);
    return builder.resolveLength(builder.baseRadius());
  }

  /**
   * The frame of the virtual vertical parent of root stems, rotated by the tree roll.
   *
   * @param preset   Preset whose tree roll is used.
   * @param resolver Rolls the tree's variation.
   */
  public static Frame rootFrame(SpeciesPreset preset, ParamResolver resolver) {
    double roll = StrictMath.toRadians(resolver.bind(preset.tree().roll(), "tree.roll", "root").at(0));
    Vec3 normal = new Vec3(StrictMath.cos(roll), 0, -StrictMath.sin(roll));
    return new Frame(Vec3.UP, normal, Vec3.UP.cross(normal));
  }

  /**
   * False for a point with an infinite or undefined coordinate, which extreme settings can produce.
   *
   * @param point Point to check.
   */
  private static boolean finite(Vec3 point) {
    return Double.isFinite(point.x()) && Double.isFinite(point.y()) && Double.isFinite(point.z());
  }

  /**
   * The unit heading a wind force takes on the tree with this seed.
   *
   * @param force A wind force.
   * @param seed  Tree seed.
   */
  public static Vec3 windHeading(ForceSettings force, long seed) {
    double turn = force.wind().directionVariance() == 0 ? 0 : TreeRandom
            .forPath(seed, "force/" + force.name() + "/heading")
            .range(-force.wind().directionVariance(), force.wind().directionVariance());
    return rotate(force.vector().normalized(), Vec3.UP, StrictMath.toRadians(turn));
  }

  /**
   * The push of a non-wind force at a point, weakening with distance by its falloff. Direction pushes one way,
   * an attractor pulls toward its position, and curl swirls around its axis through the tree's center.
   *
   * @param force A force other than wind.
   * @param point Where to measure the push.
   */
  private static Vec3 forceAt(ForceSettings force, Vec3 point) {
    Vec3 reference = force.type() == ForceSettings.Type.ATTRACTOR ? force.position() : BASE;
    double distance = point.sub(reference).length();
    double strength = force.strength() / (1 + force.falloff() * distance);
    return switch (force.type()) {
      case DIRECTION, WIND -> force.vector().normalized().scale(strength);
      case ATTRACTOR -> force.position().sub(point).normalized().scale(strength);
      case CURL -> force.vector().normalized().cross(point.sub(BASE)).normalized().scale(strength);
    };
  }

  /**
   * The path cut where it reaches {@code fraction} of its length, ending exactly at that point.
   *
   * @param path     Points along the branch.
   * @param fraction Share of the length to keep, from 0 to 1.
   */
  private static List<Vec3> cutAtFraction(List<Vec3> path, double fraction) {
    double total = 0;
    for (int i = 1; i < path.size(); i++) {
      total += path.get(i).sub(path.get(i - 1)).length();
    }
    double target = total * fraction;
    List<Vec3> result = new ArrayList<>();
    result.add(path.get(0));
    double walked = 0;
    for (int i = 1; i < path.size(); i++) {
      double piece = path.get(i).sub(path.get(i - 1)).length();
      if (walked + piece >= target) {
        if (piece > 0) {
          result.add(path.get(i - 1).lerp(path.get(i), (target - walked) / piece));
        }
        return result;
      }
      result.add(path.get(i));
      walked += piece;
    }
    return result;
  }

  /**
   * Noise with small values removed on each axis, so only the larger wobbles bend the branch.
   *
   * @param value     Noise to clean up.
   * @param threshold Size below which a value is removed.
   */
  private static Vec3 thresholded(Vec3 value, double threshold) {
    return new Vec3(soft(value.x(), threshold), soft(value.y(), threshold), soft(value.z(), threshold));
  }

  /**
   * 0 inside the threshold; beyond it, the excess rescaled so the full range still reaches 1.
   *
   * @param value     Value from -1 to 1.
   * @param threshold Size below which the value becomes 0.
   */
  private static double soft(double value, double threshold) {
    double magnitude = Math.abs(value);
    if (magnitude <= threshold || threshold >= 1) {
      return 0;
    }
    return Math.signum(value) * (magnitude - threshold) / (1 - threshold);
  }

  /**
   * Rotates a vector around a unit axis (Rodrigues' formula).
   *
   * @param vector  Vector to rotate.
   * @param axis    Unit axis.
   * @param radians Angle to turn.
   */
  private static Vec3 rotate(Vec3 vector, Vec3 axis, double radians) {
    if (radians == 0) {
      return vector;
    }
    double cosine = StrictMath.cos(radians);
    double sine = StrictMath.sin(radians);
    return vector.scale(cosine)
            .add(axis.cross(vector).scale(sine))
            .add(axis.scale(axis.dot(vector) * (1 - cosine)));
  }

  /**
   * Smoothstep from 0 to 1, clamped outside that range.
   *
   * @param value Input value.
   */
  private static double smooth(double value) {
    double t = Math.clamp(value, 0, 1);
    return t * t * (3 - 2 * t);
  }

  /**
   * Puts a trunk base on sloped ground, starting slightly under it along its lean so no edge floats.
   *
   * @param base Trunk base before it is moved.
   */
  private Vec3 onGround(Vec3 base) {
    GroundPlane ground = preset.tree().ground();
    if (ground.flat()) {
      return base;
    }
    Vec3 surface = base.add(new Vec3(0, ground.heightAt(base.x(), base.z()), 0));
    Vec3 lean = preset.tree().slopeRecovery() > 0 ? ground.normal().normalized() : Vec3.UP;
    return surface.sub(lean.scale(baseRadius() * ground.steepness()));
  }

  /**
   * Moves a trunk out from the tree's center, in the direction it leans, when the tree has several.
   */
  private Vec3 trunkOffset() {
    double spread = preset.tree().trunkSpread() * scale;
    if (spread <= 0 || request.rootCount() < 2) {
      return Vec3.ZERO;
    }
    double roll = StrictMath.toRadians(request.placement().rollDeg()
            + bind(level.orientation().startRoll(), "orientation.startRoll").at(parentT));
    Vec3 normal = parentFrame.normal();
    Vec3 binormal = parentFrame.tangent().cross(normal).normalized();
    Vec3 radial = normal.scale(StrictMath.cos(roll)).add(binormal.scale(StrictMath.sin(roll)));
    double reach = 0.6 + 0.8 * TreeRandom.forPath(request.seed(), request.pathId() + "#trunk-spread").nextDouble();
    return radial.scale(spread * reach);
  }

  /**
   * Runs every stage for this branch.
   */
  private Optional<List<Node>> run() {
    double baseRadius = baseRadius();
    if (!Double.isFinite(baseRadius) || baseRadius > GenerationBudget.WORLD_LIMIT) {
      throw invalid("radius is too large");
    }
    orient();
    // Sink starts the branch a little inside its parent, so the joint has no gap.
    Vec3 start = attach.sub(startDirection.scale(request.placement().sink() * scale));
    double length = fitToEnvelope(resolveLength(baseRadius), start);
    if (length <= 0) {
      return Optional.empty();
    }
    // Split the length into equal steps about one segment length each.
    request.budget().charge(length / (level.segmentLength() * scale));
    int segments = Math.max(1, (int) Math.min(MAX_NODES + 1.0, Math.ceil(length / (level.segmentLength() * scale))));
    if (segments > MAX_NODES) {
      throw new GenerationLimitException("preset exceeds the limit of " + MAX_NODES + " spine nodes");
    }
    double step = length / segments;
    Vec3[] points = grow(start, segments, step);
    points = deform(points, step);
    for (Vec3 point : points) {
      if (!finite(point)) {
        throw invalid("settings produced invalid positions");
      }
    }
    List<Vec3> limited = applyLimits(points);
    if (limited.size() < 2) {
      return Optional.empty();
    }
    for (Vec3 point : limited) {
      if (Math.abs(point.x()) > GenerationBudget.WORLD_LIMIT || Math.abs(point.y()) > GenerationBudget.WORLD_LIMIT
              || Math.abs(point.z()) > GenerationBudget.WORLD_LIMIT) {
        throw invalid("branch extends more than " + (int) GenerationBudget.WORLD_LIMIT + " blocks from the origin");
      }
    }
    List<Node> nodes = applyRadius(limited, baseRadius, length);
    if (removed(limited, baseRadius, length)) {
      return Optional.empty();
    }
    return Optional.of(List.copyOf(nodes));
  }

  /**
   * An error naming this branch's level.
   *
   * @param problem What is wrong.
   */
  private GenerationLimitException invalid(String problem) {
    return new GenerationLimitException("level '" + level.name() + "': " + problem);
  }

  /**
   * One of this level's settings with its variation rolled for this branch.
   *
   * @param param   The setting.
   * @param setting Setting path within the level, such as {@code radius.base}.
   */
  private BoundParam bind(Param param, String setting) {
    return request.resolver().bind(param, "levels." + level.name() + "." + setting, request.pathId());
  }

  /**
   * Whether this level, or any level it grows from, is set to ignore the crown shape.
   */
  private boolean exemptFromEnvelope() {
    LevelPreset current = level;
    while (current != null) {
      if (current.length().stopShapeControl()) {
        return true;
      }
      String parentName = current.parent();
      current = parentName == null ? null : preset.levels().stream()
              .filter(candidate -> candidate.name().equals(parentName))
              .findFirst().orElse(null);
    }
    return false;
  }

  /**
   * The radius where the branch starts: an absolute size plus a share of the parent's radius there, clamped to the
   * level's range and, when asked, to the parent.
   */
  private double baseRadius() {
    RadiusSettings settings = level.radius();
    double radius = Math.max(0, bind(settings.absolute(), "radius.absolute").at(parentT)) * scale;
    double parentRadius = request.parent() == null ? 0 : request.parent().radius(parentT);
    radius += Math.max(0, bind(settings.percentOfParent(), "radius.percentOfParent").at(parentT)) * parentRadius;
    radius = Math.clamp(radius, settings.min() * scale, settings.max() * scale);
    if (request.parent() != null && settings.clampToParent()) {
      radius = Math.min(radius, parentRadius);
    }
    return radius;
  }

  /**
   * The planned length: an absolute length plus shares of the parent's length and of this branch's radius, clamped
   * to the level's range. Zero means the branch is not grown.
   *
   * @param baseRadius This branch's radius at its base.
   */
  private double resolveLength(double baseRadius) {
    LengthSettings settings = level.length();
    double parentLength = request.parent() == null ? 0 : request.parent().length();
    double value = Math.max(0, bind(settings.absolute(), "length.absolute").at(parentT)) * scale
            + Math.max(0, bind(settings.percentOfParent(), "length.percentOfParent").at(parentT)) * parentLength
            + Math.max(0, bind(settings.percentOfRadius(), "length.percentOfRadius").at(parentT)) * baseRadius;
    if (value <= 0) {
      return 0;
    }
    return Math.clamp(value, settings.min() * scale, settings.max() * scale);
  }

  /**
   * Sets the starting heading. The branch leaves its parent at the start angle (0 along the parent, 0.5 straight
   * out), on the side picked by its roll. Alignment turns the parent's frame toward the sky first, and align turns
   * the chosen side toward the sky, so branches favor growing upward instead of down.
   */
  private void orient() {
    Vec3 tangent = parentFrame.tangent();
    // "Up" as seen from the parent: the sky direction with the part along the parent removed.
    Vec3 sky = Vec3.UP.sub(tangent.scale(Vec3.UP.dot(tangent))).normalized();
    Vec3 normal = parentFrame.normal();
    double alignment = level.orientation().alignment();
    if (alignment > 0 && !sky.equals(Vec3.ZERO)) {
      Vec3 blended = normal.lerp(sky, alignment).normalized();
      normal = blended.equals(Vec3.ZERO) ? sky : blended;
    }
    Vec3 binormal = tangent.cross(normal).normalized();
    double rollDeg = request.placement().rollDeg() + bind(level.orientation().startRoll(), "orientation.startRoll").at(parentT);
    double roll = StrictMath.toRadians(rollDeg);
    Vec3 radial = normal.scale(StrictMath.cos(roll)).add(binormal.scale(StrictMath.sin(roll)));
    double align = level.generation().align();
    if (align > 0 && !sky.equals(Vec3.ZERO)) {
      Vec3 blended = radial.lerp(sky, align).normalized();
      radial = blended.equals(Vec3.ZERO) ? sky : blended;
    }
    double angle = request.placement().extendsParent() ? 0
            : bind(level.orientation().startAngle(), "orientation.startAngle").at(parentT) * Math.PI;
    startDirection = tangent.scale(StrictMath.cos(angle)).add(radial.scale(StrictMath.sin(angle))).normalized();
    startRadial = radial;
    startSide = tangent.cross(radial).normalized();
  }

  /**
   * With the "scale length" crown action, shortens the branch to the distance it can travel in a straight line
   * before leaving the envelope, times the level's shape scale. Other actions keep the length.
   *
   * @param length Planned length.
   * @param start  Where the branch starts.
   */
  private double fitToEnvelope(double length, Vec3 start) {
    if (!usesEnvelope || preset.shapeControl().action() != ShapeControl.Action.SCALE_LENGTH || length <= 0) {
      return length;
    }
    request.budget().charge(length / 0.1);
    double inside = 0;
    for (double distance = 0; distance <= length; distance += 0.1) {
      if (!insideEnvelope(start.add(startDirection.scale(distance)))) {
        break;
      }
      inside = distance;
    }
    return Math.min(length, inside * level.length().shapeScale());
  }

  /**
   * Whether a point lies inside the crown envelope, measured around the trunks' center line.
   *
   * @param point Point to check.
   */
  private boolean insideEnvelope(Vec3 point) {
    return envelope.contains(centered(point), request.treeHeight(), scale);
  }

  /**
   * The point moved so the trunk at its height sits on the center line; unchanged while trunks are still growing.
   *
   * @param point Point to move.
   */
  private Vec3 centered(Vec3 point) {
    return request.axis() == null ? point : request.axis().recenter(point);
  }

  /**
   * Walks the spine one step at a time. At each step the heading is nudged by early noise, gravity, the pull of the
   * parent, every force, the three curls and twist, then steered along the envelope; the next point is one step
   * along the new heading.
   *
   * @param start    Where the branch starts.
   * @param segments Number of steps.
   * @param step     Length of each step.
   */
  private Vec3[] grow(Vec3 start, int segments, double step) {
    ShapeSettings shape = level.shape();
    NoiseSettings noise = level.noise();
    BoundParam gravity = bind(shape.gravity(), "shape.gravity");
    BoundParam ancestor = bind(shape.ancestor(), "shape.ancestor");
    BoundParam straightness = bind(shape.straightness(), "shape.straightness");
    BoundParam twist = bind(shape.twist(), "shape.twist");
    BoundParam curlUp = bind(shape.curlUp(), "shape.curlUp");
    BoundParam curlRight = bind(shape.curlRight(), "shape.curlRight");
    BoundParam curlOut = bind(shape.curlOut(), "shape.curlOut");
    BoundParam earlyAmount = bind(noise.earlyAmount(), "noise.earlyAmount");
    SmoothNoise earlyNoise = earlyAmount.value() == 0 ? null : new SmoothNoise(
            TreeRandom.forPath(request.seed(), request.pathId() + "#noise-early"),
            2 + (int) Math.round(noise.earlyTurbulence() * segments));
    List<BoundForce> forces = new ArrayList<>(preset.forceList().size());
    for (ForceSettings force : preset.forceList()) {
      Param response = level.forces().getOrDefault(force.name(), FULL_RESPONSE);
      forces.add(new BoundForce(force, bind(response, "forces." + force.name()), windHeading(force),
              gustNoise(force)));
    }
    // Thick branches resist wind: 1 for a radius of one block or more.
    double thickness = Math.min(1, baseRadius() / scale);

    Vec3[] points = new Vec3[segments + 1];
    points[0] = start;
    Vec3 direction = startDirection;
    for (int i = 0; i < segments; i++) {
      double s = i / (double) segments;
      Vec3 point = points[i];
      Vec3 heading = direction;
      // Bending settings are per block at unit scale, so segment length does not change the shape.
      double blocks = step / scale;
      if (earlyNoise != null) {
        // Straightness quiets the noise near the base, fading out toward the tip.
        double amount = earlyAmount.at(s) * (1 - straightness.at(s) * (1 - s)) * blocks;
        heading = heading.add(thresholded(earlyNoise.at(s), noise.earlyThreshold()).scale(amount));
      }
      heading = heading.add(Vec3.UP.scale(-gravity.at(s) * blocks));
      heading = applyAncestor(heading, point, ancestor.at(s) * blocks);
      for (BoundForce force : forces) {
        Vec3 push = force.force().type() == ForceSettings.Type.WIND
                ? windAt(force, point, thickness) : forceAt(force.force(), point);
        heading = heading.add(push.scale(force.response().at(s) * blocks));
      }
      // Curls are in degrees per block, toward the parent's direction, the side, and away from the parent.
      double perStep = StrictMath.toRadians(1) * blocks;
      heading = heading
              .add(parentFrame.tangent().scale(curlUp.at(s) * perStep))
              .add(startSide.scale(curlRight.at(s) * perStep))
              .add(startRadial.scale(curlOut.at(s) * perStep));
      heading = rotate(heading, startDirection, StrictMath.toRadians(twist.at(s) * blocks));
      heading = applySteer(heading, point);
      if (!finite(heading) || !Double.isFinite(heading.length())) {
        throw invalid("bending settings are too large");
      }
      // If the pushes cancel out exactly, keep going the same way.
      Vec3 next = heading.normalized();
      direction = next.equals(Vec3.ZERO) ? direction : next;
      points[i + 1] = point.add(slopeBent(direction, (i + 0.5) / segments).scale(step));
    }
    return points;
  }

  /**
   * Tilts a trunk's growth toward the ground normal near its base, fading out with height. Only the drawn step is
   * tilted, so sun, wind and noise keep acting on the upright heading the trunk recovers to.
   *
   * @param direction Growth direction.
   * @param s         Fraction of the way along the branch.
   */
  private Vec3 slopeBent(Vec3 direction, double s) {
    double recovery = preset.tree().slopeRecovery();
    if (request.parent() != null || preset.tree().ground().flat() || recovery <= 0) {
      return direction;
    }
    Vec3 normal = preset.tree().ground().normal().normalized();
    double angle = StrictMath.acos(Math.clamp(normal.y(), -1, 1)) * (1 - smooth(s / recovery));
    return rotate(direction, Vec3.UP.cross(normal).normalized(), angle);
  }

  /**
   * Bends toward or away from the parent. A heading directly opposed turns along the parent to bend.
   *
   * @param heading Current growth direction.
   * @param point   Current point.
   * @param amount  How strongly to bend: positive toward the parent, negative away.
   */
  private Vec3 applyAncestor(Vec3 heading, Vec3 point, double amount) {
    if (request.parent() == null || amount == 0) {
      return heading;
    }
    // The direction back to the parent's center line, ignoring how far along the parent the point is.
    Vec3 offset = point.sub(attach);
    Vec3 toParent = offset.sub(parentFrame.tangent().scale(offset.dot(parentFrame.tangent()))).normalized().scale(-1);
    Vec3 pull = toParent.scale(amount);
    double opposing = Math.max(0, -heading.normalized().dot(pull.normalized()));
    return heading.add(pull).add(parentFrame.tangent().scale(Math.abs(amount) * opposing));
  }

  /**
   * The wind's heading for this tree. Every branch shares it, so the whole tree leans one way.
   *
   * @param force A wind force.
   */
  private Vec3 windHeading(ForceSettings force) {
    return force.type() != ForceSettings.Type.WIND ? null : windHeading(force, request.seed());
  }

  /**
   * One gust pattern per tree and force, so neighboring branches feel the same gust.
   *
   * @param force A wind force.
   */
  private ValueNoise3 gustNoise(ForceSettings force) {
    if (force.type() != ForceSettings.Type.WIND) {
      return null;
    }
    return new ValueNoise3(Double.doubleToRawLongBits(
            TreeRandom.forPath(request.seed(), "force/" + force.name() + "/gust").nextDouble()));
  }

  /**
   * Wind grows with height, varies with gusts, and bends a branch more the thinner it is.
   *
   * @param bound     The wind and this branch's response to it.
   * @param point     Current point.
   * @param thickness Branch radius here.
   */
  private Vec3 windAt(BoundForce bound, Vec3 point, double thickness) {
    ForceSettings force = bound.force();
    ForceSettings.Wind wind = force.wind();
    double strength = force.strength() / (1 + force.falloff() * point.sub(BASE).length());
    // 0 at the ground and 1 at the treetop, raised to the height power so the top sways most.
    double height = request.treeHeight() <= 0 ? 1 : Math.clamp(point.y() / request.treeHeight(), 0, 1);
    double size = wind.gustSize() * scale;
    double gust = 1 + wind.gust() * (2 * bound.gusts().at(point.x() / size, point.y() / size, point.z() / size) - 1);
    double flex = 1 + wind.flexibility() * (1 - thickness);
    return bound.heading().scale(strength * StrictMath.pow(height, wind.heightPower()) * gust * flex);
  }

  /**
   * Near the boundary, turns outward growth along the crown surface instead.
   *
   * @param heading Current growth direction.
   * @param point   Current point.
   */
  private Vec3 applySteer(Vec3 heading, Vec3 point) {
    double steer = preset.shapeControl().steer();
    if (!usesEnvelope || steer <= 0) {
      return heading;
    }
    double limit = envelope.radiusAt(point.y(), request.treeHeight(), scale);
    if (limit <= 0) {
      return heading;
    }
    Vec3 relative = centered(point);
    double proximity = CrownEnvelope.radialDistance(relative) / limit;
    if (proximity <= STEER_START) {
      return heading;
    }
    Vec3 outward = new Vec3(relative.x() - BASE.x(), 0, relative.z() - BASE.z()).normalized();
    double outwardSpeed = heading.dot(outward);
    if (outwardSpeed <= 0) {
      return heading;
    }
    // Grows from 0 where steering starts to full strength at the envelope. The outward part of the heading is
    // moved, in proportion, into the direction along the surface.
    double strength = Math.min(1, steer * (proximity - STEER_START) / (1 - STEER_START));
    Vec3 along = heading.sub(outward.scale(outwardSpeed));
    along = along.length() < 1e-6 ? Vec3.UP : along.normalized();
    return heading.sub(outward.scale(outwardSpeed * strength)).add(along.scale(outwardSpeed * strength));
  }

  /**
   * Offsets the grown points sideways without changing the heading that produced them: a repeating wave (path), a
   * sawtooth zigzag, and late noise. The base point never moves, so the branch stays attached.
   *
   * @param points Grown points; changed in place.
   * @param step   Length of each step.
   */
  private Vec3[] deform(Vec3[] points, double step) {
    ShapeSettings shape = level.shape();
    NoiseSettings noise = level.noise();
    PathSettings pathSettings = level.path();
    BoundParam pathAmount = bind(pathSettings.amount(), "path.amount");
    BoundParam pathOffset = bind(pathSettings.offset(), "path.offset");
    BoundParam zigzag = bind(shape.zigzag(), "shape.zigzag");
    BoundParam zigzagLength = bind(shape.zigzagLength(), "shape.zigzagLength");
    BoundParam lateAmount = bind(noise.lateAmount(), "noise.lateAmount");
    BoundParam straightness = bind(shape.straightness(), "shape.straightness");
    int segments = points.length - 1;
    SmoothNoise lateNoise = lateAmount.value() == 0 ? null : new SmoothNoise(
            TreeRandom.forPath(request.seed(), request.pathId() + "#noise-late"),
            2 + (int) Math.round(noise.lateTurbulence() * segments * (1 - noise.lateSmooth())));
    double tile = pathSettings.length() * scale;
    List<Frame> frames = Frame.parallelTransport(Arrays.asList(points), parentFrame.normal());

    Vec3[] deformed = new Vec3[points.length];
    deformed[0] = points[0];
    for (int i = 1; i < points.length; i++) {
      double s = i / (double) segments;
      double arc = i * step;
      Frame frame = frames.get(i);
      Vec3 offset = Vec3.ZERO;
      if (pathAmount.value() != 0) {
        // A sine wave one tile long, whose side turns by the path twist per tile and which eases in over the first
        // quarter tile.
        double rotation = StrictMath.toRadians(pathSettings.twist()) * arc / tile;
        Vec3 lateral = frame.normal().scale(StrictMath.cos(rotation)).add(frame.binormal().scale(StrictMath.sin(rotation)));
        double phase = 2 * Math.PI * (arc / tile + pathOffset.at(s));
        offset = offset.add(lateral.scale(StrictMath.sin(phase) * pathAmount.at(s) * scale
                * smooth(arc / (tile * 0.25))));
      }
      double zigAngle = zigzag.at(s);
      if (zigAngle != 0) {
        // A triangle wave whose slope gives the zigzag angle.
        double zigLength = Math.max(0.25, zigzagLength.at(s)) * scale;
        double phase = (arc / zigLength) % 2;
        double triangle = phase <= 1 ? phase : 2 - phase;
        offset = offset.add(frame.binormal().scale(triangle * zigLength * StrictMath.tan(StrictMath.toRadians(zigAngle))));
      }
      if (lateNoise != null) {
        // Faded in over the first 15% so the base stays where it attaches.
        double amount = lateAmount.at(s) * (1 - straightness.at(s) * (1 - s)) * smooth(s / 0.15) * scale;
        offset = offset.add(lateNoise.at(s).scale(amount));
      }
      deformed[i] = points[i].add(offset);
    }
    return applyJink(deformed);
  }

  /**
   * Makes the branch angular: it is split into a few pieces and each piece is pulled toward a straight line between
   * its ends, so the bends gather at the joints. Uniformity controls how evenly the joints are spaced.
   *
   * @param points Points of the branch; changed in place.
   */
  private Vec3[] applyJink(Vec3[] points) {
    JinkSettings jink = level.jink();
    if (jink.amount() <= 0) {
      return points;
    }
    int segments = points.length - 1;
    int pieces = Math.max(1, Math.min(segments, (int) Math.round(bind(jink.segments(), "jink.segments").at(0.5))));
    TreeRandom random = TreeRandom.forPath(request.seed(), request.pathId() + "#jink");
    int[] breaks = new int[pieces + 1];
    breaks[pieces] = segments;
    // Joint positions: evenly spaced, jittered, and kept in order with at least one segment between them.
    for (int j = 1; j < pieces; j++) {
      double jitter = random.range(-0.5, 0.5) * (1 - jink.uniformity());
      int index = (int) Math.round((j + jitter) * segments / pieces);
      breaks[j] = Math.max(breaks[j - 1] + 1, Math.min(segments - (pieces - j), index));
    }
    Vec3[] jinked = points.clone();
    for (int j = 0; j < pieces; j++) {
      Vec3 from = points[breaks[j]];
      Vec3 to = points[breaks[j + 1]];
      int span = breaks[j + 1] - breaks[j];
      for (int i = breaks[j]; i <= breaks[j + 1]; i++) {
        Vec3 straight = from.lerp(to, (i - breaks[j]) / (double) span);
        jinked[i] = points[i].lerp(straight, jink.amount());
      }
    }
    return jinked;
  }

  /**
   * Shortens the spine: a random break, then the tree's height limit, then the crown envelope.
   *
   * @param points Points of the branch.
   */
  private List<Vec3> applyLimits(Vec3[] points) {
    List<Vec3> path = new ArrayList<>(Arrays.asList(points));
    BreakSettings breakage = level.breakage();
    if (breakage.chance() > 0
            && TreeRandom.forPath(request.seed(), request.pathId() + "#break").chance(breakage.chance())) {
      path = cutAtFraction(path, Math.clamp(bind(breakage.spot(), "break.spot").at(parentT), 0.05, 1));
    }
    path = cutAtHeight(path);
    if (usesEnvelope) {
      path = cutAtEnvelope(path);
    }
    return path;
  }

  /**
   * The path up to where it first rises past the maximum height, ending exactly at that height. Empty when it
   * starts above it.
   *
   * @param path Points along the branch.
   */
  private List<Vec3> cutAtHeight(List<Vec3> path) {
    double maximum = preset.tree().maxHeight();
    if (path.get(0).y() > maximum) {
      return List.of();
    }
    List<Vec3> result = new ArrayList<>();
    result.add(path.get(0));
    for (int i = 1; i < path.size(); i++) {
      Vec3 point = path.get(i);
      if (point.y() > maximum) {
        Vec3 previous = path.get(i - 1);
        result.add(previous.lerp(point, (maximum - previous.y()) / (point.y() - previous.y())));
        return result;
      }
      result.add(point);
    }
    return result;
  }

  /**
   * The path up to where it first leaves the envelope. The exit point is found by halving the last segment 24
   * times, which pins it to well under a millionth of a block. Empty when the branch starts outside.
   *
   * @param path Points along the branch.
   */
  private List<Vec3> cutAtEnvelope(List<Vec3> path) {
    if (path.isEmpty() || !insideEnvelope(path.get(0))) {
      return List.of();
    }
    List<Vec3> result = new ArrayList<>();
    result.add(path.get(0));
    for (int i = 1; i < path.size(); i++) {
      Vec3 point = path.get(i);
      if (insideEnvelope(point)) {
        result.add(point);
        continue;
      }
      Vec3 inside = path.get(i - 1);
      Vec3 outside = point;
      for (int iteration = 0; iteration < 24; iteration++) {
        Vec3 middle = inside.lerp(outside, 0.5);
        if (insideEnvelope(middle)) {
          inside = middle;
        } else {
          outside = middle;
        }
      }
      result.add(inside);
      return result;
    }
    return result;
  }

  /**
   * Gives every point a radius from the taper curve, measured against the planned length so a branch cut short
   * keeps a thick end rather than re-tapering. Length compensation pulls the radius back toward the base radius.
   *
   * @param path          Points along the branch.
   * @param baseRadius    Radius at the base.
   * @param plannedLength Length before any cuts.
   */
  private List<Node> applyRadius(List<Vec3> path, double baseRadius, double plannedLength) {
    RadiusSettings settings = level.radius();
    List<Node> nodes = new ArrayList<>(path.size());
    double arc = 0;
    for (int i = 0; i < path.size(); i++) {
      if (i > 0) {
        arc += path.get(i).sub(path.get(i - 1)).length();
      }
      double s = Math.min(1, arc / plannedLength);
      double radius = baseRadius * settings.taper().eval(s);
      radius += (baseRadius - radius) * settings.lengthCompensation() * s;
      nodes.add(new Node(path.get(i), Math.clamp(radius, settings.min() * scale, settings.max() * scale)));
    }
    return nodes;
  }

  /**
   * Whether pruning removes the whole branch: at random; more often the more it points up or down; more often the
   * more it points back in toward the trunk; when it dips under the ground; or when it is too long for its radius.
   *
   * @param path       Points along the branch.
   * @param baseRadius Radius at the base.
   * @param length     Planned length.
   */
  private boolean removed(List<Vec3> path, double baseRadius, double length) {
    PruneSettings prune = level.prune();
    // Every roll is drawn even when unused, so changing one prune setting never reshuffles the others.
    TreeRandom random = TreeRandom.forPath(request.seed(), request.pathId() + "#prune");
    double generalRoll = random.nextDouble();
    double upRoll = random.nextDouble();
    double downRoll = random.nextDouble();
    double interiorRoll = random.nextDouble();
    double groundRoll = random.nextDouble();
    Vec3 start = path.get(0);
    Vec3 direction = path.get(path.size() - 1).sub(start).normalized();
    if (generalRoll < prune.general()
            || upRoll < prune.up() * Math.max(0, direction.y())
            || downRoll < prune.down() * Math.max(0, -direction.y())) {
      return true;
    }
    Vec3 outward = new Vec3(start.x() - BASE.x(), 0, start.z() - BASE.z()).normalized();
    if (interiorRoll < prune.interior() * Math.max(0, -direction.dot(outward))) {
      return true;
    }
    GroundPlane ground = preset.tree().ground();
    // A trunk on a slope starts under the ground on purpose.
    boolean buriedBase = request.parent() == null && !ground.flat();
    if (prune.ground() > 0 && groundRoll < prune.ground() && !buriedBase
            && path.stream().anyMatch(point -> point.y() < ground.heightAt(point.x(), point.z()))) {
      return true;
    }
    double killRatio = level.radius().lengthKillRatio();
    return killRatio > 0 && (baseRadius <= 0 || length / baseRadius > killRatio);
  }

  /**
   * Everything needed to grow one branch. A null parent means a root stem.
   *
   * @param preset     Species being generated.
   * @param levelIndex Index of this branch's level in the preset.
   * @param parent     Sampler of the parent branch, or null for a trunk.
   * @param placement  Where this branch starts on its parent.
   * @param treeHeight Height used to measure the crown.
   * @param pathId     Unique path of this branch.
   * @param resolver   Applies this seed's random variance to parameters.
   * @param seed       Tree seed.
   * @param budget     Work limit that stops runaway presets and allows cancelling.
   * @param axis       Center line of the trunks, used by the crown shape; null while trunks are built.
   * @param rootCount  Number of trunks on this tree.
   */
  public record SpineRequest(SpeciesPreset preset, int levelIndex, BranchSampler parent, Placement placement,
                             double treeHeight, String pathId, ParamResolver resolver, long seed,
                             GenerationBudget budget, TrunkAxis axis, int rootCount) {
  }

  /**
   * A force with this branch's response.
   *
   * @param force    The force settings.
   * @param response This branch's multiplier on the force.
   * @param heading  This tree's wind direction, or null for other force types.
   * @param gusts    This tree's gust pattern, or null for other force types.
   */
  private record BoundForce(ForceSettings force, BoundParam response, Vec3 heading, ValueNoise3 gusts) {
  }

  /**
   * Smooth 3D noise along a branch, in the range -1 to 1.
   */
  private static final class SmoothNoise {
    /**
     * Random x, y, z values at evenly spaced knots along the branch.
     */
    private final double[] knots;
    private final int count;

    /**
     * @param random Random source for the knots.
     * @param count  Number of knots; more knots give a busier, more turbulent wobble.
     */
    SmoothNoise(TreeRandom random, int count) {
      this.count = Math.max(2, count);
      knots = new double[this.count * 3];
      for (int i = 0; i < knots.length; i++) {
        knots[i] = random.range(-1, 1);
      }
    }

    /**
     * The noise at fraction {@code s} along the branch, eased between the two nearest knots.
     *
     * @param s Fraction of the way along the branch, from 0 to 1.
     */
    Vec3 at(double s) {
      double position = Math.clamp(s, 0, 1) * (count - 1);
      int lower = Math.min(count - 2, (int) position);
      double blend = smooth(position - lower);
      return new Vec3(component(lower, 0, blend), component(lower, 1, blend), component(lower, 2, blend));
    }

    /**
     * One axis of the noise between knot {@code lower} and the next.
     *
     * @param lower Index of the knot before the point.
     * @param axis  0 for x, 1 for y, 2 for z.
     * @param blend How far toward the next knot, from 0 to 1.
     */
    private double component(int lower, int axis, double blend) {
      double a = knots[lower * 3 + axis];
      return a + (knots[(lower + 1) * 3 + axis] - a) * blend;
    }
  }
}
