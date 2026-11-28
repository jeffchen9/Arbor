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
import com.jeffch.arbor.preset.*;
import com.jeffch.arbor.shape.ShapeClass;
import com.jeffch.arbor.skeleton.Branch;
import com.jeffch.arbor.skeleton.Node;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Configuration;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * The interactive tree viewer and one-frame screenshot renderer.
 */
public final class ViewerApp {
  /**
   * The --views sheet: yaw and pitch of each view. Front and side at eye level match most photos; the side shows the
   * depth the front hides, and the top shows the crown's spread.
   */
  private static final List<float[]> SHEET_ANGLES = List.of(new float[]{0, 0}, new float[]{90, 0},
          new float[]{35, 18}, new float[]{0, OrbitCamera.MAX_PITCH});
  private static final List<String> SHEET_LABELS = List.of("Front", "Side", "Three-quarter", "Top");
  /**
   * Background kept around the tree when a sheet's tiles are cropped, in pixels.
   */
  private static final int CROP_MARGIN = 6;
  private static final int DEFAULT_WIDTH = 1280;
  private static final int DEFAULT_HEIGHT = 800;
  private static final String SIMPLE_VIEW_KEY = "simpleTreeView";
  private static final String SPLIT_SCREEN_KEY = "splitScreen";
  private static final String SHOWN_PAGES_KEY = "splitScreenPages";
  private static final String MINECRAFT_JAR_KEY = "minecraftJar";
  private static final int GL_FAILURE = 3;
  private static final long COMMAND_WAIT_MILLIS = 8;
  private static final int SPLIT_DIVIDER = 2;
  /**
   * The settings pane's starting width: enough for the Tree, Voxelizer, Decorators and Export tabs in one row.
   */
  private static final int SETTINGS_WIDTH = 540;
  private static final int SETTINGS_MIN_WIDTH = 180;
  /**
   * The origin axes' length as a share of the camera's distance from the origin.
   */
  private static final float AXIS_SCREEN_SIZE = 0.06f;
  private static final float[][] LEVEL_COLORS = {
          {0.93f, 0.91f, 0.86f}, {0.98f, 0.58f, 0.22f}, {0.40f, 0.85f, 0.45f}, {0.40f, 0.68f, 1.00f},
          {0.85f, 0.50f, 0.95f},
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
  private JScrollPane controls;
  private JMenu viewMenu;
  /**
   * The 3D view's own window in split screen, or null.
   */
  private JFrame viewFrame;
  private boolean splitScreen;
  /**
   * On one screen, whether the right pane holds Export rather than the settings.
   */
  private boolean exportPane;
  /**
   * Settings pages shown side by side in split screen.
   */
  private final Set<String> shownPages = new HashSet<>();
  private List<ViewPanel.Legend> levelLegend = List.of();
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
  private LineRenderer axisLines;
  private LineRenderer envelopeLines;
  /**
   * The crown radius points while the settings show the crown profile editor, or null.
   */
  private double[][] editingProfile;
  /**
   * The current tree's height over its size scale, for converting the crown radius between blocks and shares of the
   * height. Written on the render thread, read by the settings.
   */
  private volatile double treeSize;
  private LineRenderer skeletonLines;
  private LineRenderer voxelLines;
  private LineRenderer forceLines;
  private GroundRenderer groundRenderer;
  private ShadowMap shadowMap;
  private boolean shadowStale = true;
  private OcclusionVolume occlusion;
  private boolean occlusionStale = true;
  /**
   * The shape classes that shaded the textured mesh now on screen, or null when there is none.
   */
  private Set<ShapeClass> meshOccluders;
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
    if (options.wireframe()) {
      state.mode = ViewerState.Mode.BRANCHES;
    }
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
   * Where a game jar usually is on this system, as an example for the user.
   */
  private static String exampleJar() {
    Path versions = MinecraftAssets.versionsFolder();
    Path example = Path.of("1.21.4", "1.21.4.jar");
    return (versions == null ? Path.of("versions").resolve(example) : versions.resolve(example)).toString();
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
   * Creates a hidden OpenGL 3.3 context and every renderer, loads the Minecraft assets if found, and shows the first
   * tree. All drawing happens off screen; the window only shows the finished image.
   */
  private void open() {
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")) {
      // On macOS, GLFW must run on the main thread, which AWT and Swing own. LWJGL's async build passes GLFW's work to
      // that thread instead, so no -XstartOnFirstThread is needed. AWT is started first so the main thread is ready.
      Configuration.GLFW_LIBRARY_NAME.set("glfw_async");
      Toolkit.getDefaultToolkit();
    }
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
    axisLines = new LineRenderer();
    LineRenderer.Lines axes = new LineRenderer.Lines();
    // Lifted a little so they do not flicker against the grid lines through the origin.
    axes.add(0, 0.003f, 0, 1, 0.003f, 0, 0.95f, 0.3f, 0.3f);
    axes.add(0, 0, 0, 0, 1, 0, 0.3f, 0.95f, 0.4f);
    axes.add(0, 0.003f, 0, 0, 0.003f, 1, 0.35f, 0.55f, 1f);
    axisLines.upload(axes);
    skeletonLines = new LineRenderer();
    voxelLines = new LineRenderer();
    forceLines = new LineRenderer();
    envelopeLines = new LineRenderer();
    groundRenderer = new GroundRenderer();
    meshes = new MeshRenderer();
    density = new DensityRenderer();
    occlusion = new OcclusionVolume();
    TexturedMesher.Result firstTextured = loadAssets();
    uploadTree(ShapeMesher.mesh(tree.shapes()), firstTextured);
    frameTree();
  }

  /**
   * Loads block models and textures when an assets folder is found, and returns the first textured mesh. A jar the
   * user picked in an earlier session comes before the automatic search of the Minecraft install.
   */
  private TexturedMesher.Result loadAssets() {
    return findAssets(options.assets()).map(this::useAssets).orElse(null);
  }

  /**
   * The extracted Minecraft assets the editor uses: the --assets folder, then the environment variable, then a jar the
   * user picked, then the newest installed version. Empty when there are none.
   *
   * @param explicit Folder from --assets, or null.
   */
  static Optional<Path> findAssets(Path explicit) {
    String pickedJar = Preferences.userNodeForPackage(ViewerApp.class).get(MINECRAFT_JAR_KEY, null);
    return AssetsLocator.find(explicit, System.getenv(AssetsLocator.ENVIRONMENT_VARIABLE), Path.of("").toAbsolutePath(),
            () -> MinecraftAssets.remembered(pickedJar, MinecraftAssets.cacheFolder()).or(MinecraftAssets::installed));
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
            + "\n\nYou can locate the Minecraft game jar yourself, for example " + exampleJar()
            + ". Arbor reads only block models and textures from it and "
            + "remembers your choice.\n\nYou can also do this later from View > Locate Minecraft Jar.";
    Object[] choices = {"Locate Jar...", "Continue Without Textures"};
    int picked = JOptionPane.showOptionDialog(frame, dialogText(message), "Minecraft Textures Not Found",
            JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[0]);
    if (picked == 0) {
      locateMinecraftJar();
    }
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
      occlusionStale = true;
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
                    + "\n\nPick the game jar inside your Minecraft versions folder, for example " + exampleJar()
                    + "."),
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
            preferences.getBoolean(SIMPLE_VIEW_KEY, true),
            simple -> preferences.putBoolean(SIMPLE_VIEW_KEY, simple));
    settings.showContext(context);
    settings.onTreeSize(() -> treeSize);
    settings.onProfileShown(points -> redrawAfter(() -> {
      editingProfile = points;
      uploadEnvelope();
    }));
    if (splitScreen) {
      settings.showPages(shownPages);
    } else if (exportPane) {
      settings.showExport();
    } else {
      settings.showPages(null);
    }
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
    attachSettings(new SettingsPanel.Context(blockCatalog, Set.of()));
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
    if (viewFrame != null) {
      viewFrame.setTitle(sourceName + " - 3D view - Arbor");
    }
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
   * Builds the editor window, its File and View menus, and lays it out as one screen or split screen, whichever was
   * used last.
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
      occlusionStale = true;
    });
    panel = new ControlPanel(state, viewEdit, seed -> {
      editorSeed = seed;
      requestGeneration();
    }, () -> redrawAfter(this::frameTree));
    panel.showStats(stats);
    viewPanel.showOver(panel.viewControls());
    controls = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    controls.getVerticalScrollBar().setUnitIncrement(18);
    controls.setBorder(null);
    // Dragging stops before the view mode buttons' names are cut short, leaving room for the scroll bar; the edge
    // button still folds the panel away completely.
    int controlsMin = panel.getMinimumSize().width + UIManager.getInt("ScrollBar.width");
    controls.setMinimumSize(new Dimension(controlsMin, 0));
    controls.setPreferredSize(new Dimension(Math.max(280, controlsMin), 300));
    frame = new JFrame("Arbor");
    frame.setIconImages(com.formdev.flatlaf.extras.FlatSVGUtils.createWindowIconImages("/icons/arbor.svg"));
    frame.setLayout(new BorderLayout());
    files = new PresetFiles(frame, this);
    settingsHolder.setPreferredSize(new Dimension(SETTINGS_WIDTH, DEFAULT_HEIGHT));
    // Narrow enough for half a laptop screen; the tabs wrap and the rows inside wrap or shrink.
    settingsHolder.setMinimumSize(new Dimension(SETTINGS_MIN_WIDTH, 0));
    Preferences preferences = Preferences.userNodeForPackage(ViewerApp.class);
    String pages = preferences.get(SHOWN_PAGES_KEY, String.join(",", SettingsPanel.PAGES));
    shownPages.addAll(pages.isEmpty() ? List.of() : List.of(pages.split(",")));
    attachSettings(context);
    JMenuBar menuBar = files.menuBar();
    // Made here, after the theme, so it looks like the File menu.
    viewMenu = new JMenu("View");
    menuBar.add(viewMenu);
    viewMenu.setMnemonic(KeyEvent.VK_V);
    frame.setJMenuBar(menuBar);
    watchCurrentFile();
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
    // After the window is shown, so a split screen knows which screen the editor is on.
    layOut(preferences.getBoolean(SPLIT_SCREEN_KEY, false));
    statusChanged();
  }

  /**
   * Lays the editor out as one screen, with the 3D view between the controls and the settings, or as split screen,
   * with the 3D view in a window of its own, on another screen when there is one, and the controls beside the Tree,
   * Voxelizer, Decorators and Export pages. The controls always show in split screen; the pages each have a button.
   *
   * @param split Whether to split the screen.
   */
  private void layOut(boolean split) {
    splitScreen = split;
    Preferences.userNodeForPackage(ViewerApp.class).putBoolean(SPLIT_SCREEN_KEY, split);
    Container content = frame.getContentPane();
    content.removeAll();
    viewMenu.removeAll();
    ButtonGroup screens = new ButtonGroup();
    for (boolean splitItem : new boolean[]{false, true}) {
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(splitItem ? "Split Screen" : "Single Screen",
              splitItem == split);
      item.setToolTipText(splitItem ? "The 3D view in its own window, and every settings page side by side"
              : "The 3D view and the settings in this window");
      item.addActionListener(event -> {
        if (splitScreen != splitItem) {
          layOut(splitItem);
        }
      });
      screens.add(item);
      viewMenu.add(item);
    }
    viewMenu.addSeparator();
    if (split) {
      List<JComponent> buttons = new ArrayList<>();
      for (String page : SettingsPanel.PAGES) {
        ButtonModel shown = new JToggleButton.ToggleButtonModel();
        shown.setSelected(shownPages.contains(page));
        shown.addItemListener(event -> showPage(page, shown.isSelected()));
        buttons.add(new UiParts.EdgeButton(page, new FlatSVGIcon("icons/page-" + page.toLowerCase(Locale.ROOT)
                + ".svg"), true, shown));
        viewMenu.add(menuToggle("Show " + page, shown, 0));
      }
      content.add(controls, BorderLayout.WEST);
      content.add(settingsHolder, BorderLayout.CENTER);
      content.add(edgeStrip(false, buttons.toArray(JComponent[]::new)), BorderLayout.EAST);
      settingsPanel.showPages(shownPages);
      openViewWindow();
    } else {
      closeViewWindow();
      JSplitPane inner = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controls, viewPanel);
      inner.setResizeWeight(0);
      JSplitPane outer = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inner, settingsHolder);
      outer.setResizeWeight(1);
      // Each side panel folds away with a button on its window edge, as tool windows do in IntelliJ, so a narrow
      // window can still show the tree.
      ButtonModel showControls = UiParts.sideModel(inner, true, controls.getPreferredSize().width);
      // The right pane holds the settings or Export, one at a time, as tool windows sharing an edge do in IntelliJ:
      // a button opens its pane in place of the other, and clicking the lit one closes the pane.
      ButtonModel rightOpen = UiParts.sideModel(outer, false, SETTINGS_WIDTH);
      ButtonModel showSettings = new JToggleButton.ToggleButtonModel();
      ButtonModel showExport = new JToggleButton.ToggleButtonModel();
      Runnable lightUp = () -> {
        showSettings.setSelected(rightOpen.isSelected() && !exportPane);
        showExport.setSelected(rightOpen.isSelected() && exportPane);
      };
      rightOpen.addItemListener(event -> lightUp.run());
      showSettings.addActionListener(event -> showRight(rightOpen, false, showSettings.isSelected(), lightUp));
      showExport.addActionListener(event -> showRight(rightOpen, true, showExport.isSelected(), lightUp));
      lightUp.run();
      viewMenu.add(menuToggle("Show Controls", showControls, KeyEvent.VK_OPEN_BRACKET));
      viewMenu.add(menuToggle("Show Settings", showSettings, KeyEvent.VK_CLOSE_BRACKET));
      viewMenu.add(menuToggle("Show Export", showExport, 0));
      content.add(edgeStrip(true, new UiParts.EdgeButton("Controls", new FlatSVGIcon("icons/controls.svg"), false,
              showControls)), BorderLayout.WEST);
      content.add(outer, BorderLayout.CENTER);
      content.add(edgeStrip(false,
              new UiParts.EdgeButton("Settings", new FlatSVGIcon("icons/settings.svg"), true, showSettings),
              new UiParts.EdgeButton("Export", new FlatSVGIcon("icons/page-export.svg"), true, showExport)),
              BorderLayout.EAST);
      if (exportPane) {
        settingsPanel.showExport();
      } else {
        settingsPanel.showPages(null);
      }
      SwingUtilities.invokeLater(() -> {
        // Once laid out: the settings get their starting width and the controls column keeps its own, and the view
        // takes the rest. Left to the split panes, the settings got only what was left over.
        outer.setDividerLocation(Math.max(outer.getWidth() / 2,
                outer.getWidth() - outer.getDividerSize() - SETTINGS_WIDTH));
        inner.setDividerLocation(controls.getPreferredSize().width);
        controls.getViewport().setViewPosition(new java.awt.Point(0, 0));
      });
    }
    viewMenu.addSeparator();
    JMenuItem locate = new JMenuItem("Locate Minecraft Jar...");
    locate.addActionListener(event -> locateMinecraftJar());
    viewMenu.add(locate);
    content.revalidate();
    content.repaint();
  }

  /**
   * On one screen: opens the right pane on the settings or on Export, or closes it.
   *
   * @param rightOpen Whether the right pane is open.
   * @param export    Export rather than the settings.
   * @param open      Whether to open it on that, or else close the pane.
   * @param lightUp   Lights the edge button of whatever shows.
   */
  private void showRight(ButtonModel rightOpen, boolean export, boolean open, Runnable lightUp) {
    if (open) {
      exportPane = export;
      if (export) {
        settingsPanel.showExport();
      } else {
        settingsPanel.showPages(null);
      }
    }
    rightOpen.setSelected(open);
    lightUp.run();
  }

  /**
   * Shows or hides one settings page in split screen, and remembers the choice.
   *
   * @param page Page name.
   * @param show Whether to show it.
   */
  private void showPage(String page, boolean show) {
    if (show) {
      shownPages.add(page);
    } else {
      shownPages.remove(page);
    }
    Preferences.userNodeForPackage(ViewerApp.class).put(SHOWN_PAGES_KEY, String.join(",", shownPages));
    settingsPanel.showPages(shownPages);
  }

  /**
   * Gives the 3D view a window of its own: filling another screen when there is one, otherwise the right half of
   * this one. Closing it goes back to one screen.
   */
  private void openViewWindow() {
    viewFrame = new JFrame();
    viewFrame.setIconImages(frame.getIconImages());
    viewFrame.add(viewPanel, BorderLayout.CENTER);
    viewFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    viewFrame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent event) {
        layOut(false);
      }
    });
    GraphicsDevice editorScreen = frame.getGraphicsConfiguration().getDevice();
    GraphicsConfiguration target = frame.getGraphicsConfiguration();
    for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      if (device != editorScreen) {
        target = device.getDefaultConfiguration();
        break;
      }
    }
    Rectangle bounds = target.getBounds();
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(target);
    Rectangle usable = new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
            bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
    boolean ownScreen = target.getDevice() != editorScreen;
    if (!ownScreen) {
      usable.x += usable.width / 2;
      usable.width -= usable.width / 2;
    }
    viewFrame.setBounds(usable);
    statusChanged();
    viewFrame.setVisible(true);
    if (ownScreen) {
      viewFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }
  }

  private void closeViewWindow() {
    if (viewFrame == null) {
      return;
    }
    viewFrame.getContentPane().remove(viewPanel);
    viewFrame.dispose();
    viewFrame = null;
  }

  /**
   * A View menu checkbox that shares its state with an edge button.
   *
   * @param text  Menu text.
   * @param model Shared state.
   * @param key   Key pressed with Ctrl to toggle it, or 0 for none.
   */
  private static JCheckBoxMenuItem menuToggle(String text, ButtonModel model, int key) {
    JCheckBoxMenuItem item = new JCheckBoxMenuItem(text);
    item.setModel(model);
    if (key != 0) {
      item.setAccelerator(KeyStroke.getKeyStroke(key, java.awt.event.InputEvent.CTRL_DOWN_MASK));
    }
    return item;
  }

  /**
   * A strip down one edge of the window holding tool window buttons, as in IntelliJ.
   *
   * @param left    Whether the strip is on the left edge.
   * @param buttons The buttons, top to bottom.
   */
  private static JComponent edgeStrip(boolean left, JComponent... buttons) {
    JPanel strip = new JPanel();
    strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
    strip.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, left ? 0 : 1, 0, left ? 1 : 0, UIManager.getColor("Component.borderColor")),
            BorderFactory.createEmptyBorder(4, 2, 4, 2)));
    for (JComponent button : buttons) {
      button.setAlignmentX(Component.CENTER_ALIGNMENT);
      strip.add(button);
      strip.add(Box.createVerticalStrut(4));
    }
    return strip;
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
    return new SettingsPanel.Context(blockCatalog,
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
    meshOccluders = textured == null ? null : textured.occluders();
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
    sceneLines.upload(grid);
    // Wide enough to hold the longest shadow the angled sun can cast.
    groundRenderer.upload(ground, extent + 1.3f * (bounds.max.y - bounds.min.y));

    forceMarkers = ForceMarkers.build(preset.forceList(), tree.seed(), bounds.min, bounds.max);
    forceLines.upload(forceMarkers.lines());
    uploadEnvelope();

    LineRenderer.Lines branches = new LineRenderer.Lines();
    TreeMap<Integer, ViewPanel.Legend> legend = new TreeMap<>();
    for (Branch branch : tree.skeleton().branches()) {
      float[] color = LEVEL_COLORS[branch.level() % LEVEL_COLORS.length];
      legend.putIfAbsent(branch.level(), new ViewPanel.Legend(branch.level(), branch.levelName(), color));
      for (int i = 1; i < branch.nodes().size(); i++) {
        Vec3 a = branch.nodes().get(i - 1).position();
        Vec3 b = branch.nodes().get(i).position();
        branches.add((float) a.x(), (float) a.y(), (float) a.z(), (float) b.x(), (float) b.y(), (float) b.z(),
                color[0], color[1], color[2]);
      }
    }
    skeletonLines.upload(branches);
    levelLegend = List.copyOf(legend.values());
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
    occlusionStale = true;
    frameStale = true;
  }

  /**
   * The up-to-date occupancy for ambient occlusion, in every lighting mode. It is rebuilt when the tree or what is
   * shown changes, so hidden shapes darken nothing.
   *
   * @return the occupancy.
   */
  private OcclusionVolume currentOcclusion() {
    if (occlusionStale) {
      occlusionStale = false;
      occlusion.upload(OcclusionVolume.build(tree.shapes(), state::isVisible));
    }
    return occlusion;
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
    if (options.variations() > 0 || !options.compare().isEmpty()) {
      renderVariations();
      return;
    }
    if (options.closeup() != null) {
      renderCloseup();
      return;
    }
    if (!options.views()) {
      renderScene(DEFAULT_WIDTH, DEFAULT_HEIGHT);
      Screenshot.write(options.screenshot(),
              keyed(Screenshot.image(target.readPixels(), target.width(), target.height()), true));
      return;
    }
    // Only the tree, framed once for every view so sizes compare across them: these are for matching a photo, where
    // the sun and wind markers and the ground would only hide and shrink it. Each tile is then cropped to the tree.
    state.showForces = false;
    state.showGround = false;
    frameTree();
    List<BufferedImage> tiles = new ArrayList<>();
    for (float[] angles : SHEET_ANGLES) {
      camera.turnTo(angles[0], angles[1]);
      renderScene(DEFAULT_WIDTH / sheetDivisor(), DEFAULT_HEIGHT / sheetDivisor());
      tiles.add(Screenshot.image(target.readPixels(), target.width(), target.height()));
    }
    Screenshot.write(options.screenshot(), keyed(Screenshot.sheet(Screenshot.cropToContent(tiles, CROP_MARGIN),
            SHEET_LABELS), true));
  }

  /**
   * One part of the tree, filling the image: the --closeup fractions pick a window of the tree as seen from the
   * chosen view, bottom to top and left to right. In the top view, bottom to top runs from the near side to the far
   * side.
   */
  private void renderCloseup() {
    state.showForces = false;
    state.showGround = false;
    float[] angles = SHEET_ANGLES.get(options.view());
    double yaw = Math.toRadians(angles[0]);
    boolean top = options.view() == ViewerOptions.VIEWS.indexOf("top");
    Vector3f right = new Vector3f((float) Math.cos(yaw), 0, (float) -Math.sin(yaw));
    Vector3f up = top ? new Vector3f((float) -Math.sin(yaw), 0, (float) -Math.cos(yaw)) : new Vector3f(0, 1, 0);
    // The blocks alone, so the fractions match what render's views show; the full bounds also hold invisible leaf
    // density and skeleton.
    Bounds bounds = Bounds.of(tree);
    if (!tree.shapes().isEmpty()) {
      int[] low = tree.shapes().min();
      int[] high = tree.shapes().max();
      bounds.min.set(low[0], low[1], low[2]);
      bounds.max.set(high[0] + 1, high[1] + 1, high[2] + 1);
    }
    Vector3f center = new Vector3f(bounds.min).add(bounds.max).mul(0.5f);
    float rightLow = Float.MAX_VALUE;
    float rightHigh = -Float.MAX_VALUE;
    float upLow = Float.MAX_VALUE;
    float upHigh = -Float.MAX_VALUE;
    for (int corner = 0; corner < 8; corner++) {
      Vector3f point = new Vector3f((corner & 1) == 0 ? bounds.min.x : bounds.max.x,
              (corner & 2) == 0 ? bounds.min.y : bounds.max.y, (corner & 4) == 0 ? bounds.min.z : bounds.max.z)
              .sub(center);
      rightLow = Math.min(rightLow, point.dot(right));
      rightHigh = Math.max(rightHigh, point.dot(right));
      upLow = Math.min(upLow, point.dot(up));
      upHigh = Math.max(upHigh, point.dot(up));
    }
    double[] region = options.closeup();
    float halfWidth = (float) (region[3] - region[2]) * (rightHigh - rightLow) / 2;
    float halfHeight = (float) (region[1] - region[0]) * (upHigh - upLow) / 2;
    Vector3f focus = new Vector3f(center)
            .add(new Vector3f(right).mul(rightLow + (float) (region[2] + region[3]) / 2 * (rightHigh - rightLow)))
            .add(new Vector3f(up).mul(upLow + (float) (region[0] + region[1]) / 2 * (upHigh - upLow)));
    // frame() only uses the box's center and diagonal, so a flat box facing any way frames the window.
    camera.frame(new Vector3f(focus).sub(halfWidth, halfHeight, 0), new Vector3f(focus).add(halfWidth, halfHeight, 0));
    camera.turnTo(angles[0], angles[1]);
    renderScene(DEFAULT_WIDTH / sheetDivisor(), DEFAULT_HEIGHT / sheetDivisor());
    BufferedImage image = Screenshot.image(target.readPixels(), target.width(), target.height());
    Screenshot.write(options.screenshot(), keyed(Screenshot.cropToContent(List.of(image), CROP_MARGIN).get(0), true));
  }

  /**
   * A wireframe screenshot with a key under it: each branch level's color and name, and with {@code counts} how many
   * branches of that level the tree has. Any other screenshot is returned as it is.
   *
   * @param image  The finished image.
   * @param counts Whether to add branch counts; a sheet of several trees has no single count.
   */
  private BufferedImage keyed(BufferedImage image, boolean counts) {
    if (!options.wireframe()) {
      return image;
    }
    List<Screenshot.Key> keys = new ArrayList<>();
    for (int i = 0; i < preset.levels().size(); i++) {
      int level = i;
      long branches = tree.skeleton().branches().stream().filter(branch -> branch.level() == level).count();
      keys.add(new Screenshot.Key(preset.levels().get(i).name() + (counts ? ": " + branches : ""),
              LEVEL_COLORS[i % LEVEL_COLORS.length]));
    }
    return Screenshot.withKey(image, keys);
  }

  /**
   * How much smaller than a full screenshot each sheet tile is drawn: half size normally, full size with
   * --high-detail, for about four times the pixels when a half-size tree is too small to read.
   */
  private int sheetDivisor() {
    return options.highDetail() ? 1 : 2;
  }

  /**
   * Several trees side by side from the front, framed once around all of them so their sizes compare too: the same
   * preset on several seeds, which shows how much the trees differ, or the --compare designs on one seed, which shows
   * what a setting does.
   */
  private void renderVariations() {
    List<GeneratedTree> trees = new ArrayList<>(List.of(tree));
    List<String> labels = new ArrayList<>(List.of(options.compare().isEmpty() ? "Seed " + tree.seed() : preset.name()));
    for (int i = 1; i < options.variations(); i++) {
      trees.add(TreeGenerator.generate(preset, options.seed() + i));
      labels.add("Seed " + (options.seed() + i));
    }
    for (Path other : options.compare()) {
      SpeciesPreset design = PresetLoader.load(other);
      trees.add(TreeGenerator.generate(design, options.seed()));
      labels.add(design.name());
    }
    Bounds all = Bounds.of(trees.get(0));
    for (GeneratedTree other : trees) {
      Bounds bounds = Bounds.of(other);
      all.include(bounds.min.x, bounds.min.y, bounds.min.z);
      all.include(bounds.max.x, bounds.max.y, bounds.max.z);
    }
    state.showForces = false;
    state.showGround = false;
    camera.frame(all.min, all.max);
    camera.turnTo(0, 0);
    List<BufferedImage> tiles = new ArrayList<>();
    for (GeneratedTree variation : trees) {
      tree = variation;
      uploadTree(ShapeMesher.mesh(tree.shapes()),
              texturedMesher == null ? null : texturedMesher.mesh(tree.shapes(), tree.blocks()));
      shadowStale = true;
      occlusionStale = true;
      renderScene(DEFAULT_WIDTH / sheetDivisor(), DEFAULT_HEIGHT / sheetDivisor());
      tiles.add(Screenshot.image(target.readPixels(), target.width(), target.height()));
    }
    Screenshot.write(options.screenshot(), keyed(Screenshot.sheet(Screenshot.cropToContent(tiles, CROP_MARGIN), labels),
            false));
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
        reshadeIfShownChanged();
        if (frameStale && viewWidth > 0 && viewHeight > 0) {
          frameStale = false;
          renderScene(viewWidth, viewHeight);
          int bytes = viewWidth * viewHeight * 4;
          if (framePixels == null || framePixels.capacity() < bytes) {
            framePixels = BufferUtils.createByteBuffer(bytes);
          }
          boolean split = state.mode == ViewerState.Mode.SPLIT;
          viewPanel.present(target.readPixels(framePixels), viewWidth, viewHeight,
                  split || state.drawsSkeleton() ? levelLegend : List.of(), split);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Rebuilds the textured mesh when solid blocks or leaves are shown or hidden: its corner shading counts only the
   * blocks shown, so hidden leaves leave no dark patches on the wood. Runs on the render thread.
   */
  private void reshadeIfShownChanged() {
    if (texturedMesher == null || tree == null || meshOccluders == null) {
      return;
    }
    Set<ShapeClass> shown = EnumSet.noneOf(ShapeClass.class);
    TexturedMesher.OCCLUDERS.stream().filter(state::isVisible).forEach(shown::add);
    if (!shown.equals(meshOccluders)) {
      worker.useOccluders(shown);
      uploadTree(ShapeMesher.mesh(tree.shapes()), texturedMesher.mesh(tree.shapes(), tree.blocks(), shown));
      frameStale = true;
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
    // Branch view shows plain lines over the grid, without lighting or the white ground.
    ShadowMap shadows = state.drawsShapes() ? currentShadows() : null;
    target.ensureSize(width, height);
    target.bind();
    glEnable(GL_DEPTH_TEST);
    glClearColor(0.09f, 0.10f, 0.12f, 1);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    camera.view(view);
    if (state.mode == ViewerState.Mode.SPLIT) {
      int leftWidth = Math.max(1, (width - SPLIT_DIVIDER) / 2);
      int rightStart = leftWidth + SPLIT_DIVIDER;
      // Blocks first: the density cloud ends smoothing, since it reads depth from the resolved frame.
      usePane(rightStart, Math.max(1, width - rightStart), height);
      drawBlocks(shadows, false, false);
      usePane(0, leftWidth, height);
      drawGrid();
      drawSkeleton();
      drawEnvelope();
      drawDensity();
      drawForces();
      glEnable(GL_SCISSOR_TEST);
      glScissor(leftWidth, 0, SPLIT_DIVIDER, height);
      glClearColor(0.42f, 0.45f, 0.5f, 1);
      glClear(GL_COLOR_BUFFER_BIT);
      glDisable(GL_SCISSOR_TEST);
    } else {
      usePane(0, width, height);
      drawBlocks(shadows, state.drawsSkeleton(), true);
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
   * Draws the ground grid and the axes at the origin, scaled with the camera's distance so they stay one size on
   * screen.
   */
  private void drawGrid() {
    sceneLines.draw(viewProjection);
    float length = camera.eye().length() * AXIS_SCREEN_SIZE;
    axisLines.draw(new Matrix4f(viewProjection).scale(length));
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
   * Rebuilds the crown envelope's cage: from the radius being edited when there is one, otherwise from the preset
   * when it uses the envelope.
   */
  private void uploadEnvelope() {
    // The generator's own view of the preset: Simple sliders and this seed's size variety applied.
    SpeciesPreset grown = SimpleAdjuster.apply(preset).forSeed(tree.seed());
    treeSize = tree.skeleton().treeHeight() / grown.tree().sizeScale();
    ShapeControl control = grown.shapeControl();
    CrownEnvelope envelope = control.enabled() ? control.envelope(tree.seed()) : null;
    if (editingProfile != null) {
      double[] pairs = Arrays.stream(editingProfile).flatMapToDouble(Arrays::stream).toArray();
      try {
        Curve radius = Curve.of(pairs).scaled(preset.simple().crownWidth());
        envelope = new CrownEnvelope(control.crownBase(), radius, control.relativeRadius());
      } catch (IllegalArgumentException e) {
        // Points that do not make a curve yet; keep the preset's cage.
      }
    }
    LineRenderer.Lines cage = new LineRenderer.Lines();
    if (envelope != null) {
      for (double[] s : EnvelopeOutline.segments(envelope, tree.skeleton(), grown.tree().sizeScale())) {
        cage.add((float) s[0], (float) s[1], (float) s[2], (float) s[3], (float) s[4], (float) s[5],
                0.55f, 0.75f, 0.95f);
      }
    }
    envelopeLines.upload(cage);
  }

  /**
   * Draws the crown envelope's cage, when shown or while its radius is being edited, hidden behind the blocks.
   */
  private void drawEnvelope() {
    if (state.showEnvelope || editingProfile != null) {
      envelopeLines.draw(viewProjection);
    }
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
   * @param withDensity  Whether the leaf density cloud belongs in this pane; split view shows it with the wireframe.
   */
  private void drawBlocks(ShadowMap shadows, boolean withSkeleton, boolean withDensity) {
    if (!state.showGround) {
      // The screenshot sheets leave the ground out, so they can be cropped down to the tree.
    } else if (shadows != null) {
      groundRenderer.draw(viewProjection, shadows, currentOcclusion());
    } else {
      drawGrid();
    }
    if (state.drawsShapes()) {
      if (state.useTextures && texturedMesh != null) {
        texturedRenderer.draw(viewProjection, state, shadows, currentOcclusion());
      } else {
        meshes.draw(viewProjection, state, shadows, currentOcclusion());
      }
    }
    if (state.drawsVoxelGrid()) {
      updateVoxelLines();
      voxelLines.draw(viewProjection);
    }
    if (withSkeleton) {
      drawSkeleton();
    }
    drawEnvelope();
    if (withDensity) {
      drawDensity();
    }
    drawForces();
  }

  /**
   * Draws the leaf density cloud, when shown, hidden behind whatever is already drawn.
   */
  private void drawDensity() {
    if (state.showDensity && density.hasVolume()) {
      target.resolve();
      target.detachDepth();
      density.render(view, projection, target.depthTexture(), state.densityThreshold, state.densityOpacity);
      target.attachDepth();
    }
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
    for (AutoCloseable resource : new AutoCloseable[]{occlusion, shadowMap, groundRenderer, density, texturedRenderer, meshes, forceLines, envelopeLines, voxelLines, skeletonLines, sceneLines, axisLines, target}) {
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
