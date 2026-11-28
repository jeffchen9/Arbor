# Arbor: Procedural Voxel Tree Generation

Arbor procedurally generates Minecraft trees from JSON tree designs. It has an editor with a live 3D preview and can export
trees as `.schem` files.

> Arbor is unfinished. Features, settings and the tree design format may change or
> break between snapshots, and tree designs made now may not load in later versions.

## Features

* Trees grown from tree designs: trunk, branch levels, leaves, forces such as wind, and a crown shape
* Wood drawn with logs, walls, fences, stairs, slabs and other shapes to show thin branches
* Plant layers and decorators (pinecones, vines and moss)
* A Survival mode that uses only blocks a real tree can have
* Editor with Simple and Advanced settings and a textured 3D preview
* Batch export to `.schem`

## Building

Arbor needs Java 21 or later, either on your `PATH` or set in `JAVA_HOME`. Maven is not needed: the project includes the
Maven wrapper. From the project folder, run:

```
.\mvnw.cmd package
```

On macOS or Linux, use `sh mvnw package`. This runs the tests and builds `ArborGUI/target/Arbor-<version>.jar` (for
example
`Arbor-1.0.0-SNAPSHOT-1.jar`, from the version in `pom.xml`), which runs on Windows, macOS and Linux. To build without
running the tests, add `-DskipTests`. The editor needs OpenGL 3.3.

## Usage

The commands below are for Windows and must be run from the project folder after building. On macOS or Linux, replace
`.\arbor.cmd` with `sh arbor.sh` and `.\mvnw.cmd` with `sh mvnw`.

Open the editor, empty or with a tree design:

```
.\arbor.cmd
.\arbor.cmd presets/english_oak.json
```

Export trees from a tree design as `.schem` files:

```
.\arbor.cmd export presets/english_oak.json --count 10 --out exports
```

`--count` is required. Use `--seed <n>` with the editor to start from a particular tree.

You can also run Arbor through Maven without building the jar first. Arguments go in `-Dexec.args`:

```
.\mvnw.cmd compile exec:java
.\mvnw.cmd compile exec:java "-Dexec.args=export presets/english_oak.json --count 10 --out exports"
```

The preview uses block textures from your own Minecraft installation, which Arbor looks for automatically. If none is
found, the editor tells you and lets you pick the game jar yourself, such as `versions/1.21.4/1.21.4.jar` inside the
Minecraft folder (`%APPDATA%\.minecraft` on Windows, `~/Library/Application Support/minecraft` on macOS and
`~/.minecraft` on Linux). You can pick another one at any time with **View > Locate Minecraft Jar**, or point to an
extracted assets folder with
`--assets <folder>`. No Minecraft assets are included with Arbor.

## Building trees with an AI assistant

Arbor includes an [MCP](https://modelcontextprotocol.io) server, so an assistant such as Claude can create and refine
tree designs itself. For example, it can build a tree that matches a photo, then check its work with rendered images.

### Connecting

Build Arbor first (see [Building](#building)). Then register the server with your assistant, using the full path to
your Arbor folder. The assistant starts the server itself when it needs it; running `arbor mcp` yourself does not
connect anything.

For Claude Code on Windows:

```
claude mcp add arbor -- cmd /c C:\path\to\Arbor\arbor.cmd mcp
```

On macOS or Linux:

```
claude mcp add arbor -- sh /path/to/Arbor/arbor.sh mcp
```

For Claude Desktop, add this to `claude_desktop_config.json` (on macOS or Linux, use `"command": "sh"` and
`"args": ["/path/to/Arbor/arbor.sh", "mcp"]`), then restart Claude Desktop:

```json
{
  "mcpServers": {
    "arbor": {
      "command": "cmd",
      "args": ["/c", "C:\\path\\to\\Arbor\\arbor.cmd", "mcp"]
    }
  }
}
```

### Workflow

1. **Ask for a tree and say where to save it.** For example, attach a photo and write: "Using Arbor, build a tree design
   that matches this photo and save it as `C:\trees\garden_oak.json`." Use a full path: a bare file name is saved in the
   folder the assistant started the server from.
2. **The assistant builds it.** It starts from a bare trunk, looks at the built-in species to learn the format, then
   adds branch levels, leaves, blocks, forces such as sun or wind, and decorators such as moss or vines.
3. **It checks its work.** It renders front, side, three-quarter and top views to compare with the photo, zooms in on
   details such as the trunk base, and reads measurements such as crown width and trunk thickness. It then adjusts a
   few settings at a time and renders again until the tree matches.
4. **It checks other seeds.** Each seed grows a different tree from the same design. The assistant compares several so
   the match is not one lucky seed, and lowers the randomness if the trees differ too much.
5. **Open the design in the editor** to look at it yourself: `.\arbor.cmd C:\trees\garden_oak.json`. The editor does
   not reload on its own. If the assistant changes the file while it is open, the editor says the file changed on
   disk; use **File > Revert** to load the new version.
6. **Export.** Ask the assistant to export a number of trees as `.schem` files, or use the editor's Export pane.

The assistant can only see the images it renders, not the editor window. Rendered images use up its context, so it
draws small images by default and larger, more detailed ones only when it needs them. If the result is close but not
right, tell it what differs ("the crown should be wider and start lower") rather than starting over.

The tools it uses:

| Tool | What it does |
|---|---|
| `bundled_presets` | Lists the built-in example species |
| `new_design` | Creates a tree design, from a bare trunk or a copy of a built-in species |
| `read_design` | Shows a design's JSON, or a built-in species' as an example |
| `describe_settings` | Lists every setting with its unit, range, current value and what it does |
| `set_setting`, `edit_design` | Change settings; `edit_design` also adds and removes levels, forces, leaves, plant layers and decorators, several at once |
| `generate` | Builds the tree and describes its size, crown, trunk and blocks in numbers, with branches and wood per level, leaf clusters per leaf set and plants per layer |
| `render`, `render_closeup`, `render_variations` | Images of the tree from four sides, of one part up close, and of several seeds side by side, as blocks or as a wireframe of the branches alone, with a key of the levels' colors |
| `compare` | Images of one setting at several values side by side, without changing the file |
| `undo` | Takes back the newest edits the assistant made |
| `list_blocks` | Lists the block names your Minecraft version has; edits also warn about names it does not have |
| `block_colors` | Gives blocks' real colors from your Minecraft textures, so blocks are chosen by color rather than by name |
| `export` | Writes trees as `.schem` files |

## Note

Arbor is under development. If you find a bug, please create an issue.

## Distributing

Arbor is _open source_ software, licensed under the GPL version 3. This means that if you distribute it, original or in
modified form, you must also distribute it (including your changes) as open source under the GPL version 3 (or later).
See the [LICENCE](LICENCE) file for details.