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

import com.jeffch.arbor.export.BatchExporter;
import com.jeffch.arbor.export.SchematicWriter;
import com.jeffch.arbor.pipeline.GeneratedTree;
import com.jeffch.arbor.pipeline.TreeGenerator;
import com.jeffch.arbor.pipeline.TrunkMeasure;
import com.jeffch.arbor.foliage.DensityGenerator;
import com.jeffch.arbor.preset.PresetDocument;
import com.jeffch.arbor.preset.SettingField;
import com.jeffch.arbor.preset.SpeciesPreset;
import com.jeffch.arbor.shape.BranchLevels;
import com.jeffch.arbor.shape.ShapeClass;
import com.jeffch.arbor.shape.ShapeGrid;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What the MCP server's tools do, without MCP. Every tool opens the preset file, works on it and saves it back, so
 * there is no state between calls. Problems come back as one-line {@link IllegalArgumentException}s. The SDK runs
 * calls in parallel, so the tools take turns: otherwise two edits to one file would each save over the other.
 */
final class ArborTools {
  static final int MAX_EXPORT = 1000;
  private static final int RENDER_SECONDS = 60;
  private static final List<String> DECORATORS = List.of("pinecones", "vines", "moss");
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int HISTORY_LIMIT = 50;
  /**
   * Each file's edits through these tools, newest last, kept while the server runs.
   */
  private static final Map<Path, Deque<Edit>> HISTORY = new HashMap<>();
  private static final Set<String> BLOCK_RULE_KEYS = Set.of("rule", "clump", "below");
  /**
   * How a value of each kind is written in JSON, for kinds a range or option list cannot describe.
   */
  private static final Map<SettingField.Kind, String> FORMATS = Map.of(
          SettingField.Kind.PARAM, "a number, or {\"base\": n, \"treeVariance\": n, \"branchVariance\": n,"
                  + " \"profile\": CURVE}. base is the value; treeVariance is the largest random change, up or down,"
                  + " chosen once per tree; branchVariance the same, chosen per branch; profile multiplies base along"
                  + " the parent, from its base (0) to its tip (1). Keys left out are 0, and profile 1.",
          SettingField.Kind.CURVE, "a number for a flat curve, or [[t, value], ...] with t rising from 0 to 1;"
                  + " values between points are interpolated.",
          SettingField.Kind.VECTOR, "[x, y, z]; y is up.",
          SettingField.Kind.RANGE, "[min, max] with min at most max.",
          SettingField.Kind.WEIGHTS, "a block name such as \"short_grass\", or an object of block names to weights,"
                  + " such as {\"short_grass\": 3, \"tall_grass\": 1}: each is picked with the chance weight / total."
                  + " A name may carry a block state, such as \"wheat[age=7]\". Check names with list_blocks.",
          SettingField.Kind.BLOCK_RULE, "one of: a block name; a WEIGHTS object, whose blocks are mixed at random,"
                  + " block by block; {\"rule\": \"noise\", \"blocks\": WEIGHTS, \"clump\": 1 to "
                  + com.jeffch.arbor.preset.BlockRule.MAX_CLUMP + "}, the same mix in patches about clump blocks"
                  + " wide; {\"rule\": \"height\", \"bands\": [{\"below\": 0.3, \"block\": \"a\"}, {\"block\": \"b\"}]},"
                  + " bands from the bottom up, each to a share of the tree's height, the last with no below; or"
                  + " {\"rule\": \"level\", \"levels\": {\"trunk\": \"a\"}, \"other\": \"b\"}, a block per branch level"
                  + " name and one for the rest. There is no \"random\" rule: a WEIGHTS object is the random mix."
                  + " \"off\" turns a type off, for every type but logs; a type left out uses the default its"
                  + " current value shows, such as follows log. Check names with list_blocks.",
          SettingField.Kind.CHOICE, "one of the allowed options, as a string.");
  /**
   * Block names in the Minecraft version found, with that version's name, or null before the first look.
   */
  private static Map.Entry<String, Set<String>> knownBlocks;
  private static final int MAX_COLORS = 120;
  /**
   * Reads block colors from the Minecraft textures; made on first use.
   */
  private static BlockColors blockColors;
  /**
   * A tree from scratch: one plain trunk, for the assistant to add branches, leaves and blocks to.
   */
  private static final String SCRATCH = "{\"name\":\"new_tree\",\"tree\":{\"maxHeight\":32},\"levels\":[{\"name\":"
          + "\"trunk\",\"length\":{\"absolute\":12},\"radius\":{\"absolute\":1}}]}";

  private ArborTools() {
  }

  static String bundledPresets() {
    return String.join(", ", PresetFiles.TEMPLATES);
  }

