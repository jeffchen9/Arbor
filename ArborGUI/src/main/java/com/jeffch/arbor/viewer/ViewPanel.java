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
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Shows frames rendered by OpenGL and turns mouse drags into camera moves.
 */
final class ViewPanel extends JPanel {
  /**
   * One line per camera action, as shown over the view.
   */
  static final String CAMERA_HELP = "<html>Drag to orbit.<br>Right, middle or Shift drag to pan.<br>"
          + "Scroll or pinch to zoom.<br>Scroll sideways to turn.</html>";
  private static final double WHEEL_ORBIT_PIXELS = 12;
  private static final int LEGEND_MARGIN = 10;
  private BufferedImage image;
  private List<Legend> legend = List.of();
  private boolean legendLeftHalf;
  private int lastX;
  private int lastY;

  /**
   * Sends camera input and view size to the viewer, in physical pixels.
   *
   * @param app Viewer that receives the input.
   */
  ViewPanel(ViewerApp app) {
    setBackground(new Color(23, 26, 31));
    setPreferredSize(new Dimension(800, 760));
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        lastX = event.getX();
        lastY = event.getY();
      }

      @Override
      public void mouseDragged(MouseEvent event) {
        double scale = scale();
        double dx = (event.getX() - lastX) * scale;
        double dy = (event.getY() - lastY) * scale;
        lastX = event.getX();
        lastY = event.getY();
        // Shift with a left drag pans too, for touchpads without a middle or easy right drag.
        if (SwingUtilities.isLeftMouseButton(event) && !event.isShiftDown()) {
          app.orbitCamera(dx, dy);
        } else {
          app.panCamera(dx, dy);
        }
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent event) {
        // Touchpads send a sideways two-finger swipe as a wheel turn with Shift held, and a pinch as one with Ctrl.
        if (event.isShiftDown()) {
          app.orbitCamera(-event.getPreciseWheelRotation() * WHEEL_ORBIT_PIXELS, 0);
        } else {
          app.zoomCamera(-event.getPreciseWheelRotation());
        }
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
    addMouseWheelListener(mouse);
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        double scale = scale();
        app.viewResized((int) Math.round(getWidth() * scale), (int) Math.round(getHeight() * scale));
      }
    });
  }

  /**
   * Shows controls over the top left of the view.
   *
   * @param controls Controls to show.
   */
  void showOver(JComponent controls) {
    setLayout(new FlowLayout(FlowLayout.LEFT, LEGEND_MARGIN, LEGEND_MARGIN));
    add(controls);
  }

  /**
   * The screen's scaling, so the view renders at full resolution on high-DPI displays.
   */
  private double scale() {
    return getGraphicsConfiguration() == null ? 1 : getGraphicsConfiguration().getDefaultTransform().getScaleX();
  }

  /**
   * Copies a bottom-up RGBA frame. Called from the render thread.
   *
   * @param pixels   Frame pixels, bottom row first.
   * @param width    Frame width.
   * @param height   Frame height.
   * @param legend   Branch level colors to show, or empty for none.
   * @param leftHalf Whether the branches are in the left half, as in split view.
   */
  void present(ByteBuffer pixels, int width, int height, List<Legend> legend, boolean leftHalf) {
    synchronized (this) {
      this.legend = legend;
      this.legendLeftHalf = leftHalf;
      if (image == null || image.getWidth() != width || image.getHeight() != height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      }
      int[] out = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
      for (int y = 0; y < height; y++) {
        int source = (height - 1 - y) * width * 4;
        int target = y * width;
        for (int x = 0; x < width; x++, source += 4) {
          out[target + x] = (pixels.get(source) & 255) << 16 | (pixels.get(source + 1) & 255) << 8
                  | pixels.get(source + 2) & 255;
        }
      }
    }
    repaint();
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    synchronized (this) {
      if (image != null) {
        graphics.drawImage(image, 0, 0, getWidth(), getHeight(), null);
      }
      paintLegend((Graphics2D) graphics);
    }
  }

  /**
   * Draws the branch level colors in the top right corner of the pane that shows the branches.
   *
   * @param graphics Panel graphics.
   */
  private void paintLegend(Graphics2D graphics) {
    if (legend.isEmpty()) {
      return;
    }
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    graphics.setFont(getFont());
    FontMetrics metrics = graphics.getFontMetrics();
    int line = metrics.getHeight() + 4;
    int box = metrics.getAscent() - 2;
    int textWidth = 0;
    for (Legend entry : legend) {
      textWidth = Math.max(textWidth, metrics.stringWidth(entry.label() + "(" + entry.name() + ")"));
    }
    int right = legendLeftHalf ? getWidth() / 2 - 1 : getWidth();
    int x = right - LEGEND_MARGIN - textWidth - box - 6;
    int y = LEGEND_MARGIN;
    for (Legend entry : legend) {
      float[] color = entry.color();
      graphics.setColor(new Color(color[0], color[1], color[2]));
      graphics.fillRect(x, y + (line - box) / 2, box, box);
      int baseline = y + (line - metrics.getHeight()) / 2 + metrics.getAscent();
      graphics.setColor(Color.WHITE);
      graphics.drawString(entry.label(), x + box + 6, baseline);
      graphics.setColor(Color.GRAY);
      graphics.drawString("(" + entry.name() + ")", x + box + 6 + metrics.stringWidth(entry.label()), baseline);
      y += line;
    }
  }

  /**
   * One branch level's color in the legend.
   *
   * @param level Level index, 0 for the trunk.
   * @param name  Level name.
   * @param color Line color, red, green and blue from 0 to 1.
   */
  record Legend(int level, String name, float[] color) {
    /**
     * The white part of the entry, such as "Level 1 ".
     */
    String label() {
      return "Level " + (level + 1) + " ";
    }
  }
}
