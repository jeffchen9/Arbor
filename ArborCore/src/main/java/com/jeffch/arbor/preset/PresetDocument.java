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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * A preset being edited live: its JSON, its settings, and the last version that was valid.
 */
public final class PresetDocument {
  /**
   * The kinds of force the editor can add.
   */
  public static final List<String> FORCE_KINDS = List.of("sun", "wind", "direction", "attractor", "curl");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String VOXEL_PREFIX = "shapes.";
  private static final Map<String, List<String>> VOXEL_GROUPS = voxelGroups();
  private static final String SURVIVAL_PREFIX = "survival.";
  private static final double SIMPLE_NEUTRAL_TOLERANCE = 1e-9;
  private static final String SURVIVAL_BLOCKS = SURVIVAL_PREFIX + "blocks.";
  private static final List<BlockRole> SURVIVAL_ROLES = List.of(BlockRole.LOG, BlockRole.JUNCTION, BlockRole.CUT_END,
          BlockRole.LEAVES);
  private static final Path UNTITLED = Path.of("untitled.json");
  /**
   * Rule values set aside by switching rule type or removing a block type, so switching back restores them.
   */
  private final Map<String, JsonNode> ruleMemory = new HashMap<>();
  private Path file;
  private boolean untitled;
  private ObjectNode root;
  private ObjectNode savedRoot;
  private String savedText;
  private SpeciesPreset preset;
  private Map<String, SettingField> fields = Map.of();
  private String error;
  private boolean diskError;
  private boolean changedOnDisk;

  private PresetDocument(Path file) {
    this.file = file;
  }

  /**
   * Opens a preset file for editing.
   *
   * @param file Preset file to open.
   */
  public static PresetDocument open(Path file) {
    PresetDocument document = new PresetDocument(file);
    String text = PresetLoader.readText(file);
    document.adopt(text, PresetLoader.parseText(file, text));
    if (document.preset == null) {
      throw new PresetException(file + ": " + document.error);
    }
    return document;
  }

  /**
   * A new preset from JSON text that has no file until it is saved. A non-null name replaces the preset's name.
   *
   * @param text JSON text of the preset.
   * @param name Name shown for the preset until it is saved.
   */
  public static PresetDocument create(String text, String name) {
    PresetDocument document = new PresetDocument(UNTITLED);
    document.untitled = true;
    ObjectNode node = PresetLoader.parseText(UNTITLED, text);
    if (name != null) {
      node.put("name", name);
    }
    document.adopt(text, node);
    if (document.preset == null) {
      throw new PresetException(UNTITLED + ": " + document.error);
    }
    return document;
  }

  /**
   * The Voxelizer tab's sections, each listing its settings in display order.
   */
  private static Map<String, List<String>> voxelGroups() {
    Map<String, List<String>> groups = new LinkedHashMap<>();
    groups.put("Branch blocks", List.of("branchDensity", "fullRadius", "wallRadius", "trunkFullRadius", "wallGapChance", "thinCoverage"));
    groups.put("Fence gates", List.of("gateChance", "gateInWallChance", "openGateChance"));
    groups.put("Fine and implied branches", List.of("impliedRadius", "impliedLeafSize", "impliedLeafStrength",
            "fineBranchChance", "fineGateChance", "tipCanopyDensity", "trimToCanopy"));
    groups.put("Branch clusters", List.of("deadBushChance", "deadBushDensity", "deadBushOffFenceChance",
            "saplingLeafDensity"));
    groups.put("Trunk smoothing", List.of("smoothTrunk", "smoothStairDepth", "smoothSlabDepth", "smoothWallDepth",
            "smoothFenceDepth", "buttonChance", "buttonSpacing"));
    groups.put("Leaf blocks", List.of("leafBlockDensity"));
    groups.put("Randomness", List.of("voxelRandomness"));
    return groups;
  }

  /**
   * Names identify parts of the tree, and the root level's parent and mode are fixed.
   *
   * @param field Setting to check.
   */
  private static boolean hiddenInEditor(SettingField field) {
    return field.key().equals("name") || field.path().equals("levels[0].parent")
            || field.path().equals("levels[0].generation.mode");
  }

  /**
   * A name such as {@code level4} that no level uses yet.
   *
   * @param levels The preset's levels.
   */
  private static String newLevelName(ArrayNode levels) {
    List<String> names = new ArrayList<>();
    levels.forEach(level -> names.add(level.path("name").asString()));
    int number = levels.size() + 1;
    while (names.contains("level" + number)) {
      number++;
    }
    return "level" + number;
  }

  /**
   * Renames a level inside a block-by-level rule, keeping the order of its entries.
   *
   * @param rule Block-by-level rule to change.
   * @param old  Level name to replace.
   * @param name New level name.
   */
  private static void renameRuleLevel(JsonNode rule, String old, String name) {
    if (rule instanceof ObjectNode object && "level".equals(object.path("rule").asString())
            && object.get("levels") instanceof ObjectNode levels && levels.has(old)) {
      ObjectNode renamed = JsonNodeFactory.instance.objectNode();
      levels.properties().forEach(entry ->
              renamed.set(entry.getKey().equals(old) ? name : entry.getKey(), entry.getValue()));
      object.set("levels", renamed);
    }
  }

  /**
   * Why a name cannot be used for item {@code index}, or null when it can: names must be present and unique.
   *
   * @param items List the item belongs to.
   * @param index Position of the item being named.
   * @param name  Name to check.
   */
  private static String nameProblem(ArrayNode items, int index, String name) {
    if (name.isEmpty()) {
      return "A name is required.";
    }
    for (int i = 0; i < items.size(); i++) {
      if (i != index && name.equals(items.get(i).path("name").asString())) {
        return "\"" + name + "\" is already used.";
      }
    }
    return null;
  }

  /**
   * Updates every item whose {@code key} names {@code old}, such as leaves whose parent level was renamed.
   *
   * @param items Items to update.
   * @param key   Field that may name {@code old}.
   * @param old   Name being replaced.
   * @param name  Replacement name.
   */
  private static void replaceReferences(JsonNode items, String key, String old, String name) {
    if (items instanceof ArrayNode list) {
      for (JsonNode item : list) {
        if (item instanceof ObjectNode object && object.path(key).isString()
                && old.equals(object.get(key).asString())) {
          object.put(key, name);
        }
      }
    }
  }

  /**
   * The block type a setting path ends with, such as LOG for {@code blocks.log}, or null for any other setting.
   *
   * @param path Setting path.
   */
  private static BlockRole roleOf(String path) {
    String key = path.substring(path.lastIndexOf('.') + 1);
    for (BlockRole role : BlockRole.values()) {
      if (role.jsonKey().equals(key)) {
        return role;
      }
    }
    return null;
  }

  /**
   * The six-sided wood block made from a log, such as {@code oak_wood} for {@code oak_log}.
   *
   * @param log Log block name.
   */
  private static String sixSided(String log) {
    if (log.endsWith("_log")) {
      return log.substring(0, log.length() - "_log".length()) + "_wood";
    }
    if (log.endsWith("crimson_stem") || log.endsWith("warped_stem")) {
      return log.substring(0, log.length() - "_stem".length()) + "_hyphae";
    }
    return log;
  }

