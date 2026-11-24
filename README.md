# Arbor

Arbor procedurally generates Minecraft trees from JSON presets. It has an editor with a live 3D preview and can export
trees as `.schem` files.

> **This is a development snapshot.** Arbor is unfinished. Features, settings and the preset format may change or
> break between snapshots, and presets made now may not load in later versions.

## Features

* Trees grown from presets: trunk, branch levels, leaves, forces such as wind, and a crown shape
* Wood drawn with logs, walls, fences, stairs, slabs and other shapes to show thin branches
* Plant layers and decorators (pinecones, vines and moss)
* A Survival mode that uses only blocks a real tree can have
* Editor with Simple and Advanced settings and a textured 3D preview
* Batch export to `.schem`

## Building

Arbor needs Java 21 or later, either on your `PATH` or set in `JAVA_HOME`. Maven is not needed: the project includes
the Maven wrapper. From the project folder, run:

```
.\mvnw.cmd package
```

On macOS or Linux, use `./mvnw package`. This runs the tests and builds `ArborGUI/target/arbor.jar`. To build without
running the tests, add `-DskipTests`.

## Usage

The commands below are for Windows and must be run from the project folder after building. On macOS or Linux, replace
`.\arbor.cmd` with `java -jar ArborGUI/target/arbor.jar`.

Open the editor, empty or with a preset:

```
.\arbor.cmd
.\arbor.cmd presets/oak.json
```

Export trees from a preset as `.schem` files:

```
.\arbor.cmd export presets/oak.json --count 10 --out exports
```

`--count` is required. Use `--seed <n>` with the editor to start from a particular tree.

You can also run Arbor through Maven without building the jar first. Arguments go in `-Dexec.args`:

```
.\mvnw.cmd compile exec:java
.\mvnw.cmd compile exec:java "-Dexec.args=export presets/oak.json --count 10 --out exports"
```

The preview uses block textures from your own Minecraft installation, which Arbor looks for automatically. If none is
found, the editor tells you and lets you pick the game jar yourself, such as `.minecraft\versions\1.21.4\1.21.4.jar`.
You can pick another one at any time with **View > Locate Minecraft Jar**, or point to an extracted assets folder with
`--assets <folder>`. No Minecraft assets are included with Arbor.

## Note

Arbor is under development. If you find a bug, please create an issue.

## Distributing

Arbor is _open source_ software, licensed under the GPL version 3. This means that if you distribute it, original or in
modified form, you must also distribute it (including your changes) as open source under the GPL version 3 (or later).
See the [LICENCE](LICENCE) file for details.
