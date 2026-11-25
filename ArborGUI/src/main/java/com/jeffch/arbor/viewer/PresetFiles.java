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
import com.jeffch.arbor.preset.PresetException;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

/**
 * The File menu: new, open, save and exit, with a prompt before unsaved edits are lost. Runs on the Swing thread.
 */
final class PresetFiles {
  static final List<String> TEMPLATES = List.of("oak", "apple", "birch", "forest_beech", "spruce",
          "western_hemlock", "shore_pine", "palm", "willow");
  private static final String STARTING_TEMPLATE = "oak";
  private static final String NEW_NAME = "new_tree";

  private final JFrame frame;
  private final ViewerApp app;
  private final JMenuItem save = new JMenuItem("Save");
  private final JMenuItem saveAs = new JMenuItem("Save As...");
  private final JMenuItem revert = new JMenuItem("Revert");
  private Path folder = Files.isDirectory(Path.of("presets")) ? Path.of("presets") : Path.of("");

  /**
   * @param frame Parent of the file dialogs.
   * @param app   The viewer whose preset these files hold.
   */
  PresetFiles(JFrame frame, ViewerApp app) {
    this.frame = frame;
    this.app = app;
  }

  /**
   * A new untitled preset from the starting template.
   */
  static PresetDocument newDocument() {
    return PresetDocument.create(templateText(STARTING_TEMPLATE), NEW_NAME);
  }

  /**
   * A new untitled copy of a bundled preset.
   *
   * @param name Bundled preset name.
   */
  static PresetDocument fromTemplate(String name) {
    return PresetDocument.create(templateText(name), null);
  }

  /**
   * A bundled preset's text from inside the program.
   *
   * @param name Bundled preset name.
   */
  private static String templateText(String name) {
    try (InputStream in = PresetFiles.class.getResourceAsStream("/presets/" + name + ".json")) {
      if (in == null) {
        throw new IllegalStateException("missing bundled preset " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("cannot read bundled preset " + name + ": " + e.getMessage());
    }
  }

  /**
   * The file name with {@code .json} added when it has none.
   *
   * @param file File chosen in a dialog.
   */
  static Path withJsonExtension(Path file) {
    String name = file.getFileName().toString();
    return name.toLowerCase(Locale.ROOT).endsWith(".json") ? file : file.resolveSibling(name + ".json");
  }

  /**
   * Applies a value still being typed, then runs the action once that edit has landed.
   *
   * @param action Action to run.
   */
  private static void afterEdits(Runnable action) {
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner instanceof JFormattedTextField number) {
      try {
        number.commitEdit();
      } catch (ParseException ignored) {
        // Text that is not a number is dropped, as it is when focus leaves the field.
      }
    } else if (owner instanceof JTextField text) {
      text.postActionEvent();
    }
    SwingUtilities.invokeLater(action);
  }

  /**
   * The File menu: New, New from template, Open, Save, Save As, Revert and Exit, with their shortcuts.
   */
  JMenuBar menuBar() {
    JMenu menu = new JMenu("File");
    menu.setMnemonic(KeyEvent.VK_F);
    menu.add(item(new JMenuItem("New"), KeyEvent.VK_N, 0, () -> replaceWith(PresetFiles::newDocument)));
    JMenu templates = new JMenu("New From Preset");
    for (String name : TEMPLATES) {
      JMenuItem template = new JMenuItem(name);
      template.addActionListener(event -> afterEdits(() -> replaceWith(() -> fromTemplate(name))));
      templates.add(template);
    }
    menu.add(templates);
    menu.add(item(new JMenuItem("Open..."), KeyEvent.VK_O, 0, this::open));
    menu.addSeparator();
    menu.add(item(save, KeyEvent.VK_S, 0, this::saveCurrent));
    menu.add(item(saveAs, KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK, this::saveCurrentAs));
    revert.addActionListener(event -> afterEdits(this::revertCurrent));
    menu.add(revert);
    menu.addSeparator();
    menu.add(item(new JMenuItem("Exit"), KeyEvent.VK_Q, 0, this::exit));
    JMenuBar bar = new JMenuBar();
    bar.add(menu);
    update();
    return bar;
  }

  /**
   * Gives a menu item its Ctrl shortcut and action.
   *
   * @param item          Menu item.
   * @param key           Key code of the shortcut.
   * @param extraModifier Modifier added to Ctrl, or 0.
   * @param action        Runs when the item is chosen.
   */
  private JMenuItem item(JMenuItem item, int key, int extraModifier, Runnable action) {
    item.setAccelerator(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK | extraModifier));
    item.addActionListener(event -> afterEdits(action));
    return item;
  }

  /**
   * Matches the menu items to the current preset.
   */
  void update() {
    PresetDocument document = app.document();
    boolean valid = document != null && document.error() == null;
    save.setEnabled(valid && (document.dirty() || document.file() == null));
    saveAs.setEnabled(valid);
    revert.setEnabled(document != null && (document.dirty() || document.changedOnDisk()));
  }

