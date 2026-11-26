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
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.jeffch.arbor.math.Vec3;
import com.jeffch.arbor.preset.BlockRole;
import com.jeffch.arbor.preset.LevelPreset;
import com.jeffch.arbor.preset.PresetDocument;
import com.jeffch.arbor.preset.SettingField;
import com.jeffch.arbor.shape.TreeBlocks;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.function.*;

import static com.jeffch.arbor.viewer.UiParts.*;

/**
 * Live editors for every preset setting, split into tree, voxelizer and export tabs. Runs on the Swing thread.
 */
final class SettingsPanel extends JPanel {
  private static final double NEW_LIMIT = 64;
  private static final double PRECISION = 10_000;
  private static final Color KEY_COLOR = UiParts.MUTED;
  private static final Color ERROR_COLOR = UiParts.ERROR;
  private static final Color WARNING_COLOR = UiParts.WARNING;
  private static final Color DROP_COLOR = UiParts.ACCENT;
  private static final int DRAG_START_PIXELS = 5;
  private static final int LEAF_INDENT = 22;
  private static final int ICON_SIZE = 14;
  /**
   * An L-shaped line down the left of an open variance pane, so its rows read as part of the setting above.
   */
  private static final javax.swing.border.Border BRACKET = new javax.swing.border.AbstractBorder() {
    @Override
    public Insets getBorderInsets(Component component) {
      return new Insets(2, 16, 8, 0);
    }

    @Override
    public void paintBorder(Component component, Graphics g, int x, int y, int width, int height) {
      g.setColor(UiParts.ACCENT);
      g.fillRect(x + 5, y, 2, height - 4);
      g.fillRect(x + 5, y + height - 4, 9, 2);
    }
  };
  /**
   * Each decorator's title and its key under {@code decorators}, in display order.
   */
  private static final Map<String, String> DECORATORS = new LinkedHashMap<>();
  private static final String CROWN_RADIUS = "shapeControl.radius";
  /**
   * Level settings whose profile runs along the branch itself; the rest run along the parent it grows on.
   */
  private static final String ALONG_OWN_BRANCH = "levels\\[\\d+]\\.(shape|noise|path|forces|jink)\\..*";
  private static final String RELATIVE_RADIUS = "shapeControl.relativeRadius";
  private static final List<ProfileShape> PROFILE_SHAPES = List.of(
          new ProfileShape("Flat", "No change anywhere", s -> null),
          new ProfileShape("Taper", "Full at the base, less toward the tip", s -> new double[][]{{0, 1}, {1, 1 - s}}),
          new ProfileShape("Grow", "Less at the base, full at the tip", s -> new double[][]{{0, 1 - s}, {1, 1}}),
          new ProfileShape("Bulge", "Full in the middle, less at both ends",
                  s -> new double[][]{{0, 1 - s}, {0.5, 1}, {1, 1 - s}}),
          new ProfileShape("Pinch", "Less in the middle, full at both ends",
                  s -> new double[][]{{0, 1}, {0.5, 1 - s}, {1, 1}}));

  static {
    DECORATORS.put("Pinecones", "pinecones");
    DECORATORS.put("Vines", "vines");
    DECORATORS.put("Moss", "moss");
  }

  private final PresetDocument document;
  private final Runnable regenerate;
  private final Runnable statusChanged;
  private final ExportControls exportControls;
  private final BlockRuleEditor ruleEditor;
  private final Consumer<String> startFrom;
  private final Runnable newRandom;
  private final Consumer<Boolean> viewChosen;
  private final JLabel status = new JLabel();
  private final JTextArea problem = wrappedText(ERROR_COLOR);
  private final JTextArea unknownBlocks = wrappedText(WARNING_COLOR);
  private final Body treeBody = new Body();
  private final Body voxelBody = new Body();
  private final JScrollPane treeScroll = new JScrollPane(treeBody);
  private final JScrollPane voxelScroll = new JScrollPane(voxelBody);
  private final Body decoratorBody = new Body();
  private final JScrollPane decoratorScroll = new JScrollPane(decoratorBody);
  private final Set<String> expanded = new HashSet<>();
  private final List<Runnable> refreshers = new ArrayList<>();
  private final List<LevelRow> levelRows = new ArrayList<>();
  private Consumer<double[][]> profileShown = points -> {
  };
  private DoubleSupplier treeSize = () -> 0;
  private boolean simpleView;
  private BlockCatalog catalog;

  /**
   * @param document      Preset being edited.
   * @param regenerate    Asks for a new tree after an edit.
   * @param statusChanged Told whenever the saved state or validity may have changed.
   */
  SettingsPanel(PresetDocument document, Runnable regenerate, Runnable statusChanged) {
    this(document, regenerate, statusChanged, name -> {
    }, () -> {
    }, false, view -> {
    });
  }

  /**
   * @param document      Preset being edited.
   * @param regenerate    Asks for a new tree after an edit.
   * @param statusChanged Told whenever the saved state or validity may have changed.
   * @param startFrom     Asked to start over from a bundled preset.
   * @param newRandom     Asked to show a new random tree.
   * @param startSimple   Whether the Tree tab opens in the Simple view.
   * @param viewChosen    Told when the user picks a view; true is Simple.
   */
  SettingsPanel(PresetDocument document, Runnable regenerate, Runnable statusChanged, Consumer<String> startFrom,
                Runnable newRandom, boolean startSimple, Consumer<Boolean> viewChosen) {
    super(new BorderLayout());
    this.document = document;
    this.regenerate = regenerate;
    this.statusChanged = statusChanged;
    this.startFrom = startFrom;
    this.newRandom = newRandom;
    this.viewChosen = viewChosen;
    simpleView = startSimple;
    exportControls = new ExportControls(document);
    ruleEditor = new BlockRuleEditor(document, (role, current, picked) -> blockPicker(role, false, current, picked),
            path -> weightsControl(document.field(path)), this::structuralLater, this::edited);
    // Revert is in the File menu.
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
    buttons.add(status);
    JPanel top = new JPanel(new BorderLayout());
    top.add(buttons, BorderLayout.NORTH);
    top.add(problem, BorderLayout.CENTER);
    add(top, BorderLayout.NORTH);
    JTabbedPane tabs = new JTabbedPane();
    for (JScrollPane scroll : List.of(treeScroll, voxelScroll, decoratorScroll)) {
      scroll.getVerticalScrollBar().setUnitIncrement(18);
      scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }
    tabs.addTab("Tree", treeScroll);
    tabs.addTab("Voxelizer", voxelScroll);
    tabs.addTab("Decorators", decoratorScroll);
    tabs.addTab("Export", exportControls);
    add(tabs, BorderLayout.CENTER);
    rebuild();
  }

  /**
   * The position in the list of a plant layer or force section, or -1 for any other section.
   *
   * @param section     Section to look up.
   * @param titlePrefix Start of the titles of that kind of section.
   */
  private static int listIndex(PresetDocument.Section section, String titlePrefix) {
    if (!section.title().startsWith(titlePrefix) || section.groups().isEmpty()
            || section.groups().get(0).fields().isEmpty()) {
      return -1;
    }
    String path = section.groups().get(0).fields().get(0).path();
    return Integer.parseInt(path.substring(path.indexOf('[') + 1, path.indexOf(']')));
  }