  /**
   * Starts a new preset file: a bare trunk to build on, or a copy of a bundled species.
   *
   * @param template Bundled preset name, or null to start from scratch.
   * @param file     New preset file; must not exist yet.
   */
  static synchronized String newDesign(String template, Path file) {
    Path target = file.toAbsolutePath();
    if (template != null && !PresetFiles.TEMPLATES.contains(template)) {
      throw new IllegalArgumentException("no bundled preset " + template + "; choose from " + bundledPresets());
    }
    if (Files.exists(target)) {
      throw new IllegalArgumentException(target + " already exists");
    }
    return guarded(() -> {
      String name = target.getFileName().toString().replaceFirst("(?i)\\.json$", "");
      PresetDocument document = template != null ? PresetFiles.fromTemplate(template)
              : PresetDocument.create(SCRATCH, name);
      if (!document.saveAs(target)) {
        throw new IllegalArgumentException(target + ": " + document.error());
      }
      return "created " + target;
    });
  }

  /**
   * A header, one line per setting (path, readable name, unit, kind, range or options, current value), then what each
   * setting means. Levels share their settings' meanings, so each meaning is written once, by the path's last two
   * parts, which keeps the reply short.
   *
   * @param file Tree design file.
   */
  static synchronized String describeSettings(Path file) {
    return guarded(() -> {
      PresetDocument document = open(file);
      List<String> lines = new ArrayList<>(List.of("path | name | unit | kind | allowed | current"));
      List<String> paths = new ArrayList<>();
      Set<SettingField.Kind> kinds = new TreeSet<>();
      for (String line : SimpleTreePanel.describe(document)) {
        lines.add(line);
        paths.add(line.substring(0, line.indexOf(' ')));
      }
      List<PresetDocument.Section> sections = new ArrayList<>(document.sections(false));
      sections.addAll(document.sections(true));
      sections.addAll(document.decoratorSections());
      for (PresetDocument.Section section : sections) {
        for (PresetDocument.Group group : section.groups()) {
          for (SettingField field : group.fields()) {
            Object current = document.value(field.path()) != null ? document.value(field.path()) : field.fallback();
            String range = field.options() != null && !field.options().isEmpty() ? String.join("/", field.options())
                    : field.min() < field.max() ? field.min() + ".." + field.max()
                    : FORMATS.containsKey(field.kind()) ? "see " + field.kind() + " below" : "";
            lines.add(field.path() + " | " + SettingNames.of(field.path()) + " | " + SettingNames.unit(field.path())
                    + " | " + field.kind() + " | " + range + " | current " + current);
            paths.add(field.path());
            kinds.add(field.kind());
          }
        }
      }
      lines.add("");
      lines.add("What each setting does, by the last two parts of its path (levels[2].length.min is length.min):");
      java.util.Set<String> written = new java.util.HashSet<>();
      for (String path : paths) {
        String key = SettingNames.lookupKey(path);
        if (written.add(key)) {
          lines.add(key + ": " + SettingNames.description(path));
        }
      }
      lines.add("");
      lines.add("How values are written, by kind:");
      kinds.stream().filter(FORMATS::containsKey).forEach(kind -> lines.add(kind + ": " + FORMATS.get(kind)));
      return String.join("\n", lines);
    });
  }

  /**
   * A preset file's JSON, or a bundled species' JSON when given its name, as an example to learn the format from.
   *
   * @param file Preset file, or a bundled species name.
   */
  static synchronized String readDesign(Path file) {
    if (PresetFiles.TEMPLATES.contains(file.toString())) {
      return PresetFiles.templateText(file.toString());
    }
    return guarded(() -> {
      try {
        return Files.readString(open(file).file());
      } catch (IOException e) {
        throw new IllegalArgumentException(file.toAbsolutePath() + ": " + e.getMessage());
      }
    });
  }

  /**
   * Sets one setting and saves, or leaves the file alone when the value is invalid.
   *
   * @param file Preset file.
   * @param path Setting path.
   * @param json New value as JSON.
   */
  static synchronized String setSetting(Path file, String path, String json) {
    return guarded(() -> {
      PresetDocument document = open(file);
      document.setJsonText(path, json);
      if (document.error() != null) {
        throw new IllegalArgumentException(document.error());
      }
      saved(document, "set " + path);
      return "ok" + blockWarning(document);
    });
  }

