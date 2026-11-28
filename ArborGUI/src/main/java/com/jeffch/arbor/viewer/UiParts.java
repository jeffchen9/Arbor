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
  /**
   * An L-shaped line down the left of an open sub-pane, so its rows read as part of the control above.
   */
  static final javax.swing.border.Border BRACKET = new javax.swing.border.AbstractBorder() {
    @Override
    public Insets getBorderInsets(Component component) {
      return new Insets(2, 16, 8, 0);
    }

    @Override
    public void paintBorder(Component component, Graphics g, int x, int y, int width, int height) {
      g.setColor(ACCENT);
      g.fillRect(x + 5, y, 2, height - 4);
      g.fillRect(x + 5, y + height - 4, 9, 2);
    }
  };

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
   * Stops a row from growing taller than it needs.
   *
   * @param component Row to limit.
   * @param <T>       Type of the row, returned unchanged.
   */
  static <T extends JComponent> T capped(T component) {
    component.setAlignmentX(Component.LEFT_ALIGNMENT);
    component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
    return component;
  }

  /**
   * Whether one side of a split pane is open, that is, the divider is not pushed against that edge.
   *
   * @param pane The split pane.
   * @param left The left side, or else the right.
   */
  static boolean sideShown(JSplitPane pane, boolean left) {
    int location = pane.getDividerLocation();
    return left ? location > pane.getInsets().left + 4 : location < collapsedRight(pane) - 4;
  }

  /**
   * Opens or closes one side of a split pane. Opening goes back to where the divider was, or to {@code width} when
   * that was itself closed.
   *
   * @param pane  The split pane.
   * @param left  The left side, or else the right.
   * @param show  Whether to open it.
   * @param width The side's width when there is no earlier one to go back to.
   */
  static void showSide(JSplitPane pane, boolean left, boolean show, int width) {
    if (show == sideShown(pane, left)) {
      return;
    }
    if (!show) {
      pane.setDividerLocation(left ? pane.getInsets().left : collapsedRight(pane));
      return;
    }
    int last = pane.getLastDividerLocation();
    boolean lastShown = left ? last > pane.getInsets().left + 4 : last > 0 && last < collapsedRight(pane) - 4;
    pane.setDividerLocation(lastShown ? last : left ? width : collapsedRight(pane) - width);
  }

  /**
   * Whether one side of a split pane is open. Ticking it opens the side and unticking closes it, and dragging the
   * divider to the edge or away from it ticks or unticks it, so the edge button and menu always show the truth.
   *
   * @param pane  The split pane.
   * @param left  The left side, or else the right.
   * @param width The side's width when it opens with no earlier width.
   */
  static ButtonModel sideModel(JSplitPane pane, boolean left, int width) {
    ButtonModel model = new JToggleButton.ToggleButtonModel();
    model.setSelected(true);
    model.addItemListener(event -> showSide(pane, left, model.isSelected(), width));
    pane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
            event -> model.setSelected(sideShown(pane, left)));
    return model;
  }

  private static int collapsedRight(JSplitPane pane) {
    return pane.getWidth() - pane.getDividerSize() - pane.getInsets().right;
  }

  /**
   * A tool window button as IntelliJ draws them on a window edge: an upright icon, then the label turned to run along
   * the edge. It is lit while its panel is open.
   */
  static final class EdgeButton extends JToggleButton {
    private static final int PAD = 8;
    private static final int GAP = 5;
    private final boolean clockwise;

    /**
     * @param text      Label.
     * @param icon      Icon, drawn upright before the label.
     * @param clockwise Whether the label reads top to bottom, as on a right edge; otherwise bottom to top.
     * @param model     Whether the panel is open, shared with its menu item.
     */
    EdgeButton(String text, Icon icon, boolean clockwise, ButtonModel model) {
      super(text, icon);
      this.clockwise = clockwise;
      setModel(model);
      setFocusable(false);
      setRolloverEnabled(true);
      setContentAreaFilled(false);
      setBorderPainted(false);
      setToolTipText("Show or hide the " + text.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public Dimension getPreferredSize() {
      FontMetrics metrics = getFontMetrics(getFont());
      int across = Math.max(metrics.getHeight(), getIcon().getIconWidth()) + 2 * GAP;
      int along = 2 * PAD + getIcon().getIconHeight() + GAP + metrics.stringWidth(getText());
      return new Dimension(across, along);
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      if (isSelected() || getModel().isRollover()) {
        g.setColor(new Color(255, 255, 255, isSelected() ? 38 : 18));
        g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
      }
      Icon icon = getIcon();
      int iconX = (getWidth() - icon.getIconWidth()) / 2;
      int iconY = clockwise ? PAD : getHeight() - PAD - icon.getIconHeight();
      icon.paintIcon(this, g, iconX, iconY);
      g.setFont(getFont());
      g.setColor(isSelected() ? getForeground() : MUTED);
      FontMetrics metrics = g.getFontMetrics();
      int start = PAD + icon.getIconHeight() + GAP;
      if (clockwise) {
        g.translate(getWidth(), 0);
        g.rotate(Math.PI / 2);
      } else {
        g.translate(0, getHeight());
        g.rotate(-Math.PI / 2);
      }
      int baseline = (getWidth() - metrics.getHeight()) / 2 + metrics.getAscent();
      g.drawString(getText(), start, baseline);
      g.dispose();
    }
  }

  /**
   * Gives a small button, such as × beside a row, one look: tight margin, no focus ring.
   *
   * @param button Button to style.
   * @param <T>    Type of the button, returned unchanged.
   */
  static <T extends AbstractButton> T small(T button) {
    button.setMargin(new Insets(0, 4, 0, 4));
    button.setFocusable(false);
    return button;
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
