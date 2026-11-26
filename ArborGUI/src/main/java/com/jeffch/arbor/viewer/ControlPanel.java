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

import com.jeffch.arbor.shape.ShapeClass;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import static com.jeffch.arbor.viewer.UiParts.row;

/**
 * The view controls and readouts.
 */
final class ControlPanel extends JPanel implements Scrollable {
  static final Map<String, Set<ShapeClass>> SHOW_GROUPS = showGroups();
  private static final int SCROLL_UNIT = 18;
  private static final int RANDOM_SEED_LIMIT = 1_000_000;
  private static final int SLIDER_STEPS = 1000;
  private final Consumer<Consumer<ViewerState>> view;
  private final LongConsumer seedChanged;
  private final JTextField seedField = new JTextField(12);
  /**
   * The camera reset and camera help, shown over the top left of the view.
   */
  private final JPanel viewControls = new JPanel();
  private final JTextArea notice = readout(UiParts.WARNING);
  private final JTextArea forces = readout(UiParts.INFO);
  private final StatsPanel statsPanel = new StatsPanel();
  private final List<JComponent> voxelControls = new ArrayList<>();
  private long seed;

  /**
   * Builds the seed controls, the View, Show and Leaf density sections, and the camera controls that the view shows
   * over its top left.
   *
   * @param state       Starting view settings.
   * @param view        Applies a change to the view settings on the render thread.
   * @param seedChanged Told each new seed.
   * @param resetCamera Puts the camera back to frame the tree.
   */
  ControlPanel(ViewerState state, Consumer<Consumer<ViewerState>> view, LongConsumer seedChanged, Runnable resetCamera) {
    this.view = view;
    this.seedChanged = seedChanged;
    seed = state.seed;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    viewControls.setLayout(new BoxLayout(viewControls, BoxLayout.Y_AXIS));
    // No backdrop: the camera controls sit straight on the view.
    viewControls.setOpaque(false);
    addSeed();
    JButton reset = new JButton("Reset camera");
    reset.addActionListener(event -> resetCamera.run());
    // Outline only, so the view shows through the button.
    reset.setBackground(new Color(0, 0, 0, 0));
    // FlatLaf ignores setFocusPainted, so the button takes no focus and shows no blue outline after a click.
    reset.setFocusable(false);
    reset.setAlignmentX(Component.LEFT_ALIGNMENT);
    viewControls.add(reset);
    JLabel help = new JLabel(ViewPanel.CAMERA_HELP);
    help.setForeground(UiParts.MUTED);
    help.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    help.setAlignmentX(Component.LEFT_ALIGNMENT);
    viewControls.add(help);

    section("View");
    JRadioButton voxels = new JRadioButton("Voxels", !state.branchView);
    JRadioButton branches = new JRadioButton("Branches", state.branchView);
    ButtonGroup group = new ButtonGroup();
    group.add(voxels);
    group.add(branches);
    voxels.addActionListener(event -> setBranchView(false));
    branches.addActionListener(event -> setBranchView(true));
    add(row(voxels, branches));
    voxelToggle("Block textures", state.useTextures, (viewer, on) -> viewer.useTextures = on);
    add(notice);

    section("Show");
    Map<String, JCheckBox> shown = new HashMap<>();
    SHOW_GROUPS.forEach((label, shapes) -> shown.put(label, voxelToggle(label,
            state.isVisible(shapes.iterator().next()),
            (viewer, on) -> shapes.forEach(shape -> viewer.setVisible(shape, on)))));
    // Unticks Plants and Leaf blocks together, leaving only the trunk and branches; ticking it again shows them.
    JCheckBox woodOnly = new JCheckBox("Wood only (hide leaves and plants)");
    woodOnly.setAlignmentX(Component.LEFT_ALIGNMENT);
    woodOnly.addActionListener(event -> List.of("Plants", "Leaf blocks").forEach(label -> {
      JCheckBox box = shown.get(label);
      if (box.isSelected() == woodOnly.isSelected()) {
        box.doClick();
      }
    }));
    voxelControls.add(woodOnly);
    add(woodOnly);
    voxelToggle("Skeleton lines", state.showSkeleton, (viewer, on) -> viewer.showSkeleton = on);
    voxelToggle("Voxel grid", state.showVoxelGrid, (viewer, on) -> viewer.showVoxelGrid = on);
    add(toggle("Leaf density", state.showDensity, (viewer, on) -> viewer.showDensity = on));
    add(toggle("Sun and wind", state.showForces, (viewer, on) -> viewer.showForces = on));
    add(forces);
    add(toggle("Crown envelope", state.showEnvelope, (viewer, on) -> viewer.showEnvelope = on));

    section("Leaf density");
    addSlider("Threshold", 0, 0.95f, state.densityThreshold, (viewer, value) -> viewer.densityThreshold = value);
    addSlider("Opacity", 0.05f, 6, state.densityOpacity, (viewer, value) -> viewer.densityOpacity = value);

    statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(statsPanel);
    setBranchView(state.branchView);
  }