  /**
   * Applies a list of changes in order and saves once at the end. Nothing is saved if any change fails, so the design
   * is never left half edited. Each change is one of: {@code {"set": path, "value": json}}, {@code {"reset": path}},
   * {@code {"add": part, ...}} or {@code {"remove": part, ...}}, where a part is level, force, leaves, plant_layer or
   * decorator.
   *
   * @param file    Tree design file.
   * @param changes The changes, as a JSON array.
   */
  static synchronized String editDesign(Path file, String changes) {
    return guarded(() -> {
      JsonNode list;
      try {
        list = JSON.readTree(changes);
      } catch (JacksonException e) {
        throw new IllegalArgumentException("changes: invalid JSON");
      }
      if (!list.isArray() || list.isEmpty()) {
        throw new IllegalArgumentException("changes must be a list with at least one change");
      }
      PresetDocument document = open(file);
      List<String> done = new ArrayList<>();
      for (int i = 0; i < list.size(); i++) {
        JsonNode change = list.get(i);
        String what = "change " + (i + 1) + " " + change;
        String result;
        try {
          result = apply(document, change);
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException(what + ": " + e.getMessage() + "; nothing was saved");
        }
        if (document.error() != null) {
          throw new IllegalArgumentException(what + ": " + document.error() + "; nothing was saved");
        }
        done.add(result);
      }
      saved(document, String.join("; ", done));
      return "saved " + done.size() + " change" + (done.size() == 1 ? "" : "s") + ":\n" + String.join("\n", done)
              + blockWarning(document);
    });
  }

  /**
   * One change of {@link #editDesign}. Returns what it did, naming anything it added.
   *
   * @param document Design being edited.
   * @param change   The change.
   */
  private static String apply(PresetDocument document, JsonNode change) {
    if (!change.isObject()) {
      throw new IllegalArgumentException("each change must be an object");
    }
    if (change.has("set")) {
      String path = change.path("set").asString("");
      if (!change.has("value")) {
        throw new IllegalArgumentException("a set needs a value; use reset to return a setting to its default");
      }
      document.setJsonText(path, JSON.writeValueAsString(change.get("value")));
      return "set " + path;
    }
    if (change.has("reset")) {
      String path = change.path("reset").asString("");
      document.clear(path);
      return "reset " + path + " to its default";
    }
    boolean adding = change.has("add");
    if (!adding && !change.has("remove")) {
      throw new IllegalArgumentException("a change needs set, reset, add or remove");
    }
    String part = change.path(adding ? "add" : "remove").asString("");
    String kind = change.path("kind").asString("");
    switch (part) {
      case "level" -> {
        if (adding) {
          document.addLevel();
          int index = document.levelCount() - 1;
          return "added level " + document.value("levels[" + index + "].name").asString() + " as levels[" + index + "]";
        }
        if (document.levelCount() <= 1) {
          throw new IllegalArgumentException("the trunk cannot be removed");
        }
        int index = document.levelCount() - 1;
        String name = document.value("levels[" + index + "].name").asString();
        document.removeLevel();
        return "removed the finest level, " + name + "; its leaves and plant layers moved to its parent";
      }
      case "force" -> {
        if (adding) {
          if (!PresetDocument.FORCE_KINDS.contains(kind)) {
            throw new IllegalArgumentException("kind must be one of " + PresetDocument.FORCE_KINDS);
          }
          document.addForce(kind);
          int index = size(document, "forces") - 1;
          return "added force " + document.value("forces[" + index + "].name").asString() + " as forces[" + index + "]";
        }
        int index = index(document, change, "forces");
        document.removeForce(index);
        return "removed forces[" + index + "] and every level's response to it";
      }
      case "leaves" -> {
        if (adding) {
          String parent = change.has("parent") ? change.path("parent").asString("")
                  : document.value("levels[" + (document.levelCount() - 1) + "].name").asString();
          ArrayNode leaves = document.value("leaves") instanceof ArrayNode existing ? (ArrayNode) existing.deepCopy()
                  : JSON.createArrayNode();
          ObjectNode added = leaves.addObject().put("parent", parent);
          added.putObject("generation").put("mode", "absolute").put("count", 2).put("first", 0.4);
          added.put("size", 2);
          document.setJsonText("leaves", JSON.writeValueAsString(leaves));
          return "added leaves on " + parent + " as leaves[" + (leaves.size() - 1) + "]";
        }
        int index = index(document, change, "leaves");
        document.removeLeaves(index);
        return "removed leaves[" + index + "]";
      }
      case "plant_layer" -> {
        if (adding) {
          document.addLayer();
          int index = document.layerCount() - 1;
          return "added plant layer " + document.value("plantLayers[" + index + "].name").asString()
                  + " as plantLayers[" + index + "]";
        }
        int index = index(document, change, "plantLayers");
        document.removeLayer(index);
        return "removed plantLayers[" + index + "]";
      }
      case "decorator" -> {
        if (!DECORATORS.contains(kind)) {
          throw new IllegalArgumentException("kind must be one of " + DECORATORS);
        }
        if (adding) {
          document.addDecorator(kind);
          return "added decorator " + kind + " as decorators." + kind;
        }
        if (!document.hasDecorator(kind)) {
          throw new IllegalArgumentException("there is no " + kind + " decorator");
        }
        document.removeDecorator(kind);
        return "removed decorator " + kind;
      }
      default -> throw new IllegalArgumentException("the part must be level, force, leaves, plant_layer or decorator");
    }
  }

  private static int size(PresetDocument document, String list) {
    return document.value(list) instanceof ArrayNode array ? array.size() : 0;
  }