  /**
   * A number box whose arrows move between neat values. It accepts anything from min to max when typed.
   *
   * @param value    Starting value.
   * @param min      Lowest allowed value.
   * @param max      Highest allowed value.
   * @param scale    Steps the arrows move between.
   * @param whole    Whether only whole numbers are allowed.
   * @param onChange Told each new value.
   */
  static JSpinner spinner(double value, double min, double max, SliderScale scale, boolean whole,
                          DoubleConsumer onChange) {
    Double low = Double.isFinite(min) ? Double.valueOf(min) : null;
    Double high = Double.isFinite(max) ? Double.valueOf(max) : null;
    double shown = low != null ? Math.max(low, value) : value;
    shown = high != null ? Math.min(high, shown) : shown;
    SpinnerNumberModel model = new SpinnerNumberModel(Double.valueOf(shown), low, high, Double.valueOf(scale.step())) {
      @Override
      public Object getNextValue() {
        double current = getNumber().doubleValue();
        return high != null && current >= high ? null
                : Double.valueOf(high == null ? scale.stepUp(current) : Math.min(high, scale.stepUp(current)));
      }

      @Override
      public Object getPreviousValue() {
        double current = getNumber().doubleValue();
        return low != null && current <= low ? null
                : Double.valueOf(low == null ? scale.stepDown(current) : Math.max(low, scale.stepDown(current)));
      }
    };
    JSpinner spinner = new JSpinner(model);
    spinner.setEditor(new JSpinner.NumberEditor(spinner, whole ? "0" : "0.####"));
    spinner.addChangeListener(event -> onChange.accept(((Number) spinner.getValue()).doubleValue()));
    return spinner;
  }

