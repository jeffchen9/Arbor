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

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The MCP server behind {@code arbor mcp}: Arbor's tools over standard input and output, so an AI assistant can build
 * a tree that matches a picture. The descriptions are what the model reads, so they carry the whole workflow; all of
 * the wording is in {@code mcp.txt} beside this class's resources.
 */
public final class ArborMcp {
  private static final McpJsonMapper MAPPER = McpJsonDefaults.getMapper();
  private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();
  private static final Map<String, String> WORDS = load();
  static final String INSTRUCTIONS = words("instructions");
  private static final String STRING = "\"type\":\"string\"";
  private static final String INTEGER = "\"type\":\"integer\"";
  private static final String BOOLEAN = "\"type\":\"boolean\"";
  private static final String FILE = property("file", STRING);
  private static final String SEED = property("seed", INTEGER);
  private static final String HIGH_DETAIL = property("high_detail", BOOLEAN);
  private static final String WIREFRAME = property("wireframe", BOOLEAN);
  private static final List<String> REQUIRED = List.of("file", "changes");

  private ArborMcp() {
  }

  /**
   * Reads {@code mcp.txt}: each "== key" line starts a text, whose following lines are joined with spaces; lines
   * starting with # are skipped.
   */
  private static Map<String, String> load() {
    try (InputStream in = ArborMcp.class.getResourceAsStream("/mcp.txt")) {
      Map<String, String> words = new HashMap<>();
      String key = null;
      for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
        if (line.startsWith("== ")) {
          key = line.substring(3).strip();
          words.put(key, "");
        } else if (key != null && !line.isBlank() && !line.startsWith("#")) {
          words.merge(key, line.strip(), (a, b) -> a.isEmpty() ? b : a + " " + b);
        }
      }
      return words;
    } catch (IOException | NullPointerException e) {
      throw new IllegalStateException("cannot read mcp.txt", e);
    }
  }

  /**
   * One text from {@code mcp.txt}.
   *
   * @param key Its key.
   */
  static String words(String key) {
    String words = WORDS.get(key);
    if (words == null) {
      throw new IllegalStateException("mcp.txt has no text for " + key);
    }
    return words;
  }

  /**
   * A schema property shared by several tools, described by its bare key in {@code mcp.txt}.
   *
   * @param name   Property name.
   * @param schema The property's schema members other than its description.
   */
  private static String property(String name, String schema) {
    return member(name, schema, words(name));
  }

  /**
   * A schema property of one tool, described by "tool.name" in {@code mcp.txt}.
   *
   * @param tool   Tool name.
   * @param name   Property name.
   * @param schema The property's schema members other than its description.
   */
  private static String property(String tool, String name, String schema) {
    return member(name, schema, words(tool + "." + name));
  }

  private static String member(String name, String schema, String description) {
    return "\"" + name + "\":{" + (schema.isEmpty() ? "" : schema + ",") + "\"description\":"
            + JSON.writeValueAsString(description) + "}";
  }

  /**
   * Serves requests until the input ends.
   *
   * @param in  Requests, one JSON-RPC message per line.
   * @param out Responses; nothing else may be written here.
   */
  public static void serve(InputStream in, OutputStream out) {
    CountDownLatch ended = new CountDownLatch(1);
    // The SDK keeps reading on its own thread, so the end of input is the signal to shut down.
    // A broken pipe ends the input just as surely as its end does.
    InputStream watched = new FilterInputStream(in) {
      @Override
      public int read() throws IOException {
        try {
          return ended(super.read());
        } catch (IOException e) {
          ended.countDown();
          throw e;
        }
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        try {
          return ended(super.read(buffer, offset, length));
        } catch (IOException e) {
          ended.countDown();
          throw e;
        }
      }

      private int ended(int read) {
        if (read < 0) {
          ended.countDown();
        }
        return read;
      }
    };
    McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(MAPPER, watched, out))
            .serverInfo("arbor", "1")
            .instructions(INSTRUCTIONS)
            .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
            .tools(tools())
            .build();
    try {
      ended.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    server.closeGracefully();
  }

  private static List<SyncToolSpecification> tools() {
    String fraction = "\"type\":\"number\",\"minimum\":0,\"maximum\":1";
    return List.of(
            tool("bundled_presets", "", args -> ArborTools.bundledPresets()),
            tool("new_design", property("new_design", "template", STRING) + "," + FILE,
                    args -> ArborTools.newDesign(args.get("template") == null ? null : text(args, "template"),
                            file(args))),
            tool("describe_settings", FILE, args -> ArborTools.describeSettings(file(args))),
            tool("read_design", property("read_design", "file", STRING), args -> ArborTools.readDesign(file(args))),
            tool("set_setting", FILE + "," + property("set_setting", "path", STRING) + ","
                            + property("set_setting", "json", ""),
                    args -> ArborTools.setSetting(file(args), text(args, "path"), text(args, "json"))),
            tool("edit_design", FILE + "," + property("edit_design", "changes",
                            "\"type\":\"array\",\"items\":{\"type\":\"object\"}"),
                    args -> ArborTools.editDesign(file(args), text(args, "changes"))),
            tool("generate", FILE + "," + SEED, args -> ArborTools.generate(file(args), seed(args))),
            image("render", FILE + "," + SEED + "," + HIGH_DETAIL + "," + WIREFRAME, args -> {
              Path file = file(args);
              long seed = seed(args);
              return CallToolResult.builder()
                      .addContent(png(ArborTools.render(file, seed, highDetail(args), wireframe(args))))
                      .addTextContent(words("render.reply") + " " + ArborTools.generate(file, seed))
                      .build();
            }),
            image("render_closeup", FILE + "," + SEED + ","
                    + property("render_closeup", "view", STRING
                    + ",\"enum\":[\"front\",\"side\",\"three-quarter\",\"top\"]") + ","
                    + property("render_closeup", "bottom", fraction) + ","
                    + property("render_closeup", "top", fraction) + ","
                    + property("render_closeup", "left", fraction) + ","
                    + property("render_closeup", "right", fraction) + "," + HIGH_DETAIL + "," + WIREFRAME, args -> {
              Object view = args.get("view");
              double[] region = {number(args, "bottom", 0), number(args, "top", 1), number(args, "left", 0),
                      number(args, "right", 1)};
              return CallToolResult.builder()
                      .addContent(png(ArborTools.renderCloseup(file(args), seed(args),
                              view == null ? "front" : text(args, "view"), region, highDetail(args), wireframe(args))))
                      .build();
            }),
            image("render_variations", FILE + "," + property("render_variations", "seed", INTEGER) + ","
                    + property("render_variations", "count", INTEGER) + "," + HIGH_DETAIL + "," + WIREFRAME, args -> {
              Path file = file(args);
              long seed = seed(args);
              long count = whole(args, "count", 4);
              return CallToolResult.builder()
                      .addContent(png(ArborTools.renderVariations(file, seed, count, highDetail(args),
                              wireframe(args))))
                      .addTextContent(ArborTools.variationsSummary(file, seed, count))
                      .build();
            }),
            image("compare", FILE + "," + property("compare", "path", STRING) + ","
                    + property("compare", "values", "\"type\":\"array\"") + "," + SEED + "," + HIGH_DETAIL + ","
                    + WIREFRAME, args -> {
              ArborTools.Comparison comparison = ArborTools.compare(file(args), text(args, "path"),
                      text(args, "values"), seed(args), highDetail(args), wireframe(args));
              return CallToolResult.builder()
                      .addContent(png(comparison.png()))
                      .addTextContent(comparison.summary())
                      .build();
            }),
            tool("undo", FILE + "," + property("undo", "steps", INTEGER),
                    args -> ArborTools.undo(file(args), whole(args, "steps", 1))),
            tool("list_blocks", property("list_blocks", "text", STRING),
                    args -> ArborTools.listBlocks(args.get("text") == null ? "" : text(args, "text"))),
            tool("block_colors", property("block_colors", "text", STRING),
                    args -> ArborTools.blockColors(text(args, "text"))),
            tool("export", FILE + "," + property("export", "count", INTEGER) + ","
                            + property("export", "folder", STRING),
                    args -> ArborTools.export(file(args), whole(args, "count", 0),
                            args.get("folder") == null ? null : Path.of(text(args, "folder")))));
  }

  /**
   * One tool whose answer is text, described by its name in {@code mcp.txt}. Any problem becomes an error result, so
   * the server keeps running.
   *
   * @param name       Tool name.
   * @param properties The input schema's properties, as JSON members.
   * @param call       The tool's work.
   */
  private static SyncToolSpecification tool(String name, String properties,
                                            Function<Map<String, Object>, String> call) {
    return image(name, properties, args -> CallToolResult.builder().addTextContent(call.apply(args)).build());
  }

  /**
   * One tool whose answer is built by the tool itself, such as an image, described by its name in {@code mcp.txt}.
   *
   * @param name       Tool name.
   * @param properties The input schema's properties, as JSON members.
   * @param call       The tool's work.
   */
  private static SyncToolSpecification image(String name, String properties,
                                             Function<Map<String, Object>, CallToolResult> call) {
    return SyncToolSpecification.builder()
            .tool(McpSchema.Tool.builder(name, MAPPER, schema(properties)).description(words(name)).build())
            .callHandler((exchange, request) -> answer(() -> call.apply(request.arguments())))
            .build();
  }

  private static McpSchema.ImageContent png(byte[] png) {
    return McpSchema.ImageContent.builder(Base64.getEncoder().encodeToString(png), "image/png").build();
  }

  /**
   * An input schema; the preset file is required wherever a tool takes one. An export left without a count is
   * refused by the tool itself.
   *
   * @param properties The schema's properties, as JSON members.
   */
  private static String schema(String properties) {
    List<String> required = REQUIRED.stream().filter(key -> properties.contains("\"" + key + "\":{"))
            .map(key -> "\"" + key + "\"").toList();
    return "{\"type\":\"object\"," + (required.isEmpty() ? "" : "\"required\":" + required + ",")
            + "\"properties\":{" + properties + "}}";
  }

  /**
   * Runs a tool, turning any problem into an error result so the server keeps running. A runaway tree can overflow
   * the stack or run out of memory; that too must still get an answer.
   *
   * @param work The tool's work.
   */
  private static CallToolResult answer(Supplier<CallToolResult> work) {
    try {
      return work.get();
    } catch (RuntimeException e) {
      return error(String.valueOf(e.getMessage()));
    } catch (StackOverflowError | OutOfMemoryError e) {
      return error(words("error.runaway"));
    }
  }

  private static CallToolResult error(String message) {
    return CallToolResult.builder().addTextContent(message).isError(true).build();
  }

  /**
   * An argument as text. Anything other than a string, such as a JSON object for set_setting, is written as JSON.
   *
   * @param args Tool arguments.
   * @param key  Argument name.
   */
  private static String text(Map<String, Object> args, String key) {
    Object value = args.get(key);
    if (value == null) {
      throw new IllegalArgumentException(words("error.required").formatted(key));
    }
    return value instanceof String string ? string : JSON.writeValueAsString(value);
  }

  private static Path file(Map<String, Object> args) {
    return Path.of(text(args, "file"));
  }

  /**
   * A whole-number argument, refused rather than rounded or wrapped when it is anything else.
   *
   * @param args     Tool arguments.
   * @param key      Argument name.
   * @param fallback Value when it is left out.
   */
  private static long whole(Map<String, Object> args, String key, long fallback) {
    Object value = args.get(key);
    if (value == null) {
      return fallback;
    }
    if (!(value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger)) {
      throw new IllegalArgumentException(words("error.whole").formatted(key));
    }
    try {
      return ((Number) value) instanceof java.math.BigInteger big ? big.longValueExact() : ((Number) value).longValue();
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(words("error.too_large").formatted(key));
    }
  }


  /**
   * A number argument.
   *
   * @param args     Tool arguments.
   * @param key      Argument name.
   * @param fallback Value when it is left out.
   */
  private static double number(Map<String, Object> args, String key, double fallback) {
    Object value = args.get(key);
    if (value == null) {
      return fallback;
    }
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException(words("error.number").formatted(key));
    }
    return number.doubleValue();
  }

  private static boolean wireframe(Map<String, Object> args) {
    return Boolean.TRUE.equals(args.get("wireframe"));
  }

  private static boolean highDetail(Map<String, Object> args) {
    return Boolean.TRUE.equals(args.get("high_detail"));
  }

  private static long seed(Map<String, Object> args) {
    return whole(args, "seed", 1);
  }
}