  /**
   * The {@code index} of a change, refused when that list has no such item.
   *
   * @param document Design being edited.
   * @param change   The change.
   * @param list     Top-level list the index points into.
   */
  private static int index(PresetDocument document, JsonNode change, String list) {
    int size = size(document, list);
    JsonNode index = change.get("index");
    if (index == null || !index.canConvertToInt() || !index.isIntegralNumber()
            || index.asInt() < 0 || index.asInt() >= size) {
      throw new IllegalArgumentException("index must be a whole number from 0 to " + (size - 1) + " for " + list
              + (size == 0 ? ", which is empty" : ""));
    }
    return index.asInt();
  }

  /**
   * Builds the tree and sums it up: its size, the shape of its crown and trunk, where the leaves are, and its blocks,
   * so a tree can be compared with a photo in numbers as well as by eye.
   *
   * @param file Preset file.
   * @param seed Tree seed.
   */
  static synchronized String generate(Path file, long seed) {
    return guarded(() -> {
      PresetDocument document = open(file);
      SpeciesPreset preset = document.preset();
      return describe(TreeGenerator.generate(preset, seed), preset) + blockWarning(document);
    });
  }

  /**
   * The {@link #generate} summary of several seeds, one line each after a heading, for render_variations.
   *
   * @param file  Preset file.
   * @param seed  First seed.
   * @param count Number of seeds.
   */
  static synchronized String variationsSummary(Path file, long seed, long count) {
    return guarded(() -> {
      var preset = open(file).preset();
      StringBuilder lines = new StringBuilder("Seeds " + seed + " to " + (seed + count - 1)
              + ", front view, one scale.");
      for (long next = seed; next < seed + count; next++) {
        lines.append("\nSeed ").append(next).append(": ").append(describe(TreeGenerator.generate(preset, next), preset));
      }
      return lines.toString();
    });
  }

