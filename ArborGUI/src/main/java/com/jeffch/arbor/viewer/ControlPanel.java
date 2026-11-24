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
final class ControlPanel extends JPanel {
  static final Map<String, Set<ShapeClass>> SHOW_GROUPS = showGroups();
  private static final int RANDOM_SEED_LIMIT = 1_000_000;
  private static final int SLIDER_STEPS = 1000;
  private final Consumer<Consumer<ViewerState>> view;
  private final LongConsumer seedChanged;
  private final JTextField seedField = new JTextField(12);
  private final JLabel source = new JLabel();
  private final JTextArea notice = readout(UiParts.WARNING);
  private final JTextArea forces = readout(UiParts.INFO);
  private final StatsPanel statsPanel = new StatsPanel();
  private final List<JComponent> voxelControls = new ArrayList<>();
  private long seed;
  /**
   * Builds the seed controls, the camera reset, and the View, Show and Leaf density sections.
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
    add(source);
    addSeed();
    JButton reset = new JButton("Reset camera");
    reset.addActionListener(event -> resetCamera.run());
    add(row(reset));

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
    SHOW_GROUPS.forEach((label, shapes) -> voxelToggle(label, state.isVisible(shapes.iterator().next()),
            (viewer, on) -> shapes.forEach(shape -> viewer.setVisible(shape, on))));
    voxelToggle("Skeleton lines", state.showSkeleton, (viewer, on) -> viewer.showSkeleton = on);
    voxelToggle("Voxel grid", state.showVoxelGrid, (viewer, on) -> viewer.showVoxelGrid = on);
    add(toggle("Leaf density", state.showDensity, (viewer, on) -> viewer.showDensity = on));
    add(toggle("Sun and wind", state.showForces, (viewer, on) -> viewer.showForces = on));
    add(forces);

    section("Leaf density");
    addSlider("Threshold", 0, 0.95f, state.densityThreshold, (viewer, value) -> viewer.densityThreshold = value);
    addSlider("Opacity", 0.05f, 6, state.densityOpacity, (viewer, value) -> viewer.densityOpacity = value);

    add(Box.createVerticalGlue());
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
  private void voxelToggle(String label, boolean selected, BiConsumer<ViewerState, Boolean> apply) {
    JCheckBox box = toggle(label, selected, apply);
    voxelControls.add(box);
    add(box);
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
   * Shows which preset is open.
   *
   * @param name Preset name.
   */
  void showSource(String name) {
    source.setText(name);
  }

  /**
   * Updates the readouts for the current tree.
   *
   * @param stats The current tree's numbers.
   */
  void showStats(Stats stats) {
    source.setText(stats.source());
    show(notice, stats.textureNotice());
    show(forces, stats.forces().isEmpty() ? "No sun or wind on this tree" : stats.forces());
    statsPanel.show(stats);
    revalidate();
  }

  StatsPanel statsPanel() {
    return statsPanel;
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
