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

import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.pipeline.GeneratedTree;
import com.jeffch.arbor.pipeline.TreeGenerator;
import com.jeffch.arbor.preset.GroundPlane;
import com.jeffch.arbor.preset.PresetDocument;
import com.jeffch.arbor.preset.PresetLoader;
import com.jeffch.arbor.preset.SpeciesPreset;
import com.jeffch.arbor.shape.ShapeClass;
import com.jeffch.arbor.skeleton.Branch;
import com.jeffch.arbor.skeleton.Node;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * The interactive tree viewer and one-frame screenshot renderer. OpenGL stays on the calling thread and draws into an
 * offscreen target; each frame is shown in a Swing panel. Swing owns the preset document, and reaches the render
 * thread only through the command queue.
 */
public final class ViewerApp {
  private static final int DEFAULT_WIDTH = 1280;
  private static final int DEFAULT_HEIGHT = 800;
  private static final String SIMPLE_VIEW_KEY = "simpleTreeView";
  private static final String MINECRAFT_JAR_KEY = "minecraftJar";
  private static final int GL_FAILURE = 3;
  private static final long COMMAND_WAIT_MILLIS = 8;
  private static final int SPLIT_DIVIDER = 2;
  private static final float[][] LEVEL_COLORS = {
          {0.92f, 0.72f, 0.42f}, {0.95f, 0.52f, 0.34f}, {0.48f, 0.82f, 0.56f}, {0.46f, 0.72f, 0.95f},
          {0.78f, 0.60f, 0.95f},
  };

  private final ViewerOptions options;
  private final ViewerState state;
  private final OrbitCamera camera = new OrbitCamera();
  private final Matrix4f view = new Matrix4f();
  private final Matrix4f projection = new Matrix4f();
  private final Matrix4f viewProjection = new Matrix4f();
  private final LinkedBlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
  private final JPanel settingsHolder = new JPanel(new BorderLayout());
  private JFrame frame;
  private ViewPanel viewPanel;
  private ControlPanel panel;
  private PresetFiles files;
  private volatile SettingsPanel settingsPanel;
  private PresetDocument document;
  private volatile String sourceName;
  private Path watchedFile;
  private boolean frameNextTree;
  private long editorSeed;
  private GeneratedTree tree;
  private SpeciesPreset preset;
  private long window;
  private OffscreenTarget target;
  private LineRenderer sceneLines;
  private LineRenderer skeletonLines;
  private LineRenderer voxelLines;
  private LineRenderer forceLines;
  private GroundRenderer groundRenderer;
  private ShadowMap shadowMap;
  private boolean shadowStale = true;
  private ForceMarkers.Markers forceMarkers;
  private MeshRenderer meshes;
  private TexturedRenderer texturedRenderer;
  private TexturedMesher texturedMesher;
  private BlockCatalog blockCatalog;
  private String assetNotice;
  private String textureSource;
  private TexturedMesher.Result texturedMesh;
  private DensityRenderer density;
  private GenerationWorker worker;
  private volatile PresetWatcher watcher;
  private ByteBuffer framePixels;
  private boolean voxelLinesStale = true;
  private boolean running = true;
  private boolean frameStale = true;
  private int viewWidth;
  private int viewHeight;
  private String errorMessage;
  private String rendererName = "";
  private int leafVoxels;
  private PrintStream watchErrors;

  /**
   * @param options    Command line options.
   * @param tree       First tree to show.
   * @param preset     Preset the first tree came from.
   * @param document   The preset being edited, or null to open it from the options' file later.
   * @param sourceName Name of the preset shown in the window.
   */
  private ViewerApp(ViewerOptions options, GeneratedTree tree, SpeciesPreset preset, PresetDocument document,
                    String sourceName) {
    this.options = options;
    this.tree = tree;
    this.preset = preset;
    this.document = document;
    this.sourceName = sourceName;
    state = new ViewerState(options.seed());
    state.realisticLighting = options.realisticLighting();
    editorSeed = options.seed();
  }

  /**
   * Opens the editor, or renders one screenshot without a window, and returns the exit code. With no preset given, the
   * editor starts on a new untitled tree.
   *
   * @param options Command line options.
   * @param out     Receives normal output.
   * @param err     Receives errors.
   */
  public static int run(ViewerOptions options, PrintStream out, PrintStream err) {
    boolean newTree = options.preset() == null;
    PresetDocument starting = newTree ? PresetFiles.newDocument() : null;
    SpeciesPreset preset = newTree ? starting.preset() : PresetLoader.load(options.preset());
    GeneratedTree tree = TreeGenerator.generate(preset, options.seed());
    String source = newTree ? starting.displayName() : options.preset().getFileName().toString();
    ViewerApp app = new ViewerApp(options, tree, preset, starting, source);
    try {
      app.open();
      if (options.screenshot() != null) {
        app.renderScreenshot();
        out.println("wrote " + options.screenshot());
      } else {
        app.startInteractive(err);
        app.loop();
      }
      return 0;
    } catch (IllegalStateException e) {
      err.println("viewer error: " + e.getMessage());
      return GL_FAILURE;
    } finally {
      app.close();
    }
  }