  /**
   * The name of every plant layer, in order.
   *
   * @param layers The preset's plant layers.
   */
  private static List<String> layerNames(ArrayNode layers) {
    List<String> names = new ArrayList<>();
    layers.forEach(layer -> names.add(layer.path("name").asString()));
    return names;
  }

  /**
   * The first of {@code prefix + start}, {@code prefix + (start + 1)}, ... that no layer uses.
   *
   * @param layers The preset's plant layers.
   * @param prefix Start of the name.
   * @param start  First number to try.
   */
  private static String uniqueLayerName(ArrayNode layers, String prefix, int start) {
    List<String> names = layerNames(layers);
    int number = start;
    while (names.contains(prefix + number)) {
      number++;
    }
    return prefix + number;
  }

  /**
   * Splits a setting path such as {@code levels[2].radius.min} into object keys and list indexes.
   *
   * @param path Setting path to split.
   */
  private static List<Object> keys(String path) {
    List<Object> keys = new ArrayList<>();
    for (String part : path.split("\\.")) {
      int bracket = part.indexOf('[');
      if (bracket < 0) {
        keys.add(part);
      } else {
        keys.add(part.substring(0, bracket));
        keys.add(Integer.valueOf(part.substring(bracket + 1, part.length() - 1)));
      }
    }
    return keys;
  }

  /**
   * A JSON number, written without a decimal point when it is whole, so saved files stay tidy.
   *
   * @param value Number to write.
   */
  private static JsonNode numberNode(double value) {
    boolean whole = value == Math.rint(value) && Math.abs(value) < Integer.MAX_VALUE;
    return whole ? IntNode.valueOf((int) value) : DoubleNode.valueOf(value);
  }

  /**
   * A variable setting written as a plain number when only its base is left.
   *
   * @param param Variable setting in object form.
   */
  private static JsonNode collapsed(ObjectNode param) {
    return param.size() == 1 && param.has("base") ? param.get("base") : param;
  }

  /**
   * The file this preset saves to, or null until it has been saved.
   */
  public Path file() {
    return untitled ? null : file;
  }

  /**
   * The file name, or a placeholder for a preset with no file yet.
   */
  public String displayName() {
    return file.getFileName().toString();
  }

  /**
   * Starts editing this JSON as the saved state, forgetting any rule values set aside earlier.
   *
   * @param text JSON text as saved.
   * @param node Parsed form of {@code text}.
   */
  private void adopt(String text, ObjectNode node) {
    root = node;
    savedRoot = node.deepCopy();
    savedText = text;
    changedOnDisk = false;
    diskError = false;
    ruleMemory.clear();
    validate();
  }

  /**
   * Re-reads the JSON. When it is valid, the preset and the setting list are replaced; when not, the error is kept and
   * the last valid preset stays in use.
   */
  private void validate() {
    List<SettingField> schema = new ArrayList<>();
    try {
      preset = PresetLoader.parse(file, root, schema);
      Map<String, SettingField> byPath = new LinkedHashMap<>();
      schema.forEach(field -> byPath.put(field.path(), field));
      fields = byPath;
      error = null;
    } catch (PresetException e) {
      error = withoutFile(e.getMessage());
    }
  }

  /**
   * An error message without the leading file name, which the editor already shows.
   *
   * @param message Error message to shorten.
   */
  private String withoutFile(String message) {
    String prefix = file + ": ";
    return message.startsWith(prefix) ? message.substring(prefix.length()) : message;
  }

  /**
   * The newest valid preset, which stays available while an edit is invalid.
   */
  public SpeciesPreset preset() {
    return preset;
  }

  public String error() {
    return error;
  }

  public boolean dirty() {
    return !root.equals(savedRoot);
  }

  public boolean changedOnDisk() {
    return changedOnDisk;
  }

  public SettingField field(String path) {
    return fields.get(path);
  }

  /**
   * Settings for one tab, either the voxelizer's or the tree's.
   *
   * @param voxelizer Whether to list the voxelizer's settings rather than the tree's.
   */
  public List<Section> sections(boolean voxelizer) {
    return voxelizer ? voxelSections() : treeSections();
  }

  /**
   * The settings of each decorator, one section apiece.
   */
  public List<Section> decoratorSections() {
    List<Section> sections = new ArrayList<>();
    for (String[] decorator : new String[][]{{"Pinecones", "pinecones"}, {"Vines", "vines"}, {"Moss", "moss"}}) {
      if (!root.path("decorators").has(decorator[1])) {
        continue;
      }
      String prefix = "decorators." + decorator[1] + ".";
      List<SettingField> settings = fields.values().stream().filter(field -> field.path().startsWith(prefix)).toList();
      sections.add(new Section(decorator[0], List.of(new Group("", settings))));
    }
    return sections;
  }

  /**
   * Whether the preset has this decorator, such as {@code moss}, even if it is switched off.
   *
   * @param key Decorator name, such as {@code pinecones}.
   */
  public boolean hasDecorator(String key) {
    return root.path("decorators").has(key);
  }

  /**
   * Adds a decorator, switched on, with its default settings.
   *
   * @param key Decorator name, such as {@code pinecones}.
   */
  public void addDecorator(String key) {
    setBoolean("decorators." + key + ".enabled", true);
  }

  public void removeDecorator(String key) {
    clear("decorators." + key);
  }

  /**
   * The Voxelizer tab: the current mode's voxel settings in their groups, then its block types, then each plant layer
   * (Advanced only).
   */
  private List<Section> voxelSections() {
    List<Section> sections = new ArrayList<>();
    List<SettingField> remaining = new ArrayList<>();
    boolean survival = preset.shapes().survival();
    // Each mode edits its own stored values, so a change in one never reaches the other.
    String shapesPrefix = survival ? SURVIVAL_PREFIX + VOXEL_PREFIX : VOXEL_PREFIX;
    String blocksPrefix = survival ? SURVIVAL_BLOCKS : "blocks.";
    fields.values().stream().filter(field -> field.path().startsWith(shapesPrefix)
            && !field.path().equals(VOXEL_PREFIX + "mode")).forEach(remaining::add);
    VOXEL_GROUPS.forEach((title, keys) -> {
      List<SettingField> group = new ArrayList<>();
      for (String key : keys) {
        SettingField field = fields.get(shapesPrefix + key);
        if (field != null) {
          group.add(field);
          remaining.remove(field);
        }
      }
      if (!group.isEmpty()) {
        sections.add(new Section(title, List.of(new Group("", group))));
      }
    });
    if (!remaining.isEmpty()) {
      sections.add(new Section("Other", List.of(new Group("", remaining))));
    }
    List<BlockRole> active = activeRoles(survival);
    List<SettingField> blocks = fields.values().stream().filter(field -> field.path().startsWith(blocksPrefix)
            && active.contains(roleOf(field.path()))).toList();
    if (!blocks.isEmpty()) {
      sections.add(new Section("Blocks", List.of(new Group("", blocks))));
    }
    for (int i = 0; !survival && i < layerCount(); i++) {
      String prefix = "plantLayers[" + i + "].";
      if (!(root.get("plantLayers").get(i) instanceof ObjectNode)) {
        continue;
      }
      List<SettingField> layer = fields.values().stream().filter(field -> field.path().startsWith(prefix)).toList();
      if (!layer.isEmpty()) {
        sections.add(new Section("Plant layer: " + nameAt(prefix + "name"), List.of(new Group("", layer))));
      }
    }
    return sections;
  }

