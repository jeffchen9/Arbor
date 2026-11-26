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

import com.jeffch.arbor.export.BatchExporter;
import com.jeffch.arbor.export.SchematicWriter;
import com.jeffch.arbor.preset.PresetDocument;
import com.jeffch.arbor.preset.SpeciesPreset;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Batch .schem export from the editor. It uses the current settings, including unsaved edits, and random seeds.
 */
final class ExportControls extends JPanel implements AutoCloseable {
  private static final int MAX_COUNT = 100_000;
  private static final int PROGRESS_MILLIS = 150;

  private final PresetDocument document;
  private final JSpinner count = new JSpinner(new SpinnerNumberModel(10, 1, MAX_COUNT, 5));
  private final JTextField folder = new JTextField();
  private final JTextField name = new JTextField();
  private final JButton browse = new JButton("Browse...");
  private final JButton export = new JButton("Export");
  private final JButton cancel = new JButton("Cancel");
  private final JProgressBar progress = new JProgressBar();
  private final JTextArea result = UiParts.textBlock(null);
  private final Timer timer = new Timer(PROGRESS_MILLIS, event -> refresh());
  private boolean filling;
  private boolean customFolder;
  private boolean customName;
  private volatile boolean cancelled;
  private volatile int finished;
  private volatile int total;
  private volatile String message = "";
  private Thread thread;

  /**
   * Builds the folder, file name and count fields with the export and cancel buttons. The folder and name follow the
   * preset until the user types in them.
   *
   * @param document Preset being edited.
   */
  ExportControls(PresetDocument document) {
    this.document = document;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    JPanel target = new JPanel(new GridBagLayout());
    JPanel folderRow = new JPanel(new BorderLayout(4, 0));
    folderRow.add(folder, BorderLayout.CENTER);
    folderRow.add(browse, BorderLayout.EAST);
    addRow(target, 0, "Folder", folderRow);
    addRow(target, 1, "File name", name);
    addRow(target, 2, "Number of trees", count);
    target.setMaximumSize(new Dimension(Integer.MAX_VALUE, target.getPreferredSize().height));
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
    controls.add(export);
    controls.add(Box.createHorizontalStrut(4));
    controls.add(cancel);
    controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, controls.getPreferredSize().height));
    for (JComponent part : new JComponent[]{target, controls, progress, result}) {
      part.setAlignmentX(Component.LEFT_ALIGNMENT);
      add(part);
    }
    add(Box.createVerticalGlue());
    progress.setStringPainted(true);
    progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, progress.getPreferredSize().height));
    result.setFocusable(false);
    onTyped(folder, () -> customFolder = true);
    onTyped(name, () -> customName = true);
    browse.addActionListener(event -> chooseFolder());
    export.addActionListener(event -> start(document.preset(), (Integer) count.getValue()));
    cancel.addActionListener(event -> cancelled = true);
    refresh();
  }

  /**
   * A labeled field in the form's grid.
   *
   * @param panel Form panel.
   * @param row   Grid row.
   * @param label Label text.
   * @param field Field to add.
   */
  private static void addRow(JPanel panel, int row, String label, JComponent field) {
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridy = row;
    constraints.insets = new Insets(2, 0, 2, 6);
    constraints.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel(label), constraints);
    constraints.gridx = 1;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(2, 0, 2, 0);
    panel.add(field, constraints);
  }

  /**
   * Sets a field's text only when it differs, so the cursor does not jump.
   *
   * @param field Field to update.
   * @param text  New text.
   */
  private static void setIfChanged(JTextField field, String text) {
    if (!field.getText().equals(text)) {
      field.setText(text);
    }
  }

  /**
   * Runs {@code typed} when the user edits the field, but not when the program fills it in.
   *
   * @param field Field to watch.
   * @param typed Runs after a user edit.
   */
  private void onTyped(JTextField field, Runnable typed) {
    field.getDocument().addDocumentListener(new DocumentListener() {
      private void changed() {
        if (!filling) {
          typed.run();
          refresh();
        }
      }

      @Override
      public void insertUpdate(DocumentEvent event) {
        changed();
      }

      @Override
      public void removeUpdate(DocumentEvent event) {
        changed();
      }

      @Override
      public void changedUpdate(DocumentEvent event) {
        changed();
      }
    });
  }

  /**
   * Picks a folder with a dialog that opens at the typed one.
   */
  private void chooseFolder() {
    JFileChooser chooser = new JFileChooser(BatchExporter.browseStart(folder.getText()).toFile());
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setDialogTitle("Export Folder");
    if (chooser.showDialog(this, "Select") == JFileChooser.APPROVE_OPTION) {
      folder.setText(chooser.getSelectedFile().getPath());
    }
  }

  /**
   * Whether an export is in progress.
   */
  boolean running() {
    return thread != null && thread.isAlive();
  }

  /**
   * Updates the buttons, target and progress. The target follows the preset until the user edits it.
   */
  void refresh() {
    boolean running = thread != null && thread.isAlive();
    filling = true;
    if (!customFolder) {
      setIfChanged(folder, BatchExporter.defaultFolder(document.preset()).toString());
    }
    if (!customName) {
      setIfChanged(name, BatchExporter.defaultName(document.preset()));
    }
    filling = false;
    String problem = targetProblem();
    export.setEnabled(!running && document.error() == null && problem == null);
    count.setEnabled(!running);
    folder.setEnabled(!running);
    name.setEnabled(!running);
    browse.setEnabled(!running);
    cancel.setEnabled(running);
    progress.setVisible(running);
    progress.setMaximum(Math.max(1, total));
    progress.setValue(finished);
    progress.setString(finished + " of " + total + " trees");
    result.setText(problem != null ? problem : message);
    if (!running) {
      timer.stop();
    }
  }

  /**
   * Why the typed folder or name cannot be used, or null when both can.
   */
  private String targetProblem() {
    if (BatchExporter.safeFileName(name.getText()).isEmpty()) {
      return "Enter a file name.";
    }
    if (folder.getText().isBlank()) {
      return "Choose a folder.";
    }
    try {
      Path.of(folder.getText().trim());
      return null;
    } catch (InvalidPathException e) {
      return "That folder path is not valid.";
    }
  }

  /**
   * Starts an export on a background thread, showing progress as trees finish.
   *
   * @param preset Preset to export.
   * @param trees  Number of trees.
   */
  private void start(SpeciesPreset preset, int trees) {
    Path target = Path.of(folder.getText().trim());
    String fileName = name.getText();
    cancelled = false;
    finished = 0;
    total = trees;
    message = "";
    thread = new Thread(() -> {
      try {
        int written = BatchExporter.export(preset, trees, target, fileName,
                SchematicWriter.DEFAULT_DATA_VERSION, done -> finished = Math.max(finished, done), () -> cancelled);
        message = (cancelled ? "Cancelled after " : "Wrote ") + written + " trees to " + target.toAbsolutePath();
      } catch (Exception | OutOfMemoryError e) {
        message = "Export failed: " + e.getMessage();
      }
    }, "schematic-export");
    thread.setDaemon(true);
    thread.start();
    timer.start();
    refresh();
  }

  /**
   * Stops after the trees in progress, so closing the viewer never leaves a half-written file.
   */
  @Override
  public void close() throws InterruptedException {
    cancelled = true;
    if (thread != null) {
      thread.join();
    }
  }
}