  /**
   * A slider beside the spinner, then three equal slots for the reset, randomness and profile buttons. A slot stays
   * empty when the setting has no such button, so the number boxes of every row line up. A guard stops the slider's
   * coarser steps rewriting a typed value.
   *
   * @param spinner Number box the slider follows.
   * @param scale   Maps slider positions to values.
   * @param random  The dice button, or null for a setting without variance.
   * @param profile The chart button, or null for a setting without a profile.
   * @param reset   The reset button, or null to leave its slot empty.
   */
  private static JPanel withSlider(JSpinner spinner, SliderScale scale, JToggleButton random, JToggleButton profile,
                                   JButton reset) {
    double current = ((Number) spinner.getValue()).doubleValue();
    JSlider slider = new JSlider(0, SliderScale.TICKS, scale.tickOf(current));
    Hashtable<Integer, JLabel> labels = new Hashtable<Integer, JLabel>();
    DecimalFormat format = new DecimalFormat("0.####");
    for (double mark : scale.marks()) {
      JLabel label = new JLabel(format.format(mark));
      label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() - 2));
      labels.put(scale.tickOf(mark), label);
    }
    slider.setLabelTable(labels);
    slider.setPaintLabels(true);
    boolean[] syncing = {false};
    slider.addChangeListener(event -> {
      if (!syncing[0]) {
        syncing[0] = true;
        spinner.setValue(scale.valueAt(slider.getValue()));
        syncing[0] = false;
      }
    });
    spinner.addChangeListener(event -> {
      if (!syncing[0]) {
        syncing[0] = true;
        slider.setValue(scale.tickOf(((Number) spinner.getValue()).doubleValue()));
        syncing[0] = false;
      }
    });
    spinner.setPreferredSize(new Dimension(90, spinner.getPreferredSize().height));
    JPanel row = new JPanel(new BorderLayout(4, 0));
    row.add(slider, BorderLayout.CENTER);
    // Kept at the top so the taller labelled slider does not stretch the number box.
    JPanel spinnerHolder = new JPanel(new BorderLayout());
    // The slots are as wide as an icon button and as tall as the number box.
    JPanel buttons = new JPanel(new GridLayout(1, 3, 2, 0));
    for (AbstractButton button : new AbstractButton[]{reset, random, profile}) {
      buttons.add(button != null ? button : new JPanel());
    }
    int slot = small(new JButton(new FlatSVGIcon("icons/dice.svg", ICON_SIZE, ICON_SIZE))).getPreferredSize().width;
    buttons.setPreferredSize(new Dimension(3 * slot + 4, spinner.getPreferredSize().height));
    JPanel boxAndButtons = new JPanel(new BorderLayout(2, 0));
    boxAndButtons.add(spinner, BorderLayout.CENTER);
    boxAndButtons.add(buttons, BorderLayout.EAST);
    spinnerHolder.add(boxAndButtons, BorderLayout.NORTH);
    row.add(spinnerHolder, BorderLayout.EAST);
    return row;
  }

  /**
   * Applies typed text on Enter or when the field loses focus.
   *
   * @param text   Text field to watch.
   * @param commit Receives the text.
   */
  private static void onCommit(JTextField text, Consumer<String> commit) {
    text.addActionListener(event -> commit.accept(text.getText()));
    text.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent event) {
        commit.accept(text.getText());
      }
    });
  }

  private static double number(JSpinner spinner) {
    return ((Number) spinner.getValue()).doubleValue();
  }

  /**
   * A label with its JSON key shown faintly beside it.
   *
   * @param name Human name.
   * @param key  JSON key.
   */
  private static JPanel plainName(String name, String key) {
    JLabel keyLabel = new JLabel("(" + key + ")");
    keyLabel.setForeground(KEY_COLOR);
    return row(new JLabel(name), keyLabel);
  }

  /**
   * Wrapping read-only text with a little padding.
   *
   * @param color Text color.
   */
  private static JTextArea wrappedText(Color color) {
    JTextArea area = UiParts.textBlock(color);
    area.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
    return area;
  }

  /**
   * Keeps saved values readable instead of writing noise such as 0.34999999.
   *
   * @param value Value to round.
   */
  private static double rounded(double value) {
    return Math.round(value * PRECISION) / PRECISION;
  }

  /**
   * Enables or disables a control and everything inside it.
   *
   * @param component Control to change.
   * @param enabled   Whether it can be used.
   */
  private static void enableAll(Component component, boolean enabled) {
    component.setEnabled(enabled);
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        enableAll(child, enabled);
      }
    }
  }

  /**
   * Waits for a running export to stop cleanly.
   */
  void close() throws InterruptedException {
    exportControls.close();
  }

  boolean exporting() {
    return exportControls.running();
  }

  /**
   * Discards edits and redraws the settings.
   */
  void revert() {
    structural(document::revert);
  }

  /**
   * Shows what the newest tree received.
   *
   * @param context What the newest tree received.
   */
  void showContext(Context context) {
    boolean first = catalog == null && context.catalog() != null;
    catalog = context.catalog();
    // Only a problem is worth the space: the blocks in use are already in the settings below.
    unknownBlocks.setText(context.unknownBlocks().isEmpty() ? ""
            : "Unknown blocks: " + String.join(", ", context.unknownBlocks()));
    unknownBlocks.setVisible(!unknownBlocks.getText().isEmpty());
    if (first) {
      rebuild();
    }
  }

  /**
   * Rebuilds after the document changed outside this panel.
   */
  void reloaded() {
    rebuild();
  }

  /**
   * Updates save state and error text.
   */
  void refresh() {
    boolean hasFile = document.file() != null;
    status.setText(document.dirty() ? "Unsaved changes" : hasFile ? "Saved" : "Not saved to a file yet");
    String text = document.error();
    if (text == null && document.changedOnDisk()) {
      text = "The file changed on disk. Revert to load it.";
    }
    problem.setForeground(document.error() != null ? ERROR_COLOR : WARNING_COLOR);
    problem.setText(text == null ? "" : text);
    problem.setVisible(text != null);
    exportControls.refresh();
    refreshers.forEach(Runnable::run);
    statusChanged.run();
  }

  /**
   * After a value changes: updates the status and, when the preset is still valid, asks for a new tree.
   */
  private void edited() {
    refresh();
    if (document.error() == null) {
      regenerate.run();
    }
  }

  /**
   * Runs an edit that changes which rows exist, then redraws them.
   *
   * @param edit Edit to run.
   */
  private void structural(Runnable edit) {
    edit.run();
    rebuild();
    if (document.error() == null) {
      regenerate.run();
    }
  }

  /**
   * Runs after the current event, so the control that fired it is not removed while still handling it.
   *
   * @param edit Edit to run.
   */
  private void structuralLater(Runnable edit) {
    SwingUtilities.invokeLater(() -> structural(edit));
  }

  /**
   * Rebuilds every tab from the document, keeping each tab's scroll position and open sections.
   */
  private void rebuild() {
    int treeY = treeScroll.getVerticalScrollBar().getValue();
    int voxelY = voxelScroll.getVerticalScrollBar().getValue();
    int decoratorY = decoratorScroll.getVerticalScrollBar().getValue();
    refreshers.clear();
    levelRows.clear();
    treeBody.removeAll();
    voxelBody.removeAll();
    decoratorBody.removeAll();
    if (document.preset().shapes().survival()) {
      JTextArea notice = wrappedText(KEY_COLOR);
      notice.setText("Survival trees have no decorators. Any decorators are kept for Advanced mode.");
      notice.setAlignmentX(LEFT_ALIGNMENT);
      decoratorBody.add(notice);
    } else {
      addSections(decoratorBody, document.decoratorSections());
      decoratorBody.add(addDecoratorRow());
    }
    treeBody.add(viewSwitch());
    if (simpleView) {
      treeBody.add(new SimpleTreePanel(document, this::edited, startFrom, newRandom));
    } else {
      if (document.simpleActive()) {
        JButton reset = new JButton("Reset sliders");
        reset.addActionListener(event -> structuralLater(document::resetSimple));
        treeBody.add(row(new JLabel("Simple sliders are adjusting this tree."), reset));
      }
      addLevelControls(treeBody);
      addSections(treeBody, document.sections(false));
    }
    voxelBody.add(modeSwitch());
    unknownBlocks.setAlignmentX(LEFT_ALIGNMENT);
    unknownBlocks.setVisible(!unknownBlocks.getText().isEmpty());
    voxelBody.add(unknownBlocks);
    addSections(voxelBody, document.sections(true));
    if (!document.preset().shapes().survival()) {
      JButton addLayer = new JButton("Add plant layer");
      addLayer.addActionListener(event -> structuralLater(document::addLayer));
      voxelBody.add(row(addLayer));
    }
    refresh();
    treeBody.revalidate();
    voxelBody.revalidate();
    decoratorBody.revalidate();
    treeBody.repaint();
    voxelBody.repaint();
    decoratorBody.repaint();
    SwingUtilities.invokeLater(() -> {
      treeScroll.getVerticalScrollBar().setValue(treeY);
      voxelScroll.getVerticalScrollBar().setValue(voxelY);
      decoratorScroll.getVerticalScrollBar().setValue(decoratorY);
    });
  }

  /**
   * Switches the Tree tab's view. This only changes what is shown, so the preset and tree stay as they are.
   *
   * @param simple Whether to show the Simple view rather than the Advanced one.
   */
  void showSimple(boolean simple) {
    simpleView = simple;
    viewChosen.accept(simple);
    rebuild();
  }

  /**
   * The Simple and Advanced choice at the top of the Tree tab.
   */
  private JPanel viewSwitch() {
    JRadioButton simple = new JRadioButton("Simple", simpleView);
    JRadioButton advanced = new JRadioButton("Advanced", !simpleView);
    simple.setToolTipText("A few whole-tree sliders");
    advanced.setToolTipText("Every setting for every branch level");
    ButtonGroup group = new ButtonGroup();
    group.add(simple);
    group.add(advanced);
    simple.addActionListener(event -> {
      if (!simpleView) {
        SwingUtilities.invokeLater(() -> showSimple(true));
      }
    });
    advanced.addActionListener(event -> {
      if (simpleView) {
        SwingUtilities.invokeLater(() -> showSimple(false));
      }
    });
    return row(new JLabel("View"), simple, advanced);
  }

  /**
   * Advanced uses every shape. Survival uses only logs, wood and leaves that will not decay.
   */
  private JPanel modeSwitch() {
    boolean survival = document.preset().shapes().survival();
    JRadioButton advanced = new JRadioButton("Advanced", !survival);
    JRadioButton survivalMode = new JRadioButton("Survival", survival);
    advanced.setToolTipText("Uses every block shape, plants and fine detail");
    survivalMode.setToolTipText("Only logs, wood and leaves, with leaves kept within 6 blocks of a log");
    ButtonGroup group = new ButtonGroup();
    group.add(advanced);
    group.add(survivalMode);
    advanced.addActionListener(event -> {
      if (document.preset().shapes().survival()) {
        structuralLater(() -> document.clear("shapes.mode"));
      }
    });
    survivalMode.addActionListener(event -> {
      if (!document.preset().shapes().survival()) {
        structuralLater(() -> document.setText("shapes.mode", "survival"));
      }
    });
    return row(new JLabel("Mode"), advanced, survivalMode);
  }

  /**
   * One button whose menu lists the decorators the preset does not have yet, with a hint while there are none.
   */
  private JPanel addDecoratorRow() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setAlignmentX(LEFT_ALIGNMENT);
    JPopupMenu menu = new JPopupMenu();
    DECORATORS.forEach((title, key) -> {
      if (!document.hasDecorator(key)) {
        JMenuItem item = new JMenuItem(title);
        item.addActionListener(event -> structuralLater(() -> {
          expanded.add(title);
          document.addDecorator(key);
        }));
        menu.add(item);
      }
    });
    if (document.decoratorSections().isEmpty()) {
      JLabel hint = new JLabel("No decorators yet.");
      hint.setToolTipText("Pinecones, vines and moss added to the finished tree");
      hint.setForeground(KEY_COLOR);
      panel.add(row(hint));
    }
    if (menu.getComponentCount() > 0) {
      JButton add = new JButton("Add decorator");
      add.setComponentPopupMenu(menu);
      add.addActionListener(event -> menu.show(add, 0, add.getHeight()));
      panel.add(row(add));
    }
    return panel;
  }

  /**
   * The level count with buttons to add or remove a level and to add a force.
   *
   * @param parent Panel to add to.
   */
  private void addLevelControls(JPanel parent) {
    JButton remove = new JButton("-");
    JButton add = new JButton("+");
    JButton force = new JButton("Add force");
    remove.setToolTipText("Remove the finest level");
    add.setToolTipText("Add a finer level");
    remove.setEnabled(document.levelCount() > 1);
    remove.addActionListener(event -> structuralLater(document::removeLevel));
    add.addActionListener(event -> structuralLater(document::addLevel));
    JPopupMenu kinds = new JPopupMenu();
    for (String kind : PresetDocument.FORCE_KINDS) {
      JMenuItem item = new JMenuItem(Character.toUpperCase(kind.charAt(0)) + kind.substring(1));
      item.addActionListener(event -> structuralLater(() -> document.addForce(kind)));
      kinds.add(item);
    }
    force.addActionListener(event -> kinds.show(force, 0, force.getHeight()));
    parent.add(row(new JLabel("Branch levels: " + document.levelCount()), remove, add, force));
  }

  /**
   * Lets a leaf section be dragged so that it belongs to the level it is dropped below.
   *
   * @param header Section header to drag.
   * @param leaf   Position of the leaf generator.
   */
  private void makeDraggable(JToggleButton header, int leaf) {
    header.setToolTipText("Drag below a level to grow these leaves on it");
    MouseAdapter drag = new MouseAdapter() {
      private Point start;
      private boolean dragging;
      private LevelRow target;

      @Override
      public void mousePressed(MouseEvent event) {
        start = event.getPoint();
        dragging = false;
      }

      @Override
      public void mouseDragged(MouseEvent event) {
        if (start == null || !dragging && start.distance(event.getPoint()) < DRAG_START_PIXELS) {
          return;
        }
        if (!dragging) {
          dragging = true;
          // Disarming stops the release from also opening or closing the section.
          header.getModel().setArmed(false);
          header.getModel().setPressed(false);
          header.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
        Point point = SwingUtilities.convertPoint(header, event.getPoint(), treeBody);
        treeBody.scrollRectToVisible(new Rectangle(point.x, point.y - 24, 1, 48));
        target = levelAt(point.y);
        treeBody.showDropLine(target == null ? -1 : target.bottom());
      }

      @Override
      public void mouseReleased(MouseEvent event) {
        if (!dragging) {
          return;
        }
        dragging = false;
        header.setCursor(Cursor.getDefaultCursor());
        treeBody.showDropLine(-1);
        String path = "leaves[" + leaf + "].parent";
        if (target != null && !target.name().equals(document.value(path).asText())) {
          String level = target.name();
          structuralLater(() -> document.setText(path, level));
        }
      }
    };
    header.addMouseListener(drag);
    header.addMouseMotionListener(drag);
  }

  /**
   * The level whose section contains this height in the tree tab, or null above the first level.
   *
   * @param y Height in the tree tab's coordinates.
   */
  private LevelRow levelAt(int y) {
    LevelRow found = null;
    for (LevelRow row : levelRows) {
      if (row.fold().header().getY() <= y) {
        found = row;
      }
    }
    return found;
  }

  /**
   * Adds a fold-out section for each part of the preset, with the extra buttons some need: layer controls, rename
   * fields, remove buttons and the add-block-type menu. Sections keep their open state across rebuilds, even after a
   * rename.
   *
   * @param parent   Panel to add to.
   * @param sections Sections to show.
   */
  private void addSections(JPanel parent, List<PresetDocument.Section> sections) {
    for (PresetDocument.Section section : sections) {
      int layer = listIndex(section, "Plant layer: ");
      int force = listIndex(section, "Force: ");
      int leaf = listIndex(section, "Leaves ");
      int level = listIndex(section, "Level: ");
      // A renamed layer, force or level, or moved leaves, keep their identity, so the section stays open.
      String id = layer >= 0 ? "plant-layer-" + layer : force >= 0 ? "force-" + force
              : leaf >= 0 ? "leaves-" + leaf : level >= 0 ? "level-" + level : section.title();
      String title = leaf >= 0 ? "≡ " + section.title() : section.title();
      parent.add(UiParts.separator());
      Fold fold = addFold(parent, id, title, leaf >= 0 ? 1 : 0, body -> {
        if (layer >= 0) {
          body.add(layerButtons(layer));
        }
        if (level >= 0) {
          addRename(body, "levels[" + level + "].name", typed -> document.renameLevel(level, typed));
        }
        if (force >= 0) {
          addRename(body, "forces[" + force + "].name", typed -> document.renameForce(force, typed));
          JButton remove = new JButton("Remove force");
          remove.addActionListener(event -> structuralLater(() -> document.removeForce(force)));
          body.add(row(remove));
        }
        for (PresetDocument.Group group : section.groups()) {
          if (group.title().isEmpty()) {
            addFields(body, group.fields());
          } else {
            addFold(body, id + "/" + group.title(), SettingNames.group(group.title()), 1,
                    inner -> addFields(inner, group.fields()));
          }
        }
        if (section.title().equals("Blocks")) {
          body.add(addBlockTypeButton());
        }
      });
      if (parent == decoratorBody) {
        String key = DECORATORS.get(section.title());
        addClose(fold, "Remove this decorator", () -> document.removeDecorator(key));
      }
      if (layer >= 0) {
        addClose(fold, "Remove this plant layer", () -> document.removeLayer(layer));
      }
      if (force >= 0) {
        addClose(fold, "Remove this force", () -> document.removeForce(force));
      }
      if (leaf >= 0) {
        addClose(fold, "Remove these leaves", () -> document.removeLeaves(leaf));
      }
      // Only the finest level can go: finer levels and leaves grow on the ones above.
      if (level > 0 && level == document.levelCount() - 1 && parent == treeBody) {
        addClose(fold, "Remove this level", document::removeLevel);
      }
      if (leaf >= 0) {
        Insets margin = fold.header().getMargin();
        fold.header().setMargin(new Insets(margin.top, LEAF_INDENT, margin.bottom, margin.right));
        makeDraggable(fold.header(), leaf);
      }
      if (level >= 0 && parent == treeBody) {
        levelRows.add(new LevelRow(document.value("levels[" + level + "].name").asText(), fold));
      }
    }
    if (!sections.isEmpty()) {
      parent.add(UiParts.separator());
    }
  }

  /**
   * Puts a small × at the right end of a section header that removes the section.
   *
   * @param fold   Section to give the button.
   * @param tip    Tooltip saying what goes.
   * @param remove Removes it from the preset.
   */
  private void addClose(Fold fold, String tip, Runnable remove) {
    JButton close = small(new JButton("×"));
    close.putClientProperty("JButton.buttonType", "borderless");
    close.setToolTipText(tip);
    close.addActionListener(event -> structuralLater(remove));
    fold.header().setLayout(new BorderLayout());
    fold.header().add(close, BorderLayout.EAST);
  }

  /**
   * A button whose menu lists the block types that can still be added.
   */
  private JPanel addBlockTypeButton() {
    JPopupMenu menu = ruleEditor.addMenu(document.preset().shapes().survival());
    JButton add = new JButton("Add block type");
    add.setEnabled(menu.getComponentCount() > 0);
    add.addActionListener(event -> menu.show(add, 0, add.getHeight()));
    return row(add);
  }

  /**
   * A name box that applies on Enter or leaving the field. A refused name is flagged and changes nothing.
   *
   * @param body   Panel to add to.
   * @param path   Path of the name setting.
   * @param rename Applies a typed name, returning an error message or null.
   */
  private void addRename(JPanel body, String path, Function<String, String> rename) {
    JsonNode node = document.value(path);
    String current = node == null ? "" : node.asText();
    JTextField text = new JTextField(current);
    text.setAlignmentX(LEFT_ALIGNMENT);
    JLabel refused = new JLabel();
    refused.setForeground(ERROR_COLOR);
    refused.setAlignmentX(LEFT_ALIGNMENT);
    refused.setVisible(false);
    onCommit(text, typed -> {
      String problem = typed.trim().equals(current) ? null : rename.apply(typed);
      text.putClientProperty("JComponent.outline", problem == null ? null : "error");
      refused.setText(problem);
      refused.setVisible(problem != null);
      body.revalidate();
      if (problem == null && !typed.trim().equals(current)) {
        structuralLater(() -> {
        });
      }
    });
    body.add(plainName("Name", "name"));
    body.add(capped(text));
    body.add(refused);
  }

  /**
   * Duplicate, remove and move buttons for one plant layer.
   *
   * @param index Position of the layer.
   */
  private JPanel layerButtons(int index) {
    JButton duplicate = new JButton("Duplicate");
    JButton remove = new JButton("Remove");
    JButton up = new JButton("Up");
    JButton down = new JButton("Down");
    up.setEnabled(index > 0);
    down.setEnabled(index < document.layerCount() - 1);
    duplicate.addActionListener(event -> structuralLater(() -> document.duplicateLayer(index)));
    remove.addActionListener(event -> structuralLater(() -> document.removeLayer(index)));
    up.addActionListener(event -> structuralLater(() -> document.moveLayer(index, -1)));
    down.addActionListener(event -> structuralLater(() -> document.moveLayer(index, 1)));
    return row(duplicate, remove, up, down);
  }

  /**
   * A titled section whose rows are only built while it is open, so large presets stay responsive.
   *
   * @param parent Panel to add to.
   * @param id     Remembers whether the section is open across redraws.
   * @param title  Header text.
   * @param depth  Nesting depth, which sets the indent.
   * @param fill   Builds the rows when the section opens.
   */
  private Fold addFold(JPanel parent, String id, String title, int depth, Consumer<JPanel> fill) {
    boolean open = expanded.contains(id);
    JToggleButton header = new JToggleButton((open ? "▾ " : "▸ ") + title, open);
    header.putClientProperty("JButton.buttonType", "borderless");
    header.setHorizontalAlignment(JToggleButton.LEFT);
    // FlatLaf ignores setFocusPainted on toggle buttons, so the header takes no focus at all.
    header.setFocusable(false);
    header.setAlignmentX(LEFT_ALIGNMENT);
    header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setAlignmentX(LEFT_ALIGNMENT);
    body.setBorder(BorderFactory.createEmptyBorder(2, 10 + depth * 4, 6, 0));
    body.setVisible(open);
    if (open) {
      fill.accept(body);
    }
    header.addActionListener(event -> {
      boolean nowOpen = header.isSelected();
      header.setText((nowOpen ? "▾ " : "▸ ") + title);
      if (nowOpen) {
        expanded.add(id);
        if (body.getComponentCount() == 0) {
          fill.accept(body);
          refresh();
        }
      } else {
        expanded.remove(id);
      }
      body.setVisible(nowOpen);
      parent.revalidate();
      parent.repaint();
    });
    parent.add(header);
    parent.add(body);
    return new Fold(header, body);
  }

  /**
   * Adds a named control for each setting, picked by its kind.
   *
   * @param parent Panel to add to.
   * @param fields Settings to show.
   */
  private void addFields(JPanel parent, List<SettingField> fields) {
    for (SettingField field : fields) {
      JButton reset = resetButton(field);
      JComponent control = switch (field.kind()) {
        case NUMBER, INTEGER -> numberControl(field, reset);
        case BOOLEAN -> booleanControl(field);
        case CHOICE -> choiceControl(field);
        case TEXT -> textControl(field);
        case VECTOR -> vectorControl(field);
        case PARAM -> null;
        case CURVE -> field.path().equals(CROWN_RADIUS) ? crownRadiusControl(field.path())
                : curveField(field.path(), text -> document.setJsonText(field.path(), text));
        case WEIGHTS -> weightsControl(field);
        case BLOCK_RULE -> ruleEditor.control(field.path(), field.key());
        case RANGE -> rangeControl(field);
      };
      if (field.kind() == SettingField.Kind.PARAM) {
        // The name goes above the slider, which is added first so it can take the reset button.
        int at = parent.getComponentCount();
        addParam(parent, field, reset);
        parent.add(nameRow(field, reset), at);
      } else {
        parent.add(nameRow(field, reset));
        control.setAlignmentX(LEFT_ALIGNMENT);
        parent.add(capped(control));
        // The crown settings do nothing until the crown shape style is the envelope.
        if (field.path().startsWith("shapeControl.") && !field.key().equals("style")) {
          refreshers.add(() -> enableAll(control, document.preset().shapeControl().enabled()));
        }
      }
    }
  }

  /**
   * The button that undoes edits to a setting, back to its last saved value. It is enabled only while the setting
   * differs from the save.
   *
   * @param field The setting.
   */
  private JButton resetButton(SettingField field) {
    JButton reset = small(new JButton("×"));
    reset.setToolTipText("Undo changes to this setting (back to the saved value)");
    reset.addActionListener(event -> structuralLater(() -> document.revertValue(field.path())));
    return reset;
  }

  /**
   * The human name with the JSON key beside it, and the reset button at the end of this row unless the control already
   * placed it beside its number box. An unwritten setting's name is greyed, as it uses its default.
   *
   * @param field The setting.
   * @param reset The setting's reset button.
   */
  private JPanel nameRow(SettingField field, JButton reset) {
    JLabel name = new JLabel(SettingNames.of(field.path()));
    JLabel key = new JLabel("(" + field.key() + ")");
    key.setForeground(KEY_COLOR);
    JPanel labels = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    labels.add(name);
    labels.add(key);
    JPanel row = new JPanel(new BorderLayout());
    row.add(labels, BorderLayout.CENTER);
    Color normal = UIManager.getColor("Label.foreground");
    if (reset.getParent() == null) {
      row.add(reset, BorderLayout.EAST);
    }
    refreshers.add(() -> {
      reset.setEnabled(document.changed(field.path()));
      name.setForeground(document.value(field.path()) != null ? normal : KEY_COLOR);
    });
    return capped(row);
  }

  /**
   * A number field with a slider and the reset button beside it. An unlimited value shows a button to set a finite one
   * instead.
   *
   * @param field The setting.
   * @param reset The setting's reset button.
   */
  private JComponent numberControl(SettingField field, JButton reset) {
    JsonNode node = document.value(field.path());
    double value = node != null ? node.asDouble() : field.fallback() instanceof Double number ? number : 0;
    if (Double.isInfinite(value)) {
      JButton set = new JButton("Set");
      set.addActionListener(event -> structuralLater(
              () -> document.setNumber(field.path(), Math.min(NEW_LIMIT, field.max()))));
      return row(new JLabel("No limit"), set);
    }
    boolean whole = field.kind() == SettingField.Kind.INTEGER;
    SliderScale scale = SliderScale.of(field.path(), field.min(), field.max(), whole);
    JSpinner spinner = spinner(value, field.min(), field.max(), scale, whole,
            number -> {
              document.setNumber(field.path(), whole ? Math.rint(number) : rounded(number));
              edited();
            });
    return withSlider(spinner, scale, null, null, reset);
  }

  /**
   * A checkbox.
   *
   * @param field The setting.
   */
  private JComponent booleanControl(SettingField field) {
    JsonNode node = document.value(field.path());
    JCheckBox box = new JCheckBox("Enabled", node != null ? node.asBoolean() : Boolean.TRUE.equals(field.fallback()));
    box.setName(field.path());
    if (field.path().equals(RELATIVE_RADIUS)) {
      // The curve is rewritten in the new unit so the crown keeps its size, and the panel rebuilt to show it.
      box.addActionListener(event -> {
        boolean relative = box.isSelected();
        structuralLater(() -> {
          convertCrownRadius(relative);
          document.setBoolean(field.path(), relative);
        });
      });
      return box;
    }
    box.addActionListener(event -> {
      document.setBoolean(field.path(), box.isSelected());
      edited();
    });
    return box;
  }

  /**
   * Rewrites the crown radius curve between blocks and shares of the tree's height, using the current tree: a radius
   * in blocks is multiplied by the size scale, and a relative one by the tree's height, so dividing by height over size
   * scale keeps the crown the same. Left alone when no tree has been measured yet.
   *
   * @param toRelative Whether the curve becomes relative.
   */
  private void convertCrownRadius(boolean toRelative) {
    double blocksPerShare = treeSize.getAsDouble();
    if (!(blocksPerShare > 0) || document.preset().shapeControl().relativeRadius() == toRelative) {
      return;
    }
    double[][] points = document.preset().shapeControl().radius().points();
    double steps = toRelative ? 100 : 10;
    for (double[] point : points) {
      double value = toRelative ? point[1] / blocksPerShare : point[1] * blocksPerShare;
      point[1] = Math.round(value * steps) / steps;
    }
    document.setJsonText(CROWN_RADIUS, CrownProfileEditor.json(points));
  }

  /**
   * Gives the current tree's height divided by its size scale: how many blocks, before size scale, a relative crown
   * radius of 1 stands for.
   *
   * @param size Supplies the value, or 0 when there is no tree yet.
   */
  void onTreeSize(DoubleSupplier size) {
    treeSize = size;
  }

  /**
   * A drop-down of the allowed values; branch level lists show the preset's current levels.
   *
   * @param field The setting.
   */
  private JComponent choiceControl(SettingField field) {
    JsonNode node = document.value(field.path());
    String current = node != null ? node.asText() : String.valueOf(field.fallback());
    List<String> options = field.options();
    if (field.key().equals("nearBranch")) {
      options = new ArrayList<>(List.of("any"));
      document.preset().levels().stream().map(LevelPreset::name).forEach(options::add);
    }
    JComboBox<String> combo = new JComboBox<>(options.toArray(String[]::new));
    combo.setSelectedIndex(Math.max(0, options.indexOf(current)));
    // A different type or mode can change which settings exist.
    combo.addActionListener(event -> {
      String picked = (String) combo.getSelectedItem();
      if (!picked.equals(current)) {
        structuralLater(() -> document.setText(field.path(), picked));
      }
    });
    return combo;
  }

  /**
   * A text field; clearing it puts back the saved text.
   *
   * @param field The setting.
   */
  private JComponent textControl(SettingField field) {
    JsonNode node = document.value(field.path());
    String current = node != null ? node.asText() : String.valueOf(field.fallback());
    JTextField text = new JTextField(current);
    onCommit(text, typed -> {
      String trimmed = typed.trim();
      String saved = document.value(field.path()) != null ? document.value(field.path()).asText() : current;
      if (trimmed.isEmpty()) {
        text.setText(saved);
      } else if (!trimmed.equals(saved)) {
        structuralLater(() -> document.setText(field.path(), trimmed));
      }
    });
    return text;
  }

  /**
   * Three number fields for x, y and z.
   *
   * @param field The setting.
   */
  private JComponent vectorControl(SettingField field) {
    JsonNode node = document.value(field.path());
    Vec3 fallback = field.fallback() instanceof Vec3 vector ? vector : Vec3.ZERO;
    double[] shown = node != null
            ? new double[]{node.get(0).asDouble(), node.get(1).asDouble(), node.get(2).asDouble()}
            : new double[]{fallback.x(), fallback.y(), fallback.z()};
    JSpinner[] parts = new JSpinner[3];
    DoubleConsumer write = ignored -> {
      document.setVector(field.path(), rounded(number(parts[0])), rounded(number(parts[1])), rounded(number(parts[2])));
      edited();
    };
    double step = field.key().equals("position") ? 1 : field.key().equals("groundNormal") ? 0.05 : 0.1;
    SliderScale scale = new SliderScale(-1, 1, step, false);
    JPanel row = new JPanel(new GridLayout(1, 3, 4, 0));
    for (int i = 0; i < 3; i++) {
      parts[i] = spinner(shown[i], Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, scale, false, write);
      row.add(parts[i]);
    }
    return row;
  }

  /**
   * A base value, with a dice button for its variance and a chart button for its profile, each opening a pane
   * underneath.
   *
   * @param parent Panel to add to.
   * @param field  The setting.
   * @param reset  Reset button to put beside the number box, or null.
   */
  private void addParam(JPanel parent, SettingField field, JButton reset) {
    String path = field.path();
    double fallback = field.fallback() instanceof Double number ? number : 0;
    SliderScale scale = SliderScale.of(path, field.min(), field.max(), false);
    JSpinner base = spinner(document.paramPart(path, "base", fallback), Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY, scale, false, value -> {
              document.setParamPart(path, "base", rounded(value));
              edited();
            });
    boolean ownBranch = path.matches(ALONG_OWN_BRANCH);
    Pane random = pane(parent, path + "/random", "dice", "Randomness: how much this varies per tree and per branch",
            () -> document.paramPart(path, "treeVariance", 0) != 0 || document.paramPart(path, "branchVariance", 0) != 0,
            body -> {
              addVariance(body, path, "treeVariance", "Variance per tree", scale.forVariance());
              addVariance(body, path, "branchVariance", "Variance per branch", scale.forVariance());
            });
    Pane profile = pane(parent, path + "/profile", "chart", ownBranch
                    ? "Profile: how this changes from the branch's base to its tip"
                    : "Profile: how this changes with where the branch grows on its parent",
            () -> document.value(path) != null && document.value(path).has("profile"),
            body -> addProfile(body, path, fallback, ownBranch));
    parent.add(capped(withSlider(base, scale, random.button(), profile.button(), reset)));
    parent.add(random.body());
    parent.add(profile.body());
  }

  /**
   * The profile's name, a one-line hint and its curve, with a fuller explanation on hover.
   *
   * @param body      Panel to add to.
   * @param path      Path of the variable setting.
   * @param fallback  Base value when the setting is not written.
   * @param ownBranch Whether the profile runs along the branch itself rather than its parent.
   */
  private void addProfile(JPanel body, String path, double fallback, boolean ownBranch) {
    body.add(plainName(ownBranch ? "Scale along this branch" : "Scale by spot on the parent", "profile"));
    String explained = (ownBranch
            ? "Multiplies the value from this branch's base (0) to its tip (1)."
            : "Multiplies the value by where the branch grows on its parent: 0 at the parent's base, 1 at its tip.")
            + " For example [[0, 1], [1, 0.5]] halves it toward the tip. Empty: no change.";
    JLabel hint = new JLabel(ownBranch ? "0 = branch base, 1 = branch tip; empty = none"
            : "0 = parent base, 1 = parent tip; empty = none");
    hint.setForeground(KEY_COLOR);
    hint.setToolTipText(explained);
    hint.setAlignmentX(LEFT_ALIGNMENT);
    body.add(hint);
    JTextField curve = curveField(path + ".profile", text -> document.setParamProfile(path, fallback, text));
    curve.setToolTipText(explained);
    // A shape button writes its curve at the slider's strength; moving the slider reshapes the last one picked.
    JSlider strength = new JSlider(0, 100, 50);
    strength.setName("strength");
    strength.setToolTipText("How far the shape dips below ×1");
    ProfileShape[] picked = {null};
    Runnable write = () -> {
      double[][] points = picked[0].points().apply(strength.getValue() / 100.0);
      String json = points == null ? "" : CrownProfileEditor.json(points);
      curve.setText(json);
      document.setParamProfile(path, fallback, json);
      edited();
    };
    JPanel shapes = row();
    for (ProfileShape shape : PROFILE_SHAPES) {
      JButton button = small(new JButton(shape.name()));
      button.setToolTipText(shape.tip());
      button.addActionListener(event -> {
        picked[0] = shape;
        write.run();
      });
      shapes.add(button);
    }
    strength.addChangeListener(event -> {
      if (picked[0] != null) {
        write.run();
      }
    });
    body.add(shapes);
    body.add(capped(row(new JLabel("Strength"), strength)));
    body.add(capped(curve));
  }

  /**
   * An icon toggle that opens a pane of extra controls under a setting. The icon carries a dot while what the pane
   * edits is set, and the pane remembers being open across redraws.
   *
   * @param parent Panel the pane goes in, redrawn when it opens or closes.
   * @param id     Remembers whether the pane is open; also the toggle's name.
   * @param icon   Icon file name.
   * @param tip    Tooltip.
   * @param set    Whether what the pane edits is set.
   * @param fill   Builds the pane's rows.
   */
  private Pane pane(JPanel parent, String id, String icon, String tip, BooleanSupplier set, Consumer<JPanel> fill) {
    FlatSVGIcon picture = new FlatSVGIcon("icons/" + icon + ".svg", ICON_SIZE, ICON_SIZE);
    JToggleButton button = small(new JToggleButton(picture));
    button.setName(id);
    button.setToolTipText(tip);
    button.setSelected(expanded.contains(id));
    // A set value turns the icon the accent color.
    Runnable mark = () -> {
      picture.setColorFilter(set.getAsBoolean() ? new FlatSVGIcon.ColorFilter(color -> UiParts.ACCENT) : null);
      button.repaint();
    };
    mark.run();
    refreshers.add(mark);
    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setAlignmentX(LEFT_ALIGNMENT);
    body.setBorder(BRACKET);
    if (button.isSelected()) {
      fill.accept(body);
    }
    body.setVisible(button.isSelected());
    button.addActionListener(event -> {
      if (button.isSelected()) {
        expanded.add(id);
        if (body.getComponentCount() == 0) {
          fill.accept(body);
        }
      } else {
        expanded.remove(id);
      }
      body.setVisible(button.isSelected());
      parent.revalidate();
      parent.repaint();
    });
    return new Pane(button, body);
  }

  /**
   * A labeled number field and slider for one variance of a variable setting.
   *
   * @param body  Panel to add to.
   * @param path  Path of the variable setting.
   * @param part  {@code treeVariance} or {@code branchVariance}.
   * @param label Label shown.
   * @param scale Maps slider positions to values.
   */
  private void addVariance(JPanel body, String path, String part, String label, SliderScale scale) {
    body.add(plainName(label, part));
    JSpinner spinner = spinner(document.paramPart(path, part, 0), 0, Double.POSITIVE_INFINITY, scale, false, value -> {
      document.setParamPart(path, part, rounded(value));
      edited();
    });
    body.add(capped(withSlider(spinner, scale, null, null, null)));
  }

  /**
   * Tells the viewer the crown radius points while the crown profile editor is on screen and usable, so it can draw
   * them around the tree; null when the editor is hidden or disabled.
   *
   * @param shown Receives the points, or null.
   */
  void onProfileShown(Consumer<double[][]> shown) {
    profileShown = shown;
  }

  /**
   * The crown radius curve as text with the crown's side view below it. Either one updates the other.
   *
   * @param path Path of the radius curve.
   */
  private JComponent crownRadiusControl(String path) {
    JTextField text = curveField(path, typed -> document.setJsonText(path, typed));
    CrownProfileEditor editor = new CrownProfileEditor(document.preset().shapeControl().radius().points(), json -> {
      text.setText(json);
      document.setJsonText(path, json);
      edited();
    });
    editor.setRelative(document.preset().shapeControl().relativeRadius());
    refreshers.add(() -> {
      editor.setRelative(document.preset().shapeControl().relativeRadius());
      editor.setPoints(document.preset().shapeControl().radius().points());
    });
    editor.onPreview(points -> profileShown.accept(points));
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    text.setAlignmentX(LEFT_ALIGNMENT);
    editor.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(text);
    panel.add(Box.createVerticalStrut(4));
    panel.add(editor);
    return panel;
  }

  /**
   * Curves are typed as [[t, value], ...] and apply on Enter or when focus leaves the field.
   *
   * @param path  Path of the curve setting.
   * @param apply Receives the typed text.
   */
  private JTextField curveField(String path, Consumer<String> apply) {
    JsonNode node = document.value(path);
    JTextField text = new JTextField(node == null ? "" : node.toString());
    text.setToolTipText("[[t, value], ...] then Enter; blank for the default");
    text.setAlignmentX(LEFT_ALIGNMENT);
    String[] applied = {text.getText()};
    onCommit(text, typed -> {
      if (!typed.equals(applied[0])) {
        applied[0] = typed;
        apply.accept(typed);
        edited();
      }
    });
    return text;
  }

  /**
   * Two number fields for a minimum and maximum; a minimum above the maximum is not stored.
   *
   * @param field The setting.
   */
  private JComponent rangeControl(SettingField field) {
    JsonNode node = document.value(field.path());
    double low = field.min();
    double high = field.max();
    if (node != null && node.isArray() && node.size() == 2) {
      low = node.get(0).asDouble();
      high = node.get(1).asDouble();
    } else if (field.fallback() instanceof List<?> pair && pair.size() == 2) {
      low = ((Number) pair.get(0)).doubleValue();
      high = ((Number) pair.get(1)).doubleValue();
    }
    JSpinner[] parts = new JSpinner[2];
    DoubleConsumer write = ignored -> {
      double min = rounded(number(parts[0]));
      double max = rounded(number(parts[1]));
      if (min <= max) {
        document.setRange(field.path(), min, max);
        edited();
      }
    };
    SliderScale scale = new SliderScale(field.min(), field.max(), 0.05, false);
    parts[0] = spinner(low, field.min(), field.max(), scale, false, write);
    parts[1] = spinner(high, field.min(), field.max(), scale, false, write);
    JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
    row.add(parts[0]);
    row.add(parts[1]);
    return row;
  }

  /**
   * A weighted block list: one row per block with its picker, weight and remove button.
   *
   * @param field The setting.
   */
  private JComponent weightsControl(SettingField field) {
    String path = field.path();
    boolean plant = path.startsWith("plantLayers[") || path.startsWith("decorators.vines.");
    boolean decorator = path.startsWith("decorators.");
    String role = plant ? "plant" : path.startsWith("decorators.pinecones.") ? "button"
            : decorator ? "solid" : field.key();
    boolean allowOff = !plant && !decorator && !role.equals("log") && !path.startsWith("survival.");
    LinkedHashMap<String, Double> weights = document.weights(path);
    JPanel list = new JPanel();
    list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
    if (weights.isEmpty() && field.fallback() instanceof String note) {
      JLabel label = new JLabel(note);
      label.setForeground(KEY_COLOR);
      list.add(label);
    }
    weights.forEach((block, weight) -> {
      JComponent picker = blockPicker(role, allowOff, block,
              chosen -> structuralLater(() -> document.renameBlock(path, block, chosen)));
      JSpinner amount = spinner(weight, 0, 1000, new SliderScale(0, 1000, 0.5, false), false, value -> {
        document.setWeight(path, block, rounded(value));
        edited();
      });
      amount.setPreferredSize(new Dimension(80, amount.getPreferredSize().height));
      JButton remove = new JButton("×");
      remove.setMargin(new java.awt.Insets(0, 4, 0, 4));
      remove.setToolTipText("Remove this block");
      remove.addActionListener(event -> structuralLater(() -> document.removeBlock(path, block)));
      JPanel right = new JPanel(new BorderLayout(2, 0));
      right.add(amount, BorderLayout.CENTER);
      right.add(remove, BorderLayout.EAST);
      JPanel row = new JPanel(new BorderLayout(4, 0));
      row.add(picker, BorderLayout.CENTER);
      row.add(right, BorderLayout.EAST);
      row.setAlignmentX(LEFT_ALIGNMENT);
      list.add(capped(row));
    });
    JButton add = new JButton("Add block");
    add.addActionListener(event -> {
      String added = newBlock(path, role, weights.keySet());
      structuralLater(() -> document.addBlock(path, added));
    });
    list.add(row(add));
    return list;
  }

  /**
   * A button that opens a searchable block list. Without assets the block is a text field.
   *
   * @param role     Block list key, such as {@code log}, used to pick suitable blocks.
   * @param allowOff Whether {@code off} may be picked.
   * @param current  Block shown now.
   * @param picked   Receives the chosen block.
   */
  private JComponent blockPicker(String role, boolean allowOff, String current, Consumer<String> picked) {
    if (catalog == null) {
      JTextField text = new JTextField(current);
      onCommit(text, typed -> {
        String trimmed = typed.trim();
        if (trimmed.isEmpty()) {
          text.setText(current);
        } else if (!trimmed.equals(current)) {
          picked.accept(trimmed);
        }
      });
      return text;
    }
    JButton button = new JButton(current);
    button.setHorizontalAlignment(JButton.LEFT);
    button.addActionListener(event -> showBlockList(button, role, allowOff, current, picked));
    return button;
  }

  /**
   * A searchable pop-up list of the blocks that suit a list, opened under its button.
   *
   * @param owner    Button the list opens under.
   * @param role     Block list key, such as {@code log}, used to pick suitable blocks.
   * @param allowOff Whether {@code off} may be picked.
   * @param current  Block shown now.
   * @param picked   Receives the chosen block.
   */
  private void showBlockList(JButton owner, String role, boolean allowOff, String current, Consumer<String> picked) {
    DefaultListModel<String> model = new DefaultListModel<String>();
    JTextField search = new JTextField();
    JList<String> list = new JList<>(model);
    Runnable filter = () -> {
      model.clear();
      if (allowOff && search.getText().isBlank()) {
        model.addElement("off");
      }
      model.addAll(catalog.suitable(role, search.getText()));
      list.setSelectedValue(current, true);
    };
    filter.run();
    search.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent event) {
        filter.run();
      }

      @Override
      public void removeUpdate(DocumentEvent event) {
        filter.run();
      }

      @Override
      public void changedUpdate(DocumentEvent event) {
        filter.run();
      }
    });
    JPopupMenu popup = new JPopupMenu();
    Runnable choose = () -> {
      String value = list.getSelectedValue();
      popup.setVisible(false);
      if (value != null && !value.equals(current)) {
        picked.accept(value);
      }
    };
    search.addActionListener(event -> {
      if (list.getSelectedValue() == null && !model.isEmpty()) {
        list.setSelectedIndex(0);
      }
      choose.run();
    });
    list.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseReleased(java.awt.event.MouseEvent event) {
        choose.run();
      }
    });
    JPanel content = new JPanel(new BorderLayout(0, 4));
    content.add(search, BorderLayout.NORTH);
    JScrollPane scroll = new JScrollPane(list);
    scroll.setPreferredSize(new Dimension(Math.max(260, owner.getWidth()), 260));
    content.add(scroll, BorderLayout.CENTER);
    popup.add(content);
    popup.show(owner, 0, owner.getHeight());
    search.requestFocusInWindow();
  }

  /**
   * A block for a new row: the role's usual block when free, otherwise the first suitable block not yet listed.
   *
   * @param path   Path of the block list.
   * @param role   Block list key, such as {@code log}.
   * @param listed Blocks already in the list.
   */
  private String newBlock(String path, String role, Set<String> listed) {
    List<String> preferred = path.startsWith("decorators.moss.")
            ? List.of("moss_block", "mossy_cobblestone", "mossy_stone_bricks")
            : path.startsWith("decorators.vines.") ? List.of("vine", "weeping_vines", "sunflower") : List.of();
    List<String> known = catalog == null ? null : catalog.suitable("any", "");
    for (String name : preferred) {
      if (!listed.contains(name) && (known == null || known.contains(name))) {
        return name;
      }
    }
    String usual = "short_grass";
    for (BlockRole candidate : BlockRole.values()) {
      if (candidate.jsonKey().equals(role)) {
        usual = TreeBlocks.oakDefaults().block(candidate);
      }
    }
    List<String> suitable = catalog == null ? null : catalog.suitable(role, "");
    if (!listed.contains(usual) && (suitable == null || suitable.contains(usual))) {
      return usual;
    }
    if (suitable != null) {
      for (String name : suitable) {
        if (!listed.contains(name)) {
          return name;
        }
      }
    }
    return usual;
  }

  /**
   * A ready-made profile.
   *
   * @param name   Button text.
   * @param tip    What it does.
   * @param points Its curve for a strength from 0 to 1, or null to remove the profile.
   */
  private record ProfileShape(String name, String tip, DoubleFunction<double[][]> points) {
  }

  /**
   * An icon toggle and the pane it opens under a setting.
   *
   * @param button The toggle.
   * @param body   The pane, built the first time it opens.
   */
  private record Pane(JToggleButton button, JPanel body) {
  }

  /**
   * What the viewer knows about the current tree. The catalog is null without assets, and the unknown blocks are
   * empty without assets.
   *
   * @param catalog       Blocks offered by the editor's pickers, or null without assets.
   * @param unknownBlocks Blocks with no usable model; empty without assets.
   */
  record Context(BlockCatalog catalog, Set<String> unknownBlocks) {
  }

  /**
   * A section's parts.
   *
   * @param header Button that opens and closes the section.
   * @param body   Panel holding the section's rows.
   */
  private record Fold(JToggleButton header, JPanel body) {
  }

  /**
   * A branch level that leaves can be dropped below.
   *
   * @param name The level's name.
   * @param fold The level's section.
   */
  private record LevelRow(String name, Fold fold) {
    /**
     * Where dropped leaves will appear, just under the level's own settings.
     */
    int bottom() {
      JComponent last = fold.body().isVisible() ? fold.body() : fold.header();
      return last.getY() + last.getHeight();
    }
  }

  /**
   * A vertical list that fits the scroll pane's width.
   */
  private static final class Body extends JPanel implements Scrollable {
    private int dropLine = -1;

    Body() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }

    /**
     * Shows where a dragged section will land, or hides the line for -1.
     *
     * @param y Height of the line, or -1 to hide it.
     */
    void showDropLine(int y) {
      dropLine = y;
      repaint();
    }

    /**
     * Draws the line that shows where a dragged leaf section will land.
     *
     * @param graphics Where to draw.
     */
    @Override
    protected void paintChildren(Graphics graphics) {
      super.paintChildren(graphics);
      if (dropLine >= 0) {
        graphics.setColor(DROP_COLOR);
        graphics.fillRect(0, dropLine - 1, getWidth(), 3);
      }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
      return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
      return visible.height - 18;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }
}