  /**
   * The Tree tab: one section per top-level part (tree, crown shape, each force and level), grouped by sub-section,
   * with leaf generators placed under their levels.
   */
  private List<Section> treeSections() {
    Map<String, Map<String, List<SettingField>>> grouped = new LinkedHashMap<>();
    for (SettingField field : fields.values()) {
      String path = field.path();
      if (path.startsWith(VOXEL_PREFIX) || path.startsWith("blocks.") || path.startsWith(SURVIVAL_PREFIX)
              || path.startsWith("plantLayers[") || path.startsWith("simple.") || path.startsWith("decorators.") || hiddenInEditor(field)) {
        continue;
      }
      String[] parts = path.split("\\.");
      boolean listItem = parts[0].endsWith("]");
      String group = parts.length > 2 ? parts[1] : listItem && !parts[0].startsWith("forces[") ? "General" : "";
      grouped.computeIfAbsent(parts[0], key -> new LinkedHashMap<>())
              .computeIfAbsent(group, key -> new ArrayList<>()).add(field);
    }
    Map<String, Section> built = new LinkedHashMap<>();
    grouped.forEach((owner, groups) -> {
      List<Group> list = new ArrayList<>();
      groups.forEach((title, items) -> list.add(new Group(title, items)));
      built.put(owner, new Section(sectionTitle(owner), list));
    });
    return leavesUnderTheirLevels(built);
  }

  /**
   * Lists each leaf generator directly below the level it grows on.
   *
   * @param built Sections in preset order, keyed by the setting path that owns them.
   */
  private List<Section> leavesUnderTheirLevels(Map<String, Section> built) {
    List<Section> sections = new ArrayList<>();
    List<String> leaves = built.keySet().stream().filter(owner -> owner.startsWith("leaves[")).toList();
    List<String> placed = new ArrayList<>();
    built.forEach((owner, section) -> {
      if (owner.startsWith("leaves[")) {
        return;
      }
      sections.add(section);
      if (owner.startsWith("levels[")) {
        String level = nameAt(owner + ".name");
        for (String leaf : leaves) {
          if (!placed.contains(leaf) && level.equals(nameAt(leaf + ".parent"))) {
            sections.add(built.get(leaf));
            placed.add(leaf);
          }
        }
      }
    });
    leaves.stream().filter(leaf -> !placed.contains(leaf)).forEach(leaf -> sections.add(built.get(leaf)));
    return sections;
  }

  /**
   * The header for a section, such as {@code Level: trunk} or {@code Force: wind}.
   *
   * @param owner Setting path of the section's owner, such as {@code levels[0]}.
   */
  private String sectionTitle(String owner) {
    if (owner.equals("tree")) {
      return "Tree";
    }
    if (owner.equals("shapeControl")) {
      return "Crown shape";
    }
    if (owner.startsWith("levels[")) {
      return "Level: " + nameAt(owner + ".name");
    }
    if (owner.startsWith("forces[")) {
      return "Force: " + nameAt(owner + ".name");
    }
    if (owner.startsWith("leaves[")) {
      int index = Integer.parseInt(owner.substring(7, owner.length() - 1));
      return "Leaves " + (index + 1) + " on " + nameAt(owner + ".parent");
    }
    return owner;
  }

  /**
   * A name as written in the JSON. After a reload that failed validation it can be missing.
   *
   * @param path Path of the name setting.
   */
  private String nameAt(String path) {
    JsonNode name = value(path);
    return name == null || name.asString().isBlank() ? "(unnamed)" : name.asString();
  }

  public int levelCount() {
    return root.get("levels").size();
  }

  /**
   * Adds a finer level under the finest one, starting from that level's settings.
   */
  public void addLevel() {
    ArrayNode levels = (ArrayNode) root.get("levels");
    ObjectNode added = JsonNodeFactory.instance.objectNode();
    added.put("name", newLevelName(levels));
    if (levels.size() == 1) {
      added.putObject("generation").put("mode", "flood");
      added.putObject("length").put("percentOfParent", 0.5);
      added.putObject("radius").put("percentOfParent", 0.6);
    } else {
      ObjectNode copy = (ObjectNode) levels.get(levels.size() - 1).deepCopy();
      copy.remove(List.of("name", "parent", "flares"));
      added.setAll(copy);
    }
    levels.add(added);
    validate();
  }

  /**
   * Removes the finest level and moves its leaves to its parent. The trunk always stays.
   */
  public void removeLevel() {
    ArrayNode levels = (ArrayNode) root.get("levels");
    if (levels.size() <= 1) {
      return;
    }
    JsonNode removed = levels.get(levels.size() - 1);
    String name = removed.path("name").asString();
    String parent = removed.hasNonNull("parent")
            ? removed.get("parent").asString() : levels.get(levels.size() - 2).path("name").asString();
    if (!name.isBlank() && root.get("leaves") instanceof ArrayNode leaves) {
      for (JsonNode leaf : leaves) {
        if (leaf instanceof ObjectNode object && name.equals(leaf.path("parent").asString())) {
          object.put("parent", parent);
        }
      }
    }
    if (!name.isBlank() && root.get("plantLayers") instanceof ArrayNode layers) {
      for (JsonNode layer : layers) {
        if (layer instanceof ObjectNode object && name.equals(layer.path("nearBranch").asString())) {
          object.put("nearBranch", parent);
        }
      }
    }
    levels.remove(levels.size() - 1);
    validate();
  }

  /**
   * Adds a force of one of the {@link #FORCE_KINDS} with a uniquely numbered name. Every level responds to it fully
   * until edited.
   *
   * @param kind Force type.
   */
  public void addForce(String kind) {
    JsonNode existing = root.get("forces");
    if (existing != null && !existing.isNull() && !existing.isArray()) {
      error = "forces: must be a list, so cannot add a force";
      return;
    }
    ArrayNode forces = existing instanceof ArrayNode array ? array : root.putArray("forces");
    List<String> names = new ArrayList<>();
    forces.forEach(force -> names.add(force.path("name").asString()));
    String name = kind;
    for (int number = 2; names.contains(name); number++) {
      name = kind + number;
    }
    ObjectNode force = forces.addObject();
    force.put("name", name);
    switch (kind) {
      case "sun" -> {
        force.put("type", "attractor");
        force.putArray("position").add(0).add(90).add(0);
        force.put("strength", 0.03);
      }
      case "wind" -> {
        force.put("type", "wind");
        force.putArray("vector").add(1).add(0).add(0);
        force.put("strength", 0.04).put("directionVariance", 30).put("gust", 0.3).put("gustSize", 12)
                .put("heightPower", 1).put("flexibility", 1.5);
      }
      case "direction" -> {
        force.put("type", "direction");
        force.putArray("vector").add(1).add(0).add(0);
        force.put("strength", 0.02);
      }
      case "attractor" -> {
        force.put("type", "attractor");
        force.putArray("position").add(16).add(16).add(0);
        force.put("strength", 0.03);
      }
      case "curl" -> {
        force.put("type", "curl");
        force.putArray("vector").add(0).add(1).add(0);
        force.put("strength", 0.02);
      }
      default -> throw new IllegalArgumentException("unknown force kind: " + kind);
    }
    validate();
  }

