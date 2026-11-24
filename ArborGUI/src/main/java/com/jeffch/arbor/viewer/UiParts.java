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

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Small Swing pieces shared by the editor panels.
 */
final class UiParts {
  static final Color MUTED = new Color(145, 151, 158);
  static final Color ERROR = new Color(255, 125, 115);
  static final Color WARNING = new Color(235, 180, 85);
  static final Color INFO = new Color(105, 190, 230);
  static final Color ACCENT = new Color(75, 160, 255);

  private UiParts() {
  }

  /**
   * The flat dark look, with no animated scrolling. Call before any window is built.
   */
  static void applyTheme() {
    FlatDarkLaf.setup();
    UIManager.put("ScrollPane.smoothScrolling", false);
    UIManager.put("Button.arc", 6);
    UIManager.put("Component.arc", 6);
    UIManager.put("ScrollBar.width", 10);
  }

  /**
   * A left-aligned row that wraps when narrow and is always exactly as tall as its contents.
   *
   * @param components Controls in the row.
   */
  static JPanel row(JComponent... components) {
    JPanel row = new WrapRow();
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (JComponent component : components) {
      row.add(component);
    }
    return row;
  }

  /**
   * A one-pixel horizontal line that spans its column.
   */
  static JComponent separator() {
    JPanel line = new JPanel();
    line.setBackground(UIManager.getColor("Separator.foreground"));
    line.setAlignmentX(Component.LEFT_ALIGNMENT);
    line.setPreferredSize(new Dimension(1, 1));
    line.setMinimumSize(new Dimension(1, 1));
    line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
    return line;
  }

  /**
   * Read-only wrapping text in the label font; a null color keeps the default.
   *
   * @param color Text color, or null.
   */
  static JTextArea textBlock(Color color) {
    JTextArea area = new JTextArea();
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setOpaque(false);
    area.setFont(UIManager.getFont("Label.font"));
    if (color != null) {
      area.setForeground(color);
    }
    return area;
  }

  /**
   * A flow of controls whose height follows the lines it wraps into, which a plain flow layout does not report.
   */
  private static final class WrapRow extends JPanel {
    private static final int GAP_X = 4;
    private static final int GAP_Y = 2;
    private int lastHeight;

    WrapRow() {
      super(new FlowLayout(FlowLayout.LEFT, GAP_X, GAP_Y));
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
          int height = getPreferredSize().height;
          if (height != lastHeight) {
            lastHeight = height;
            revalidate();
          }
        }
      });
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension natural = super.getPreferredSize();
      return getWidth() <= 0 ? natural : new Dimension(natural.width, wrappedHeight());
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /**
     * Follows the same line-breaking rule as {@link FlowLayout}.
     */
    private int wrappedHeight() {
      Insets insets = getInsets();
      int available = getWidth() - insets.left - insets.right - GAP_X * 2;
      int height = insets.top + insets.bottom + GAP_Y;
      int lineWidth = 0;
      int lineHeight = 0;
      for (Component child : getComponents()) {
        if (!child.isVisible()) {
          continue;
        }
        Dimension size = child.getPreferredSize();
        if (lineWidth == 0 || lineWidth + size.width <= available) {
          lineWidth += (lineWidth > 0 ? GAP_X : 0) + size.width;
          lineHeight = Math.max(lineHeight, size.height);
        } else {
          height += lineHeight + GAP_Y;
          lineWidth = size.width;
          lineHeight = size.height;
        }
      }
      return height + lineHeight + GAP_Y;
    }
  }
}
