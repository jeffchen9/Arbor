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

import com.jeffch.arbor.shape.*;

import java.util.*;

/**
 * Builds textured triangle meshes per shape class from real block models. Each vertex holds position (3),
 * normal (3), atlas UV (2) and tint colour (3). A zero normal marks a quad without directional shading. Ambient
 * occlusion on full blocks and leaves is baked into the tint.
 */
public final class TexturedMesher {
  public static final int FLOATS_PER_VERTEX = 11;

  private static final int[] QUAD_TRIANGLES = {0, 1, 2, 0, 2, 3};
  private static final int[] FLIPPED_TRIANGLES = {1, 2, 3, 1, 3, 0};
  private static final float[] NO_OCCLUSION = {1f, 1f, 1f, 1f};
  /**
   * Light lost at a vertex for each of the four blocks around it that occludes.
   */
  private static final float OCCLUSION_STEP = 0.2f;
  private static final float[] WHITE = {1f, 1f, 1f};
  /**
   * The shape classes that darken their neighbors' corners, as in Minecraft: full blocks and leaves.
   */
  static final Set<ShapeClass> OCCLUDERS = Set.copyOf(EnumSet.of(ShapeClass.FULL, ShapeClass.LEAVES));
  private static final float[] ZERO_NORMAL = {0f, 0f, 0f};
  private static final float[][] CUBE_NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
  private static final float[][][] CUBE_FACES = {
          {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}},
          {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
          {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
          {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
          {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
          {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}},
  };
  private static final float[][] CUBE_UVS = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
  private final BlockModels models;
  private final TextureAtlas atlas;
  private final TintColors tints;

  /**
   * @param models Block models from the assets.
   * @param atlas  Packed block textures.
   * @param tints  Colors for grass, foliage and dry plants, which Minecraft tints by biome.
   */
  public TexturedMesher(BlockModels models, TextureAtlas atlas, TintColors tints) {
    this.models = models;
    this.atlas = atlas;
    this.tints = tints;
  }

  /**
   * Hides faces between full blocks only, so hiding one class never reveals holes in another.
   *
   * @param grid The tree's shapes.
   * @param x    Block x.
   * @param y    Block y.
   * @param z    Block z.
   * @param side Side the face looks toward.
   */
  private static boolean hidden(ShapeGrid grid, int x, int y, int z, ModelQuad.Direction3 side) {
    int dx = 0;
    int dy = 0;
    int dz = 0;
    switch (side) {
      case DOWN -> dy = -1;
      case UP -> dy = 1;
      case NORTH -> dz = -1;
      case SOUTH -> dz = 1;
      case WEST -> dx = -1;
      case EAST -> dx = 1;
    }
    return isCube(grid.get(x + dx, y + dy, z + dz));
  }

  /**
   * A log, wood or a decoration such as moss that fills its block.
   *
   * @param shape Shape to check.
   */
  private static boolean isCube(Shape shape) {
    return shape != null && shape.shapeClass() == ShapeClass.FULL;
  }

  /**
   * Only whole cubes cast and receive ambient occlusion, as in Minecraft.
   *
   * @param shape Shape to check.
   */
  private static boolean occludes(Shape shape) {
    return isCube(shape) || shape instanceof Shape.Leaves;
  }

  /**
   * Whether a neighbor darkens a corner: it must occlude and be shown, so hidden leaves leave no shadow behind.
   *
   * @param neighbor  The block beside the corner, or null.
   * @param occluders The shape classes shown that may darken.
   */
  private static boolean darkens(Shape neighbor, Set<ShapeClass> occluders) {
    return neighbor != null && occludes(neighbor) && occluders.contains(neighbor.shapeClass());
  }

  /**
   * Light from 0 to 1 at each quad corner, from the four blocks in front of the face that touch that corner.
   *
   * @param grid The tree's shapes.
   * @param x    Block x.
   * @param y    Block y.
   * @param z    Block z.
   * @param quad The face.
   * @param occluders The shape classes shown that may darken.
   */
  private static float[] cornerLight(ShapeGrid grid, int x, int y, int z, ModelQuad quad, Set<ShapeClass> occluders) {
    float[] normal = quad.normal();
    int axis = 0;
    for (int i = 1; i < 3; i++) {
      if (Math.abs(normal[i]) > Math.abs(normal[axis])) {
        axis = i;
      }
    }
    int[] front = {x, y, z};
    front[axis] += normal[axis] > 0 ? 1 : -1;
    int first = (axis + 1) % 3;
    int second = (axis + 2) % 3;
    float[] light = new float[4];
    for (int corner = 0; corner < 4; corner++) {
      int firstStep = quad.positions()[corner * 3 + first] >= 0.5f ? 1 : -1;
      int secondStep = quad.positions()[corner * 3 + second] >= 0.5f ? 1 : -1;
      int blocked = 0;
      for (int a = 0; a <= 1; a++) {
        for (int b = 0; b <= 1; b++) {
          int[] cell = front.clone();
          cell[first] += a * firstStep;
          cell[second] += b * secondStep;
          Shape neighbor = grid.get(cell[0], cell[1], cell[2]);
          if (darkens(neighbor, occluders)) {
            blocked++;
          }
        }
      }
      light[corner] = 1 - blocked * OCCLUSION_STEP;
    }
    return light;
  }

  /**
   * The block name without namespace or properties.
   *
   * @param state Block state.
   */
  private static String bareName(String state) {
    int bracket = state.indexOf('[');
    String name = bracket < 0 ? state : state.substring(0, bracket);
    return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
  }

  /**
   * Builds textured triangles from each block's real Minecraft model, one list per shape class. Hidden faces between
   * cubes are skipped, corners are darkened by nearby blocks, and blocks with no model are drawn magenta and reported.
   *
   * @param grid   The tree's shapes.
   * @param blocks The tree's chosen blocks.
   */
  public Result mesh(ShapeGrid grid, TreeBlocks blocks) {
    return mesh(grid, blocks, OCCLUDERS);
  }

  /**
   * As {@link #mesh(ShapeGrid, TreeBlocks)}, with corners darkened only by the shape classes that are shown, so
   * hiding leaves also takes away their shadow on the wood.
   *
   * @param grid      The tree's shapes.
   * @param blocks    The tree's chosen blocks.
   * @param occluders The shape classes shown that may darken; others are treated as empty.
   */
  public Result mesh(ShapeGrid grid, TreeBlocks blocks, Set<ShapeClass> occluders) {
    Set<ShapeClass> darkening = Set.copyOf(occluders.stream().filter(OCCLUDERS::contains).toList());
    Map<ShapeClass, Buffer> buffers = new EnumMap<>(ShapeClass.class);
    for (ShapeClass shapeClass : ShapeClass.values()) {
      buffers.put(shapeClass, new Buffer());
    }
    BlockResolver.Cached states = new BlockResolver.Cached(blocks);
    Map<String, List<ModelQuad>> quadCache = new HashMap<>();
    Set<String> unknown = new TreeSet<>();
    grid.forEachSorted((x, y, z, shape) -> {
      String state = states.at(shape, x, y, z);
      if (state == null) {
        return;
      }
      List<ModelQuad> quads = quadCache.computeIfAbsent(state, models::quads);
      Buffer buffer = buffers.get(shape.shapeClass());
      if (quads.isEmpty()) {
        unknown.add(bareName(state));
        addMagentaCube(buffer, x, y, z);
        return;
      }
      String name = bareName(state);
      boolean cube = isCube(shape);
      boolean occluded = occludes(shape);
      for (ModelQuad quad : quads) {
        if (cube && quad.cull() != null && hidden(grid, x, y, z, quad.cull())) {
          continue;
        }
        addQuad(buffer, x, y, z, quad, name, occluded ? cornerLight(grid, x, y, z, quad, darkening) : NO_OCCLUSION);
      }
    });
    Map<ShapeClass, float[]> meshes = new EnumMap<>(ShapeClass.class);
    buffers.forEach((shapeClass, buffer) -> meshes.put(shapeClass, buffer.toArray()));
    return new Result(meshes, Set.copyOf(unknown), darkening);
  }

  /**
   * Adds one model face as two triangles, tinted where the model asks and shaded per corner.
   *
   * @param out       Receives the triangles.
   * @param x         Block x.
   * @param y         Block y.
   * @param z         Block z.
   * @param quad      The face.
   * @param blockName Block name, used for its tint.
   * @param light     Light at each corner.
   */
  private void addQuad(Buffer out, int x, int y, int z, ModelQuad quad, String blockName, float[] light) {
    float[] bounds = atlas.uvBounds(quad.texture());
    float[] tint = tints.color(blockName, quad.tintIndex());
    float[] normal = quad.shade() ? quad.normal() : ZERO_NORMAL;
    // Splitting along the brighter diagonal keeps a dark corner from smearing across the whole face.
    int[] triangles = light[0] + light[2] < light[1] + light[3] ? FLIPPED_TRIANGLES : QUAD_TRIANGLES;
    for (int corner : triangles) {
      float u = bounds[0] + quad.uvs()[corner * 2] * (bounds[2] - bounds[0]);
      float v = bounds[1] + quad.uvs()[corner * 2 + 1] * (bounds[3] - bounds[1]);
      float[] lit = {tint[0] * light[corner], tint[1] * light[corner], tint[2] * light[corner]};
      out.vertex(x + quad.positions()[corner * 3], y + quad.positions()[corner * 3 + 1],
              z + quad.positions()[corner * 3 + 2], normal, u, v, lit);
    }
  }

  /**
   * A full magenta cube, which marks a block the assets have no model for.
   *
   * @param out Receives the triangles.
   * @param x   Block x.
   * @param y   Block y.
   * @param z   Block z.
   */
  private void addMagentaCube(Buffer out, int x, int y, int z) {
    float[] bounds = atlas.uvBounds(null);
    for (int face = 0; face < CUBE_FACES.length; face++) {
      for (int corner : QUAD_TRIANGLES) {
        float[] position = CUBE_FACES[face][corner];
        float[] uv = CUBE_UVS[corner];
        out.vertex(x + position[0], y + position[1], z + position[2], CUBE_NORMALS[face],
                bounds[0] + uv[0] * (bounds[2] - bounds[0]), bounds[1] + uv[1] * (bounds[3] - bounds[1]), WHITE);
      }
    }
  }

  /**
   * The meshes per shape class, and the block names that had no usable model.
   *
   * @param meshes        Vertex data for each shape class.
   * @param unknownBlocks Block names that had no usable model and were drawn magenta.
   * @param occluders     The shape classes whose blocks darkened corners, so the mesh can be rebuilt when that
   *                      changes.
   */
  public record Result(Map<ShapeClass, float[]> meshes, Set<String> unknownBlocks, Set<ShapeClass> occluders) {
  }

  /**
   * A growable list of vertices: position, normal, texture coordinates and color.
   */
  private static final class Buffer {
    private float[] data = new float[4096];
    private int size;

    /**
     * Appends one vertex.
     *
     * @param x      Position x.
     * @param y      Position y.
     * @param z      Position z.
     * @param normal Face normal.
     * @param u      Texture u.
     * @param v      Texture v.
     * @param tint   Color multiplier.
     */
    void vertex(float x, float y, float z, float[] normal, float u, float v, float[] tint) {
      if (size + FLOATS_PER_VERTEX > data.length) {
        data = Arrays.copyOf(data, data.length * 2);
      }
      data[size++] = x;
      data[size++] = y;
      data[size++] = z;
      data[size++] = normal[0];
      data[size++] = normal[1];
      data[size++] = normal[2];
      data[size++] = u;
      data[size++] = v;
      data[size++] = tint[0];
      data[size++] = tint[1];
      data[size++] = tint[2];
    }

    float[] toArray() {
      return Arrays.copyOf(data, size);
    }
  }
}
