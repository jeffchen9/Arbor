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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A table of facts about the current tree, with names on the left and values on the right.
 */
final class StatsPanel extends JPanel implements Scrollable {
  private static final int SCROLL_UNIT = 18;
  private static final Map<ShapeClass, String> SHAPE_LABELS = shapeLabels();

  private final JPanel rows = new JPanel();
  private final JTextArea error = UiParts.textBlock(UiParts.ERROR);

  StatsPanel() {
    super(new BorderLayout(0, 4));
    setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
    JLabel title = new JLabel("Stats");
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    JPanel top = new JPanel(new BorderLayout(0, 4));
    top.add(title, BorderLayout.NORTH);
    top.add(error, BorderLayout.CENTER);
    error.setVisible(false);
    rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
    add(top, BorderLayout.NORTH);
    add(rows, BorderLayout.CENTER);
  }

  /**
   * A readable name for each shape class, in display order.
   */
  private static Map<ShapeClass, String> shapeLabels() {
    Map<ShapeClass, String> labels = new LinkedHashMap<>();
    labels.put(ShapeClass.FULL, "Full blocks");
    labels.put(ShapeClass.WALL, "Walls");
    labels.put(ShapeClass.FENCE, "Fences");
    labels.put(ShapeClass.GATE, "Fence gates");
    labels.put(ShapeClass.DEAD_BUSH, "Dead bushes");
    labels.put(ShapeClass.SAPLING, "Saplings");
    labels.put(ShapeClass.STAIR, "Stairs");
    labels.put(ShapeClass.SLAB, "Slabs");
    labels.put(ShapeClass.BUTTON, "Buttons");
    labels.put(ShapeClass.LEAVES, "Leaf blocks");
    labels.put(ShapeClass.PLANT, "Plants");
    return labels;
  }

  /**
   * The name and value of each row, in display order.
   *
   * @param stats The current tree's numbers.
   */
  static Map<String, String> rowsFor(ControlPanel.Stats stats) {
    Map<String, String> values = new LinkedHashMap<>();
    int[] size = stats.dimensions();
    values.put("Size", size[0] + " × " + size[1] + " × " + size[2]);
    values.put("Ground", stats.slopeDegrees() == 0 ? "Flat" : String.format("%.1f°", stats.slopeDegrees()));
    SHAPE_LABELS.forEach((shape, label) ->
            values.put(label, String.format("%,d", stats.counts().getOrDefault(shape, 0))));
    values.put("Leaf voxels", String.format("%,d", stats.leafVoxels()));
    values.put("Generated in", String.format("%.0f ms", stats.generationMillis()));
    if (stats.textures() != null) {
      values.put("Textures", stats.textures());
    }
    values.put("Renderer", stats.renderer().split("/", 2)[0]);
    return values;
  }

  /**
   * One name and value row, with a divider line below it when asked.
   *
   * @param name  Row name.
   * @param value Row value.
   * @param tip   Tooltip text.
   * @param line  Whether to draw a divider below.
   */
  private static JPanel row(String name, String value, String tip, boolean line) {
    JLabel nameLabel = new JLabel(name);
    nameLabel.setForeground(UiParts.MUTED);
    JLabel valueLabel = new JLabel(value, SwingConstants.RIGHT);
    valueLabel.setToolTipText(tip);
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.add(nameLabel, BorderLayout.WEST);
    row.add(valueLabel, BorderLayout.CENTER);
    row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, line ? 1 : 0, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(1, 0, 1, 0)));
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
    return row;
  }

  /**
   * Shows a tree's numbers, or the reason it could not be made.
   *
   * @param stats The current tree's numbers.
   */
  void show(ControlPanel.Stats stats) {
    boolean failed = stats.error() != null && !stats.error().isEmpty();
    error.setText(failed ? stats.error() : "");
    error.setVisible(failed);
    rows.removeAll();
    Map<String, String> values = rowsFor(stats);
    int remaining = values.size();
    for (Map.Entry<String, String> entry : values.entrySet()) {
      remaining--;
      String tip = entry.getKey().equals("Renderer") ? stats.renderer() : entry.getValue();
      rows.add(row(entry.getKey(), entry.getValue(), tip, remaining > 0));
    }
    revalidate();
    repaint();
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

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }
}
