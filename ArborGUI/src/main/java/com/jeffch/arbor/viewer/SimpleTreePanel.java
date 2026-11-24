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

import com.jeffch.arbor.preset.PresetDocument;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Simple view of the Tree tab: a starting tree and ten whole-tree sliders.
 */
final class SimpleTreePanel extends JPanel {
  private static final int HALF_TRAVEL = 100;
  private static final double PRECISION = 100;
  private static final List<Control> CONTROLS = List.of(
          new Control("height", 0.5, 2, "×", "Makes the trunk shorter or taller"),
          new Control("crownWidth", 0.5, 2, "×", "Makes the crown narrower or wider"),
          new Control("trunkThickness", 0.5, 2, "×", "Makes the trunk thinner or thicker"),
          new Control("branchDensity", 0.5, 2, "×", "Changes the total number of branches"),
          new Control("branchAngle", -30, 30, "°", "Turns branches toward or away from their parent"),
          new Control("droop", -1, 1, "", "Lifts branches up or lets them hang down"),
          new Control("twist", 0, 3, "×", "Makes branches straighter or more wandering"),
          new Control("leafFullness", 0.5, 1.5, "×", "Makes foliage thinner or fuller"),
          new Control("windLean", 0, 1, "", "Leans the tree in a steady wind"),
          new Control("sizeVariety", 0, 0.5, "", "How much tree size differs between seeds"));
  private final PresetDocument document;
  private final Runnable edited;
  private final List<JSlider> sliders = new ArrayList<>();
  private boolean resetting;
  /**
   * @param document  Preset being edited.
   * @param edited    Told after a slider changes the preset.
   * @param startFrom Asked to start over from a bundled preset.
   * @param newRandom Asked to show a new random tree.
   */
  SimpleTreePanel(PresetDocument document, Runnable edited, Consumer<String> startFrom, Runnable newRandom) {
    this.document = document;
    this.edited = edited;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setAlignmentX(LEFT_ALIGNMENT);
    setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
    add(startingTree(startFrom));
    CONTROLS.forEach(control -> add(sliderRow(control)));
    JButton reset = new JButton("Reset sliders");
    reset.addActionListener(event -> reset());
    JButton random = new JButton("New random tree");
    random.addActionListener(event -> newRandom.run());
    add(UiParts.row(reset, random));
  }

  /**
   * A small label for a slider's end or center.
   *
   * @param text Label text.
   */
  private static JLabel mark(String text) {
    JLabel label = new JLabel(text);
    label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() - 2));
    return label;
  }

  /**
   * The value at a slider position. Each half has its own scale, so neutral is always in the middle.
   *
   * @param control The slider.
   * @param center  Neutral value.
   * @param tick    Slider position; negative is left of center.
   */
  private static double valueAt(Control control, double center, int tick) {
    double end = tick < 0 ? control.low() : control.high();
    double value = center + (end - center) * Math.abs(tick) / HALF_TRAVEL;
    return tick == 0 ? center : Math.rint(value * PRECISION) / PRECISION;
  }

  /**
   * The slider position for a stored value, the reverse of {@link #valueAt}.
   *
   * @param control The slider.
   * @param center  Neutral value.
   * @param value   Stored value.
   */
  private static int tickOf(Control control, double center, double value) {
    double end = value < center ? control.low() : control.high();
    if (end == center) {
      return 0;
    }
    int travel = (int) Math.rint((value - center) / (end - center) * HALF_TRAVEL);
    return value < center ? -travel : travel;
  }

  /**
   * Stops a row from growing taller than it needs.
   *
   * @param component Row to limit.
   */
  private static JComponent capped(JComponent component) {
    component.setAlignmentX(Component.LEFT_ALIGNMENT);
    component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
    return component;
  }

  /**
   * A list that starts over from a bundled tree, showing the current tree until one is picked.
   *
   * @param startFrom Asked to start over from a bundled preset.
   */
  private JComponent startingTree(Consumer<String> startFrom) {
    // A separate entry for this tree keeps every bundled tree selectable, even one with the same name.
    String current = "Current: " + document.preset().name();
    JComboBox<String> box = new JComboBox<>();
    box.addItem(current);
    PresetFiles.TEMPLATES.forEach(box::addItem);
    box.setSelectedItem(current);
    box.addActionListener(event -> {
      String chosen = (String) box.getSelectedItem();
      if (!current.equals(chosen)) {
        // The list shows this tree until the preset is really replaced, which the user may cancel.
        box.setSelectedItem(current);
        startFrom.accept(chosen);
      }
    });
    JPanel row = new JPanel(new BorderLayout(6, 0));
    row.add(new JLabel("Starting tree"), BorderLayout.WEST);
    row.add(box, BorderLayout.CENTER);
    return capped(row);
  }

  /**
   * One slider with its name, its current value and labels at both ends and the neutral center. Moving it stores the
   * new value and regenerates; the center removes the stored value.
   *
   * @param control The slider.
   */
  private JComponent sliderRow(Control control) {
    double center = Math.max(control.low(), Math.min(control.high(), document.simpleNeutral(control.key())));
    int min = center <= control.low() ? 0 : -HALF_TRAVEL;
    int max = center >= control.high() ? 0 : HALF_TRAVEL;
    int tick = tickOf(control, center, document.simpleValue(control.key()));
    JSlider slider = new JSlider(min, max, Math.max(min, Math.min(max, tick)));
    DecimalFormat format = new DecimalFormat("0.##");
    Hashtable<Integer, JLabel> marks = new Hashtable<Integer, JLabel>();
    marks.put(min, mark(format.format(min == 0 ? center : control.low())));
    // A preset's own size variety can be above the slider's range, so the end mark shows the real value.
    marks.put(max, mark(format.format(max == 0 ? document.simpleNeutral(control.key()) : control.high())));
    if (min < 0 && max > 0) {
      marks.put(0, mark(format.format(center)));
    }
    slider.setLabelTable(marks);
    slider.setPaintLabels(true);
    slider.setToolTipText(control.tip());
    JLabel value = new JLabel(format.format(document.simpleValue(control.key())) + control.unit());
    value.setHorizontalAlignment(SwingConstants.RIGHT);
    slider.addChangeListener(event -> {
      // At the center the stored value is removed, so a preset's own size variety stays exact.
      double chosen = slider.getValue() == 0 ? document.simpleNeutral(control.key())
              : valueAt(control, center, slider.getValue());
      value.setText(format.format(chosen) + control.unit());
      if (!resetting) {
        document.setSimpleValue(control.key(), chosen);
        edited.run();
      }
    });
    sliders.add(slider);
    JPanel title = new JPanel(new BorderLayout());
    JLabel name = new JLabel(SettingNames.of("simple." + control.key()));
    name.setToolTipText(control.tip());
    title.add(name, BorderLayout.WEST);
    title.add(value, BorderLayout.EAST);
    JPanel row = new JPanel(new BorderLayout());
    row.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    row.add(title, BorderLayout.NORTH);
    row.add(slider, BorderLayout.CENTER);
    return capped(row);
  }

  /**
   * Puts every slider back to center and regenerates once.
   */
  private void reset() {
    document.resetSimple();
    resetting = true;
    sliders.forEach(slider -> slider.setValue(0));
    resetting = false;
    edited.run();
  }

  /**
   * One slider.
   *
   * @param key  Name inside the preset's simple section.
   * @param low  Value at the left end.
   * @param high Value at the right end.
   * @param unit Text shown after the value.
   * @param tip  Plain description of the effect.
   */
  private record Control(String key, double low, double high, String unit, String tip) {
  }
}
