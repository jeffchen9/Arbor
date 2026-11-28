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
  private static final List<BlockGroup> BLOCK_GROUPS = List.of(
          new BlockGroup("Solid blocks", "Full blocks, stairs, slabs and buttons",
                  EnumSet.of(ShapeClass.FULL, ShapeClass.STAIR, ShapeClass.SLAB, ShapeClass.BUTTON)),
          new BlockGroup("Walls", "Walls", EnumSet.of(ShapeClass.WALL)),
          new BlockGroup("Fences", "Fences and fence gates", EnumSet.of(ShapeClass.FENCE, ShapeClass.GATE)),
          new BlockGroup("Plants", "Plants, dead bushes and saplings",
                  EnumSet.of(ShapeClass.PLANT, ShapeClass.DEAD_BUSH, ShapeClass.SAPLING)),
          new BlockGroup("Leaves", "Leaf blocks", EnumSet.of(ShapeClass.LEAVES)));
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
  /**
   * Voxels, Branches, Both and Split: the widest row that cannot wrap, so it sets the panel's narrowest width.
   */
  private final JPanel modes = new JPanel(new GridLayout(1, 0));
  private final JTextArea notice = readout(UiParts.WARNING);
  private JCheckBox forces;
  private final StatsPanel statsPanel = new StatsPanel();
  private final List<JComponent> voxelControls = new ArrayList<>();
  private final Map<BlockGroup, JCheckBox> blockBoxes = new HashMap<>();
  private final Map<BlockGroup, JLabel> blockCounts = new HashMap<>();
  /**
   * Block types the current tree has none of.
   */
  private final Set<BlockGroup> absent = new HashSet<>();
  private ViewerState.Mode mode;
  private long seed;

  /**
   * Builds the seed controls, the View, Blocks and Guides sections, and the camera controls that the view shows
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
    // One choice, so no mix of settings can contradict another.
    ButtonGroup oneMode = new ButtonGroup();
    modes.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (ViewerState.Mode mode : ViewerState.Mode.values()) {
      String name = mode.name().charAt(0) + mode.name().substring(1).toLowerCase(java.util.Locale.ROOT);
      JToggleButton button = UiParts.small(new JToggleButton(name, mode == state.mode));
      button.addActionListener(event -> setMode(mode));
      oneMode.add(button);
      modes.add(button);
    }
    add(UiParts.capped(modes));
    voxelToggle("Block textures", state.useTextures, (viewer, on) -> viewer.useTextures = on);
    voxelToggle("Realistic lighting", state.realisticLighting, (viewer, on) -> viewer.realisticLighting = on)
            .setToolTipText("Sunlight and shadows instead of Minecraft's flat face shading");
    add(notice);

    // Shortcuts that set the boxes, rather than a setting of their own that could disagree with them.
    JButton all = UiParts.small(new JButton("All"));
    all.addActionListener(event -> BLOCK_GROUPS.forEach(group -> setShown(group, true)));
    JButton woodOnly = UiParts.small(new JButton("Wood only"));
    woodOnly.setToolTipText("Hide leaves and plants, leaving the trunk and branches");
    woodOnly.addActionListener(event -> BLOCK_GROUPS.forEach(group ->
            setShown(group, Collections.disjoint(group.shapes(), ViewerState.FOLIAGE))));
    voxelControls.add(all);
    voxelControls.add(woodOnly);
    JPanel shortcuts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    shortcuts.add(all);
    shortcuts.add(woodOnly);
    add(Box.createVerticalStrut(8));
    JPanel header = new JPanel(new BorderLayout());
    header.add(heading("Blocks"), BorderLayout.WEST);
    header.add(shortcuts, BorderLayout.EAST);
    add(UiParts.capped(header));
    for (BlockGroup group : BLOCK_GROUPS) {
      Set<ShapeClass> shapes = group.shapes();
      JCheckBox box = toggle(group.label(), state.isVisible(shapes.iterator().next()),
              (viewer, on) -> shapes.forEach(shape -> viewer.setVisible(shape, on)));
      box.setToolTipText(group.tip());
      JLabel count = new JLabel();
      count.setForeground(UiParts.MUTED);
      JPanel line = new JPanel(new BorderLayout());
      line.add(box, BorderLayout.WEST);
      line.add(count, BorderLayout.EAST);
      add(UiParts.capped(line));
      blockBoxes.put(group, box);
      blockCounts.put(group, count);
    }

    section("Guides");
    voxelToggle("Voxel grid", state.showVoxelGrid, (viewer, on) -> viewer.showVoxelGrid = on);
    JCheckBox density = toggle("Leaf density", state.showDensity, (viewer, on) -> viewer.showDensity = on);
    add(density);
    // The cloud's sliders sit under its checkbox and show only while the cloud does.
    JPanel densityPane = new JPanel();
    densityPane.setName("densityPane");
    densityPane.setLayout(new BoxLayout(densityPane, BoxLayout.Y_AXIS));
    densityPane.setBorder(UiParts.BRACKET);
    densityPane.setAlignmentX(Component.LEFT_ALIGNMENT);
    densityPane.add(slider("Threshold", 0, 0.95f, state.densityThreshold, (viewer, value) -> viewer.densityThreshold = value));
    densityPane.add(slider("Opacity", 0.05f, 6, state.densityOpacity, (viewer, value) -> viewer.densityOpacity = value));
    densityPane.setVisible(state.showDensity);
    density.addActionListener(event -> densityPane.setVisible(density.isSelected()));
    add(densityPane);
    forces = toggle("Sun and wind", state.showForces, (viewer, on) -> viewer.showForces = on);
    add(forces);
    add(toggle("Crown envelope", state.showEnvelope, (viewer, on) -> viewer.showEnvelope = on));

    statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(statsPanel);
    setMode(state.mode);
  }

  /**
   * Ticks or unticks one block type and applies it.
   *
   * @param group Block type.
   * @param on    Whether to show it.
   */
  private void setShown(BlockGroup group, boolean on) {
    blockBoxes.get(group).setSelected(on);
    view.accept(viewer -> group.shapes().forEach(shape -> viewer.setVisible(shape, on)));
  }

  /**
   * Greys out what has nothing to act on: block settings in Branches view, and block types the tree has none of.
   */
  private void refreshEnabled() {
    boolean blocks = mode != ViewerState.Mode.BRANCHES;
    voxelControls.forEach(control -> control.setEnabled(blocks));
    blockBoxes.forEach((group, box) -> box.setEnabled(blocks && !absent.contains(group)));
  }

  /**
   * A bold heading.
   *
   * @param title Heading text.
   */
  private static JLabel heading(String title) {
    JLabel label = new JLabel(title);
    label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    return label;
  }

  /**
   * One Blocks checkbox. Every shape class belongs to exactly one, so nothing can be left impossible to hide.
   *
   * @param label  Checkbox text.
   * @param tip    What it covers.
   * @param shapes Shape classes it shows or hides.
   */
  private record BlockGroup(String label, String tip, Set<ShapeClass> shapes) {
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
   * As narrow as the view mode buttons allow before their names are cut short.
   */
  @Override
  public Dimension getMinimumSize() {
    Insets insets = getInsets();
    return new Dimension(modes.getPreferredSize().width + insets.left + insets.right, super.getMinimumSize().height);
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
  private void randomSeed() {
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
   * Switches what the view draws; settings that only affect blocks are greyed out when there are none.
   *
   * @param mode Blocks, branch lines, both together, or both side by side.
   */
  private void setMode(ViewerState.Mode mode) {
    this.mode = mode;
    view.accept(viewer -> viewer.mode = mode);
    refreshEnabled();
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
  private JComponent slider(String label, float min, float max, float value, BiConsumer<ViewerState, Float> apply) {
    JSlider slider = new JSlider(0, SLIDER_STEPS, Math.round((value - min) / (max - min) * SLIDER_STEPS));
    JLabel shown = new JLabel(String.format("%.2f", value));
    slider.setPreferredSize(new Dimension(150, slider.getPreferredSize().height));
    slider.addChangeListener(event -> {
      float current = min + (max - min) * slider.getValue() / SLIDER_STEPS;
      shown.setText(String.format("%.2f", current));
      view.accept(viewer -> apply.accept(viewer, current));
    });
    return row(new JLabel(label), slider, shown);
  }

  /**
   * A bold heading with a little space above it.
   *
   * @param title Heading text.
   */
  private void section(String title) {
    add(Box.createVerticalStrut(8));
    add(heading(title));
  }

  /**
   * Updates the readouts for the current tree.
   *
   * @param stats The current tree's numbers.
   */
  void showStats(Stats stats) {
    show(notice, stats.textureNotice());
    // Nothing to show on a tree without sun or wind.
    forces.setEnabled(!stats.forces().isEmpty());
    forces.setToolTipText(forces.isEnabled() ? null : "No sun or wind on this tree");
    absent.clear();
    for (BlockGroup group : BLOCK_GROUPS) {
      int count = group.shapes().stream().mapToInt(shape -> stats.counts().getOrDefault(shape, 0)).sum();
      blockCounts.get(group).setText(count == 0 ? "—" : String.format("%,d", count));
      blockBoxes.get(group).setToolTipText(count == 0 ? "None on this tree" : group.tip());
      if (count == 0) {
        absent.add(group);
      }
    }
    refreshEnabled();
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
