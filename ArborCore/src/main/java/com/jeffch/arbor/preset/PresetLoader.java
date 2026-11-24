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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jeffch.arbor.math.Vec3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Loads and validates species presets, naming the JSON path of every problem.
 */
public final class PresetLoader {
  private static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .build());
  private static final double INF = Double.POSITIVE_INFINITY;
  private static final Curve DEFAULT_TAPER = Curve.of(0, 1, 1, 0.15);

  private static final Map<String, GenerationSettings.Mode> MODES = Map.of(
          "flood", GenerationSettings.Mode.FLOOD,
          "absolute", GenerationSettings.Mode.ABSOLUTE,
          "proportional", GenerationSettings.Mode.PROPORTIONAL,
          "interval", GenerationSettings.Mode.INTERVAL,
          "phyllotaxy", GenerationSettings.Mode.PHYLLOTAXY,
          "bifurcation", GenerationSettings.Mode.BIFURCATION,
          "parent", GenerationSettings.Mode.PARENT);
  private static final Map<String, GenerationSettings.Phyllotaxy> PHYLLOTAXY = Map.of(
          "opposite_distichous", GenerationSettings.Phyllotaxy.OPPOSITE_DISTICHOUS,
          "opposite_decussate", GenerationSettings.Phyllotaxy.OPPOSITE_DECUSSATE,
          "opposite_random", GenerationSettings.Phyllotaxy.OPPOSITE_RANDOM,
          "alternate_distichous", GenerationSettings.Phyllotaxy.ALTERNATE_DISTICHOUS,
          "alternate_random", GenerationSettings.Phyllotaxy.ALTERNATE_RANDOM,
          "whorled", GenerationSettings.Phyllotaxy.WHORLED);
  private static final Map<String, ShapeControl.Style> STYLES = Map.of(
          "none", ShapeControl.Style.NONE,
          "envelope", ShapeControl.Style.ENVELOPE);
  private static final Map<String, ShapeControl.Action> ACTIONS = Map.of(
          "stop", ShapeControl.Action.STOP,
          "scaleLength", ShapeControl.Action.SCALE_LENGTH);
  private static final Map<String, ForceSettings.Type> FORCE_TYPES = Map.of(
          "direction", ForceSettings.Type.DIRECTION,
          "attractor", ForceSettings.Type.ATTRACTOR,
          "curl", ForceSettings.Type.CURL,
          "wind", ForceSettings.Type.WIND);
  private static final List<String> WIND_KEYS = List.of("directionVariance", "gust", "gustSize", "heightPower",
          "flexibility");
  private static final Map<String, PlantLayer.Side> SIDES = Map.of(
          "any", PlantLayer.Side.ANY,
          "top", PlantLayer.Side.TOP,
          "underside", PlantLayer.Side.UNDERSIDE,
          "outer", PlantLayer.Side.OUTER,
          "inner", PlantLayer.Side.INNER,
          "onTallPlant", PlantLayer.Side.ON_TALL_PLANT);
  private static final Map<String, PlantLayer.DoubleHalf> DOUBLE_HALVES = Map.of(
          "both", PlantLayer.DoubleHalf.BOTH,
          "bottom", PlantLayer.DoubleHalf.BOTTOM,
          "top", PlantLayer.DoubleHalf.TOP);
  private static final String ANY_BRANCH = "any";
  private static final double SURVIVAL_LEAF_DENSITY = 0.12;
  private static final Map<String, LeafGenerator.ShadeStyle> SHADE_STYLES = Map.of(
          "none", LeafGenerator.ShadeStyle.NONE,
          "interior", LeafGenerator.ShadeStyle.INTERIOR,
          "exterior", LeafGenerator.ShadeStyle.EXTERIOR);

  private static final List<String> REMOVED_SHAPE_SETTINGS = List.of("leafPlantDensity", "leafPlantChance", "largeFernChance");

  private static final Pattern BLOCK_NAME = Pattern.compile(
          "[a-z0-9_]+(:[a-z0-9_/]+)?(\\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*\\])?");

  private PresetLoader() {
  }

  /**
   * Reads, parses and validates a preset file.
   *
   * @param file Preset file to read.
   */
  public static SpeciesPreset load(Path file) {
    return parse(file, parseText(file, readText(file)), null);
  }

  /**
   * Validates already-parsed JSON. When {@code schema} is given, it receives every setting that was read.
   *
   * @param file   File the JSON came from, named in errors.
   * @param root   Parsed JSON.
   * @param schema Receives a description of every setting read, for the editor, or null when not needed.
   */
  public static SpeciesPreset parse(Path file, ObjectNode root, List<SettingField> schema) {
    return species(new Reader(file, root, "", schema));
  }

  /**
   * The file's text, accepting UTF-8 with or without a byte order mark and UTF-16 with one.
   *
   * @param file File to read.
   */
  public static String readText(Path file) {
    try {
      return decode(file, Files.readAllBytes(file));
    } catch (NoSuchFileException e) {
      throw new PresetException(file + ": $: file not found", e);
    } catch (IOException e) {
      throw new PresetException(file + ": $: cannot read file: " + e.getMessage(), e);
    }
  }

  /**
   * Parses JSON text into an object, rejecting duplicate keys. Errors name the line and column.
   *
   * @param file File the text came from, named in errors.
   * @param text JSON text.
   */
  public static ObjectNode parseText(Path file, String text) {
    JsonNode root;
    try {
      root = MAPPER.readTree(text);
    } catch (JsonProcessingException e) {
      throw new PresetException(file + ": " + syntaxLocation(e) + ": invalid JSON: " + e.getOriginalMessage(), e);
    }
    if (!(root instanceof ObjectNode object)) {
      throw new PresetException(file + ": $: invalid JSON: expected an object");
    }
    return object;
  }

  /**
   * Decodes UTF-8, with or without a byte order mark, and UTF-16 with a byte order mark.
   *
   * @param file  File the bytes came from, named in errors.
   * @param bytes Raw file contents.
   */
  private static String decode(Path file, byte[] bytes) {
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
      return decodeStrict(file, ByteBuffer.wrap(bytes, 3, bytes.length - 3), StandardCharsets.UTF_8);
    }
    if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
      return decodeStrict(file, ByteBuffer.wrap(bytes, 2, bytes.length - 2), StandardCharsets.UTF_16LE);
    }
    if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
      return decodeStrict(file, ByteBuffer.wrap(bytes, 2, bytes.length - 2), StandardCharsets.UTF_16BE);
    }
    return decodeStrict(file, ByteBuffer.wrap(bytes), StandardCharsets.UTF_8);
  }

  /**
   * Decodes the bytes, failing rather than inserting replacement characters for invalid text.
   *
   * @param file    File the bytes came from, named in errors.
   * @param bytes   Bytes to decode, after any byte order mark.
   * @param charset Encoding to decode with.
   */
  private static String decodeStrict(Path file, ByteBuffer bytes, Charset charset) {
    try {
      return charset.newDecoder().decode(bytes).toString();
    } catch (CharacterCodingException e) {
      throw new PresetException(file + ": $: file is not valid " + charset.name() + " text", e);
    }
  }

  /**
   * Where a JSON syntax error is, as a setting path such as {@code levels[1].length} with its line and column.
   *
   * @param e The syntax error.
   */
  private static String syntaxLocation(JsonProcessingException e) {
    String field = "$";
    if (e.getProcessor() instanceof JsonParser parser) {
      String pointer = parser.getParsingContext().pathAsPointer().toString();
      if (!pointer.isEmpty()) {
        field = pointer.substring(1).replaceAll("/(\\d+)(?=/|$)", "[$1]").replace('/', '.');
      }
    }
    JsonLocation location = e.getLocation();
    return location == null ? field
            : field + " (line " + location.getLineNr() + ", column " + location.getColumnNr() + ")";
  }

  /**
   * Reads every section of a preset, then rejects any key that was never read.
   *
   * @param json The preset's top level.
   */
  private static SpeciesPreset species(Reader json) {
    String name = json.requiredText("name");
    TreeSettings tree = tree(json.child("tree"));
    ShapeControl shapeControl = shapeControl(json.child("shapeControl"));
    List<ForceSettings> forces = forces(json);
    Reader shapesJson = json.child("shapes");
    // Presets saved by the first version of survival mode kept this value with the Advanced settings.
    double survivalLeaves = shapesJson.readNumber("survivalLeafDensity", SURVIVAL_LEAF_DENSITY, 0, 1);
    VoxelSettings shapes = shapes(shapesJson);
    BlockSettings blocks = blocks(json.child("blocks"));
    SurvivalSettings survival = survival(json.child("survival"), shapes, blocks, survivalLeaves);
    List<LevelPreset> levels = levels(json, forces);
    List<LeafGenerator> leaves = leaves(json, levels);
    List<PlantLayer> plantLayers = plantLayers(json, levels);
    SimpleSettings simple = simple(json.child("simple"));
    DecoratorSettings decorators = decorators(json.child("decorators"));
    json.finish();
    return new SpeciesPreset(name, tree, shapeControl, List.copyOf(forces), shapes, blocks, List.copyOf(levels),
            List.copyOf(leaves), List.copyOf(plantLayers), survival, simple, decorators);
  }

  /**
   * The optional decorators section; a decorator that is missing is off with default settings.
   *
   * @param json The preset's top level.
   */
  private static DecoratorSettings decorators(Reader json) {
    Reader item = json.child("pinecones");
    boolean enabled = item.bool("enabled", false);
    double chance = item.number("chance", 0.03, 0, 1);
    double share = item.number("undersideShare", 0.7, 0, 1);
    double[] height = item.range("height", 0, 1);
    int minLevel = item.integer("minLevel", 2, 0, 16);
    DecoratorSettings.Pinecones pinecones = new DecoratorSettings.Pinecones(enabled, chance, share, height[0], height[1], minLevel,
            decoratorBlocks(item, "spruce_button"));
    item.finish();
    item = json.child("vines");
    enabled = item.bool("enabled", false);
    chance = item.number("chance", 0.05, 0, 1);
    int minLength = item.integer("minLength", 2, 1, 32);
    int maxLength = item.integer("maxLength", 6, 1, 32);
    if (minLength > maxLength) {
      throw item.error("minLength", "must not exceed maxLength");
    }
    DecoratorSettings.Vines vines = new DecoratorSettings.Vines(enabled, chance, minLength, maxLength, decoratorBlocks(item, "vine"));
    item.finish();
    item = json.child("moss");
    enabled = item.bool("enabled", false);
    double amount = item.number("amount", 0.2, 0, 1);
    int patchSize = item.integer("patchSize", 3, 1, 16);
    double upward = item.number("upward", 0.7, 0, 1);
    height = item.range("height", 0, 1);
    DecoratorSettings.Moss moss = new DecoratorSettings.Moss(enabled, amount, patchSize, upward, height[0], height[1],
            decoratorBlocks(item, "moss_block"));
    item.finish();
    json.finish();
    return new DecoratorSettings(pinecones, vines, moss);
  }

  /**
   * A decorator's block list, which defaults to one block and can never be off.
   *
   * @param item  The decorator's settings.
   * @param block Block used when the list is missing.
   */
  private static WeightedBlocks decoratorBlocks(Reader item, String block) {
    return item.weights("blocks", WeightedBlocks.of(block), block, false);
  }

  /**
   * The Simple view's sliders; each missing slider is neutral.
   *
   * @param json The preset's top level.
   */
  private static SimpleSettings simple(Reader json) {
    double sizeVariety = json.number("sizeVariety", Double.NaN, 0, 0.5);
    SimpleSettings settings = new SimpleSettings(
            json.number("height", 1, 0.5, 2),
            json.number("crownWidth", 1, 0.5, 2),
            json.number("trunkThickness", 1, 0.5, 2),
            json.number("branchDensity", 1, 0.5, 2),
            json.number("branchAngle", 0, -30, 30),
            json.number("droop", 0, -1, 1),
            json.number("twist", 1, 0, 3),
            json.number("leafFullness", 1, 0.5, 1.5),
            json.number("windLean", 0, 0, 1),
            Double.isNaN(sizeVariety) ? null : Double.valueOf(sizeVariety));
    json.finish();
    return settings;
  }

  /**
   * Whole-tree settings: size, height limit, sink, roll, trunk spread, size variety and ground slope.
   *
   * @param json The preset's top level.
   */
  private static TreeSettings tree(Reader json) {
    TreeSettings settings = new TreeSettings(
            json.number("sizeScale", 1, 1e-3, 1000),
            json.requiredNumber("maxHeight", 1, 384),
            json.number("sink", 0, -INF, INF),
            json.param("roll", Param.of(0)),
            json.number("trunkSpread", 0, 0, 64),
            json.number("sizeVariance", 0, 0, 0.9),
            new GroundPlane(json.vector("groundNormal", Vec3.UP)),
            json.number("slopeRecovery", 0.3, 0, 1));
    if (settings.sizeScale() <= 0) {
      throw json.error("sizeScale", "must be greater than 0");
    }
    if (settings.ground().normal().y() <= 0 || settings.ground().degrees() > GroundPlane.MAX_DEGREES) {
      throw json.error("groundNormal", "must point upward, with a slope of at most "
              + (int) GroundPlane.MAX_DEGREES + " degrees");
    }
    json.finish();
    return settings;
  }

  /**
   * The crown shape that limits branches.
   *
   * @param json The preset's top level.
   */
  private static ShapeControl shapeControl(Reader json) {
    ShapeControl settings = new ShapeControl(
            json.choice("style", STYLES, ShapeControl.Style.NONE),
            json.number("crownBase", 0, 0, INF),
            json.curve("radius", Curve.constant(8)),
            json.choice("action", ACTIONS, ShapeControl.Action.STOP),
            json.number("steer", 0, 0, INF));
    json.finish();
    return settings;
  }

  /**
   * The named forces, which must have unique names that do not use the Simple view's reserved prefix.
   *
   * @param json The preset's top level.
   */
  private static List<ForceSettings> forces(Reader json) {
    List<ForceSettings> forces = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (Reader item : json.list("forces")) {
      String name = item.requiredText("name");
      if (name.startsWith(SimpleSettings.RESERVED_FORCE_PREFIX)) {
        throw item.error("name", "is reserved for the Simple view");
      }
      if (!names.add(name)) {
        throw item.error("name", "duplicate force name");
      }
      ForceSettings.Type type = item.requiredChoice("type", FORCE_TYPES);
      Vec3 vector = type == ForceSettings.Type.ATTRACTOR ? Vec3.ZERO : item.requiredVector("vector");
      Vec3 position = type == ForceSettings.Type.ATTRACTOR ? item.requiredVector("position") : Vec3.ZERO;
      double strength = item.number("strength", 1, -INF, INF);
      double falloff = item.number("falloff", 0, 0, INF);
      forces.add(new ForceSettings(name, type, vector, position, strength, falloff, wind(item, type)));
      item.finish();
    }
    return forces;
  }

  /**
   * Wind settings are kept but unused on other types, so switching a force's type in the editor loses nothing.
   *
   * @param item The force's settings.
   * @param type The force's type.
   */
  private static ForceSettings.Wind wind(Reader item, ForceSettings.Type type) {
    if (type != ForceSettings.Type.WIND) {
      WIND_KEYS.forEach(item::take);
      return null;
    }
    return new ForceSettings.Wind(
            item.number("directionVariance", 0, 0, 180),
            item.number("gust", 0.3, 0, 1),
            item.number("gustSize", 12, 0.5, 256),
            item.number("heightPower", 1, 0, 8),
            item.number("flexibility", 1.5, 0, 16));
  }

  /**
   * The voxelizer settings. Radii must shrink from full blocks to walls to implied leaves.
   *
   * @param json The preset's top level.
   */
  private static VoxelSettings shapes(Reader json) {
    double fullRadius = json.number("fullRadius", 0.35, 0, INF);
    double wallRadius = json.number("wallRadius", 0.18, 0, INF);
    if (wallRadius > fullRadius) {
      throw json.error("wallRadius", "must not exceed fullRadius");
    }
    double impliedRadius = json.number("impliedRadius", 0.075, 0, INF);
    if (impliedRadius > wallRadius) {
      throw json.error("impliedRadius", "must not exceed wallRadius");
    }
    VoxelSettings settings = new VoxelSettings(fullRadius, wallRadius,
            json.number("wallGapChance", 0.05, 0, 1),
            json.number("thinCoverage", 1, 0, 1),
            json.number("gateChance", 0.15, 0, 1),
            json.number("gateInWallChance", 0.25, 0, 1),
            json.number("openGateChance", 0.6, 0, 1),
            impliedRadius,
            json.number("impliedLeafSize", 1.35, 0, 64),
            json.number("impliedLeafStrength", 0.75, 0, 1),
            json.number("deadBushChance", 0.5, 0, 1),
            json.integer("deadBushDensity", 5, 1, 26),
            json.number("deadBushOffFenceChance", 0.15, 0, 1),
            json.bool("smoothTrunk", false),
            json.number("smoothStairDepth", 0.2, 0, 1),
            json.number("smoothSlabDepth", 0.38, 0, 1),
            json.number("smoothWallDepth", 0.2, 0, 1),
            json.number("smoothFenceDepth", 0.36, 0, 1),
            json.number("buttonChance", 0, 0, 1),
            json.integer("buttonSpacing", 3, 0, 8),
            json.number("tipCanopyDensity", 0, 0, 1),
            json.number("fineBranchChance", 0, 0, 1),
            json.number("fineGateChance", 0.35, 0, 1),
            json.number("trunkFullRadius", 0, 0, INF),
            json.number("saplingLeafDensity", 0.15, 0, 1),
            json.number("voxelRandomness", 0, 0, 1),
            json.number("leafBlockDensity", 0, 0, 1),
            json.bool("trimToCanopy", false),
            json.bool("removeDetached", false),
            json.choice("mode", Map.of("advanced", Boolean.FALSE, "survival", Boolean.TRUE), Boolean.FALSE));
    for (String removed : REMOVED_SHAPE_SETTINGS) {
      if (json.fieldNames().contains(removed)) {
        throw json.error(removed, "was removed; describe plants with plantLayers instead");
      }
    }
    json.finish();
    return settings;
  }

  /**
   * Survival's own values, each defaulting to Advanced's. Radii are kept in order here rather than refused, so an
   * edit in Advanced can never make a stored Survival value invalid.
   *
   * @param json               The preset's top level.
   * @param advanced           Advanced mode's voxelizer settings, the defaults for Survival's.
   * @param advancedBlocks     Advanced mode's blocks, the defaults for Survival's.
   * @param defaultLeafDensity Advanced mode's leaf density, the default for Survival's.
   */
  private static SurvivalSettings survival(Reader json, VoxelSettings advanced, BlockSettings advancedBlocks,
                                           double defaultLeafDensity) {
    Reader values = json.child("shapes");
    double full = values.number("fullRadius", advanced.fullRadius(), 0, INF);
    double wall = Math.min(full, values.number("wallRadius", advanced.wallRadius(), 0, INF));
    double implied = Math.min(wall, values.number("impliedRadius", advanced.impliedRadius(), 0, INF));
    VoxelSettings shapes = advanced.forSurvival(full, wall,
            values.number("trunkFullRadius", advanced.trunkFullRadius(), 0, INF), implied,
            values.number("impliedLeafSize", advanced.impliedLeafSize(), 0, 64),
            values.number("impliedLeafStrength", advanced.impliedLeafStrength(), 0, 1),
            values.number("tipCanopyDensity", advanced.tipCanopyDensity(), 0, 1),
            values.bool("trimToCanopy", advanced.trimToCanopy()),
            values.number("voxelRandomness", advanced.voxelRandomness(), 0, 1),
            values.number("leafBlockDensity", defaultLeafDensity, 0, 1));
    values.finish();
    Reader blockValues = json.child("blocks");
    BlockSettings inherited = SurvivalSettings.palette(advancedBlocks);
    Map<BlockRole, BlockRule> rules = new EnumMap<>(BlockRole.class);
    for (BlockRole role : List.of(BlockRole.LOG, BlockRole.JUNCTION, BlockRole.CUT_END, BlockRole.LEAVES)) {
      BlockRule fallback = inherited.rules().get(role);
      String note = fallback != null ? fallback.blockNames().get(0) : "follows log";
      BlockRule chosen = blockValues.rule(role.jsonKey(), fallback, note, false);
      if (chosen != null) {
        boolean leaves = role == BlockRole.LEAVES;
        for (String block : chosen.blockNames()) {
          if (!(leaves ? SurvivalSettings.isLeaves(block) : SurvivalSettings.isLog(block))) {
            throw blockValues.error(role.jsonKey(), "'" + block + "' is not a "
                    + (leaves ? "leaf block that decays" : "log or wood block"));
          }
        }
        rules.put(role, chosen);
      }
    }
    blockValues.finish();
    json.finish();
    return new SurvivalSettings(shapes, BlockSettings.ofRules(rules));
  }

  /**
   * The block rule for every type. Logs can never be off; any other type may be switched off.
   *
   * @param json The preset's top level.
   */
  private static BlockSettings blocks(Reader json) {
    BlockSettings defaults = BlockSettings.defaults();
    Map<BlockRole, BlockRule> rules = new EnumMap<>(BlockRole.class);
    for (BlockRole role : BlockRole.values()) {
      BlockRule fallback = defaults.rules().get(role);
      String note = fallback != null ? fallback.blockNames().get(0)
              : role == BlockRole.JUNCTION || role == BlockRole.CUT_END ? "follows log" : "follows planks";
      BlockRule chosen = json.rule(role.jsonKey(), fallback, note, role != BlockRole.LOG);
      if (chosen != null) {
        rules.put(role, chosen);
      }
    }
    json.finish();
    return BlockSettings.ofRules(rules);
  }

  /**
   * The branch levels. Names are unique, the first level is the trunk, and every other level grows from an earlier one
   * (the previous one unless named).
   *
   * @param json   The preset's top level.
   * @param forces The preset's forces, which levels may respond to.
   */
  private static List<LevelPreset> levels(Reader json, List<ForceSettings> forces) {
    List<Reader> items = json.list("levels");
    if (items.isEmpty()) {
      throw json.error("levels", "at least one level is required");
    }
    Set<String> forceNames = new HashSet<>();
    forces.forEach(force -> forceNames.add(force.name()));
    Set<String> earlier = new HashSet<>();
    List<LevelPreset> levels = new ArrayList<>();
    String previous = null;
    for (int i = 0; i < items.size(); i++) {
      Reader item = items.get(i);
      boolean root = i == 0;
      String name = item.requiredText("name");
      if (!earlier.add(name)) {
        throw item.error("name", "duplicate level name '" + name + "'");
      }
      String parent = item.text("parent", previous);
      if (root && parent != null) {
        throw item.error("parent", "the root level cannot have a parent");
      }
      if (!root && (parent.equals(name) || !earlier.contains(parent))) {
        throw item.error("parent", "must name an earlier level");
      }
      levels.add(level(item, name, parent, root, forceNames));
      item.finish();
      previous = name;
    }
    return levels;
  }

  /**
   * One level's settings, each group read from its own section.
   *
   * @param item       The level's settings.
   * @param name       The level's name.
   * @param parent     Name of the level it grows on, or null for the root.
   * @param root       Whether this is the first level.
   * @param forceNames Names of the preset's forces.
   */
  private static LevelPreset level(Reader item, String name, String parent, boolean root, Set<String> forceNames) {
    double segmentLength = item.number("segmentLength", 1, 0.25, 16);
    GenerationSettings generation = generation(item.child("generation"), root);
    LengthSettings length = length(item.child("length"));
    OrientationSettings orientation = orientation(item.child("orientation"), root);
    ShapeSettings shape = shape(item.child("shape"));
    NoiseSettings noise = noise(item.child("noise"));
    JinkSettings jink = jink(item.child("jink"));
    PathSettings path = path(item.child("path"));
    BreakSettings breakage = breakage(item.child("break"));
    PruneSettings prune = prune(item.child("prune"));
    Map<String, Param> forces = levelForces(item.child("forces"), forceNames);
    RadiusSettings radius = radius(item.child("radius"));
    FlareSettings flares = flares(item.child("flares"));
    boolean wood = item.bool("wood", true);
    return new LevelPreset(name, parent, segmentLength, generation, length, orientation, shape, noise, jink, path,
            breakage, prune, forces, radius, flares, wood);
  }

  /**
   * Where and how many children grow. The trunk level must use parent mode; leaves and branch levels must name a mode.
   *
   * @param json The level's settings.
   * @param root Whether this is the first level.
   */
  private static GenerationSettings generation(Reader json, boolean root) {
    GenerationSettings.Mode mode = root
            ? json.choice("mode", MODES, GenerationSettings.Mode.PARENT)
            : json.requiredChoice("mode", MODES);
    if (root && mode != GenerationSettings.Mode.PARENT) {
      throw json.error("mode", "the root level must use parent mode");
    }
    double first = json.number("first", 0, 0, 1);
    double last = json.number("last", 1, 0, 1);
    if (first > last) {
      throw json.error("first", "must not be after last");
    }
    double defaultSpiral = mode == GenerationSettings.Mode.PHYLLOTAXY ? 0 : 137.5;
    GenerationSettings settings = new GenerationSettings(mode, first, last,
            json.bool("clip", true),
            json.param("rotation", Param.of(0)),
            json.param("position", Param.of(0)),
            json.param("sink", Param.of(0)),
            json.curve("pruning", Curve.constant(1)),
            json.param("knockout", Param.of(0)),
            json.integer("extendParent", 0, 0, 16),
            json.param("count", Param.of(1)),
            json.param("density", Param.of(1)),
            json.param("frequency", Param.of(2)),
            json.choice("phyllotaxy", PHYLLOTAXY, GenerationSettings.Phyllotaxy.ALTERNATE_DISTICHOUS),
            json.integer("childrenPerNode", 3, 1, 32),
            json.param("internodeLength", Param.of(2)),
            json.number("spread", 0, 0, 1),
            json.number("spiral", defaultSpiral, -INF, INF),
            json.integer("limit", 0, 0, 100000),
            json.number("align", 0, 0, 1),
            json.number("balance", 0, 0, 1),
            json.number("threshold", 20, 0, 180),
            json.number("spacing", 2, 0, INF),
            json.number("locality", 0, 0, 1));
    json.finish();
    return settings;
  }

  /**
   * How a level's length is set and limited.
   *
   * @param json The level's settings.
   */
  private static LengthSettings length(Reader json) {
    double min = json.number("min", 0, 0, INF);
    double max = json.number("max", INF, 0, INF);
    if (min > max) {
      throw json.error("min", "must not exceed max");
    }
    LengthSettings settings = new LengthSettings(
            json.param("absolute", Param.of(0)),
            json.param("percentOfParent", Param.of(0)),
            json.param("percentOfRadius", Param.of(0)),
            min, max,
            json.number("shapeScale", 1, 0, INF),
            json.bool("stopShapeControl", false));
    json.finish();
    return settings;
  }

  /**
   * The starting direction; trunks start along the virtual parent (straight up), branches start straight out.
   *
   * @param json The level's settings.
   * @param root Whether this is the first level.
   */
  private static OrientationSettings orientation(Reader json, boolean root) {
    OrientationSettings settings = new OrientationSettings(
            json.param("startAngle", Param.of(root ? 0 : 0.5)),
            json.param("startRoll", Param.of(0)),
            json.number("alignment", 0, 0, 1));
    json.finish();
    return settings;
  }

  /**
   * How a branch bends as it grows.
   *
   * @param json The level's settings.
   */
  private static ShapeSettings shape(Reader json) {
    ShapeSettings settings = new ShapeSettings(
            json.param("gravity", Param.of(0)),
            json.param("ancestor", Param.of(0)),
            json.param("straightness", Param.of(0)),
            json.param("twist", Param.of(0)),
            json.param("curlUp", Param.of(0)),
            json.param("curlRight", Param.of(0)),
            json.param("curlOut", Param.of(0)),
            json.param("zigzag", Param.of(0)),
            json.param("zigzagLength", Param.of(3)));
    json.finish();
    return settings;
  }

  /**
   * Random wobble while growing (early) and after (late).
   *
   * @param json The level's settings.
   */
  private static NoiseSettings noise(Reader json) {
    NoiseSettings settings = new NoiseSettings(
            json.param("earlyAmount", Param.of(0)),
            json.number("earlyTurbulence", 0.5, 0, 1),
            json.number("earlyThreshold", 0, 0, 1),
            json.param("lateAmount", Param.of(0)),
            json.number("lateTurbulence", 0.5, 0, 1),
            json.number("lateSmooth", 0, 0, 1));
    json.finish();
    return settings;
  }

  /**
   * How much a branch is straightened into angular pieces.
   *
   * @param json The level's settings.
   */
  private static JinkSettings jink(Reader json) {
    JinkSettings settings = new JinkSettings(
            json.number("amount", 0, 0, 1),
            json.param("segments", Param.of(4)),
            json.number("uniformity", 0.5, 0, 1));
    json.finish();
    return settings;
  }

  /**
   * A repeating sideways wave along the branch.
   *
   * @param json The level's settings.
   */
  private static PathSettings path(Reader json) {
    PathSettings settings = new PathSettings(
            json.param("amount", Param.of(0)),
            json.number("twist", 0, -INF, INF),
            json.number("length", 8, 0.25, INF),
            json.param("offset", Param.of(0)));
    json.finish();
    return settings;
  }

  /**
   * The chance that a branch snaps off, and where.
   *
   * @param json The level's settings.
   */
  private static BreakSettings breakage(Reader json) {
    BreakSettings settings = new BreakSettings(
            json.number("chance", 0, 0, 1),
            json.param("spot", Param.of(0.7)));
    json.finish();
    return settings;
  }

  /**
   * The chances of removing whole branches by direction, at random, or when they dip underground.
   *
   * @param json The level's settings.
   */
  private static PruneSettings prune(Reader json) {
    PruneSettings settings = new PruneSettings(
            json.number("interior", 0, 0, 1),
            json.number("up", 0, 0, 1),
            json.number("down", 0, 0, 1),
            json.number("general", 0, 0, 1),
            json.number("ground", 0, 0, 1));
    json.finish();
    return settings;
  }

  /**
   * How strongly this level responds to each force, by name. Forces it does not list are still noted so the editor
   * shows them, at full response.
   *
   * @param json       The level's settings.
   * @param forceNames Names of the preset's forces.
   */
  private static Map<String, Param> levelForces(Reader json, Set<String> forceNames) {
    Map<String, Param> values = new LinkedHashMap<>();
    for (String name : json.fieldNames()) {
      if (!forceNames.contains(name)) {
        throw json.error(name, "unknown force");
      }
      values.put(name, json.param(name, Param.of(1)));
    }
    for (String name : new TreeSet<>(forceNames)) {
      if (!values.containsKey(name)) {
        json.note(SettingField.Kind.PARAM, name, 1.0, -INF, INF, List.of());
      }
    }
    json.finish();
    return Map.copyOf(values);
  }

  /**
   * How thick a level's branches are and how they taper.
   *
   * @param json The level's settings.
   */
  private static RadiusSettings radius(Reader json) {
    double min = json.number("min", 0, 0, INF);
    double max = json.number("max", INF, 0, INF);
    if (min > max) {
      throw json.error("min", "must not exceed max");
    }
    RadiusSettings settings = new RadiusSettings(
            json.param("absolute", Param.of(0)),
            json.param("percentOfParent", Param.of(0)),
            json.curve("taper", DEFAULT_TAPER),
            json.bool("clampToParent", true),
            min, max,
            json.number("lengthCompensation", 0, 0, 1),
            json.number("lengthKillRatio", 0, 0, INF));
    json.finish();
    return settings;
  }

  /**
   * Root flares that widen the trunk's base into lobes.
   *
   * @param json The level's settings.
   */
  private static FlareSettings flares(Reader json) {
    FlareSettings settings = new FlareSettings(
            json.number("radius", 0, 0, INF),
            json.number("height", 0, 0, INF),
            json.number("pinch", 1, 0.05, 16),
            json.integer("lobes", 0, 0, 32),
            json.number("noise", 0, 0, 1),
            json.param("angle", Param.of(0)));
    json.finish();
    return settings;
  }

  /**
   * The leaf generators, each on a named level.
   *
   * @param json   The preset's top level.
   * @param levels The preset's levels, which leaf generators name.
   */
  private static List<LeafGenerator> leaves(Reader json, List<LevelPreset> levels) {
    Set<String> names = new HashSet<>();
    levels.forEach(level -> names.add(level.name()));
    List<LeafGenerator> leaves = new ArrayList<>();
    for (Reader item : json.list("leaves")) {
      String parent = item.requiredText("parent");
      if (!names.contains(parent)) {
        throw item.error("parent", "unknown level");
      }
      GenerationSettings generation = generation(item.child("generation"), false);
      Param size = item.param("size", Param.of(2));
      Param sizePercentOfParent = item.param("sizePercentOfParent", Param.of(0));
      Reader scale = item.child("scale");
      double scaleX = scale.number("x", 1, 0.01, 100);
      double scaleY = scale.number("y", 1, 0.01, 100);
      double scaleZ = scale.number("z", 1, 0.01, 100);
      scale.finish();
      double elongation = item.number("elongation", 1, 0.01, 100);
      double skyInfluence = item.number("skyInfluence", 0, 0, 1);
      double fold = item.number("fold", 0, -1, 1);
      double align = item.number("align", 0, -1, 1);
      double reach = item.number("reach", 0, 0, 1);
      Param strength = item.param("strength", Param.of(1));
      double falloff = item.number("falloff", 2, 0.05, 32);
      double noiseScale = item.number("noiseScale", 4, 0.05, INF);
      double noiseStrength = item.number("noiseStrength", 0.4, 0, 1);
      double envelopeEdge = item.number("envelopeEdge", 2, 0, INF);
      Reader shade = item.child("shadePruning");
      LeafGenerator.ShadePruning shadePruning = new LeafGenerator.ShadePruning(
              shade.choice("style", SHADE_STYLES, LeafGenerator.ShadeStyle.NONE),
              shade.number("threshold", 0.5, 0, 1));
      shade.finish();
      leaves.add(new LeafGenerator(parent, generation, size, sizePercentOfParent, scaleX, scaleY, scaleZ,
              elongation, skyInfluence, fold, align, reach, strength, falloff, noiseScale, noiseStrength, envelopeEdge, shadePruning));
      item.finish();
    }
    return leaves;
  }

  /**
   * The plant layers, each with a unique name and a required block list.
   *
   * @param json   The preset's top level.
   * @param levels The preset's levels, which plant layers name.
   */
  private static List<PlantLayer> plantLayers(Reader json, List<LevelPreset> levels) {
    Map<String, String> branchChoices = new LinkedHashMap<>();
    branchChoices.put(ANY_BRANCH, ANY_BRANCH);
    levels.forEach(level -> branchChoices.put(level.name(), level.name()));
    Set<String> names = new HashSet<>();
    List<PlantLayer> layers = new ArrayList<>();
    for (Reader item : json.list("plantLayers")) {
      String name = item.requiredText("name");
      if (!names.add(name)) {
        throw item.error("name", "duplicate plant layer name '" + name + "'");
      }
      boolean enabled = item.bool("enabled", true);
      double treeChance = item.number("treeChance", 1, 0, 1);
      WeightedBlocks blocks = item.weights("blocks", null, null, false);
      if (blocks == null) {
        throw item.error("blocks", "is required");
      }
      double chance = item.number("chance", 0.3, 0, 1);
      double[] density = item.range("leafDensity", 0, 1);
      double[] height = item.range("height", 0, 1);
      PlantLayer.Side side = item.choice("side", SIDES, PlantLayer.Side.ANY);
      String nearBranch = item.choice("nearBranch", branchChoices, ANY_BRANCH);
      int nearDistance = item.integer("nearDistance", 2, 0, 8);
      double clumpSize = item.number("clumpSize", 0, 0, 64);
      PlantLayer.DoubleHalf doubleHalf = item.choice("doubleHalf", DOUBLE_HALVES, PlantLayer.DoubleHalf.BOTH);
      layers.add(new PlantLayer(name, enabled, treeChance, blocks, chance, density[0], density[1], height[0],
              height[1], side, nearBranch.equals(ANY_BRANCH) ? null : nearBranch, nearDistance, clumpSize, doubleHalf));
      item.finish();
    }
    return layers;
  }

  /**
   * Reads one JSON object, tracking used keys so unknown keys can be rejected.
   */
  private static final class Reader {
    private final Path file;
    private final JsonNode node;
    private final String path;
    private final Set<String> used = new HashSet<>();
    private final List<SettingField> schema;

    /**
     * @param file   File the JSON came from, named in errors.
     * @param node   This object's JSON.
     * @param path   Setting path of this object, such as {@code levels[0].radius}; empty at the top.
     * @param schema Receives a description of every setting read, for the editor, or null when not needed.
     */
    Reader(Path file, JsonNode node, String path, List<SettingField> schema) {
      this.file = file;
      this.node = node;
      this.path = path;
      this.schema = schema;
    }

    /**
     * The full setting path of a key in this object.
     *
     * @param key Key in this object.
     */
    String keyPath(String key) {
      return path.isEmpty() ? key : path + "." + key;
    }

    /**
     * Records a setting for the editor, with its type, default and limits.
     *
     * @param kind     Type of setting.
     * @param key      Key in this object.
     * @param fallback Default value.
     * @param min      Lowest allowed number.
     * @param max      Highest allowed number.
     * @param options  Names a choice can take; empty for other settings.
     */
    void note(SettingField.Kind kind, String key, Object fallback, double min, double max, List<String> options) {
      if (schema != null) {
        schema.add(new SettingField(keyPath(key), kind, fallback, min, max, options));
      }
    }

    /**
     * An error naming the file and the key's full path.
     *
     * @param key     Key in this object.
     * @param problem What is wrong.
     */
    PresetException error(String key, String problem) {
      return new PresetException(file + ": " + keyPath(key) + ": " + problem);
    }

    /**
     * A key's value, or null when missing or null, marking the key as used.
     *
     * @param key Key in this object.
     */
    private JsonNode take(String key) {
      used.add(key);
      JsonNode value = node.get(key);
      return value == null || value.isNull() ? null : value;
    }

    /**
     * A nested object; a missing one reads as empty, so every setting in it takes its default.
     *
     * @param key Key in this object.
     */
    Reader child(String key) {
      JsonNode value = take(key);
      if (value == null) {
        return new Reader(file, JsonNodeFactory.instance.objectNode(), keyPath(key), schema);
      }
      if (!value.isObject()) {
        throw error(key, "expected an object");
      }
      return new Reader(file, value, keyPath(key), schema);
    }

    /**
     * A list of objects; a missing list is empty.
     *
     * @param key Key in this object.
     */
    List<Reader> list(String key) {
      JsonNode value = take(key);
      if (value == null) {
        return List.of();
      }
      if (!value.isArray()) {
        throw error(key, "expected a list");
      }
      List<Reader> items = new ArrayList<>();
      for (int i = 0; i < value.size(); i++) {
        String itemPath = keyPath(key) + "[" + i + "]";
        if (!value.get(i).isObject()) {
          throw new PresetException(file + ": " + itemPath + ": expected an object");
        }
        items.add(new Reader(file, value.get(i), itemPath, schema));
      }
      return items;
    }

    /**
     * Every key in this object, read or not.
     */
    List<String> fieldNames() {
      List<String> names = new ArrayList<>();
      node.fieldNames().forEachRemaining(names::add);
      return names;
    }

    /**
     * A value that must be a finite number.
     *
     * @param key   Key in this object.
     * @param value Value to check.
     */
    private double finiteNumber(String key, JsonNode value) {
      if (!value.isNumber()) {
        throw error(key, "expected a number");
      }
      double number = value.asDouble();
      if (!Double.isFinite(number)) {
        throw error(key, "must be a finite number");
      }
      return number;
    }

    /**
     * A number within limits, or the fallback when missing.
     *
     * @param key      Key in this object.
     * @param fallback Value when the key is missing.
     * @param min      Lowest allowed value.
     * @param max      Highest allowed value.
     */
    double number(String key, double fallback, double min, double max) {
      note(SettingField.Kind.NUMBER, key, fallback, min, max, List.of());
      return readNumber(key, fallback, min, max);
    }

    /**
     * A whole number within limits, or the fallback when missing.
     *
     * @param key      Key in this object.
     * @param fallback Value when the key is missing.
     * @param min      Lowest allowed value.
     * @param max      Highest allowed value.
     */
    int integer(String key, int fallback, int min, int max) {
      note(SettingField.Kind.INTEGER, key, (double) fallback, min, max, List.of());
      return (int) readNumber(key, fallback, min, max);
    }

    /**
     * A number within limits without noting it for the editor, for values the editor does not show.
     *
     * @param key      Key in this object.
     * @param fallback Value when the key is missing.
     * @param min      Lowest allowed value.
     * @param max      Highest allowed value.
     */
    private double readNumber(String key, double fallback, double min, double max) {
      JsonNode value = take(key);
      if (value == null) {
        return fallback;
      }
      double number = finiteNumber(key, value);
      if (number < min || number > max) {
        throw error(key, "must be between " + min + " and " + max);
      }
      return number;
    }

    /**
     * A number within limits that must be present.
     *
     * @param key Key in this object.
     * @param min Lowest allowed value.
     * @param max Highest allowed value.
     */
    double requiredNumber(String key, double min, double max) {
      note(SettingField.Kind.NUMBER, key, null, min, max, List.of());
      if (take(key) == null) {
        throw error(key, "is required");
      }
      return readNumber(key, 0, min, max);
    }

    /**
     * True or false, or the fallback when missing.
     *
     * @param key      Key in this object.
     * @param fallback Value when the key is missing.
     */
    boolean bool(String key, boolean fallback) {
      note(SettingField.Kind.BOOLEAN, key, fallback, 0, 1, List.of());
      JsonNode value = take(key);
      if (value == null) {
        return fallback;
      }
      if (!value.isBoolean()) {
        throw error(key, "expected true or false");
      }
      return value.booleanValue();
    }

    /**
     * Non-blank text, or the fallback when missing.
     *
     * @param key      Key in this object.
     * @param fallback Value when the key is missing.
     */
    String text(String key, String fallback) {
      note(SettingField.Kind.TEXT, key, fallback, 0, 0, List.of());
      return readText(key, fallback);
    }

    /**
     * Non-blank text without noting it for the editor.
     *
     * @param key      Key in this object.
     * @param fallback Value when the key is missing.
     */
    private String readText(String key, String fallback) {
      JsonNode value = take(key);
      if (value == null) {
        return fallback;
      }
      if (!value.isTextual() || value.textValue().isBlank()) {
        throw error(key, "expected text");
      }
      return value.textValue();
    }

    /**
     * Non-blank text that must be present.
     *
     * @param key Key in this object.
     */
    String requiredText(String key) {
      String value = text(key, null);
      if (value == null) {
        throw error(key, "is required");
      }
      return value;
    }

    /**
     * One of the named options, or the fallback when missing. The editor lists the options alphabetically.
     *
     * @param key      Key in this object.
     * @param options  Allowed names and the values they stand for.
     * @param fallback Value when the key is missing.
     * @param <E>      Type of the values.
     */
    <E> E choice(String key, Map<String, E> options, E fallback) {
      TreeMap<String, E> sorted = new TreeMap<>(options);
      String fallbackName = sorted.entrySet().stream().filter(entry -> entry.getValue() == fallback)
              .map(Map.Entry::getKey).findFirst().orElse(null);
      note(SettingField.Kind.CHOICE, key, fallbackName, 0, 0, List.copyOf(sorted.keySet()));
      String value = readText(key, null);
      if (value == null) {
        return fallback;
      }
      E option = options.get(value);
      if (option == null) {
        throw error(key, "unknown value '" + value + "'");
      }
      return option;
    }

    /**
     * One of the named options, which must be present.
     *
     * @param key     Key in this object.
     * @param options Allowed names and the values they stand for.
     * @param <E>     Type of the values.
     */
    <E> E requiredChoice(String key, Map<String, E> options) {
      E option = choice(key, options, null);
      if (option == null) {
        throw error(key, "is required");
      }
      return option;
    }

    /**
     * A [minimum, maximum] pair, both inside {@code min} to {@code max}.
     *
     * @param key Key in this object.
     * @param min Lowest allowed value.
     * @param max Highest allowed value.
     */
    double[] range(String key, double min, double max) {
      note(SettingField.Kind.RANGE, key, List.of(min, max), min, max, List.of());
      JsonNode value = take(key);
      if (value == null) {
        return new double[]{min, max};
      }
      if (!value.isArray() || value.size() != 2) {
        throw error(key, "expected [minimum, maximum]");
      }
      double low = finiteNumber(key, value.get(0));
      double high = finiteNumber(key, value.get(1));
      if (low < min || high > max) {
        throw error(key, "must be between " + min + " and " + max);
      }
      if (low > high) {
        throw error(key, "minimum must not exceed maximum");
      }
      return new double[]{low, high};
    }

    /**
     * An [x, y, z] vector that must be present.
     *
     * @param key Key in this object.
     */
    Vec3 requiredVector(String key) {
      note(SettingField.Kind.VECTOR, key, null, -INF, INF, List.of());
      JsonNode value = take(key);
      if (value == null) {
        throw error(key, "is required");
      }
      return parseVector(key, value);
    }

    /**
     * An [x, y, z] vector, or the fallback when missing.
     *
     * @param key      Key in this object.
     * @param fallback Value when the key is missing.
     */
    Vec3 vector(String key, Vec3 fallback) {
      note(SettingField.Kind.VECTOR, key, fallback, -INF, INF, List.of());
      JsonNode value = take(key);
      return value == null ? fallback : parseVector(key, value);
    }

    /**
     * Checks and reads an [x, y, z] array of finite numbers.
     *
     * @param key   Key in this object, named in errors.
     * @param value Value to read.
     */
    private Vec3 parseVector(String key, JsonNode value) {
      if (!value.isArray() || value.size() != 3) {
        throw error(key, "expected [x, y, z]");
      }
      return new Vec3(finiteNumber(key, value.get(0)), finiteNumber(key, value.get(1)),
              finiteNumber(key, value.get(2)));
    }

    /**
     * A curve, or the fallback when missing.
     *
     * @param key      Key in this object.
     * @param fallback Value when the key is missing.
     */
    Curve curve(String key, Curve fallback) {
      note(SettingField.Kind.CURVE, key, "default", -INF, INF, List.of());
      JsonNode value = take(key);
      return value == null ? fallback : parseCurve(key, value);
    }

    /**
     * A single number (a flat curve) or [[t, value], ...] pairs with increasing t.
     *
     * @param key   Key in this object, named in errors.
     * @param value Value to read.
     */
    private Curve parseCurve(String key, JsonNode value) {
      if (value.isNumber()) {
        return Curve.constant(finiteNumber(key, value));
      }
      if (!value.isArray() || value.isEmpty()) {
        throw error(key, "expected a number or [[t, value], ...]");
      }
      double[] pairs = new double[value.size() * 2];
      for (int i = 0; i < value.size(); i++) {
        JsonNode point = value.get(i);
        if (!point.isArray() || point.size() != 2) {
          throw error(key, "expected [t, value] pairs");
        }
        pairs[i * 2] = finiteNumber(key, point.get(0));
        pairs[i * 2 + 1] = finiteNumber(key, point.get(1));
      }
      try {
        return Curve.of(pairs);
      } catch (IllegalArgumentException e) {
        throw error(key, e.getMessage());
      }
    }

    /**
     * A setting that may vary: a plain number, or an object with a base, per-tree and per-branch variance, and a
     * profile curve along the parent.
     *
     * @param key      Key in this object.
     * @param fallback Value when the key is missing.
     */
    Param param(String key, Param fallback) {
      note(SettingField.Kind.PARAM, key, fallback.base(), -INF, INF, List.of());
      JsonNode value = take(key);
      if (value == null) {
        return fallback;
      }
      if (value.isNumber()) {
        return Param.of(finiteNumber(key, value));
      }
      if (!value.isObject()) {
        throw error(key, "expected a number or parameter object");
      }
      Reader object = new Reader(file, value, keyPath(key), null);
      Param param = new Param(
              object.number("base", fallback.base(), -INF, INF),
              object.number("treeVariance", 0, 0, INF),
              object.number("branchVariance", 0, 0, INF),
              object.curve("profile", Curve.constant(1)));
      object.finish();
      return param;
    }

    /**
     * A block name or a weighted list of blocks, or the fallback when missing.
     *
     * @param key          Key in this object.
     * @param fallback     Value when the key is missing.
     * @param fallbackNote Default shown in the editor.
     * @param allowOff     Whether the list may be {@code off}.
     */
    WeightedBlocks weights(String key, WeightedBlocks fallback, String fallbackNote, boolean allowOff) {
      note(SettingField.Kind.WEIGHTS, key, fallbackNote, 0, 0, List.of());
      JsonNode value = take(key);
      return value == null ? fallback : parseWeights(key, value, allowOff);
    }

    /**
     * A block type's rule: a block, a weighted list, or an object naming a noise, height or level rule.
     *
     * @param key          Key in this object.
     * @param fallback     Value when the key is missing.
     * @param fallbackNote Default shown in the editor.
     * @param allowOff     Whether the type may be {@code off}.
     */
    BlockRule rule(String key, BlockRule fallback, String fallbackNote, boolean allowOff) {
      note(SettingField.Kind.BLOCK_RULE, key, fallbackNote, 0, 0, List.of());
      JsonNode value = take(key);
      if (value == null) {
        return fallback;
      }
      if (!value.isObject() || !value.has("rule")) {
        return new BlockRule.Noise(parseWeights(key, value, allowOff), 1);
      }
      String kind = value.get("rule").asText();
      return switch (kind) {
        case "noise" -> noiseRule(key, value, allowOff);
        case "height" -> heightRule(key, value);
        case "level" -> levelRule(key, value);
        default -> throw error(key, "unknown rule '" + kind + "'");
      };
    }

    /**
     * Rejects any key in a rule object that the rule does not use.
     *
     * @param key     Key in this object, named in errors.
     * @param value   Rule object to check.
     * @param allowed Keys the rule uses.
     */
    private void onlyFields(String key, JsonNode value, Set<String> allowed) {
      for (Iterator<String> names = value.fieldNames(); names.hasNext(); ) {
        String name = names.next();
        if (!allowed.contains(name)) {
          throw error(key, "unknown rule setting '" + name + "'");
        }
      }
    }

    /**
     * A noise rule: weighted blocks mixed at random, in patches about {@code clump} blocks wide.
     *
     * @param key      Key in this object, named in errors.
     * @param value    Rule object.
     * @param allowOff Whether the type may be {@code off}.
     */
    private BlockRule noiseRule(String key, JsonNode value, boolean allowOff) {
      onlyFields(key, value, Set.of("rule", "clump", "blocks"));
      JsonNode blocks = value.get("blocks");
      if (blocks == null) {
        throw error(key, "noise rule needs blocks");
      }
      JsonNode clump = value.get("clump");
      if (clump != null && (!clump.isIntegralNumber() || clump.asInt() < 1 || clump.asInt() > BlockRule.MAX_CLUMP)) {
        throw error(key, "clump must be a whole number from 1 to " + BlockRule.MAX_CLUMP);
      }
      return new BlockRule.Noise(parseWeights(key, blocks, allowOff), clump == null ? 1 : clump.asInt());
    }

    /**
     * A height rule: bands from the bottom up, each used below a fraction of the tree's height, with the last band
     * running to the top.
     *
     * @param key   Key in this object, named in errors.
     * @param value Rule object.
     */
    private BlockRule heightRule(String key, JsonNode value) {
      onlyFields(key, value, Set.of("rule", "bands"));
      JsonNode items = value.get("bands");
      if (items == null || !items.isArray() || items.isEmpty()) {
        throw error(key, "height rule needs at least one band");
      }
      List<BlockRule.Band> bands = new ArrayList<>();
      double previous = 0;
      for (int i = 0; i < items.size(); i++) {
        JsonNode item = items.get(i);
        if (!item.path("block").isTextual()) {
          throw error(key, "each band needs a block");
        }
        onlyFields(key, item, Set.of("below", "block"));
        String block = blockName(key, item.get("block").textValue(), false);
        boolean last = i == items.size() - 1;
        if (last) {
          if (item.has("below")) {
            throw error(key, "the last band runs to the top and has no 'below'");
          }
          bands.add(new BlockRule.Band(1, block));
        } else {
          if (!item.path("below").isNumber()) {
            throw error(key, "each band but the last needs 'below'");
          }
          double below = item.get("below").asDouble();
          if (!(below > 0 && below < 1)) {
            throw error(key, "band 'below' must be between 0 and 1");
          }
          if (below <= previous) {
            throw error(key, "band 'below' values must increase");
          }
          previous = below;
          bands.add(new BlockRule.Band(below, block));
        }
      }
      return new BlockRule.Height(bands);
    }

    /**
     * A level rule: a block per branch level name, and another block for every level not listed.
     *
     * @param key   Key in this object, named in errors.
     * @param value Rule object.
     */
    private BlockRule levelRule(String key, JsonNode value) {
      onlyFields(key, value, Set.of("rule", "levels", "other"));
      if (!value.path("other").isTextual()) {
        throw error(key, "level rule needs an 'other' block");
      }
      JsonNode items = value.get("levels");
      if (items == null || !items.isObject()) {
        throw error(key, "level rule needs 'levels'");
      }
      Map<String, String> levels = new HashMap<>();
      for (Iterator<Map.Entry<String, JsonNode>> fields = items.fields(); fields.hasNext(); ) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (!field.getValue().isTextual()) {
          throw error(key, "level '" + field.getKey() + "' needs a block");
        }
        levels.put(field.getKey(), blockName(key, field.getValue().textValue(), false));
      }
      return new BlockRule.ByLevel(levels, blockName(key, value.get("other").textValue(), false));
    }

    /**
     * A single block name, or an object of block names to non-negative weights with at least one above zero.
     *
     * @param key      Key in this object, named in errors.
     * @param value    Value to read.
     * @param allowOff Whether the list may be {@code off}.
     */
    private WeightedBlocks parseWeights(String key, JsonNode value, boolean allowOff) {
      if (value.isTextual()) {
        return WeightedBlocks.of(blockName(key, value.textValue(), allowOff));
      }
      if (!value.isObject() || value.isEmpty()) {
        throw error(key, "expected a block name or a list of blocks with weights");
      }
      List<WeightedBlocks.Entry> entries = new ArrayList<>();
      double total = 0;
      for (Iterator<Map.Entry<String, JsonNode>> fields = value.fields(); fields.hasNext(); ) {
        Map.Entry<String, JsonNode> field = fields.next();
        String block = blockName(key, field.getKey(), allowOff);
        if (!field.getValue().isNumber() || !Double.isFinite(field.getValue().asDouble())) {
          throw error(key, "weight of '" + block + "' must be a number");
        }
        double weight = field.getValue().asDouble();
        if (weight < 0) {
          throw error(key, "weight of '" + block + "' must not be negative");
        }
        total += weight;
        entries.add(new WeightedBlocks.Entry(block, weight));
      }
      if (total <= 0) {
        throw error(key, "needs at least one weight above 0");
      }
      return new WeightedBlocks(entries);
    }

    /**
     * Checks a block name: lowercase, optionally namespaced, optionally with a state such as {@code [age=7]}.
     *
     * @param key      Key in this object, named in errors.
     * @param block    Block name to check.
     * @param allowOff Whether {@code off} is accepted.
     */
    private String blockName(String key, String block, boolean allowOff) {
      if (block.equals(WeightedBlocks.OFF) && !allowOff) {
        throw error(key, "cannot be off");
      }
      if (!BLOCK_NAME.matcher(block).matches()) {
        throw error(key, "invalid block name '" + block + "'");
      }
      return block;
    }

    /**
     * Rejects any key in this object that was never read, which catches typos in preset files.
     */
    void finish() {
      for (String name : fieldNames()) {
        if (!used.contains(name)) {
          throw error(name, "unknown setting");
        }
      }
    }
  }
}
