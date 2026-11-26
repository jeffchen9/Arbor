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

import com.jeffch.arbor.preset.BlockRole;
import com.jeffch.arbor.preset.BlockRule;
import com.jeffch.arbor.preset.LevelPreset;
import com.jeffch.arbor.preset.PresetDocument;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.jeffch.arbor.viewer.UiParts.capped;
import static com.jeffch.arbor.viewer.UiParts.small;

/**
 * Edits how one block type chooses its block: solid, noise, by height or by branch level.
 */
final class BlockRuleEditor {
  private static final Map<String, String> LABELS = Map.of("solid", "Solid", "noise", "Noise",
          "height", "By height", "level", "By branch level");
  private static final List<String> KINDS = List.of("solid", "noise", "height", "level");
  private static final int SPINNER_WIDTH = 70;
  private final PresetDocument document;
  private final Picker picker;
  private final Function<String, JComponent> weights;
  private final Consumer<Runnable> structural;
  private final Runnable edited;

  /**
   * @param document   Preset being edited.
   * @param picker     Makes block pickers.
   * @param weights    Makes the weighted list for a path.
   * @param structural Runs an edit that changes which rows exist.
   * @param edited     Told after an edit that keeps the same rows.
   */
  BlockRuleEditor(PresetDocument document, Picker picker, Function<String, JComponent> weights,
                  Consumer<Runnable> structural, Runnable edited) {
    this.document = document;
    this.picker = picker;
    this.weights = weights;
    this.structural = structural;
    this.edited = edited;
  }

  /**
   * The block type with this JSON key.
   *
   * @param key JSON key of the block type.
   */
  private static BlockRole role(String key) {
    for (BlockRole role : BlockRole.values()) {
      if (role.jsonKey().equals(key)) {
        return role;
      }
    }
    throw new IllegalArgumentException("unknown block type: " + key);
  }

  /**
   * The bands with one band's block changed.
   *
   * @param bands Current bands.
   * @param index Position of the band.
   * @param block New block.
   */
  private static List<BlockRule.Band> replaced(List<BlockRule.Band> bands, int index, String block) {
    List<BlockRule.Band> result = new ArrayList<>(bands);
    result.set(index, new BlockRule.Band(bands.get(index).below(), block));
    return result;
  }

  /**
   * The bands with one band's upper edge changed.
   *
   * @param bands Current bands.
   * @param index Position of the band.
   * @param below New upper edge, as a fraction of the tree's height.
   */
  private static List<BlockRule.Band> moved(List<BlockRule.Band> bands, int index, double below) {
    List<BlockRule.Band> result = new ArrayList<>(bands);
    result.set(index, new BlockRule.Band(below, bands.get(index).block()));
    return result;
  }

  /**
   * The bands with one removed.
   *
   * @param bands Current bands.
   * @param index Position of the band to remove.
   */
  private static List<BlockRule.Band> without(List<BlockRule.Band> bands, int index) {
    List<BlockRule.Band> result = new ArrayList<>(bands);
    result.remove(index);
    return result;
  }

  /**
   * Splits the top band in half, so every band keeps a valid range.
   *
   * @param bands Current bands.
   */
  private static List<BlockRule.Band> withNewBand(List<BlockRule.Band> bands) {
    List<BlockRule.Band> result = new ArrayList<>(bands);
    int last = bands.size() - 1;
    double bottom = last > 0 ? bands.get(last - 1).below() : 0;
    double middle = Math.round((bottom + 1) * 50) / 100.0;
    if (middle > bottom && middle < 1) {
      result.add(last, new BlockRule.Band(middle, bands.get(last).block()));
    }
    return result;
  }

  /**
   * A control with a label on its left.
   *
   * @param text    Label text.
   * @param control Control to label.
   */
  private static JComponent labeled(String text, JComponent control) {
    JPanel row = new JPanel(new BorderLayout(4, 0));
    row.add(new JLabel(text), BorderLayout.WEST);
    row.add(control, BorderLayout.CENTER);
    return capped(row);
  }