  /**
   * The twelve edges of one block, for the voxel grid overlay.
   *
   * @param lines Receives the edges.
   * @param x     Block x.
   * @param y     Block y.
   * @param z     Block z.
   */
  private static void addCube(LineRenderer.Lines lines, int x, int y, int z) {
    float r = 0.42f;
    float g = 0.46f;
    float b = 0.52f;
    for (int a = 0; a < 2; a++) {
      for (int c = 0; c < 2; c++) {
        lines.add(x, y + a, z + c, x + 1, y + a, z + c, r, g, b);
        lines.add(x + a, y, z + c, x + a, y + 1, z + c, r, g, b);
        lines.add(x + a, y + c, z, x + a, y + c, z + 1, r, g, b);
      }
    }
  }

  /**
   * Closes something during shutdown, ignoring any failure.
   *
   * @param resource Thing to close, or null.
   */
  private static void closeQuietly(AutoCloseable resource) {
    if (resource == null) {
      return;
    }
    try {
      resource.close();
    } catch (Exception ignored) {
      // The process is exiting, so a failed cleanup has no effect.
    }
  }

  /**
   * Creates a hidden OpenGL 3.3 context and every renderer, loads the Minecraft assets if found, and shows the first
   * tree. All drawing happens off screen; the window only shows the finished image.
   */
  private void open() {
    GLFWErrorCallback.createPrint(System.err).set();
    if (!glfwInit()) {
      throw new IllegalStateException("cannot initialize GLFW");
    }
    glfwDefaultWindowHints();
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
    window = glfwCreateWindow(16, 16, "Arbor", 0, 0);
    if (window == 0) {
      throw new IllegalStateException("cannot create an OpenGL 3.3 context");
    }
    glfwMakeContextCurrent(window);
    GL.createCapabilities();
    rendererName = glGetString(GL_RENDERER);
    target = new OffscreenTarget();
    sceneLines = new LineRenderer();
    skeletonLines = new LineRenderer();
    voxelLines = new LineRenderer();
    forceLines = new LineRenderer();
    groundRenderer = new GroundRenderer();
    meshes = new MeshRenderer();
    density = new DensityRenderer();
    TexturedMesher.Result firstTextured = loadAssets();
    uploadTree(ShapeMesher.mesh(tree.shapes()), firstTextured);
    frameTree();
  }

  /**
   * Loads block models and textures when an assets folder is found, and returns the first textured mesh. A jar the
   * user picked in an earlier session comes before the automatic search of the Minecraft install.
   */
  private TexturedMesher.Result loadAssets() {
    String pickedJar = Preferences.userNodeForPackage(ViewerApp.class).get(MINECRAFT_JAR_KEY, null);
    Optional<Path> found = AssetsLocator.find(options.assets(), System.getenv(AssetsLocator.ENVIRONMENT_VARIABLE),
            Path.of("").toAbsolutePath(),
            () -> MinecraftAssets.remembered(pickedJar, MinecraftAssets.cacheFolder()).or(MinecraftAssets::installed));
    return found.map(this::useAssets).orElse(null);
  }

  /**
   * Switches to the block models and textures in an assets folder, and returns the current tree's textured mesh.
   * Nothing changes unless every step works; otherwise null is returned and the notice says why. Runs on the render
   * thread.
   *
   * @param folder Folder holding {@code assets/minecraft}.
   */
  private TexturedMesher.Result useAssets(Path folder) {
    TexturedRenderer renderer = null;
    try {
      Path textures = folder.resolve("assets").resolve("minecraft").resolve("textures");
      TextureAtlas atlas = TextureAtlas.load(textures.resolve("block"));
      BlockModels models = new BlockModels(folder);
      BlockCatalog catalog = new BlockCatalog(models.blockNames(), models.crossedPlants());
      TexturedMesher mesher = new TexturedMesher(models, atlas, new TintColors(textures.resolve("colormap")));
      renderer = new TexturedRenderer(atlas);
      TexturedMesher.Result first = mesher.mesh(tree.shapes(), tree.blocks());
      closeQuietly(texturedRenderer);
      textureSource = folder.getFileName().toString();
      blockCatalog = catalog;
      texturedMesher = mesher;
      texturedRenderer = renderer;
      assetNotice = null;
      return first;
    } catch (RuntimeException e) {
      if (renderer != null) {
        renderer.close();
      }
      assetNotice = "Minecraft assets could not be loaded (" + e + "). Showing plain colours.";
      return null;
    }
  }