  private static String describe(GeneratedTree tree, SpeciesPreset preset) {
    ShapeGrid shapes = tree.shapes();
    if (shapes.isEmpty()) {
      return "an empty tree: nothing grew";
    }
    int[] min = shapes.min();
    int[] max = shapes.max();
    int height = max[1] - min[1] + 1;
    // Leaf blocks and the plant layers that grow in the crown, such as a willow's hanging strands.
    int[] leafLow = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
    int[] leafHigh = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    long[] leaves = new long[2];
    shapes.forEachSorted((x, y, z, shape) -> {
      if (ViewerState.FOLIAGE.contains(shape.shapeClass())) {
        int[] at = {x, y, z};
        for (int i = 0; i < 3; i++) {
          leafLow[i] = Math.min(leafLow[i], at[i]);
          leafHigh[i] = Math.max(leafHigh[i], at[i]);
        }
        leaves[0]++;
        if (y - min[1] >= height / 2.0) {
          leaves[1]++;
        }
      }
    });
    StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "%d wide × %d tall × %d deep, %,d blocks.",
            max[0] - min[0] + 1, height, max[2] - min[2] + 1,
            shapes.counts().values().stream().mapToInt(Integer::intValue).sum()));
    if (leaves[0] == 0) {
      text.append(" Crown: no foliage.");
    } else {
      int crownWidth = Math.max(leafHigh[0] - leafLow[0], leafHigh[2] - leafLow[2]) + 1;
      int crownHeight = leafHigh[1] - leafLow[1] + 1;
      text.append(String.format(Locale.ROOT, " Crown: lowest foliage at %d%% of the height; %d wide, %.1f × its own"
                      + " height.", Math.round(100.0 * (leafLow[1] - min[1]) / height), crownWidth,
              crownWidth / (double) crownHeight));
    }
    if (!tree.skeleton().branches().isEmpty()) {
      text.append(String.format(Locale.ROOT, " Trunk: %.1f blocks across at breast height.",
              TrunkMeasure.breastDiameter(tree)));
    }
    if (leaves[0] > 0) {
      text.append(String.format(Locale.ROOT, " Foliage: %d%% of it is in the top half of the tree.",
              Math.round(100.0 * leaves[1] / leaves[0])));
    }
    String counts = StatsPanel.SHAPE_LABELS.entrySet().stream()
            .filter(entry -> shapes.counts().getOrDefault(entry.getKey(), 0) > 0)
            .map(entry -> String.format(Locale.ROOT, "%s %,d", entry.getValue(), shapes.counts().get(entry.getKey())))
            .collect(Collectors.joining(", "));
    return text.append(" Blocks: ").append(counts).append('.').append(parts(tree, preset)).toString();
  }

  /**
   * Where the tree's parts come from: branches and wood blocks per level, leaf clusters per leaf generator and plants
   * per plant layer, so a part that vanished can be traced to the setting behind it.
   *
   * @param tree   The generated tree.
   * @param preset The design as written.
   */
  private static String parts(GeneratedTree tree, SpeciesPreset preset) {
    Map<Integer, Integer> wood = BranchLevels.woodCounts(tree.shapes(), tree.skeleton());
    List<String> levels = new ArrayList<>();
    for (int i = 0; i < preset.levels().size(); i++) {
      int level = i;
      long branches = tree.skeleton().branches().stream().filter(branch -> branch.level() == level).count();
      levels.add(String.format(Locale.ROOT, "%s %,d (%,d wood blocks)", preset.levels().get(i).name(), branches,
              wood.getOrDefault(i, 0)));
    }
    StringBuilder text = new StringBuilder(" Branches by level: ").append(String.join(", ", levels)).append('.');
    if (!preset.leaves().isEmpty()) {
      List<Integer> clusters = DensityGenerator.clusterCounts(tree.skeleton(),
              TreeGenerator.effective(preset, tree.seed()), tree.seed());
      List<String> leaves = new ArrayList<>();
      for (int i = 0; i < clusters.size(); i++) {
        String parent = preset.leaves().get(i).parent();
        long on = tree.skeleton().branches().stream().filter(branch -> branch.levelName().equals(parent)).count();
        leaves.add(String.format(Locale.ROOT, "leaves[%d] %,d on %,d %s branches", i, clusters.get(i), on, parent));
      }
      text.append(" Leaf clusters: ").append(String.join(", ", leaves)).append('.');
    }
    if (!preset.plantLayers().isEmpty()) {
      List<String> layers = preset.plantLayers().stream().map(layer -> tree.plants().containsKey(layer.name())
              ? String.format(Locale.ROOT, "%s %,d", layer.name(), tree.plants().get(layer.name()))
              : layer.name() + " off on this seed").toList();
      text.append(" Plants by layer, after floating ones are removed: ").append(String.join(", ", layers)).append('.');
    }
    return text.toString();
  }

  /**
   * Writes trees with different seeds as structure files.
   *
   * @param file   Preset file.
   * @param count  Number of trees, 1 to {@link #MAX_EXPORT}.
   * @param folder Output folder, or null for one named after the preset.
   */
  static synchronized String export(Path file, long count, Path folder) {
    if (count < 1 || count > MAX_EXPORT) {
      throw new IllegalArgumentException("count must be 1 to " + MAX_EXPORT + " (at most " + MAX_EXPORT
              + " per call)");
    }
    return guarded(() -> {
      var preset = open(file).preset();
      Path target = (folder != null ? folder : BatchExporter.defaultFolder(preset)).toAbsolutePath();
      try {
        int written = BatchExporter.export(preset, (int) count, target, SchematicWriter.DEFAULT_DATA_VERSION, done -> {
        }, () -> false);
        return "wrote " + written + " trees to " + target;
      } catch (IOException e) {
        throw new IllegalArgumentException(target + ": cannot export: " + e.getMessage());
      }
    });
  }

  /**
   * The tree's front, side, three-quarter and top views in one PNG. Rendering runs in a child Java process, so OpenGL
   * never runs on the server's thread and a graphics failure cannot take the server down.
   *
   * @param file       Preset file.
   * @param seed       Tree seed.
   * @param highDetail Whether to draw at twice the width and height, for about four times the pixels.
   * @param wireframe  Whether to draw only the branch skeleton's lines instead of blocks.
   */
  static synchronized byte[] render(Path file, long seed, boolean highDetail, boolean wireframe) {
    return screenshot(file, seed, highDetail, wireframe, "--views");
  }

  /**
   * One part of the tree up close, as a PNG.
   *
   * @param file       Tree design file.
   * @param seed       Tree seed.
   * @param view       One of {@link ViewerOptions#VIEWS}.
   * @param region     {bottom, top, left, right}, each a fraction of the tree from 0 to 1 as seen from that view.
   * @param highDetail Whether to draw at twice the width and height, for about four times the pixels.
   * @param wireframe  Whether to draw only the branch skeleton's lines instead of blocks.
   */
  static synchronized byte[] renderCloseup(Path file, long seed, String view, double[] region, boolean highDetail,
                                          boolean wireframe) {
    if (!ViewerOptions.VIEWS.contains(view)) {
      throw new IllegalArgumentException("view must be one of " + ViewerOptions.VIEWS);
    }
    for (double part : region) {
      if (!(part >= 0 && part <= 1)) {
        throw new IllegalArgumentException("bottom, top, left and right must each be from 0 to 1");
      }
    }
    if (region[0] >= region[1] || region[2] >= region[3]) {
      throw new IllegalArgumentException("bottom must be below top, and left before right");
    }
    return screenshot(file, seed, highDetail, wireframe, "--closeup", region[0] + "," + region[1] + "," + region[2] + ","
            + region[3], "--view", view);
  }

  /**
   * Trees from several seeds, side by side from the front at one scale, in one PNG.
   *
   * @param file       Preset file.
   * @param seed       First seed; the others follow it.
   * @param count      Number of trees, 2 to 9.
   * @param highDetail Whether to draw at twice the width and height, for about four times the pixels.
   * @param wireframe  Whether to draw only the branch skeleton's lines instead of blocks.
   */
  static synchronized byte[] renderVariations(Path file, long seed, long count, boolean highDetail,
                                             boolean wireframe) {
    if (count < 2 || count > ViewerOptions.MAX_VARIATIONS) {
      throw new IllegalArgumentException("count must be 2 to " + ViewerOptions.MAX_VARIATIONS);
    }
    return screenshot(file, seed, highDetail, wireframe, "--variations", Long.toString(count));
  }

  /**
   * Runs the screenshot command in a child Java process and returns the PNG it writes.
   *
   * @param file       Preset file.
   * @param seed       Tree seed.
   * @param highDetail Whether to add --high-detail.
   * @param wireframe  Whether to add --wireframe.
   * @param extra      Screenshot options.
   */
  private static byte[] screenshot(Path file, long seed, boolean highDetail, boolean wireframe, String... extra) {
    String preset = guarded(() -> open(file).file().toAbsolutePath().toString());
    Path png = null;
    Path log = null;
    try {
      png = Files.createTempFile("arbor-views", ".png");
      log = Files.createTempFile("arbor-views", ".log");
      String java = ProcessHandle.current().info().command().orElse("java");
      // The jar's manifest grants native access, but -cp skips the manifest; UTF-8 errors read back intact.
      List<String> command = new ArrayList<>(List.of(java, "--enable-native-access=ALL-UNNAMED",
              "-Dstderr.encoding=UTF-8", "-cp", System.getProperty("java.class.path"), "com.jeffch.arbor.Main", "view",
              preset, "--seed", Long.toString(seed), "--screenshot", png.toString()));
      command.addAll(List.of(extra));
      if (highDetail) {
        command.add("--high-detail");
      }
      if (wireframe) {
        command.add("--wireframe");
      }
      Process child = new ProcessBuilder(command)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(log.toFile()).start();
      boolean finished = child.waitFor(RENDER_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        // Windows keeps the log locked until the child has really gone.
        child.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
      }
      String problem = Files.readString(log).lines().filter(line -> !line.startsWith("SLF4J")
              && !line.startsWith("WARNING")).collect(Collectors.joining(" ")).strip();
      if (!finished) {
        throw new IllegalArgumentException("render took longer than " + RENDER_SECONDS + " seconds");
      }
      if (child.exitValue() != 0) {
        throw new IllegalArgumentException(problem.isEmpty() ? "render failed" : problem);
      }
      return Files.readAllBytes(png);
    } catch (IOException e) {
      throw new IllegalArgumentException("cannot render: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException("render interrupted");
    } finally {
      for (Path temp : new Path[]{png, log}) {
        if (temp != null) {
          try {
            Files.deleteIfExists(temp);
          } catch (IOException ignored) {
            // A leftover temp file is harmless.
          }
        }
      }
    }
  }

  /**
   * One saved edit: the file before and after it, and what it changed.
   *
   * @param before File text before the edit.
   * @param after  File text the edit saved.
   * @param what   What the edit changed, in words.
   */
  private record Edit(String before, String after, String what) {
  }

  /**
   * Saves an edited design and remembers the edit, so {@link #undo} can take it back.
   *
   * @param document Edited design.
   * @param what     What the edit changed, in words.
   */
  private static void saved(PresetDocument document, String what) {
    Path file = document.file().toAbsolutePath().normalize();
    String before = read(file);
    if (!document.save()) {
      throw new IllegalArgumentException(file + ": cannot save");
    }
    Deque<Edit> edits = HISTORY.computeIfAbsent(file, key -> new ArrayDeque<>());
    edits.addLast(new Edit(before, read(file), what));
    if (edits.size() > HISTORY_LIMIT) {
      edits.removeFirst();
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new IllegalArgumentException(file + ": " + e.getMessage());
    }
  }

  /**
   * Takes back the newest edits made through set_setting and edit_design, restoring the file as it was before them.
   * Refuses when the file changed some other way since, such as in the editor, so that change is never lost.
   *
   * @param file  Tree design file.
   * @param steps How many edits to take back.
   */
  static synchronized String undo(Path file, long steps) {
    Path key = file.toAbsolutePath().normalize();
    Deque<Edit> edits = HISTORY.getOrDefault(key, new ArrayDeque<>());
    if (edits.isEmpty()) {
      return "nothing to undo: only edits made with set_setting or edit_design since the server started can be undone";
    }
    if (steps < 1 || steps > edits.size()) {
      throw new IllegalArgumentException("steps must be 1 to " + edits.size() + ", the edits that can be undone");
    }
    if (!read(key).equals(edits.peekLast().after())) {
      throw new IllegalArgumentException("the file changed some other way since the last edit, for example in the"
              + " editor; undo would lose that change, so nothing was undone");
    }
    List<String> undone = new ArrayList<>();
    Edit edit = null;
    for (int i = 0; i < steps; i++) {
      edit = edits.removeLast();
      undone.add(edit.what());
    }
    try {
      Files.writeString(key, edit.before());
    } catch (IOException e) {
      throw new IllegalArgumentException(key + ": cannot write: " + e.getMessage());
    }
    return "undid " + steps + " edit" + (steps == 1 ? "" : "s") + ", newest first:\n" + String.join("\n", undone)
            + "\n" + edits.size() + " more can be undone.";
  }

  /**
   * Images of one setting at several values, side by side from the front on one seed, with the numbers for each.
   *
   * @param png     The image.
   * @param summary The generate summary of each value.
   */
  record Comparison(byte[] png, String summary) {
  }

  /**
   * Renders the design with one setting at each of several values, without changing the file.
   *
   * @param file       Tree design file.
   * @param path       Setting path.
   * @param values     The values, as a JSON array of 2 to 9.
   * @param seed       Tree seed for every value.
   * @param highDetail Whether to draw at twice the width and height.
   * @param wireframe  Whether to draw only the branch skeleton.
   */
  static synchronized Comparison compare(Path file, String path, String values, long seed, boolean highDetail,
                                         boolean wireframe) {
    JsonNode list;
    try {
      list = JSON.readTree(values);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("values: invalid JSON");
    }
    if (!list.isArray() || list.size() < 2 || list.size() > ViewerOptions.MAX_VARIATIONS) {
      throw new IllegalArgumentException("values must be a list of 2 to " + ViewerOptions.MAX_VARIATIONS + " values");
    }
    Path folder = null;
    try {
      folder = Files.createTempDirectory("arbor-compare");
      List<String> designs = new ArrayList<>();
      StringBuilder summary = new StringBuilder("Seed " + seed + ", front view, one scale, left to right then down,"
              + " each tile labelled with its value.");
      for (int i = 0; i < list.size(); i++) {
        String value = JSON.writeValueAsString(list.get(i));
        PresetDocument document = open(file);
        document.setJsonText(path, value);
        if (document.error() != null) {
          throw new IllegalArgumentException("value " + (i + 1) + " (" + value + "): " + document.error());
        }
        String label = path + " = " + value;
        // Tiles are narrow, so each is labelled with its value alone; the summary names the setting.
        document.setJsonText("name", JSON.writeValueAsString(value.length() > 24
                ? value.substring(0, 23) + "…" : value));
        Path copy = folder.resolve(i + ".json");
        if (!document.saveAs(copy)) {
          throw new IllegalArgumentException("cannot write a copy for value " + (i + 1));
        }
        designs.add(copy.toString());
        SpeciesPreset preset = document.preset();
        summary.append("\n").append(label).append(": ").append(describe(TreeGenerator.generate(preset, seed), preset));
      }
      byte[] png = screenshot(Path.of(designs.get(0)), seed, highDetail, wireframe, "--compare",
              String.join(java.io.File.pathSeparator, designs.subList(1, designs.size())));
      return new Comparison(png, summary.toString());
    } catch (IOException e) {
      throw new IllegalArgumentException("cannot compare: " + e.getMessage());
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(e.getMessage() != null ? e.getMessage() : e.toString());
    } finally {
      if (folder != null) {
        try (var files = Files.list(folder)) {
          for (Path copy : files.toList()) {
            Files.deleteIfExists(copy);
          }
          Files.deleteIfExists(folder);
        } catch (IOException ignored) {
          // A leftover temp file is harmless.
        }
      }
    }
  }

  /**
   * Block names in the Minecraft version the editor uses, read once from its block states, with the version's name.
   * Null when no Minecraft assets are found.
   */
  private static Map.Entry<String, Set<String>> knownBlocks() {
    if (knownBlocks == null) {
      knownBlocks = ViewerApp.findAssets(null).map(folder -> {
        Path states = folder.resolve("assets").resolve("minecraft").resolve("blockstates");
        try (var files = Files.list(states)) {
          Set<String> names = files.map(state -> state.getFileName().toString())
                  .filter(name -> name.endsWith(".json")).map(name -> name.substring(0, name.length() - 5))
                  .collect(Collectors.toCollection(TreeSet::new));
          return Map.entry(folder.getFileName().toString(), names);
        } catch (IOException e) {
          return null;
        }
      }).orElse(null);
    }
    return knownBlocks;
  }

  /**
   * Block names containing some text, from the Minecraft version the editor uses.
   *
   * @param filter Text the names must contain, such as log, leaves, _wall or sapling; empty for every block.
   */
  static synchronized String listBlocks(String filter) {
    Map.Entry<String, Set<String>> known = knownBlocks();
    if (known == null) {
      throw new IllegalArgumentException("no Minecraft installation was found, so block names cannot be listed; open"
              + " the editor and use View > Locate Minecraft Jar");
    }
    String text = filter.toLowerCase(Locale.ROOT).strip();
    List<String> names = known.getValue().stream().filter(name -> name.contains(text)).toList();
    if (names.isEmpty()) {
      return "no block in Minecraft " + known.getKey() + " contains \"" + filter + "\"";
    }
    return names.size() + " blocks in Minecraft " + known.getKey()
            + (text.isEmpty() ? "" : " containing \"" + filter + "\"") + ":\n" + String.join(", ", names);
  }

  /**
   * The real colors of the blocks whose names contain some text, from their textures in the Minecraft version the
   * editor uses, so blocks can be matched to a photo by color rather than by name.
   *
   * @param filter Text the names must contain, such as planks, log or leaves.
   */
  static synchronized String blockColors(String filter) {
    String text = filter.toLowerCase(Locale.ROOT).strip();
    if (text.isEmpty()) {
      throw new IllegalArgumentException("give text the block names must contain, such as planks, log or leaves");
    }
    Map.Entry<String, Set<String>> known = knownBlocks();
    if (known == null) {
      throw new IllegalArgumentException("no Minecraft installation was found, so block colors cannot be read; open"
              + " the editor and use View > Locate Minecraft Jar");
    }
    if (blockColors == null) {
      blockColors = new BlockColors(ViewerApp.findAssets(null).orElseThrow());
    }
    List<String> names = known.getValue().stream().filter(name -> name.contains(text)).toList();
    if (names.size() > MAX_COLORS) {
      throw new IllegalArgumentException(names.size() + " blocks contain \"" + filter + "\"; narrow it to at most "
              + MAX_COLORS + ", such as oak_ or _log");
    }
    List<String> lines = new ArrayList<>();
    for (String name : names) {
      String colors = blockColors.describe(name);
      if (colors != null) {
        lines.add(name + ": " + colors);
      }
    }
    if (lines.isEmpty()) {
      return "no block with a texture in Minecraft " + known.getKey() + " contains \"" + filter + "\"";
    }
    return "Average texture colors in Minecraft " + known.getKey() + ", foliage and grass tinted as in plains:\n"
            + String.join("\n", lines);
  }

  /**
   * A warning naming the design's blocks that the Minecraft version the editor uses does not have, or nothing when
   * they all exist or no Minecraft is found. Blocks in other mods' namespaces are not checked.
   *
   * @param document Design to check.
   */
  private static String blockWarning(PresetDocument document) {
    Map.Entry<String, Set<String>> known = knownBlocks();
    if (known == null) {
      return "";
    }
    Set<String> unknown = new TreeSet<>();
    collectBlocks(JSON.readTree(read(document.file())), false, unknown);
    unknown.removeIf(known.getValue()::contains);
    return unknown.isEmpty() ? "" : "\nWarning: Minecraft " + known.getKey() + " has no block named "
            + String.join(", ", unknown) + ". Look up the right names with list_blocks.";
  }

  /**
   * Adds every block name written in a design's blocks sections: the main blocks, survival blocks, plant layers and
   * decorators. Names lose any {@code minecraft:} prefix and block state; other namespaces are skipped.
   *
   * @param node     Part of the design.
   * @param inBlocks Whether this part is inside a blocks section.
   * @param names    Collected names.
   */
  private static void collectBlocks(JsonNode node, boolean inBlocks, Set<String> names) {
    if (node.isString() && inBlocks) {
      addBlock(node.asString(), names);
    } else if (node.isArray()) {
      node.forEach(item -> collectBlocks(item, inBlocks, names));
    } else if (node.isObject()) {
      node.properties().forEach(entry -> {
        String key = entry.getKey();
        if (BLOCK_RULE_KEYS.contains(key)) {
          return;
        }
        if (inBlocks && entry.getValue().isNumber()) {
          addBlock(key, names);
        } else {
          collectBlocks(entry.getValue(), inBlocks || key.equals("blocks"), names);
        }
      });
    }
  }

  private static void addBlock(String block, Set<String> names) {
    String name = block.contains("[") ? block.substring(0, block.indexOf('[')) : block;
    if (name.startsWith("minecraft:")) {
      name = name.substring("minecraft:".length());
    }
    if (!name.contains(":") && !name.equals("off")) {
      names.add(name);
    }
  }

  private static PresetDocument open(Path file) {
    return PresetDocument.open(file.toAbsolutePath());
  }

  /**
   * Runs a tool, turning any failure, out of memory included, into one readable line.
   *
   * @param tool The tool's work.
   */
  private static String guarded(Supplier<String> tool) {
    try {
      return tool.get();
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(e.getMessage() != null ? e.getMessage() : e.toString());
    } catch (OutOfMemoryError e) {
      throw new IllegalArgumentException("not enough memory; reduce counts, lengths or sizes");
    }
  }
}