  /**
   * The rule dropdown, the remove button where allowed, and the controls for the current rule.
   *
   * @param path    Path of the block type.
   * @param roleKey Block list key, such as {@code log}.
   */
  JComponent control(String path, String roleKey) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    String kind = document.ruleKind(path);
    panel.add(ruleRow(path, roleKey, kind));
    switch (kind) {
      case "noise" -> addNoise(panel, path);
      case "height" -> addBands(panel, path, roleKey);
      case "level" -> addLevels(panel, path, roleKey);
      default -> addSolid(panel, path, roleKey);
    }
    return panel;
  }

  /**
   * The drop-down that picks how a block type chooses its block (solid, noise, by height or by level), with a remove
   * button for types that can be removed.
   *
   * @param path    Path of the block type.
   * @param roleKey Block list key, such as {@code log}.
   * @param kind    Current rule type.
   */
  private JComponent ruleRow(String path, String roleKey, String kind) {
    JComboBox<String> box = new JComboBox<>(KINDS.stream().map(LABELS::get).toArray(String[]::new));
    box.setSelectedItem(LABELS.get(kind));
    box.addActionListener(event -> {
      String chosen = KINDS.get(box.getSelectedIndex());
      if (!chosen.equals(kind)) {
        structural.accept(() -> document.setRuleKind(path, chosen));
      }
    });
    JPanel row = new JPanel(new BorderLayout(4, 0));
    row.add(new JLabel("Rule"), BorderLayout.WEST);
    row.add(box, BorderLayout.CENTER);
    boolean survival = path.startsWith("survival.");
    BlockRole role = role(roleKey);
    if (role != BlockRole.LOG && !(survival && role == BlockRole.LEAVES)) {
      JButton remove = new JButton("Remove");
      remove.setToolTipText("Stop using this block type");
      remove.addActionListener(event -> structural.accept(() -> document.removeRole(role, survival)));
      row.add(remove, BorderLayout.EAST);
    }
    return capped(row);
  }

  /**
   * A single block picker.
   *
   * @param panel   Panel to add to.
   * @param path    Path of the block type.
   * @param roleKey Block list key, such as {@code log}.
   */
  private void addSolid(JPanel panel, String path, String roleKey) {
    LinkedHashMap<String, Double> list = document.weights(path);
    String current = list.isEmpty() ? String.valueOf(document.field(path).fallback()) : list.keySet().iterator().next();
    panel.add(capped(picker.of(roleKey, current, chosen -> structural.accept(() -> document.setText(path, chosen)))));
  }

  /**
   * The weighted block list and its patch size.
   *
   * @param panel Panel to add to.
   * @param path  Path of the block type.
   */
  private void addNoise(JPanel panel, String path) {
    panel.add(capped(weights.apply(path)));
    JSpinner clump = SettingsPanel.spinner(document.clump(path), 1, BlockRule.MAX_CLUMP,
            new SliderScale(1, BlockRule.MAX_CLUMP, 1, false), true, value -> {
              document.setClump(path, (int) Math.rint(value));
              edited.run();
            });
    clump.setToolTipText("1 is speckled. Higher values form larger patches.");
    panel.add(labeled("Clump size", clump));
  }

  /**
   * One row per height band: its block, where it ends (every band but the last) and a remove button, plus a button to
   * add a band.
   *
   * @param panel   Panel to add to.
   * @param path    Path of the block type.
   * @param roleKey Block list key, such as {@code log}.
   */
  private void addBands(JPanel panel, String path, String roleKey) {
    List<BlockRule.Band> bands = document.bands(path);
    for (int i = 0; i < bands.size(); i++) {
      int index = i;
      BlockRule.Band band = bands.get(i);
      JPanel row = new JPanel(new BorderLayout(4, 0));
      row.add(picker.of(roleKey, band.block(), chosen -> structural.accept(() ->
              document.setBands(path, replaced(document.bands(path), index, chosen)))), BorderLayout.CENTER);
      JPanel right = new JPanel(new BorderLayout(4, 0));
      if (i < bands.size() - 1) {
        JSpinner top = SettingsPanel.spinner(Math.round(band.below() * 100), 1, 99,
                new SliderScale(1, 99, 5, false), true, value -> {
                  document.setBands(path, moved(document.bands(path), index, value / 100));
                  edited.run();
                });
        top.setPreferredSize(new Dimension(SPINNER_WIDTH, top.getPreferredSize().height));
        top.setToolTipText("Top of this band, as a percent of tree height");
        right.add(new JLabel("up to %"), BorderLayout.WEST);
        right.add(top, BorderLayout.CENTER);
      } else {
        right.add(new JLabel("to top"), BorderLayout.WEST);
      }
      if (bands.size() > 1) {
        JButton remove = small(new JButton("×"));
        remove.setToolTipText("Remove this band");
        remove.addActionListener(event -> structural.accept(() ->
                document.setBands(path, without(document.bands(path), index))));
        right.add(remove, BorderLayout.EAST);
      }
      row.add(right, BorderLayout.EAST);
      panel.add(capped(row));
    }
    JButton add = new JButton("Add band");
    add.addActionListener(event -> structural.accept(() -> document.setBands(path, withNewBand(document.bands(path)))));
    panel.add(UiParts.row(add));
  }

  /**
   * One block picker per branch level, plus the block for any level without its own.
   *
   * @param panel   Panel to add to.
   * @param path    Path of the block type.
   * @param roleKey Block list key, such as {@code log}.
   */
  private void addLevels(JPanel panel, String path, String roleKey) {
    Map<String, String> levels = document.levelBlocks(path);
    String other = document.otherBlock(path);
    for (LevelPreset level : document.preset().levels()) {
      String name = level.name();
      String block = levels.get(name);
      JPanel row = new JPanel(new BorderLayout(4, 0));
      JLabel label = new JLabel(name);
      label.setPreferredSize(new Dimension(SPINNER_WIDTH, label.getPreferredSize().height));
      row.add(label, BorderLayout.WEST);
      row.add(picker.of(roleKey, block != null ? block : other, chosen -> structural.accept(() ->
              document.setLevelBlock(path, name, chosen))), BorderLayout.CENTER);
      if (block != null) {
        JButton clear = small(new JButton("×"));
        clear.setToolTipText("Use the Other block for this level");
        clear.addActionListener(event -> structural.accept(() -> document.setLevelBlock(path, name, null)));
        row.add(clear, BorderLayout.EAST);
      }
      panel.add(capped(row));
    }
    JPanel row = new JPanel(new BorderLayout(4, 0));
    JLabel label = new JLabel("Other");
    label.setPreferredSize(new Dimension(SPINNER_WIDTH, label.getPreferredSize().height));
    row.add(label, BorderLayout.WEST);
    row.add(picker.of(roleKey, other, chosen -> structural.accept(() -> document.setOtherBlock(path, chosen))),
            BorderLayout.CENTER);
    panel.add(capped(row));
  }

  /**
   * A menu of the block types this mode can add.
   *
   * @param survival Whether this is the Survival blocks rather than the Creative ones.
   */
  JPopupMenu addMenu(boolean survival) {
    JPopupMenu menu = new JPopupMenu();
    List<BlockRole> active = document.activeRoles(survival);
    String prefix = survival ? "survival.blocks." : "blocks.";
    for (BlockRole role : BlockRole.values()) {
      boolean offered = !survival || role == BlockRole.JUNCTION || role == BlockRole.CUT_END;
      if (offered && !active.contains(role)) {
        JMenuItem item = new JMenuItem(SettingNames.of(prefix + role.jsonKey()));
        item.addActionListener(event -> structural.accept(() -> document.addRole(role, survival)));
        menu.add(item);
      }
    }
    return menu;
  }

  /**
   * Makes a control that picks a block for a role.
   */
  @FunctionalInterface
  interface Picker {
    JComponent of(String role, String current, Consumer<String> picked);
  }
}