  /**
   * The Show checkboxes. Every shape class belongs to exactly one, so nothing can be left impossible to hide.
   */
  private static Map<String, Set<ShapeClass>> showGroups() {
    Map<String, Set<ShapeClass>> groups = new LinkedHashMap<>();
    groups.put("Full blocks", EnumSet.of(ShapeClass.FULL, ShapeClass.STAIR, ShapeClass.SLAB, ShapeClass.BUTTON));
    groups.put("Walls", EnumSet.of(ShapeClass.WALL));
    groups.put("Fences", EnumSet.of(ShapeClass.FENCE, ShapeClass.GATE));
    groups.put("Plants", EnumSet.of(ShapeClass.PLANT, ShapeClass.DEAD_BUSH, ShapeClass.SAPLING));
    groups.put("Leaf blocks", EnumSet.of(ShapeClass.LEAVES));
    return groups;
  }

  /**
   * A hidden message area that appears when it has text.
   *
   * @param color Text color.
   */
  private static JTextArea readout(Color color) {
    JTextArea area = UiParts.textBlock(color);
    area.setAlignmentX(Component.LEFT_ALIGNMENT);
    area.setVisible(false);
    return area;
  }

  /**
   * Shows the text, or hides the area when there is none.
   *
   * @param area Message area.
   * @param text Text to show, or null or empty to hide it.
   */
  private static void show(JTextArea area, String text) {
    boolean visible = text != null && !text.isEmpty();
    area.setText(visible ? text : "");
    area.setVisible(visible);
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
    return SCROLL_UNIT;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
    return visible.height;
  }

  /**
   * Fills the column's width, so text wraps to it.
   */
  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  /**
   * Keeps its own height, so each section sits right below the one above instead of spreading out to fill the column.
   */
  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  /**
   * The seed field with Previous, Next and Random buttons.
   */
  private void addSeed() {
    seedField.setText(Long.toString(seed));
    seedField.setMaximumSize(seedField.getPreferredSize());
    seedField.addActionListener(event -> commitSeedText());
    seedField.addFocusListener(new java.awt.event.FocusAdapter() {
      @Override
      public void focusLost(java.awt.event.FocusEvent event) {
        commitSeedText();
      }
    });
    add(row(new JLabel("Seed"), seedField));
    JButton previous = new JButton("Previous");
    JButton next = new JButton("Next");
    JButton random = new JButton("Random");
    previous.addActionListener(event -> setSeed(seed - 1));
    next.addActionListener(event -> setSeed(seed + 1));
    random.addActionListener(event -> randomSeed());
    add(row(previous, next, random));
  }

  /**
   * The camera reset and camera help, for the view to show over its top left.
   */
  JComponent viewControls() {
    return viewControls;
  }

  /**
   * Shows a new random tree.
   */
  void randomSeed() {
    setSeed(ThreadLocalRandom.current().nextLong(RANDOM_SEED_LIMIT));
  }

  /**
   * Uses the typed seed, or restores the current one when the text is not a whole number.
   */
  private void commitSeedText() {
    try {
      long typed = Long.parseLong(seedField.getText().trim());
      if (typed != seed) {
        setSeed(typed);
      }
    } catch (NumberFormatException e) {
      seedField.setText(Long.toString(seed));
    }
  }