  /**
   * Gives a force the vector or position its new type needs, so changing type in the editor stays valid.
   *
   * @param path Path of the force's type setting.
   * @param type New force type.
   */
  private void fillForceType(String path, String type) {
    if (!path.startsWith("forces[") || !path.endsWith("].type")
            || !(value(path.substring(0, path.length() - ".type".length())) instanceof ObjectNode force)) {
      return;
    }
    if (type.equals("attractor")) {
      force.remove("vector");
      if (!force.has("position")) {
        force.putArray("position").add(0).add(90).add(0);
      }
    } else {
      force.remove("position");
      if (!force.has("vector")) {
        force.putArray("vector").add(type.equals("curl") ? 0 : 1).add(type.equals("curl") ? 1 : 0).add(0);
      }
    }
  }

  /**
   * Removes a force and every level's response to it, since a response to a missing force is invalid.
   *
   * @param index Position of the force.
   */
  public void removeForce(int index) {
    if (!(root.get("forces") instanceof ArrayNode forces) || index < 0 || index >= forces.size()) {
      return;
    }
    String name = forces.get(index).path("name").asString();
    forces.remove(index);
    if (forces.isEmpty()) {
      root.remove("forces");
    }
    if (root.get("levels") instanceof ArrayNode levels) {
      for (JsonNode level : levels) {
        if (level instanceof ObjectNode object && object.get("forces") instanceof ObjectNode responses) {
          responses.remove(name);
          if (responses.isEmpty()) {
            object.remove("forces");
          }
        }
      }
    }
    validate();
  }

  /**
   * Renames a level and everything that refers to it. Returns why the name was refused, or null.
   *
   * @param index Position of the level.
   * @param typed Name as typed.
   */
  public String renameLevel(int index, String typed) {
    if (!(root.get("levels") instanceof ArrayNode levels) || !(levels.get(index) instanceof ObjectNode level)) {
      return "This level no longer exists.";
    }
    String name = typed.trim();
    String old = level.path("name").asString();
    String problem = name.equals("any") ? "\"any\" is reserved for plant layers." : nameProblem(levels, index, name);
    if (problem != null || name.equals(old)) {
      return problem;
    }
    level.put("name", name);
    replaceReferences(levels, "parent", old, name);
    replaceReferences(root.get("leaves"), "parent", old, name);
    replaceReferences(root.get("plantLayers"), "nearBranch", old, name);
    root.path("blocks").forEach(rule -> renameRuleLevel(rule, old, name));
    root.path("survival").path("blocks").forEach(rule -> renameRuleLevel(rule, old, name));
    ruleMemory.values().forEach(rule -> renameRuleLevel(rule, old, name));
    validate();
    return null;
  }

  /**
   * Renames a force and every level's response to it. Returns why the name was refused, or null.
   *
   * @param index Position of the force.
   * @param typed Name as typed.
   */
  public String renameForce(int index, String typed) {
    if (!(root.get("forces") instanceof ArrayNode forces) || !(forces.get(index) instanceof ObjectNode force)) {
      return "This force no longer exists.";
    }
    String name = typed.trim();
    String old = force.path("name").asString();
    String problem = nameProblem(forces, index, name);
    if (problem != null || name.equals(old)) {
      return problem;
    }
    force.put("name", name);
    if (root.get("levels") instanceof ArrayNode levels) {
      for (JsonNode level : levels) {
        if (level instanceof ObjectNode object && object.get("forces") instanceof ObjectNode responses
                && responses.has(old)) {
          ObjectNode renamed = JsonNodeFactory.instance.objectNode();
          responses.properties().forEach(entry ->
                  renamed.set(entry.getKey().equals(old) ? name : entry.getKey(), entry.getValue()));
          object.set("forces", renamed);
        }
      }
    }
    validate();
    return null;
  }

