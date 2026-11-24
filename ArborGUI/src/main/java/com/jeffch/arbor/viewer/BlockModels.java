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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeffch.arbor.viewer.ModelQuad.Direction3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Reads vanilla block state and model JSON files into block-space quads.
 */
public final class BlockModels {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final double[][] NORMALS = {
          {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
  private static final String[] FACE_NAMES = {"down", "up", "north", "south", "west", "east"};

  private final Path stateDir;
  private final Path modelDir;
  private final Map<String, List<ModelQuad>> cache = new ConcurrentHashMap<>();
  private volatile List<String> names;
  private volatile Set<String> crossed;

  /**
   * @param assetsRoot Folder holding {@code assets/minecraft}, from the game jar or a resource pack.
   */
  public BlockModels(Path assetsRoot) {
    Path mc = assetsRoot.resolve("assets").resolve("minecraft");
    this.stateDir = mc.resolve("blockstates");
    this.modelDir = mc.resolve("models");
  }

  /**
   * Collects every property a multipart condition mentions, looking inside OR and AND groups.
   *
   * @param when  A multipart condition.
   * @param names Receives the property names.
   */
  private static void whenProperties(JsonNode when, Set<String> names) {
    Iterator<Map.Entry<String, JsonNode>> it = when.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> e = it.next();
      if (e.getKey().equals("OR") || e.getKey().equals("AND")) {
        for (JsonNode c : e.getValue()) whenProperties(c, names);
      } else {
        names.add(e.getKey());
      }
    }
  }

  /**
   * The first of several weighted model choices, or the only one.
   *
   * @param n A model choice or an array of weighted choices.
   */
  private static JsonNode first(JsonNode n) {
    return n != null && n.isArray() ? n.get(0) : n;
  }

  /**
   * A variant key matches when every property it names equals the state's value, if the state sets it.
   *
   * @param key   Variant key, such as {@code facing=east,open=true}.
   * @param props The block state's properties.
   */
  private static boolean variantMatches(String key, Map<String, String> props) {
    if (key.isEmpty()) return true;
    for (String kv : key.split(",")) {
      int eq = kv.indexOf('=');
      if (eq <= 0) continue;
      String have = props.get(kv.substring(0, eq));
      if (have != null && !have.equals(kv.substring(eq + 1))) return false;
    }
    return true;
  }

  /**
   * A multipart condition needs every named property set to one of its alternatives.
   *
   * @param when  A multipart condition.
   * @param props The block state's properties.
   */
  private static boolean whenMatches(JsonNode when, Map<String, String> props) {
    boolean ok = true;
    Iterator<Map.Entry<String, JsonNode>> it = when.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> e = it.next();
      String k = e.getKey();
      if (k.equals("OR")) {
        boolean any = false;
        for (JsonNode c : e.getValue()) any |= whenMatches(c, props);
        ok &= any;
      } else if (k.equals("AND")) {
        for (JsonNode c : e.getValue()) ok &= whenMatches(c, props);
      } else {
        String have = props.get(k);
        ok &= have != null && List.of(e.getValue().asText().split("\\|")).contains(have);
      }
    }
    return ok;
  }

  // ---- block state ----

  /**
   * The model a block uses with no particular state: its first variant, or its first multipart piece.
   *
   * @param state A block state file.
   */
  private static String firstModel(JsonNode state) {
    JsonNode variants = state.get("variants");
    JsonNode chosen = null;
    if (variants != null && variants.fields().hasNext()) {
      chosen = first(variants.fields().next().getValue());
    } else if (state.has("multipart") && state.get("multipart").size() > 0) {
      chosen = first(state.get("multipart").get(0).get("apply"));
    }
    return chosen == null || chosen.get("model") == null ? null : chosen.get("model").asText();
  }

  /**
   * Follows {@code #name} references through the model's texture table to a real texture path, giving up after 16
   * steps.
   *
   * @param ref      Texture reference, either a path or {@code #name}.
   * @param textures The model's texture table.
   */
  private static String texture(String ref, Map<String, String> textures) {
    String r = ref;
    for (int i = 0; i < 16 && r.startsWith("#"); i++) {
      String next = textures.get(r.substring(1));
      if (next == null) return r;
      r = next;
    }
    return strip(r);
  }