  /**
   * Opens the preset for editing, starts the background generator and builds the window.
   *
   * @param err Receives errors.
   */
  private void startInteractive(PrintStream err) {
    if (options.preset() != null) {
      document = PresetDocument.open(options.preset());
    }
    worker = new GenerationWorker(TreeGenerator::generate, texturedMesher);
    watchErrors = err;
    ControlPanel.Stats stats = stats();
    SettingsPanel.Context context = panelContext();
    try {
      SwingUtilities.invokeAndWait(() -> buildWindow(stats, context));
    } catch (InterruptedException | InvocationTargetException e) {
      throw new IllegalStateException("cannot open the viewer window: " + e);
    }
    if (texturedMesher == null) {
      String reason = assetNotice;
      SwingUtilities.invokeLater(() -> offerToLocateJar(reason));
    }
  }

  /**
   * Tells the user that no usable Minecraft textures were found and offers to pick the game jar by hand. Runs on the
   * Swing thread.
   *
   * @param reason Why found assets could not be loaded, or null when none were found.
   */
  private void offerToLocateJar(String reason) {
    String message = (reason != null ? reason
            : "Arbor could not find an up-to-date Minecraft installation, so the preview shows plain colours "
            + "instead of block textures.")
            + "\n\nYou can locate the Minecraft game jar yourself, for example "
            + ".minecraft\\versions\\1.21.4\\1.21.4.jar. Arbor reads only block models and textures from it and "
            + "remembers your choice.\n\nYou can also do this later from View > Locate Minecraft Jar.";
    Object[] choices = {"Locate Jar...", "Continue Without Textures"};
    int picked = JOptionPane.showOptionDialog(frame, dialogText(message), "Minecraft Textures Not Found",
            JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[0]);
    if (picked == 0) {
      locateMinecraftJar();
    }
  }

  /**
   * A dialog message that wraps at a readable width, with blank lines kept as paragraph breaks.
   *
   * @param message Plain text, with {@code \n} for line breaks.
   */
  private static JLabel dialogText(String message) {
    String html = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    return new JLabel("<html><body style='width: 380px'>" + html + "</body></html>");
  }

  /**
   * Asks for a Minecraft game jar and, once its block assets are extracted, shows its textures. The extraction runs
   * in the background, and a file that is not a usable game jar is reported. Runs on the Swing thread.
   */
  void locateMinecraftJar() {
    JFileChooser chooser = new JFileChooser();
    Path versions = MinecraftAssets.versionsFolder();
    if (versions != null && Files.isDirectory(versions)) {
      chooser.setCurrentDirectory(versions.toFile());
    }
    chooser.setDialogTitle("Locate Minecraft Jar");
    chooser.setFileFilter(new FileNameExtensionFilter("Minecraft game jar (*.jar)", "jar"));
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path jar = chooser.getSelectedFile().toPath().toAbsolutePath();
    frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    Thread extraction = new Thread(() -> {
      try {
        Path folder = MinecraftAssets.fromJar(jar, MinecraftAssets.cacheFolder());
        redrawAfter(() -> usePickedAssets(jar, folder));
      } catch (IOException | RuntimeException e) {
        String reason = e.getMessage() == null ? e.toString() : e.getMessage();
        SwingUtilities.invokeLater(() -> {
          frame.setCursor(null);
          reportUnusableJar(jar, reason);
        });
      }
    }, "minecraft-jar");
    extraction.setDaemon(true);
    extraction.start();
  }

  /**
   * Shows a picked jar's textures on the current tree and for every later tree, and remembers the jar for next time.
   * Runs on the render thread.
   *
   * @param jar    The picked jar.
   * @param folder Its extracted assets.
   */
  private void usePickedAssets(Path jar, Path folder) {
    TexturedMesher.Result textured = useAssets(folder);
    String problem = textured == null ? assetNotice : null;
    if (textured != null) {
      Preferences.userNodeForPackage(ViewerApp.class).put(MINECRAFT_JAR_KEY, jar.toString());
      worker.useTexturedMesher(texturedMesher);
      uploadTree(ShapeMesher.mesh(tree.shapes()), textured);
      shadowStale = true;
    }
    ControlPanel.Stats stats = stats();
    SettingsPanel.Context context = panelContext();
    SwingUtilities.invokeLater(() -> {
      frame.setCursor(null);
      panel.showStats(stats);
      settingsPanel.showContext(context);
      if (problem != null) {
        reportUnusableJar(jar, problem);
      }
    });
  }