  /**
   * A role's blocks in order. An unset role shows its default, and an unset derived role shows nothing.
   *
   * @param path Path of the block type.
   */
  public LinkedHashMap<String, Double> weights(String path) {
    LinkedHashMap<String, Double> result = new LinkedHashMap<>();
    JsonNode node = value(path);
    if (node == null && path.startsWith(SURVIVAL_BLOCKS)) {
      // An unset survival role shows the full list it follows, so a first edit keeps the other choices.
      String key = path.substring(SURVIVAL_BLOCKS.length());
      preset.survival().blocks().roles().forEach((role, choices) -> {
        if (role.jsonKey().equals(key)) {
          choices.entries().forEach(entry -> result.put(entry.block(), entry.weight()));
        }
      });
    } else if (node == null) {
      SettingField field = fields.get(path);
      if (field != null && field.fallback() instanceof String block && !block.startsWith("follows ")) {
        result.put(block, 1.0);
      }
    } else if (node.isString()) {
      result.put(node.stringValue(), 1.0);
    } else if (node.has("rule")) {
      JsonNode blocks = node.get("blocks");
      if (blocks != null && blocks.isString()) {
        result.put(blocks.stringValue(), 1.0);
      } else if (blocks != null) {
        blocks.properties().iterator().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asDouble()));
      } else {
        BlockRule rule = ruleOf(path);
        if (rule != null) {
          rule.blockNames().forEach(block -> result.put(block, 1.0));
        }
      }
    } else {
      node.properties().iterator().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asDouble()));
    }
    return result;
  }

  /**
   * The rule a block type uses right now, inherited or written, from the current mode's blocks.
   *
   * @param path Path of the block type.
   */
  private BlockRule ruleOf(String path) {
    BlockSettings settings = path.startsWith(SURVIVAL_BLOCKS) ? preset.survival().blocks() : preset.blocks();
    BlockRole role = roleOf(path);
    return role == null ? null : settings.rules().get(role);
  }

  /**
   * How a block type chooses its block: {@code solid}, {@code noise}, {@code height} or {@code level}.
   *
   * @param path Path of the block type.
   */
  public String ruleKind(String path) {
    JsonNode node = value(path);
    if (node != null) {
      return node.isString() ? "solid" : node.has("rule") ? node.get("rule").asString() : "noise";
    }
    return switch (ruleOf(path)) {
      case null -> "solid";
      case BlockRule.Height height -> "height";
      case BlockRule.ByLevel level -> "level";
      case BlockRule.Noise noise -> noise.clump() > 1 || noise.blocks().entries().size() > 1 ? "noise" : "solid";
    };
  }

  /**
   * Changes the rule type. The old type's values are kept for this session and return when it is chosen again.
   *
   * @param path Path of the block type.
   * @param kind New rule type: {@code solid}, {@code noise}, {@code height} or {@code level}.
   */
  public void setRuleKind(String path, String kind) {
    String current = ruleKind(path);
    if (kind.equals(current)) {
      return;
    }
    List<String> names = usableBlocks(path);
    JsonNode node = value(path);
    ruleMemory.put(path + "|" + current, node != null ? node.deepCopy() : NullNode.getInstance());
    JsonNode remembered = ruleMemory.get(path + "|" + kind);
    if (remembered != null) {
      if (remembered.isNull()) {
        clear(path);
      } else {
        put(path, remembered.deepCopy());
      }
      return;
    }
    String first = names.get(0);
    ObjectNode rule = JsonNodeFactory.instance.objectNode();
    switch (kind) {
      case "solid" -> {
        setText(path, first);
        return;
      }
      case "noise" -> {
        rule.put("rule", "noise");
        rule.put("clump", 1);
        ObjectNode blocks = rule.putObject("blocks");
        names.forEach(block -> blocks.put(block, 1));
      }
      case "height" -> {
        rule.put("rule", "height");
        ArrayNode bands = rule.putArray("bands");
        bands.addObject().put("below", 0.5).put("block", first);
        bands.addObject().put("block", names.get(Math.min(1, names.size() - 1)));
      }
      case "level" -> {
        rule.put("rule", "level");
        ObjectNode levels = rule.putObject("levels");
        List<LevelPreset> treeLevels = preset.levels();
        for (int i = 0; i < treeLevels.size(); i++) {
          levels.put(treeLevels.get(i).name(), names.get(i % names.size()));
        }
        rule.put("other", first);
      }
      default -> throw new IllegalArgumentException("unknown rule: " + kind);
    }
    put(path, rule);
  }

  /**
   * The real blocks a rule can start from; an unset type starts from the blocks it follows.
   *
   * @param path Path of the block type.
   */
  private List<String> usableBlocks(String path) {
    BlockRule rule = ruleOf(path);
    List<String> names = rule == null ? List.of() : rule.blockNames().stream()
            .filter(block -> !block.equals(WeightedBlocks.OFF)).toList();
    if (!names.isEmpty()) {
      return names;
    }
    BlockSettings settings = path.startsWith(SURVIVAL_BLOCKS) ? preset.survival().blocks() : preset.blocks();
    BlockRole role = roleOf(path);
    if (role == BlockRole.STAIRS || role == BlockRole.SLAB) {
      BlockRule planks = settings.rules().get(BlockRole.PLANKS);
      String plank = planks == null ? "oak_planks" : planks.blockNames().get(0);
      String family = plank.endsWith("_planks") ? plank.substring(0, plank.length() - "_planks".length()) : "oak";
      return List.of(family + (role == BlockRole.STAIRS ? "_stairs" : "_slab"));
    }
    if (role == BlockRole.JUNCTION) {
      return settings.rules().get(BlockRole.LOG).blockNames().stream().map(PresetDocument::sixSided)
              .distinct().toList();
    }
    if (role == BlockRole.CUT_END) {
      return settings.rules().get(BlockRole.LOG).blockNames();
    }
    BlockRule fallback = BlockSettings.defaults().rules().get(role);
    return fallback == null ? List.of("oak_log") : fallback.blockNames();
  }

  /**
   * Patch size of a noise rule; 1 for any other rule.
   *
   * @param path Path of the block type.
   */
  public int clump(String path) {
    return ruleOf(path) instanceof BlockRule.Noise noise ? noise.clump() : 1;
  }

  /**
   * Writes the type as a noise rule with this patch size, keeping its blocks and weights.
   *
   * @param path  Path of the block type.
   * @param clump Patch size.
   */
  public void setClump(String path, int clump) {
    putNoise(path, weights(path), clump);
  }

  /**
   * Writes a full noise rule object.
   *
   * @param path  Path of the block type.
   * @param list  Blocks and their weights.
   * @param clump Patch size.
   */
  private void putNoise(String path, Map<String, Double> list, int clump) {
    ObjectNode rule = JsonNodeFactory.instance.objectNode();
    rule.put("rule", "noise");
    rule.put("clump", clump);
    ObjectNode blocks = rule.putObject("blocks");
    list.forEach((block, weight) -> blocks.set(block, numberNode(weight)));
    put(path, rule);
  }

  /**
   * The height bands of a block type, lowest first; empty for any other rule.
   *
   * @param path Path of the block type.
   */
  public List<BlockRule.Band> bands(String path) {
    // Read the JSON first, so an edit builds on a value that is invalid only until the next one is typed.
    if (value(path) instanceof ObjectNode rule && rule.get("bands") instanceof ArrayNode items) {
      List<BlockRule.Band> bands = new ArrayList<>();
      items.forEach(item -> bands.add(new BlockRule.Band(item.path("below").asDouble(1), item.path("block").asString())));
      return bands;
    }
    return ruleOf(path) instanceof BlockRule.Height height ? height.bands() : List.of();
  }

  /**
   * Writes height bands, lowest first. The last band always runs to the top.
   *
   * @param path  Path of the block type.
   * @param bands Height bands, lowest first.
   */
  public void setBands(String path, List<BlockRule.Band> bands) {
    if (bands.isEmpty()) {
      return;
    }
    ObjectNode rule = JsonNodeFactory.instance.objectNode();
    rule.put("rule", "height");
    ArrayNode items = rule.putArray("bands");
    for (int i = 0; i < bands.size(); i++) {
      ObjectNode item = items.addObject();
      if (i < bands.size() - 1) {
        item.set("below", numberNode(bands.get(i).below()));
      }
      item.put("block", bands.get(i).block());
    }
    put(path, rule);
  }

  /**
   * The block chosen for each named level in a level rule; empty for any other rule.
   *
   * @param path Path of the block type.
   */
  public Map<String, String> levelBlocks(String path) {
    return ruleOf(path) instanceof BlockRule.ByLevel level ? level.levels() : Map.of();
  }

  /**
   * The block a level rule uses for levels it does not name, or null for any other rule.
   *
   * @param path Path of the block type.
   */
  public String otherBlock(String path) {
    return ruleOf(path) instanceof BlockRule.ByLevel level ? level.other() : null;
  }

  /**
   * Sets one level's block; null makes that level use the rule's other block.
   *
   * @param path  Path of the block type.
   * @param level Level name.
   * @param block Block for that level, or null to use the other block.
   */
  public void setLevelBlock(String path, String level, String block) {
    ObjectNode rule = editableLevelRule(path);
    if (rule == null) {
      return;
    }
    ObjectNode levels = rule.get("levels") instanceof ObjectNode existing ? existing : rule.putObject("levels");
    if (block == null) {
      levels.remove(level);
    } else {
      levels.put(level, block);
    }
    put(path, rule);
  }

  /**
   * Sets the block a level rule uses for levels it does not name.
   *
   * @param path  Path of the block type.
   * @param block Block for levels the rule does not name.
   */
  public void setOtherBlock(String path, String block) {
    ObjectNode rule = editableLevelRule(path);
    if (rule != null) {
      put(path, rule.put("other", block));
    }
  }

  /**
   * A copy of the level rule to edit, written out in full when it is inherited. Null for any other rule.
   *
   * @param path Path of the block type.
   */
  private ObjectNode editableLevelRule(String path) {
    JsonNode node = value(path);
    if (node instanceof ObjectNode current && "level".equals(current.path("rule").asString())) {
      return current.deepCopy();
    }
    if (node != null || !(ruleOf(path) instanceof BlockRule.ByLevel inherited)) {
      return null;
    }
    ObjectNode rule = JsonNodeFactory.instance.objectNode();
    rule.put("rule", "level");
    ObjectNode levels = rule.putObject("levels");
    inherited.levels().forEach(levels::put);
    rule.put("other", inherited.other());
    return rule;
  }

  /**
   * The block types this mode shows, in order. Logs are always listed, and so are Survival's leaves.
   *
   * @param survival Whether to list the Survival blocks rather than the Creative ones.
   */
  public List<BlockRole> activeRoles(boolean survival) {
    String prefix = survival ? SURVIVAL_BLOCKS : "blocks.";
    List<BlockRole> active = new ArrayList<>();
    for (BlockRole role : survival ? SURVIVAL_ROLES : List.of(BlockRole.values())) {
      JsonNode node = value(prefix + role.jsonKey());
      boolean off = node != null && node.isString() && node.stringValue().equals(WeightedBlocks.OFF);
      boolean listed = role == BlockRole.LOG || (survival && role == BlockRole.LEAVES)
              || (survival || role == BlockRole.CUT_END ? node != null && !off : !off);
      if (listed) {
        active.add(role);
      }
    }
    return active;
  }

  /**
   * Turns a block type on, restoring its rule if it was removed this session.
   *
   * @param role     Block type to turn on.
   * @param survival Whether this is the Survival blocks rather than the Creative ones.
   */
  public void addRole(BlockRole role, boolean survival) {
    if (activeRoles(survival).contains(role)) {
      return;
    }
    String path = (survival ? SURVIVAL_BLOCKS : "blocks.") + role.jsonKey();
    JsonNode remembered = ruleMemory.get(path + "|removed");
    if (remembered != null) {
      put(path, remembered.deepCopy());
    } else if (survival || role == BlockRole.CUT_END) {
      setText(path, usableBlocks(path).get(0));
    } else {
      clear(path);
    }
  }

  /**
   * Turns a block type off. Logs, and Survival's leaves, are required and stay.
   *
   * @param role     Block type to turn off.
   * @param survival Whether this is the Survival blocks rather than the Creative ones.
   */
  public void removeRole(BlockRole role, boolean survival) {
    if (role == BlockRole.LOG || (survival && role == BlockRole.LEAVES)) {
      return;
    }
    String path = (survival ? SURVIVAL_BLOCKS : "blocks.") + role.jsonKey();
    JsonNode node = value(path);
    if (node != null) {
      ruleMemory.put(path + "|removed", node.deepCopy());
    }
    if (survival || role == BlockRole.CUT_END) {
      clear(path);
    } else {
      setText(path, WeightedBlocks.OFF);
    }
  }

  /**
   * Adds a block with weight 1 to a list; a block already listed is left alone.
   *
   * @param path  Path of the block list.
   * @param block Block to add.
   */
  public void addBlock(String path, String block) {
    LinkedHashMap<String, Double> list = weights(path);
    if (block.isBlank() || list.containsKey(block)) {
      return;
    }
    list.put(block, 1.0);
    writeWeights(path, list);
  }

  /**
   * Removes a block from a list. A plant layer or decorator always keeps at least one block, so removing its last one
   * does nothing.
   *
   * @param path  Path of the block list.
   * @param block Block to remove.
   */
  public void removeBlock(String path, String block) {
    LinkedHashMap<String, Double> list = weights(path);
    if (list.remove(block) == null) {
      return;
    }
    if (list.isEmpty() && (path.startsWith("plantLayers[") || path.startsWith("decorators."))) {
      return;
    }
    writeWeights(path, list);
  }

  /**
   * Renames a block keeping its place and weight. Renaming onto a block already listed adds the weights.
   *
   * @param path    Path of the block list.
   * @param oldName Block to rename.
   * @param newName Its new name.
   */
  public void renameBlock(String path, String oldName, String newName) {
    LinkedHashMap<String, Double> list = weights(path);
    if (newName.isBlank() || oldName.equals(newName) || !list.containsKey(oldName)) {
      return;
    }
    LinkedHashMap<String, Double> renamed = new LinkedHashMap<>();
    list.forEach((block, weight) -> {
      if (block.equals(oldName)) {
        renamed.merge(newName, weight, Double::sum);
      } else {
        renamed.merge(block, weight, Double::sum);
      }
    });
    writeWeights(path, renamed);
  }

  /**
   * Changes one listed block's weight; a block not in the list is ignored.
   *
   * @param path   Path of the block list.
   * @param block  Block to change.
   * @param weight New weight.
   */
  public void setWeight(String path, String block, double weight) {
    LinkedHashMap<String, Double> list = weights(path);
    if (list.containsKey(block)) {
      list.put(block, weight);
      writeWeights(path, list);
    }
  }

  /**
   * Writes a block list in its simplest form: removed when empty, a noise rule when one is in use, a plain name for a
   * single block of weight 1, or an object of weights.
   *
   * @param path Path of the block list.
   * @param list Blocks and their weights, in order.
   */
  private void writeWeights(String path, LinkedHashMap<String, Double> list) {
    if (list.isEmpty()) {
      clear(path);
    } else if (value(path) instanceof ObjectNode rule && "noise".equals(rule.path("rule").asString())) {
      putNoise(path, list, rule.path("clump").asInt(1));
    } else if (value(path) == null && ruleOf(path) instanceof BlockRule.Noise inherited && inherited.clump() > 1) {
      putNoise(path, list, inherited.clump());
    } else if (list.size() == 1 && list.values().iterator().next() == 1.0) {
      setText(path, list.keySet().iterator().next());
    } else {
      ObjectNode object = JsonNodeFactory.instance.objectNode();
      list.forEach((block, weight) -> object.set(block, numberNode(weight)));
      put(path, object);
    }
  }

  /**
   * A Simple slider's value with nothing stored: 0 for an offset, 1 for a multiplier, or the preset's own size variance or crown start.
   *
   * @param key Simple slider name.
   */
  public double simpleNeutral(String key) {
    return switch (key) {
      case "branchAngle", "droop", "windLean" -> 0;
      case "sizeVariety" -> preset.tree().sizeVariance();
      case "crownStart" -> SimpleAdjuster.crownStart(preset);
      default -> 1;
    };
  }

  /**
   * A Simple slider's value, or its neutral value when nothing is stored.
   *
   * @param key Simple slider name.
   */
  public double simpleValue(String key) {
    JsonNode node = value("simple." + key);
    return node != null ? node.asDouble() : simpleNeutral(key);
  }

  /**
   * Stores a Simple slider. A neutral value is removed, along with the section once it is empty.
   *
   * @param key    Simple slider name.
   * @param number Value to store.
   */
  public void setSimpleValue(String key, double number) {
    if (Math.abs(number - simpleNeutral(key)) < SIMPLE_NEUTRAL_TOLERANCE) {
      clear("simple." + key);
    } else {
      setNumber("simple." + key, number);
    }
  }

  /**
   * Whether any Simple slider is changing the tree.
   */
  public boolean simpleActive() {
    return value("simple") != null;
  }

  /**
   * Moves the Simple sliders the same way they were moved in another preset, such as the tree this one replaces as the
   * starting tree. Sliders left at neutral there keep this preset's own neutral values.
   *
   * @param previous Preset whose Simple sliders to copy.
   */
  public void keepSimpleFrom(PresetDocument previous) {
    if (previous.value("simple") instanceof ObjectNode stored) {
      stored.propertyNames().forEach(key -> setSimpleValue(key, stored.get(key).asDouble()));
    }
  }

  /**
   * Returns every Simple slider to neutral.
   */
  public void resetSimple() {
    clear("simple");
  }

  /**
   * Writes a [minimum, maximum] pair.
   *
   * @param path Setting path.
   * @param min  Minimum.
   * @param max  Maximum.
   */
  public void setRange(String path, double min, double max) {
    ArrayNode range = JsonNodeFactory.instance.arrayNode();
    range.add(numberNode(min)).add(numberNode(max));
    put(path, range);
  }

  /**
   * The number of plant layers.
   */
  public int layerCount() {
    return root.get("plantLayers") instanceof ArrayNode layers ? layers.size() : 0;
  }

  /**
   * The layer list, or null when there is none. Only {@link #addLayer} may create it.
   */
  private ArrayNode layers() {
    return root.get("plantLayers") instanceof ArrayNode layers ? layers : null;
  }

  /**
   * Adds a plant layer of short grass at the end, with a new unique name.
   */
  public void addLayer() {
    JsonNode existing = root.get("plantLayers");
    if (existing != null && !existing.isNull() && !existing.isArray()) {
      error = "plantLayers: must be a list, so cannot add a layer";
      return;
    }
    ArrayNode layers = existing instanceof ArrayNode array ? array : root.putArray("plantLayers");
    ObjectNode layer = JsonNodeFactory.instance.objectNode();
    layer.put("name", uniqueLayerName(layers, "layer", layers.size() + 1));
    layer.put("blocks", "short_grass");
    layer.put("chance", 0.1);
    layers.add(layer);
    validate();
  }

  /**
   * Copies a layer and inserts it right after the original, named {@code <name> copy} (numbered if needed).
   *
   * @param index Position of the layer to copy.
   */
  public void duplicateLayer(int index) {
    ArrayNode layers = layers();
    if (layers == null || index < 0 || index >= layers.size()) {
      return;
    }
    if (!(layers.get(index) instanceof ObjectNode source)) {
      return;
    }
    ObjectNode copy = source.deepCopy();
    String base = copy.path("name").asString() + " copy";
    String name = base;
    for (int number = 2; layerNames(layers).contains(name); number++) {
      name = base + " " + number;
    }
    copy.put("name", name);
    layers.insert(index + 1, copy);
    validate();
  }

  /**
   * Removes a layer, and the list itself once it is empty.
   *
   * @param index Position of the layer.
   */
  public void removeLayer(int index) {
    ArrayNode layers = layers();
    if (layers == null || index < 0 || index >= layers.size()) {
      return;
    }
    layers.remove(index);
    if (layers.isEmpty()) {
      root.remove("plantLayers");
    }
    validate();
  }

  /**
   * Removes one leaf generator. An index out of range does nothing.
   *
   * @param index Position of the leaf generator.
   */
  public void removeLeaves(int index) {
    if (!(root.get("leaves") instanceof ArrayNode leaves) || index < 0 || index >= leaves.size()) {
      return;
    }
    leaves.remove(index);
    if (leaves.isEmpty()) {
      root.remove("leaves");
    }
    validate();
  }

  /**
   * Moves a layer up (negative) or down. A move past either end does nothing.
   *
   * @param index Position of the layer.
   * @param delta Places to move; negative moves up.
   */
  public void moveLayer(int index, int delta) {
    ArrayNode layers = layers();
    int target = index + delta;
    if (layers == null || index < 0 || index >= layers.size() || target < 0 || target >= layers.size() || delta == 0) {
      return;
    }
    JsonNode moved = layers.remove(index);
    layers.insert(target, moved);
    validate();
  }

  /**
   * The value written in the JSON, or null when the setting uses its default.
   *
   * @param path Setting path.
   */
  public JsonNode value(String path) {
    return valueIn(root, path);
  }

  /**
   * Whether a setting differs from the last saved version.
   *
   * @param path Setting path.
   */
  public boolean changed(String path) {
    return !Objects.equals(value(path), valueIn(savedRoot, path));
  }

  /**
   * Puts a setting back to its last saved value, or removes it when the save did not have it.
   *
   * @param path Setting path.
   */
  public void revertValue(String path) {
    JsonNode saved = valueIn(savedRoot, path);
    if (saved == null) {
      clear(path);
    } else {
      put(path, saved.deepCopy());
    }
  }

  /**
   * A value inside a JSON tree, or null when it or anything on the way to it is missing.
   *
   * @param top  Top of the tree.
   * @param path Setting path.
   */
  private JsonNode valueIn(JsonNode top, String path) {
    JsonNode node = top;
    for (Object key : keys(path)) {
      node = key instanceof Integer index ? node.get(index) : node.get((String) key);
      if (node == null || node.isNull()) {
        return null;
      }
    }
    return node;
  }

  /**
   * Writes a value, creating missing objects along the path. A path through a list item that does not exist, or through
   * a value that is not an object, is reported as an error instead.
   *
   * @param path  Setting path.
   * @param value Value to write.
   */
  private void put(String path, JsonNode value) {
    List<Object> keys = keys(path);
    JsonNode node = root;
    for (int i = 0; i + 1 < keys.size(); i++) {
      Object key = keys.get(i);
      JsonNode next = key instanceof Integer index ? node.get(index) : node.get((String) key);
      if (next == null || next.isNull()) {
        if (!(node instanceof ObjectNode parent) || !(key instanceof String name)) {
          error = path + ": cannot set a value here because the tree design has no object to hold it";
          return;
        }
        next = JsonNodeFactory.instance.objectNode();
        parent.set(name, next);
      }
      node = next;
    }
    if (!(node instanceof ObjectNode holder)) {
      error = path + ": cannot set a value here because its parent is not an object";
      return;
    }
    holder.set((String) keys.get(keys.size() - 1), value);
    validate();
  }

  /**
   * Writes a number.
   *
   * @param path  Setting path.
   * @param value Number to write.
   */
  public void setNumber(String path, double value) {
    put(path, numberNode(value));
  }

  /**
   * Writes true or false.
   *
   * @param path  Setting path.
   * @param value Value to write.
   */
  public void setBoolean(String path, boolean value) {
    put(path, BooleanNode.valueOf(value));
  }

  /**
   * Writes text. Changing a force's type also fills in the settings the new type requires.
   *
   * @param path  Setting path.
   * @param value Text to write.
   */
  public void setText(String path, String value) {
    fillForceType(path, value);
    put(path, StringNode.valueOf(value));
  }

  /**
   * Writes an [x, y, z] vector.
   *
   * @param path Setting path.
   * @param x    X component.
   * @param y    Y component.
   * @param z    Z component.
   */
  public void setVector(String path, double x, double y, double z) {
    ArrayNode vector = JsonNodeFactory.instance.arrayNode();
    vector.add(numberNode(x)).add(numberNode(y)).add(numberNode(z));
    put(path, vector);
  }

  /**
   * Sets a curve from typed JSON. Blank text returns it to its default.
   *
   * @param path Setting path.
   * @param text Curve as JSON text.
   */
  public void setJsonText(String path, String text) {
    if (text.isBlank()) {
      clear(path);
      return;
    }
    JsonNode parsed;
    try {
      parsed = MAPPER.readTree(text);
    } catch (JacksonException e) {
      error = path + ": invalid JSON";
      return;
    }
    put(path, parsed);
  }

  /**
   * Returns to the default by removing the setting and any section left empty.
   *
   * @param path Setting path.
   */
  public void clear(String path) {
    List<Object> keys = keys(path);
    List<JsonNode> containers = new ArrayList<>();
    JsonNode node = root;
    for (int i = 0; i < keys.size(); i++) {
      containers.add(node);
      Object key = keys.get(i);
      node = key instanceof Integer index ? node.get(index) : node.get((String) key);
      if (node == null) {
        return;
      }
    }
    for (int i = keys.size() - 1; i >= 0 && keys.get(i) instanceof String key; i--) {
      ObjectNode container = (ObjectNode) containers.get(i);
      container.remove(key);
      if (!container.isEmpty() || i == 0 || keys.get(i - 1) instanceof Integer) {
        break;
      }
    }
    validate();
  }

  /**
   * One part of a variable setting (base, treeVariance or branchVariance). A missing variance is 0 and a missing base
   * is the fallback.
   *
   * @param path     Setting path.
   * @param part     {@code base}, {@code treeVariance} or {@code branchVariance}.
   * @param fallback Base value when the setting is not written.
   */
  public double paramPart(String path, String part, double fallback) {
    JsonNode node = value(path);
    boolean base = part.equals("base");
    if (node == null) {
      return base ? fallback : 0;
    }
    if (node.isNumber()) {
      return base ? node.asDouble() : 0;
    }
    JsonNode inner = node.get(part);
    return inner != null ? inner.asDouble() : base ? fallback : 0;
  }

  /**
   * Writes the plain number form whenever only a base value is left.
   *
   * @param path  Setting path.
   * @param part  {@code base}, {@code treeVariance} or {@code branchVariance}.
   * @param value Value to write.
   */
  public void setParamPart(String path, String part, double value) {
    ObjectNode param = expanded(path, value);
    if (part.equals("base") || value != 0) {
      param.set(part, numberNode(value));
    } else {
      param.remove(part);
    }
    put(path, collapsed(param));
  }

  /**
   * Sets a variable setting's profile curve from typed JSON; blank text removes it.
   *
   * @param path     Setting path.
   * @param fallback Base value when the setting is not written.
   * @param text     Profile curve as JSON text, or blank to remove it.
   */
  public void setParamProfile(String path, double fallback, String text) {
    ObjectNode param = expanded(path, fallback);
    if (text.isBlank()) {
      param.remove("profile");
      put(path, collapsed(param));
      return;
    }
    try {
      param.set("profile", MAPPER.readTree(text));
      put(path, param);
    } catch (JacksonException e) {
      error = path + ".profile: invalid JSON";
    }
  }

  /**
   * A copy of a variable setting in object form, so one part can be changed without losing the others.
   *
   * @param path     Setting path.
   * @param fallback Base value when the setting is not written.
   */
  private ObjectNode expanded(String path, double fallback) {
    JsonNode node = value(path);
    if (node instanceof ObjectNode object) {
      return object.deepCopy();
    }
    ObjectNode param = JsonNodeFactory.instance.objectNode();
    param.set("base", node != null ? node : numberNode(fallback));
    return param;
  }

  /**
   * Writes to a new file and keeps editing it. If that fails, the preset stays with its current file.
   *
   * @param target File to write.
   */
  public boolean saveAs(Path target) {
    Path previous = file;
    boolean wasUntitled = untitled;
    file = target;
    untitled = false;
    if (save()) {
      return true;
    }
    file = previous;
    untitled = wasUntitled;
    return false;
  }

  /**
   * Clears a failed save's message so saving can be tried again.
   */
  public void clearSaveError() {
    validate();
  }

  /**
   * Writes the file atomically and returns whether it worked. An invalid or untitled preset is never saved.
   */
  public boolean save() {
    if (error != null || untitled) {
      return false;
    }
    try {
      String text = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + System.lineSeparator();
      Path absolute = file.toAbsolutePath();
      Path temporary = Files.createTempFile(absolute.getParent(), ".preset", ".tmp");
      try {
        Files.writeString(temporary, text);
        Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
      savedRoot = root.deepCopy();
      savedText = text;
      changedOnDisk = false;
      return true;
    } catch (IOException e) {
      error = "cannot save: " + e.getMessage();
      return false;
    }
  }

  /**
   * Discards edits and returns to the file on disk, or to the starting point of a preset with no file.
   */
  public void revert() {
    ruleMemory.clear();
    if (untitled) {
      root = savedRoot.deepCopy();
      validate();
      return;
    }
    try {
      String text = PresetLoader.readText(file);
      adopt(text, PresetLoader.parseText(file, text));
    } catch (PresetException e) {
      root = savedRoot.deepCopy();
      changedOnDisk = false;
      validate();
    }
  }

  /**
   * Picks up a save from another program, unless that would overwrite unsaved edits.
   */
  public boolean reloadFromDisk() {
    if (untitled) {
      return false;
    }
    String text;
    try {
      text = PresetLoader.readText(file);
    } catch (PresetException e) {
      return false;
    }
    if (text.equals(savedText)) {
      changedOnDisk = false;
      if (diskError) {
        diskError = false;
        validate();
        return error == null;
      }
      return false;
    }
    if (dirty()) {
      changedOnDisk = true;
      return false;
    }
    try {
      adopt(text, PresetLoader.parseText(file, text));
    } catch (PresetException e) {
      error = withoutFile(e.getMessage());
      diskError = true;
      return false;
    }
    diskError = error != null;
    return error == null;
  }

  /**
   * A titled set of settings inside an editor section.
   *
   * @param title  Fold-out title, or empty to show the fields directly.
   * @param fields Settings in this group, in display order.
   */
  public record Group(String title, List<SettingField> fields) {
  }

  /**
   * One collapsible section of the settings editor.
   *
   * @param title  Header text.
   * @param groups Groups of settings in this section.
   */
  public record Section(String title, List<Group> groups) {
  }
}