  /**
   * Face vertices in vanilla order, in 0..16 units.
   *
   * @param d Face index, in the order down, up, north, south, west, east.
   * @param f The element's lower corner.
   * @param t The element's upper corner.
   */
  private static double[][] cornerPositions(int d, double[] f, double[] t) {
    double x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
    return switch (d) {
      case 0 -> new double[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
      case 1 -> new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
      case 2 -> new double[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
      case 3 -> new double[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
      case 4 -> new double[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
      default -> new double[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
    };
  }

  /**
   * The texture area a face uses when the model does not give one: the part of the 16 by 16 texture the face covers, as
   * vanilla does.
   *
   * @param d Face index, in the order down, up, north, south, west, east.
   * @param f The element's lower corner.
   * @param t The element's upper corner.
   */
  private static double[] defaultUv(int d, double[] f, double[] t) {
    double x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
    return switch (d) {
      case 0 -> new double[]{x0, 16 - z1, x1, 16 - z0};
      case 1 -> new double[]{x0, z0, x1, z1};
      case 2 -> new double[]{16 - x1, 16 - y1, 16 - x0, 16 - y0};
      case 3 -> new double[]{x0, 16 - y1, x1, 16 - y0};
      case 4 -> new double[]{z0, 16 - y1, z1, 16 - y0};
      default -> new double[]{16 - z1, 16 - y1, 16 - z0, 16 - y0};
    };
  }

  /**
   * Inverse of the default per-face UV rule: block-space point on the face plane.
   *
   * @param d     Face index, in the order down, up, north, south, west, east.
   * @param u     Texture u, in 0..16 units.
   * @param v     Texture v, in 0..16 units.
   * @param plane Position of the face plane along its normal.
   */
  private static double[] facePoint(int d, double u, double v, double plane) {
    return switch (d) {
      case 0 -> new double[]{u, plane, 1 - v};
      case 1 -> new double[]{u, plane, v};
      case 2 -> new double[]{1 - u, 1 - v, plane};
      case 3 -> new double[]{u, 1 - v, plane};
      case 4 -> new double[]{plane, 1 - v, u};
      default -> new double[]{plane, 1 - v, 1 - u};
    };
  }

  /**
   * The default per-face UV rule applied to a final block-space position.
   *
   * @param d Face index, in the order down, up, north, south, west, east.
   * @param p Position in block space.
   */
  private static double[] worldUv(int d, double[] p) {
    double x = p[0], y = p[1], z = p[2];
    return switch (d) {
      case 0 -> new double[]{x, 1 - z};
      case 1 -> new double[]{x, z};
      case 2 -> new double[]{1 - x, 1 - y};
      case 3 -> new double[]{x, 1 - y};
      case 4 -> new double[]{z, 1 - y};
      default -> new double[]{1 - z, 1 - y};
    };
  }

  /**
   * Right-handed rotation about an axis, then scaling of the two other coordinates.
   *
   * @param v     Point to rotate; changed in place.
   * @param axis  {@code x}, {@code y} or {@code z}.
   * @param c     Cosine of the angle.
   * @param s     Sine of the angle.
   * @param scale Factor for the two other coordinates.
   */
  private static void rotateAxis(double[] v, String axis, double c, double s, double scale) {
    double x = v[0], y = v[1], z = v[2];
    switch (axis) {
      case "x" -> {
        v[1] = (y * c - z * s) * scale;
        v[2] = (y * s + z * c) * scale;
      }
      case "z" -> {
        v[0] = (x * c - y * s) * scale;
        v[1] = (x * s + y * c) * scale;
      }
      default -> {
        v[0] = (x * c + z * s) * scale;
        v[2] = (-x * s + z * c) * scale;
      }
    }
  }

  // ---- models ----

  /**
   * Variant rotation: x first, then y, about (cx,cx,cx). Positive y turns north toward east.
   *
   * @param v    Point to rotate; changed in place.
   * @param xDeg Rotation about x, a multiple of 90 degrees.
   * @param yDeg Rotation about y, a multiple of 90 degrees.
   * @param cx   Center coordinate on every axis.
   */
  private static void variantRotate(double[] v, int xDeg, int yDeg, double cx) {
    for (int i = 0; i < 3; i++) v[i] -= cx;
    if (xDeg != 0) {
      double c = cos90(xDeg), s = sin90(xDeg), y = v[1], z = v[2];
      v[1] = y * c + z * s;
      v[2] = -y * s + z * c;
    }
    if (yDeg != 0) {
      double c = cos90(yDeg), s = sin90(yDeg), x = v[0], z = v[2];
      v[0] = x * c - z * s;
      v[2] = x * s + z * c;
    }
    for (int i = 0; i < 3; i++) v[i] += cx;
  }

  /**
   * The exact cosine of a multiple of 90 degrees, avoiding rounding errors.
   *
   * @param deg Angle, a multiple of 90 degrees.
   */
  private static double cos90(int deg) {
    return switch (((deg % 360) + 360) % 360) {
      case 90, 270 -> 0;
      case 180 -> -1;
      default -> 1;
    };
  }

  /**
   * The exact sine of a multiple of 90 degrees, avoiding rounding errors.
   *
   * @param deg Angle, a multiple of 90 degrees.
   */
  private static double sin90(int deg) {
    return switch (((deg % 360) + 360) % 360) {
      case 90 -> 1;
      case 270 -> -1;
      default -> 0;
    };
  }

  /**
   * The block side a direction points toward most.
   *
   * @param n Direction vector.
   */
  private static Direction3 snap(double[] n) {
    int best = 0;
    for (int i = 1; i < 3; i++) if (Math.abs(n[i]) > Math.abs(n[best])) best = i;
    boolean pos = n[best] > 0;
    return switch (best) {
      case 0 -> pos ? Direction3.EAST : Direction3.WEST;
      case 1 -> pos ? Direction3.UP : Direction3.DOWN;
      default -> pos ? Direction3.SOUTH : Direction3.NORTH;
    };
  }

  /**
   * Scales a vector to length 1.
   *
   * @param n Vector to scale; changed in place.
   */
  private static void normalize(double[] n) {
    double l = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
    if (l > 0) for (int i = 0; i < 3; i++) n[i] /= l;
  }

  /**
   * An [x, y, z] array as numbers.
   *
   * @param a JSON array of three numbers.
   */
  private static double[] vec(JsonNode a) {
    return new double[]{a.get(0).asDouble(), a.get(1).asDouble(), a.get(2).asDouble()};
  }

  /**
   * A four-number array, as used for texture areas.
   *
   * @param a JSON array of four numbers.
   */
  private static double[] vec4(JsonNode a) {
    return new double[]{a.get(0).asDouble(), a.get(1).asDouble(), a.get(2).asDouble(), a.get(3).asDouble()};
  }

  /**
   * A reference without its {@code minecraft:} prefix.
   *
   * @param ref Reference to strip.
   */
  private static String strip(String ref) {
    return ref.startsWith("minecraft:") ? ref.substring(10) : ref;
  }

  /**
   * Whether a reference is a plain lowercase path, so it cannot reach files outside the assets.
   *
   * @param ref Reference to check.
   */
  private static boolean safe(String ref) {
    return !ref.isEmpty() && ref.matches("[a-z0-9_./-]+") && !ref.contains("..");
  }

  /**
   * Sorted block names (no namespace) that have a block state file.
   */
  public List<String> blockNames() {
    List<String> n = names;
    if (n == null) {
      List<String> out = new ArrayList<>();
      if (Files.isDirectory(stateDir)) {
        try (Stream<Path> s = Files.list(stateDir)) {
          s.map(p -> p.getFileName().toString())
                  .filter(f -> f.endsWith(".json"))
                  .forEach(f -> out.add(f.substring(0, f.length() - 5)));
        } catch (IOException e) {
          out.clear();
        }
      }
      out.sort(null);
      names = n = List.copyOf(out);
    }
    return n;
  }

  /**
   * Whether the assets have a block of this name.
   *
   * @param blockName Block name without properties.
   */
  public boolean exists(String blockName) {
    return blockNames().contains(strip(blockName));
  }

  /**
   * Quads for "minecraft:oak_fence[east=true]" or "oak_fence"; empty for unknown or broken blocks.
   *
   * @param blockState Block state text.
   */
  public List<ModelQuad> quads(String blockState) {
    return cache.computeIfAbsent(blockState, k -> {
      try {
        return List.copyOf(build(k));
      } catch (Exception | StackOverflowError e) {
        return List.of();
      }
    });
  }

  /**
   * Blocks whose first model inherits from block/cross or block/tinted_cross.
   */
  public Set<String> crossedPlants() {
    Set<String> c = crossed;
    if (c == null) {
      Set<String> out = new TreeSet<>();
      for (String name : blockNames()) {
        try {
          String model = firstModel(JSON.readTree(stateDir.resolve(name + ".json").toFile()));
          if (model != null && inheritsCross(model)) out.add(name);
        } catch (Exception e) {
          // broken block: not a plant
        }
      }
      crossed = c = Collections.unmodifiableSet(out);
    }
    return c;
  }

  /**
   * Reads a block state such as {@code oak_fence[east=true]}: the first variant matching its properties, plus every
   * multipart piece whose condition holds.
   *
   * @param blockState Block state text.
   */
  private List<ModelQuad> build(String blockState) throws IOException {
    String s = blockState.trim();
    Map<String, String> props = new HashMap<>();
    int br = s.indexOf('[');
    String name = strip(br < 0 ? s : s.substring(0, br));
    if (br >= 0) {
      String body = s.substring(br + 1, s.endsWith("]") ? s.length() - 1 : s.length());
      for (String kv : body.split(",")) {
        int eq = kv.indexOf('=');
        if (eq > 0) props.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
      }
    }
    if (!safe(name)) return List.of();
    Path file = stateDir.resolve(name + ".json");
    if (!Files.isRegularFile(file)) return List.of();
    JsonNode state = JSON.readTree(file.toFile());

    List<ModelQuad> out = new ArrayList<>();
    JsonNode variants = state.get("variants");
    if (variants != null) {
      Iterator<Map.Entry<String, JsonNode>> it = variants.fields();
      while (it.hasNext()) {
        Map.Entry<String, JsonNode> e = it.next();
        if (variantMatches(e.getKey(), props)) {
          bake(first(e.getValue()), out);
          break;
        }
      }
    }
    JsonNode multipart = state.get("multipart");
    if (multipart != null) {
      boolean matched = false;
      boolean setsNamedProperty = false;
      Set<String> named = new HashSet<>();
      for (JsonNode part : multipart) {
        JsonNode when = part.get("when");
        if (when == null || whenMatches(when, props)) {
          bake(first(part.get("apply")), out);
          matched = true;
        }
        if (when != null) whenProperties(when, named);
      }
      for (String property : named) setsNamedProperty |= props.containsKey(property);
      // A block with no state has nothing to match, so it shows the first part instead of nothing.
      if (!matched && !named.isEmpty() && !setsNamedProperty && multipart.size() > 0) {
        bake(first(multipart.get(0).get("apply")), out);
      }
    }
    return out;
  }

  /**
   * Whether a model, or any model it inherits from, is the crossed-plane plant model.
   *
   * @param model Model reference.
   */
  private boolean inheritsCross(String model) throws IOException {
    Set<String> seen = new HashSet<>();
    String cur = strip(model);
    while (cur != null && seen.add(cur)) {
      if (cur.equals("block/cross") || cur.equals("block/tinted_cross")) return true;
      JsonNode m = readModel(cur);
      cur = m.has("parent") ? strip(m.get("parent").asText()) : null;
    }
    return false;
  }

  /**
   * Reads one model file, refusing unsafe references.
   *
   * @param ref Model reference.
   */
  private JsonNode readModel(String ref) throws IOException {
    if (!safe(ref)) throw new IOException("bad model reference " + ref);
    return JSON.readTree(modelDir.resolve(ref + ".json").toFile());
  }

  /**
   * Turns one model choice into faces: follows the model's parents to gather textures (the child's win) and the first
   * element list found, then bakes each element with the variant's rotation.
   *
   * @param variant A model choice with its rotation and uvlock.
   * @param out     Receives the faces.
   */
  private void bake(JsonNode variant, List<ModelQuad> out) throws IOException {
    if (variant == null || variant.get("model") == null) return;
    int vx = variant.path("x").asInt(0);
    int vy = variant.path("y").asInt(0);
    boolean uvlock = variant.path("uvlock").asBoolean(false);

    Map<String, String> textures = new LinkedHashMap<>();
    JsonNode elements = null;
    Set<String> seen = new HashSet<>();
    String cur = strip(variant.get("model").asText());
    while (cur != null) {
      if (!seen.add(cur)) throw new IOException("parent cycle at " + cur);
      JsonNode m = readModel(cur);
      JsonNode t = m.get("textures");
      if (t != null) {
        t.fields().forEachRemaining(e -> textures.putIfAbsent(e.getKey(), e.getValue().asText()));
      }
      if (elements == null && m.has("elements")) elements = m.get("elements");
      cur = m.has("parent") ? strip(m.get("parent").asText()) : null;
    }
    if (elements == null) return;
    for (JsonNode el : elements) bakeElement(el, textures, vx, vy, uvlock, out);
  }

  /**
   * Turns one model box into its faces in block space: applies the element's own rotation, then the variant's, works
   * out the texture coordinates (kept fixed to the world when uvlock asks), the side that hides the face, and the
   * texture rotation.
   *
   * @param el       The element box.
   * @param textures The model's resolved texture table.
   * @param vx       Variant rotation about x, in degrees.
   * @param vy       Variant rotation about y, in degrees.
   * @param uvlock   Whether texture coordinates stay fixed to the world as the variant rotates.
   * @param out      Receives the faces.
   */
  private void bakeElement(JsonNode el, Map<String, String> textures, int vx, int vy,
                           boolean uvlock, List<ModelQuad> out) {
    double[] from = vec(el.get("from"));
    double[] to = vec(el.get("to"));
    JsonNode rot = el.get("rotation");
    boolean shade = el.path("shade").asBoolean(true);
    JsonNode faces = el.get("faces");
    if (faces == null) return;

    for (int d = 0; d < 6; d++) {
      JsonNode face = faces.get(FACE_NAMES[d]);
      if (face == null || !face.has("texture")) continue;
      double[][] p = cornerPositions(d, from, to);
      double[] n = NORMALS[d].clone();
      for (double[] v : p) for (int i = 0; i < 3; i++) v[i] /= 16;
      if (rot != null) {
        double[] origin = vec(rot.get("origin"));
        String axis = rot.path("axis").asText("y");
        double ang = Math.toRadians(rot.path("angle").asDouble(0));
        double c = Math.cos(ang), s = Math.sin(ang);
        double scale = rot.path("rescale").asBoolean(false) ? 1 / c : 1;
        for (double[] v : p) {
          for (int i = 0; i < 3; i++) v[i] -= origin[i] / 16;
          rotateAxis(v, axis, c, s, scale);
          for (int i = 0; i < 3; i++) v[i] += origin[i] / 16;
        }
        rotateAxis(n, axis, c, s, 1);
      }
      for (double[] v : p) variantRotate(v, vx, vy, 0.5);
      variantRotate(n, vx, vy, 0);
      normalize(n);

      Direction3 cull = null;
      if (face.has("cullface")) {
        try {
          int cd = Direction3.valueOf(face.get("cullface").asText().toUpperCase()).ordinal();
          double[] c = NORMALS[cd].clone();
          variantRotate(c, vx, vy, 0);
          cull = snap(c);
        } catch (IllegalArgumentException e) {
          cull = null;
        }
      }

      // UV corners: 0:(u0,v0) 1:(u0,v1) 2:(u1,v1) 3:(u1,v0), or the world mapping under uvlock
      double[][] corner = new double[4][];
      if (uvlock && (vx != 0 || vy != 0) && !face.has("uv")) {
        int fd = snap(n).ordinal();
        for (int i = 0; i < 4; i++) corner[i] = worldUv(fd, p[i]);
      } else {
        double[] uv = face.has("uv") ? vec4(face.get("uv")) : defaultUv(d, from, to);
        corner[0] = new double[]{uv[0] / 16, uv[1] / 16};
        corner[1] = new double[]{uv[0] / 16, uv[3] / 16};
        corner[2] = new double[]{uv[2] / 16, uv[3] / 16};
        corner[3] = new double[]{uv[2] / 16, uv[1] / 16};
        if (uvlock && (vx != 0 || vy != 0)) {
          int fd = snap(n).ordinal();
          double plane = new double[]{from[1], to[1], from[2], to[2], from[0], to[0]}[d] / 16;
          for (int i = 0; i < 4; i++) {
            double[] q = facePoint(d, corner[i][0], corner[i][1], plane);
            variantRotate(q, vx, vy, 0.5);
            corner[i] = worldUv(fd, q);
          }
        }
      }
      int shift = ((face.path("rotation").asInt(0) / 90) % 4 + 4) % 4;
      float[] pos = new float[12];
      float[] uvs = new float[8];
      for (int i = 0; i < 4; i++) {
        double[] c = corner[(i + shift) % 4];
        for (int k = 0; k < 3; k++) pos[i * 3 + k] = (float) p[i][k];
        uvs[i * 2] = (float) c[0];
        uvs[i * 2 + 1] = (float) c[1];
      }
      out.add(new ModelQuad(pos, uvs, new float[]{(float) n[0], (float) n[1], (float) n[2]},
              texture(face.get("texture").asText(), textures),
              face.path("tintindex").asInt(-1), cull, shade));
    }
  }
}