  /**
   * Saves to the current file, asking for one if the preset has none. Returns whether it was saved.
   */
  boolean saveCurrent() {
    PresetDocument document = app.document();
    if (document == null || document.error() != null) {
      return false;
    }
    if (document.file() == null) {
      return saveCurrentAs();
    }
    if (!document.save()) {
      return reportSaveFailure(document);
    }
    app.presetSaved();
    return true;
  }

  /**
   * Saves under a new name chosen in a dialog, adding {@code .json} when needed. Returns whether it was saved.
   */
  private boolean saveCurrentAs() {
    PresetDocument document = app.document();
    if (document == null || document.error() != null) {
      return false;
    }
    JFileChooser chooser = chooser();
    chooser.setSelectedFile(folder.resolve(document.file() != null ? document.displayName()
            : document.preset().name() + ".json").toAbsolutePath().toFile());
    if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
      return false;
    }
    Path target = withJsonExtension(chooser.getSelectedFile().toPath());
    boolean sameFile = document.file() != null
            && target.toAbsolutePath().normalize().equals(document.file().toAbsolutePath().normalize());
    if (!sameFile && Files.exists(target) && JOptionPane.showConfirmDialog(frame,
            target.getFileName() + " already exists. Replace it?", "Save As", JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
      return false;
    }
    if (!document.saveAs(target)) {
      return reportSaveFailure(document);
    }
    remember(target);
    app.presetSaved();
    return true;
  }

  /**
   * Shows why a save failed. Returns false, so callers can return it directly.
   *
   * @param document Preset that failed to save.
   */
  private boolean reportSaveFailure(PresetDocument document) {
    JOptionPane.showMessageDialog(frame, document.error(), "Could not save", JOptionPane.ERROR_MESSAGE);
    document.clearSaveError();
    app.presetSaved();
    return false;
  }

  /**
   * Opens a preset file, asking first if there are unsaved changes.
   */
  private void open() {
    if (!readyToReplace()) {
      return;
    }
    JFileChooser chooser = chooser();
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path file = chooser.getSelectedFile().toPath();
    try {
      PresetDocument opened = PresetDocument.open(file);
      remember(file);
      app.showDocument(opened);
    } catch (PresetException e) {
      JOptionPane.showMessageDialog(frame, e.getMessage(), "Could not open preset", JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Starts a new preset from a bundled one, keeping the Simple sliders as they are, and asking first if there are
   * unsaved changes.
   *
   * @param template Bundled preset name.
   */
  void startFrom(String template) {
    afterEdits(() -> replaceWith(() -> {
      PresetDocument next = fromTemplate(template);
      PresetDocument current = app.document();
      if (current != null) {
        next.keepSimpleFrom(current);
      }
      return next;
    }));
  }

  /**
   * Switches to another preset when it is safe to.
   *
   * @param next Makes the preset to switch to.
   */
  private void replaceWith(java.util.function.Supplier<PresetDocument> next) {
    if (readyToReplace()) {
      app.showDocument(next.get());
    }
  }

  /**
   * Whether the current preset may be replaced: not during an export, and not without asking about unsaved changes.
   */
  private boolean readyToReplace() {
    if (app.exporting()) {
      JOptionPane.showMessageDialog(frame, "An export is running. Wait for it or cancel it first.",
              "Export running", JOptionPane.INFORMATION_MESSAGE);
      return false;
    }
    return confirmUnsaved();
  }

  /**
   * Discards unsaved changes after asking.
   */
  private void revertCurrent() {
    PresetDocument document = app.document();
    if (document != null && JOptionPane.showConfirmDialog(frame, "Discard changes to " + document.displayName() + "?",
            "Revert", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
      app.revertPreset();
    }
  }

  /**
   * Asks before unsaved edits are lost. Returns false if the user wants to keep working.
   */
  private boolean confirmUnsaved() {
    PresetDocument document = app.document();
    if (document == null || !document.dirty()) {
      return true;
    }
    if (document.error() != null) {
      return JOptionPane.showConfirmDialog(frame,
              document.displayName() + " has an invalid setting and cannot be saved. Discard your changes?",
              "Unsaved changes", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }
    int answer = JOptionPane.showConfirmDialog(frame, "Save changes to " + document.displayName() + "?",
            "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
    return answer == JOptionPane.NO_OPTION || answer == JOptionPane.YES_OPTION && saveCurrent();
  }

  /**
   * Closes the program unless the user chooses to keep working.
   */
  void exit() {
    afterEdits(() -> {
      if (confirmUnsaved()) {
        app.exitViewer();
      }
    });
  }

  /**
   * A file dialog for presets, opening in the last folder used.
   */
  private JFileChooser chooser() {
    JFileChooser chooser = new JFileChooser(folder.toAbsolutePath().toFile());
    chooser.setFileFilter(new FileNameExtensionFilter("Tree presets (*.json)", "json"));
    return chooser;
  }

  /**
   * Remembers a file's folder for the next dialog.
   *
   * @param file File chosen in a dialog.
   */
  private void remember(Path file) {
    Path parent = file.toAbsolutePath().getParent();
    if (parent != null) {
      folder = parent;
    }
  }
}