  /**
   * Explains why a picked file could not be used. Runs on the Swing thread.
   *
   * @param jar    The picked file.
   * @param reason What went wrong.
   */
  private void reportUnusableJar(Path jar, String reason) {
    JOptionPane.showMessageDialog(frame, dialogText("Arbor could not use this file:\n" + jar + "\n\n" + reason
                    + "\n\nPick the game jar inside your Minecraft versions folder, for example "
                    + ".minecraft\\versions\\1.21.4\\1.21.4.jar."),
            "Not a Minecraft Game Jar", JOptionPane.ERROR_MESSAGE);
  }

  /**
   * Follows outside edits to the current preset's file.
   */
  private void watchCurrentFile() {
    Path file = document.file();
    if (java.util.Objects.equals(file, watchedFile)) {
      return;
    }
    closeQuietly(watcher);
    watcher = null;
    watchedFile = file;
    if (file != null) {
      try {
        watcher = new PresetWatcher(file, () -> SwingUtilities.invokeLater(this::presetFileChanged));
      } catch (IOException e) {
        watchErrors.println("live reload is unavailable: " + e.getMessage());
      }
    }
  }

  /**
   * Puts a preset's settings in the window.
   *
   * @param context What the newest tree received.
   */
  private void attachSettings(SettingsPanel.Context context) {
    settingsHolder.removeAll();
    Preferences preferences = Preferences.userNodeForPackage(ViewerApp.class);
    SettingsPanel settings = new SettingsPanel(document, this::requestGeneration, this::statusChanged, files::startFrom,
            panel::randomSeed, preferences.getBoolean(SIMPLE_VIEW_KEY, true),
            simple -> preferences.putBoolean(SIMPLE_VIEW_KEY, simple));
    settings.showContext(context);
    settingsPanel = settings;
    settingsHolder.add(settings, BorderLayout.CENTER);
    settingsHolder.revalidate();
    settingsHolder.repaint();
  }

  /**
   * Switches the editor to another preset and frames its first tree.
   *
   * @param next Preset to show.
   */
  void showDocument(PresetDocument next) {
    document = next;
    sourceName = next.displayName();
    panel.showSource(sourceName);
    attachSettings(new SettingsPanel.Context(blockCatalog, tree.blocks(), tree.plantLayers(), Set.of()));
    watchCurrentFile();
    statusChanged();
    commands.add(() -> frameNextTree = true);
    requestGeneration();
  }

  /**
   * Refreshes the File menu and the window title, which starts with * while there are unsaved changes.
   */
  private void statusChanged() {
    if (files == null || frame == null) {
      return;
    }
    files.update();
    frame.setTitle((document.dirty() ? "*" : "") + sourceName + " - Arbor");
  }

  /**
   * The preset being edited.
   */
  PresetDocument document() {
    return document;
  }

  /**
   * Whether an export is running, so the program does not exit in the middle of one.
   */
  boolean exporting() {
    return settingsPanel.exporting();
  }

  /**
   * Called after a save, which may have given the preset a new file.
   */
  void presetSaved() {
    sourceName = document.displayName();
    panel.showSource(sourceName);
    watchCurrentFile();
    settingsPanel.refresh();
  }

  /**
   * Throws away unsaved edits.
   */
  void revertPreset() {
    settingsPanel.revert();
  }

  /**
   * Asks the render loop to stop.
   */
  void exitViewer() {
    commands.add(() -> running = false);
  }

  /**
   * Orbits the camera by a mouse drag.
   *
   * @param dx Horizontal drag in pixels.
   * @param dy Vertical drag in pixels.
   */
  void orbitCamera(double dx, double dy) {
    redrawAfter(() -> camera.orbit(dx, dy));
  }

  /**
   * Pans the camera by a mouse drag.
   *
   * @param dx Horizontal drag in pixels.
   * @param dy Vertical drag in pixels.
   */
  void panCamera(double dx, double dy) {
    redrawAfter(() -> camera.pan(dx, dy, Math.max(1, viewHeight)));
  }

  /**
   * Zooms the camera by mouse wheel ticks.
   *
   * @param ticks Wheel ticks.
   */
  void zoomCamera(double ticks) {
    redrawAfter(() -> camera.zoom(ticks));
  }