  /**
   * Shows a seed and asks for its tree.
   *
   * @param value New seed.
   */
  private void setSeed(long value) {
    seed = value;
    seedField.setText(Long.toString(value));
    seedChanged.accept(value);
  }

  /**
   * Switches between blocks and branch lines; settings that only affect blocks are greyed out in branch view.
   *
   * @param on Whether to show branch lines rather than blocks.
   */
  private void setBranchView(boolean on) {
    view.accept(viewer -> viewer.branchView = on);
    voxelControls.forEach(control -> control.setEnabled(!on));
  }

  /**
   * A checkbox that only applies to the block view.
   *
   * @param label    Checkbox text.
   * @param selected Whether it starts checked.
   * @param apply    Sets the view setting.
   */
  private JCheckBox voxelToggle(String label, boolean selected, BiConsumer<ViewerState, Boolean> apply) {
    JCheckBox box = toggle(label, selected, apply);
    voxelControls.add(box);
    add(box);
    return box;
  }

  /**
   * A checkbox that changes one view setting.
   *
   * @param label    Checkbox text.
   * @param selected Whether it starts checked.
   * @param apply    Sets the view setting.
   */
  private JCheckBox toggle(String label, boolean selected, BiConsumer<ViewerState, Boolean> apply) {
    JCheckBox box = new JCheckBox(label, selected);
    box.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.addActionListener(event -> {
      boolean on = box.isSelected();
      view.accept(viewer -> apply.accept(viewer, on));
    });
    return box;
  }

  /**
   * A labeled slider from min to max that changes one view setting and shows its value.
   *
   * @param label Slider name.
   * @param min   Lowest value.
   * @param max   Highest value.
   * @param value Starting value.
   * @param apply Sets the view setting.
   */
  private void addSlider(String label, float min, float max, float value, BiConsumer<ViewerState, Float> apply) {
    JSlider slider = new JSlider(0, SLIDER_STEPS, Math.round((value - min) / (max - min) * SLIDER_STEPS));
    JLabel shown = new JLabel(String.format("%.2f", value));
    slider.setPreferredSize(new Dimension(150, slider.getPreferredSize().height));
    slider.addChangeListener(event -> {
      float current = min + (max - min) * slider.getValue() / SLIDER_STEPS;
      shown.setText(String.format("%.2f", current));
      view.accept(viewer -> apply.accept(viewer, current));
    });
    add(row(new JLabel(label), slider, shown));
  }

  /**
   * A bold heading with a little space above it.
   *
   * @param title Heading text.
   */
  private void section(String title) {
    add(Box.createVerticalStrut(8));
    JLabel label = new JLabel(title);
    label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(label);
  }

  /**
   * Updates the readouts for the current tree.
   *
   * @param stats The current tree's numbers.
   */
  void showStats(Stats stats) {
    show(notice, stats.textureNotice());
    show(forces, stats.forces().isEmpty() ? "No sun or wind on this tree" : stats.forces());
    statsPanel.show(stats);
    revalidate();
  }

  /**
   * Values shown in the readout.
   *
   * @param source           Preset file or scene being shown.
   * @param dimensions       Tree size in blocks along x, y and z.
   * @param counts           Number of shapes of each class.
   * @param leafVoxels       Number of voxels with leaf density.
   * @param generationMillis Time the last generation took, in milliseconds.
   * @param renderer         Name of the graphics renderer.
   * @param error            Current error message, or null.
   * @param textureNotice    Message about texture loading, or null.
   * @param forces           Sun and wind markers on this tree, or empty.
   * @param slopeDegrees     Angle of the ground from horizontal.
   * @param textures         Minecraft version or folder the textures came from, or null without textures.
   */
  record Stats(String source, int[] dimensions, Map<ShapeClass, Integer> counts, int leafVoxels,
               double generationMillis,
               String renderer, String error, String textureNotice, String forces, double slopeDegrees,
               String textures) {
  }
}
