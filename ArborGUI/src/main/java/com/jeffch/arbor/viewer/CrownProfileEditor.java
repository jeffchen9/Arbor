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

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The crown radius curve drawn as the crown seen from the side: height up the panel from the crown base to the top,
 * radius out to both sides of the trunk. Drag a point to move it, double-click to add one, right-click to remove one.
 */
class CrownProfileEditor extends JComponent {
  private static final int HEIGHT = 190;
  private static final int SIDE = 14;
  private static final int TOP = 12;
  private static final int BOTTOM = 22;
  private static final int HANDLE = 5;
  /**
   * Every height and radius is a whole number of tenths, so points can be no closer in height than this.
   */
  private static final double MIN_GAP = 0.1;
  private static final double STEPS = 10;
  private static final int LINE_REACH = 5;

  private final Consumer<String> commit;
  private final List<double[]> points = new ArrayList<>();
  /**
   * The radius at the panel's edge. It stays put during a drag so the drawing does not shift under the mouse.
   */
  private double scale;
  private int dragging = -1;
  private boolean moved;
  private boolean relative;
  private Consumer<double[][]> preview = shown -> {
  };

  /**
   * @param points The curve's [t, radius] points, in order.
   * @param commit Receives the curve's JSON after each finished change.
   */
  CrownProfileEditor(double[][] points, Consumer<String> commit) {
    this.commit = commit;
    setPoints(points);
    setFont(UIManager.getFont("Label.font"));
    setPreferredSize(new Dimension(200, HEIGHT));
    setMinimumSize(new Dimension(100, HEIGHT));
    setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
    setToolTipText("Drag a point to change the crown; click the outline to add a point, right-click one to remove it");
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        if (!isEnabled()) {
          return;
        }
        int hit = pointAt(event.getPoint());
        if (SwingUtilities.isRightMouseButton(event)) {
          if (hit >= 0) {
            removePoint(hit);
          }
          return;
        }
        if (hit < 0 && onOutline(event.getPoint())) {
          hit = addOnOutline(tAt(event.getY()));
        }
        dragging = hit;
        moved = false;
      }

      @Override
      public void mouseDragged(MouseEvent event) {
        if (dragging >= 0) {
          movePoint(dragging, tAt(event.getY()), radiusAt(event.getX()));
          moved = true;
        }
      }