  /**
   * Renders at the view's new size from the next frame.
   *
   * @param width  View width in pixels.
   * @param height View height in pixels.
   */
  void viewResized(int width, int height) {
    redrawAfter(() -> {
      viewWidth = width;
      viewHeight = height;
    });
  }

  /**
   * Lays out the window: view controls and stats on the left, the 3D view in the middle, the preset settings on the
   * right, and the File and View menus.
   *
   * @param stats   The first tree's numbers.
   * @param context What the first tree received.
   */
  private void buildWindow(ControlPanel.Stats stats, SettingsPanel.Context context) {
    UiParts.applyTheme();
    viewPanel = new ViewPanel(this);
    Consumer<Consumer<ViewerState>> viewEdit = edit -> redrawAfter(() -> {
      edit.accept(state);
      shadowStale = true;
    });
    panel = new ControlPanel(state, viewEdit, seed -> {
      editorSeed = seed;
      requestGeneration();
    }, () -> redrawAfter(this::frameTree));
    panel.showStats(stats);
    JScrollPane controls = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    controls.getVerticalScrollBar().setUnitIncrement(18);
    controls.setBorder(null);
    controls.setPreferredSize(new Dimension(280, 300));
    JScrollPane statsPane = new JScrollPane(panel.statsPanel(), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    statsPane.setBorder(null);
    JSplitPane leftColumn = new JSplitPane(JSplitPane.VERTICAL_SPLIT, controls, statsPane);
    leftColumn.setResizeWeight(1);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftColumn, viewPanel);
    split.setResizeWeight(0);
    frame = new JFrame("Arbor");
    frame.setLayout(new BorderLayout());
    files = new PresetFiles(frame, this);
    JMenuBar menuBar = files.menuBar();
    menuBar.add(viewMenu(viewEdit));
    frame.setJMenuBar(menuBar);
    settingsHolder.setPreferredSize(new Dimension(430, DEFAULT_HEIGHT));
    attachSettings(context);
    JSplitPane outer = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, split, settingsHolder);
    outer.setResizeWeight(1);
    frame.add(outer, BorderLayout.CENTER);
    watchCurrentFile();
    statusChanged();
    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent event) {
        files.exit();
      }
    });
    Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    frame.setSize(Math.min(1500, screen.width), Math.min(900, screen.height));
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    SwingUtilities.invokeLater(() -> controls.getViewport().setViewPosition(new java.awt.Point(0, 0)));
  }

  /**
   * The View menu: Minecraft shading or realistic lighting, and split view.
   *
   * @param viewEdit Applies a change to the view settings on the render thread.
   */
  private JMenu viewMenu(Consumer<Consumer<ViewerState>> viewEdit) {
    JMenu menu = new JMenu("View");
    menu.setMnemonic(KeyEvent.VK_V);
    JRadioButtonMenuItem minecraft = new JRadioButtonMenuItem("Minecraft Shading", !options.realisticLighting());
    JRadioButtonMenuItem realistic = new JRadioButtonMenuItem("Realistic Lighting", options.realisticLighting());
    ButtonGroup group = new ButtonGroup();
    group.add(minecraft);
    group.add(realistic);
    minecraft.addActionListener(event -> viewEdit.accept(viewer -> viewer.realisticLighting = false));
    realistic.addActionListener(event -> viewEdit.accept(viewer -> viewer.realisticLighting = true));
    menu.add(minecraft);
    menu.add(realistic);
    menu.addSeparator();
    JCheckBoxMenuItem split = new JCheckBoxMenuItem("Split View (Wireframe and Blocks)");
    split.addActionListener(event -> {
      boolean on = split.isSelected();
      viewEdit.accept(viewer -> viewer.splitView = on);
    });
    menu.add(split);
    menu.addSeparator();
    JMenuItem locate = new JMenuItem("Locate Minecraft Jar...");
    locate.addActionListener(event -> locateMinecraftJar());
    menu.add(locate);
    return menu;
  }

  /**
   * Runs a change on the render thread and draws a new frame.
   *
   * @param change Change to run.
   */
  private void redrawAfter(Runnable change) {
    commands.add(() -> {
      change.run();
      frameStale = true;
    });
  }

  /**
   * When the preset's file changes on disk: reloads it if there are no unsaved edits, otherwise just warns.
   */
  private void presetFileChanged() {
    if (document.reloadFromDisk()) {
      settingsPanel.reloaded();
      requestGeneration();
    } else {
      settingsPanel.refresh();
    }
  }

  /**
   * What the settings panel shows about the current tree: its blocks, active plant layers and unknown blocks.
   */
  private SettingsPanel.Context panelContext() {
    return new SettingsPanel.Context(blockCatalog, tree.blocks(), tree.plantLayers(),
            texturedMesh == null ? Set.of() : texturedMesh.unknownBlocks());
  }

  /**
   * Sends a new tree to the GPU: the ground grid and axes, force markers, the skeleton colored by level, both block
   * meshes and the leaf density.
   *
   * @param mesh     Flat-color mesh for each shape class.
   * @param textured Textured meshes, or null without assets.
   */
  private void uploadTree(Map<ShapeClass, float[]> mesh, TexturedMesher.Result textured) {
    Bounds bounds = Bounds.of(tree);
    LineRenderer.Lines grid = new LineRenderer.Lines();
    int extent = (int) Math.ceil(Math.max(Math.max(Math.abs(bounds.min.x), Math.abs(bounds.max.x)),
            Math.max(Math.abs(bounds.min.z), Math.abs(bounds.max.z)))) + 4;
    GroundPlane ground = tree.ground();
    for (int i = -extent; i <= extent; i++) {
      float shade = i % 8 == 0 ? 0.34f : 0.2f;
      grid.add(i, (float) ground.heightAt(i, -extent), -extent, i, (float) ground.heightAt(i, extent), extent,
              shade, shade, shade);
      grid.add(-extent, (float) ground.heightAt(-extent, i), i, extent, (float) ground.heightAt(extent, i), i,
              shade, shade, shade);
    }
    if (!ground.flat()) {
      Vec3 normal = ground.normal().normalized().scale(Math.max(4, extent * 0.3));
      grid.add(0.5f, 0, 0.5f, 0.5f + (float) normal.x(), (float) normal.y(), 0.5f + (float) normal.z(),
              1f, 0.55f, 0.85f);
    }
    grid.add(0, 0.01f, 0, 3, 0.01f, 0, 0.95f, 0.3f, 0.3f);
    grid.add(0, 0, 0, 0, 3, 0, 0.3f, 0.95f, 0.4f);
    grid.add(0, 0.01f, 0, 0, 0.01f, 3, 0.35f, 0.55f, 1f);
    sceneLines.upload(grid);
    // Wide enough to hold the longest shadow the angled sun can cast.
    groundRenderer.upload(ground, extent + 1.3f * (bounds.max.y - bounds.min.y));

    forceMarkers = ForceMarkers.build(preset.forceList(), tree.seed(), bounds.min, bounds.max);
    forceLines.upload(forceMarkers.lines());

    LineRenderer.Lines branches = new LineRenderer.Lines();
    for (Branch branch : tree.skeleton().branches()) {
      float[] color = LEVEL_COLORS[branch.level() % LEVEL_COLORS.length];
      for (int i = 1; i < branch.nodes().size(); i++) {
        Vec3 a = branch.nodes().get(i - 1).position();
        Vec3 b = branch.nodes().get(i).position();
        branches.add((float) a.x(), (float) a.y(), (float) a.z(), (float) b.x(), (float) b.y(), (float) b.z(),
                color[0], color[1], color[2]);
      }
    }
    skeletonLines.upload(branches);
    meshes.upload(mesh);
    texturedMesh = textured;
    if (textured != null) {
      texturedRenderer.upload(textured.meshes());
    }
    density.upload(tree.density());
    leafVoxels = 0;
    for (float value : tree.density().data()) {
      if (value > 0) {
        leafVoxels++;
      }
    }
    voxelLinesStale = true;
    shadowStale = true;
    frameStale = true;
  }

  /**
   * The up-to-date shadow map for realistic lighting, or null for Minecraft shading.
   */
  private ShadowMap currentShadows() {
    if (!state.realisticLighting) {
      return null;
    }
    if (shadowMap == null) {
      shadowMap = new ShadowMap();
    }
    if (shadowStale) {
      shadowStale = false;
      Bounds bounds = Bounds.of(tree);
      shadowMap.render(bounds.min, bounds.max, depth -> {
        if (state.drawsShapes() && state.useTextures && texturedMesh != null) {
          texturedRenderer.drawDepth(depth, state);
        } else if (state.drawsShapes()) {
          meshes.drawDepth(depth, state);
        }
      });
    }
    return shadowMap;
  }

  /**
   * Rebuilds the voxel grid overlay, only when the tree changed since it was last built.
   */
  private void updateVoxelLines() {
    if (!voxelLinesStale) {
      return;
    }
    LineRenderer.Lines lines = new LineRenderer.Lines();
    tree.shapes().forEachSorted((x, y, z, shape) -> addCube(lines, x, y, z));
    voxelLines.upload(lines);
    voxelLinesStale = false;
  }

  /**
   * Fits the tree, and its sun and wind markers when they are shown.
   */
  private void frameTree() {
    if (state.showForces && forceMarkers != null) {
      camera.frame(forceMarkers.min(), forceMarkers.max());
    } else {
      Bounds bounds = Bounds.of(tree);
      camera.frame(bounds.min, bounds.max);
    }
  }

  /**
   * Renders one frame at the default size and saves it.
   */
  private void renderScreenshot() {
    renderScene(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    Screenshot.write(options.screenshot(), target.readPixels(), target.width(), target.height());
  }

  /**
   * The render thread: runs queued commands, takes any finished tree, and redraws only when something changed, handing
   * each frame to the window.
   */
  private void loop() {
    try {
      while (running) {
        Runnable command = commands.poll(COMMAND_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        while (command != null) {
          command.run();
          command = commands.poll();
        }
        glfwPollEvents();
        applyGeneration();
        if (frameStale && viewWidth > 0 && viewHeight > 0) {
          frameStale = false;
          renderScene(viewWidth, viewHeight);
          int bytes = viewWidth * viewHeight * 4;
          if (framePixels == null || framePixels.capacity() < bytes) {
            framePixels = BufferUtils.createByteBuffer(bytes);
          }
          viewPanel.present(target.readPixels(framePixels), viewWidth, viewHeight);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Called on the Swing thread.
   */
  private void requestGeneration() {
    worker.request(document.preset(), editorSeed);
  }

  /**
   * Shows the newest finished tree, or its error, and updates the stats and settings panels.
   */
  private void applyGeneration() {
    worker.pollLatest().ifPresent(result -> {
      if (result.isSuccess()) {
        tree = result.tree();
        preset = result.preset();
        errorMessage = null;
        uploadTree(result.mesh(), result.textured());
        if (frameNextTree) {
          frameNextTree = false;
          frameTree();
        }
      } else {
        errorMessage = result.error();
      }
      ControlPanel.Stats stats = stats();
      SettingsPanel.Context context = panelContext();
      SwingUtilities.invokeLater(() -> {
        panel.showStats(stats);
        settingsPanel.showContext(context);
      });
    });
  }

  /**
   * The numbers the stats panel shows for the current tree.
   */
  private ControlPanel.Stats stats() {
    int[] dimensions = new int[3];
    if (!tree.shapes().isEmpty()) {
      int[] low = tree.shapes().min();
      int[] high = tree.shapes().max();
      for (int i = 0; i < 3; i++) {
        dimensions[i] = high[i] - low[i] + 1;
      }
    }
    return new ControlPanel.Stats(sourceName, dimensions, Map.copyOf(tree.shapes().counts()), leafVoxels,
            tree.generationNanos() / 1e6, rendererName, errorMessage, textureNotice(), forceMarkers.summary(),
            tree.ground().degrees(), textureSource);
  }

  /**
   * Why blocks show in plain colors or magenta, or null when every block is textured.
   */
  private String textureNotice() {
    if (texturedMesher == null) {
      return assetNotice != null ? assetNotice
              : "No Minecraft install found; use View > Locate Minecraft Jar. Showing plain colours.";
    }
    if (texturedMesh != null && !texturedMesh.unknownBlocks().isEmpty()) {
      return "Unknown blocks (shown magenta): " + String.join(", ", texturedMesh.unknownBlocks());
    }
    return null;
  }

  /**
   * Draws one frame off screen: the whole scene, or in split view the wireframe on the left and the blocks on the right
   * with a divider between.
   *
   * @param width  Frame width in pixels.
   * @param height Frame height in pixels.
   */
  private void renderScene(int width, int height) {
    ShadowMap shadows = currentShadows();
    target.ensureSize(width, height);
    target.bind();
    glEnable(GL_DEPTH_TEST);
    glClearColor(0.09f, 0.10f, 0.12f, 1);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    camera.view(view);
    if (state.splitView) {
      int leftWidth = Math.max(1, (width - SPLIT_DIVIDER) / 2);
      int rightStart = leftWidth + SPLIT_DIVIDER;
      usePane(0, leftWidth, height);
      sceneLines.draw(viewProjection);
      drawSkeleton();
      drawForces();
      usePane(rightStart, Math.max(1, width - rightStart), height);
      drawBlocks(shadows, false);
      glEnable(GL_SCISSOR_TEST);
      glScissor(leftWidth, 0, SPLIT_DIVIDER, height);
      glClearColor(0.42f, 0.45f, 0.5f, 1);
      glClear(GL_COLOR_BUFFER_BIT);
      glDisable(GL_SCISSOR_TEST);
    } else {
      usePane(0, width, height);
      drawBlocks(shadows, state.drawsSkeleton());
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
  }

  /**
   * Both halves of split view use the one camera, each with its own aspect ratio.
   *
   * @param x      Left edge of the pane in pixels.
   * @param width  Pane width in pixels.
   * @param height Pane height in pixels.
   */
  private void usePane(int x, int width, int height) {
    glViewport(x, 0, width, height);
    camera.projection(projection, width / (float) height);
    projection.mul(view, viewProjection);
  }

  /**
   * Draws the branch lines on top of everything.
   */
  private void drawSkeleton() {
    glDisable(GL_DEPTH_TEST);
    skeletonLines.draw(viewProjection);
    glEnable(GL_DEPTH_TEST);
  }

  /**
   * Draws the force markers on top of everything, when shown.
   */
  private void drawForces() {
    if (state.showForces) {
      glDisable(GL_DEPTH_TEST);
      forceLines.draw(viewProjection);
      glEnable(GL_DEPTH_TEST);
    }
  }

  /**
   * Draws the ground, the blocks (textured when possible), the voxel grid, the skeleton, the leaf density cloud and the
   * forces, as each is switched on.
   *
   * @param shadows      Shadow map, or null for Minecraft face shading.
   * @param withSkeleton Whether to draw the skeleton lines.
   */
  private void drawBlocks(ShadowMap shadows, boolean withSkeleton) {
    if (shadows != null) {
      groundRenderer.draw(viewProjection, shadows);
    } else {
      sceneLines.draw(viewProjection);
    }
    if (state.drawsShapes()) {
      if (state.useTextures && texturedMesh != null) {
        texturedRenderer.draw(viewProjection, state, shadows);
      } else {
        meshes.draw(viewProjection, state, shadows);
      }
    }
    if (state.drawsVoxelGrid()) {
      updateVoxelLines();
      voxelLines.draw(viewProjection);
    }
    if (withSkeleton) {
      drawSkeleton();
    }
    if (state.showDensity && density.hasVolume()) {
      target.detachDepth();
      density.render(view, projection, target.depthTexture(), state.densityThreshold, state.densityOpacity);
      target.attachDepth();
    }
    drawForces();
  }

  /**
   * Stops the watcher, generator and export, closes the window and frees every GPU resource.
   */
  private void close() {
    closeQuietly(watcher);
    closeQuietly(worker);
    closeQuietly(settingsPanel == null ? null : settingsPanel::close);
    if (frame != null) {
      SwingUtilities.invokeLater(frame::dispose);
    }
    if (window == 0) {
      glfwTerminate();
      return;
    }
    for (AutoCloseable resource : new AutoCloseable[]{shadowMap, groundRenderer, density, texturedRenderer, meshes, forceLines, voxelLines, skeletonLines, sceneLines, target}) {
      closeQuietly(resource);
    }
    glfwDestroyWindow(window);
    window = 0;
    glfwTerminate();
  }

  /**
   * Axis-aligned bounds of the generated tree, with a sensible minimum size.
   */
  private static final class Bounds {
    final Vector3f min = new Vector3f(-2, 0, -2);
    final Vector3f max = new Vector3f(3, 4, 3);

    /**
     * The box holding the skeleton, the blocks and the leaf density.
     *
     * @param tree The tree.
     */
    static Bounds of(GeneratedTree tree) {
      Bounds bounds = new Bounds();
      for (Branch branch : tree.skeleton().branches()) {
        for (Node node : branch.nodes()) {
          Vec3 p = node.position();
          bounds.include((float) p.x(), (float) p.y(), (float) p.z());
        }
      }
      if (!tree.shapes().isEmpty()) {
        int[] low = tree.shapes().min();
        int[] high = tree.shapes().max();
        bounds.include(low[0], low[1], low[2]);
        bounds.include(high[0] + 1, high[1] + 1, high[2] + 1);
      }
      if (!tree.density().isEmpty()) {
        int[] low = tree.density().min();
        int[] size = tree.density().dimensions();
        bounds.include(low[0], low[1], low[2]);
        bounds.include(low[0] + size[0], low[1] + size[1], low[2] + size[2]);
      }
      return bounds;
    }

    /**
     * Grows the box to hold a point.
     *
     * @param x Point x.
     * @param y Point y.
     * @param z Point z.
     */
    private void include(float x, float y, float z) {
      min.min(new Vector3f(x, y, z));
      max.max(new Vector3f(x, y, z));
    }
  }
}