      @Override
      public void mouseReleased(MouseEvent event) {
        if (dragging >= 0 && moved) {
          finish();
        }
        dragging = -1;
        rescale();
        repaint();
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
    addHierarchyListener(event -> {
      if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
        report();
      }
    });
    addPropertyChangeListener("enabled", event -> report());
  }

  /**
   * The points as the curve setting's JSON, such as {@code [[0, 4], [1, 2]]}.
   *
   * @param points The [t, radius] points.
   */
  static String json(double[][] points) {
    List<String> pairs = new ArrayList<>();
    for (double[] point : points) {
      pairs.add("[" + plain(point[0]) + ", " + plain(point[1]) + "]");
    }
    return "[" + String.join(", ", pairs) + "]";
  }

  /**
   * A number to two decimals, without trailing zeros.
   *
   * @param value Number to write.
   */
  private static String plain(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }

  private static double round(double value) {
    return Math.round(value * STEPS) / STEPS;
  }

  /**
   * Receives the points each time they change while the editor is on screen and usable, and null when it is not.
   *
   * @param shown Receives the points, or null.
   */
  void onPreview(Consumer<double[][]> shown) {
    preview = shown;
    report();
  }

  private void report() {
    preview.accept(isShowing() && isEnabled() && !points.isEmpty() ? points() : null);
  }

  /**
   * Replaces the points, unless a drag is under way.
   *
   * @param newPoints The curve's [t, radius] points, in order.
   */
  void setPoints(double[][] newPoints) {
    if (dragging >= 0) {
      return;
    }
    points.clear();
    for (double[] point : newPoints) {
      points.add(point.clone());
    }
    rescale();
    repaint();
    report();
  }

  /**
   * A copy of the points.
   */
  double[][] points() {
    return points.stream().map(double[]::clone).toArray(double[][]::new);
  }

  /**
   * Adds a point on the outline at a height, so the shape does not change until it is dragged.
   *
   * @param t Height, 0 at the crown base and 1 at the top.
   * @return Where the point went, or -1 when it is too close in height to another point.
   */
  int addOnOutline(double t) {
    double height = round(Math.clamp(t, 0, 1));
    return addPoint(height, radiusOfCurve(height));
  }

  /**
   * The curve's radius at a height: straight lines between points, the end values beyond them.
   *
   * @param t Height, 0 at the crown base and 1 at the top.
   */
  private double radiusOfCurve(double t) {
    double[] first = points.get(0);
    if (t <= first[0]) {
      return first[1];
    }
    for (int i = 1; i < points.size(); i++) {
      double[] low = points.get(i - 1);
      double[] high = points.get(i);
      if (t <= high[0]) {
        return low[1] + (high[1] - low[1]) * (t - low[0]) / (high[0] - low[0]);
      }
    }
    return points.get(points.size() - 1)[1];
  }

  /**
   * Moves a point, keeping it between its neighbours' heights and outside the trunk. Nothing is committed until the
   * drag ends.
   *
   * @param index  Point to move.
   * @param t      New height, 0 at the crown base and 1 at the top.
   * @param radius New radius in blocks.
   */
  void movePoint(int index, double t, double radius) {
    double low = index > 0 ? points.get(index - 1)[0] + MIN_GAP : 0;
    double high = index < points.size() - 1 ? points.get(index + 1)[0] - MIN_GAP : 1;
    points.set(index, new double[]{round(Math.clamp(t, low, high)), roundRadius(radius)});
    repaint();
    report();
  }

  /**
   * Adds a point in height order and commits.
   *
   * @param t      Height, 0 at the crown base and 1 at the top.
   * @param radius Radius in blocks.
   * @return Where the point went, or -1 when it is too close in height to another point.
   */
  int addPoint(double t, double radius) {
    double height = round(Math.clamp(t, 0, 1));
    int index = 0;
    while (index < points.size() && points.get(index)[0] < height) {
      index++;
    }
    boolean crowded = index < points.size() && points.get(index)[0] - height < MIN_GAP - 1e-9
            || index > 0 && height - points.get(index - 1)[0] < MIN_GAP - 1e-9;
    if (crowded) {
      return -1;
    }
    points.add(index, new double[]{height, roundRadius(radius)});
    finish();
    return index;
  }

  /**
   * Removes a point and commits, unless it is the only one.
   *
   * @param index Point to remove.
   */
  void removePoint(int index) {
    if (points.size() > 1) {
      points.remove(index);
      finish();
    }
  }

  /**
   * Sends the curve and redraws at a scale that fits it.
   */
  private void finish() {
    commit.accept(json(points()));
    rescale();
    repaint();
    report();
  }

  /**
   * A radius rounded to a tenth of a block, or to a hundredth of the tree's height when relative, and never negative.
   *
   * @param radius Radius to round.
   */
  private double roundRadius(double radius) {
    double steps = relative ? STEPS * STEPS : STEPS;
    return Math.round(Math.max(0, radius) * steps) / steps;
  }

  /**
   * Switches between a radius in blocks and one that is a share of the tree's height.
   *
   * @param isRelative Whether the radius is a share of the tree's height.
   */
  void setRelative(boolean isRelative) {
    if (relative != isRelative) {
      relative = isRelative;
      rescale();
      repaint();
    }
  }

  /**
   * A radius with its unit, such as "9 blocks" or "0.3 × height".
   *
   * @param radius Radius to describe.
   */
  private String radiusText(double radius) {
    return plain(radius) + (relative ? " × height" : " blocks");
  }

  /**
   * Fits the widest point with some room to drag outward: in steps of 2 blocks, or of 0.1 of the height when relative.
   */
  private void rescale() {
    double widest = points.stream().mapToDouble(point -> point[1]).max().orElse(0);
    scale = relative ? Math.max(0.2, Math.ceil(widest * 1.3 * 10) / 10) : Math.max(4, Math.ceil(widest * 1.3 / 2) * 2);
  }

  /**
   * Whether the mouse is on the outline, on either side of the trunk.
   *
   * @param mouse Mouse position.
   */
  private boolean onOutline(Point mouse) {
    List<double[]> outline = outline();
    for (int i = 1; i < outline.size(); i++) {
      double[] low = outline.get(i - 1);
      double[] high = outline.get(i);
      for (int side : new int[]{1, -1}) {
        double distance = Line2D.ptSegDist(x(low[1], side), y(low[0]), x(high[1], side), y(high[0]),
                mouse.x, mouse.y);
        if (distance <= LINE_REACH) {
          return true;
        }
      }
    }
    return false;
  }

  private int left() {
    return SIDE;
  }

  private int halfWidth() {
    return Math.max(1, (getWidth() - 2 * SIDE) / 2);
  }

  private int centerX() {
    return left() + halfWidth();
  }

  private int baseY() {
    return getHeight() - BOTTOM;
  }

  private int topY() {
    return TOP;
  }

  private double x(double radius, int side) {
    return centerX() + side * radius / scale * halfWidth();
  }

  private double y(double t) {
    return baseY() - t * (baseY() - topY());
  }

  private double tAt(int y) {
    return (baseY() - y) / (double) Math.max(1, baseY() - topY());
  }

  private double radiusAt(int x) {
    return Math.abs(x - centerX()) / (double) halfWidth() * scale;
  }

  /**
   * The point under the mouse on either side of the trunk, or -1.
   *
   * @param mouse Mouse position.
   */
  private int pointAt(Point mouse) {
    for (int i = 0; i < points.size(); i++) {
      double[] point = points.get(i);
      for (int side : new int[]{1, -1}) {
        if (mouse.distance(x(point[1], side), y(point[0])) <= HANDLE + 3) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * The outline from the crown base to the top, with the end values carried past the first and last points.
   */
  private List<double[]> outline() {
    List<double[]> outline = new ArrayList<>();
    outline.add(new double[]{0, points.get(0)[1]});
    for (double[] point : points) {
      if (point[0] > 0 && point[0] < 1) {
        outline.add(point);
      }
    }
    outline.add(new double[]{1, points.get(points.size() - 1)[1]});
    return outline;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(getFont().deriveFont(getFont().getSize2D() - 1));
    boolean on = isEnabled();
    Color line = on ? UiParts.ACCENT : UiParts.MUTED;
    Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0);

    g.setColor(UiParts.MUTED);
    g.setStroke(dashed);
    g.drawLine(left(), baseY(), getWidth() - SIDE, baseY());
    g.drawLine(left(), topY(), getWidth() - SIDE, topY());
    g.drawLine(centerX(), topY(), centerX(), baseY());
    FontMetrics metrics = g.getFontMetrics();
    g.drawString("top", left(), topY() + metrics.getAscent() + 2);
    g.drawString("crown base", left(), baseY() + metrics.getAscent() + 2);
    if (points.isEmpty()) {
      g.dispose();
      return;
    }
    // The widest point sets the radius: a line on each side at that distance, labelled below the right one.
    double widest = points.stream().mapToDouble(point -> point[1]).max().orElse(0);
    g.setColor(line);
    for (int side : new int[]{1, -1}) {
      int wx = (int) Math.round(x(widest, side));
      g.drawLine(wx, topY(), wx, baseY());
    }
    String radius = "radius " + radiusText(widest);
    int labelX = (int) Math.round(x(widest, 1)) - metrics.stringWidth(radius) / 2;
    // Clear of the "crown base" label and inside the panel; the panel edge wins when it is too narrow for both.
    labelX = Math.min(Math.max(labelX, left() + metrics.stringWidth("crown base") + 8),
            getWidth() - SIDE - metrics.stringWidth(radius));
    g.drawString(radius, labelX, baseY() + metrics.getAscent() + 2);
    List<double[]> outline = outline();
    Path2D.Double shape = new Path2D.Double();
    shape.moveTo(x(outline.get(0)[1], 1), y(0));
    for (double[] point : outline) {
      shape.lineTo(x(point[1], 1), y(point[0]));
    }
    for (int i = outline.size() - 1; i >= 0; i--) {
      shape.lineTo(x(outline.get(i)[1], -1), y(outline.get(i)[0]));
    }
    shape.closePath();
    g.setColor(new Color(line.getRed(), line.getGreen(), line.getBlue(), 45));
    g.fill(shape);
    g.setColor(line);
    g.setStroke(new BasicStroke(1.5f));
    g.draw(shape);

    for (int i = 0; i < points.size(); i++) {
      double[] point = points.get(i);
      for (int side : new int[]{1, -1}) {
        int px = (int) Math.round(x(point[1], side));
        int py = (int) Math.round(y(point[0]));
        g.setColor(i == dragging ? Color.WHITE : line);
        if (side == 1) {
          g.fillOval(px - HANDLE, py - HANDLE, HANDLE * 2, HANDLE * 2);
        } else {
          g.drawOval(px - HANDLE, py - HANDLE, HANDLE * 2, HANDLE * 2);
        }
      }
    }
    if (dragging >= 0) {
      double[] point = points.get(dragging);
      String text = "t " + plain(point[0]) + ", " + radiusText(point[1]);
      int px = (int) Math.round(x(point[1], 1));
      int tx = Math.min(px + HANDLE + 4, getWidth() - metrics.stringWidth(text) - 2);
      g.setColor(Color.WHITE);
      g.drawString(text, tx, (int) Math.round(y(point[0])) - HANDLE - 2);
    }
    g.dispose();
  }
}
