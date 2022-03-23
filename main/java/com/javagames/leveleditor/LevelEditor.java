package com.javagames.leveleditor;

import com.javagames.leveleditor.dialogs.RequestSizeDialog;
import com.javagames.leveleditor.model.ImageSize;
import com.javagames.leveleditor.model.LevelData;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

public class LevelEditor extends JPanel {
    private static final String APP_NAME = "JavaGames Level Editor";
    private static final int DEFAULT_TILE_WIDTH = 16;
    private static final int DEFAULT_TILE_HEIGHT = 16;
    private static final int DEFAULT_CANVAS_TILES_X = 64;
    private static final int DEFAULT_CANVAS_TILES_Y = 32;
    private static final int DEFAULT_INITIAL_LAYERS = 1;
    private static final int DEFAULT_SCROLL_UNIT = 16;
    private static final FileNameExtensionFilter FNX_FILTER_LEVEL
            = new FileNameExtensionFilter("Level files", "level");

    private final JFrame frame;
    private final LevelCanvas canvas;
    private final JLabel tileSizeLabel;
    private final JLabel levelSizeLabel;
    private final JLabel layerInfoLabel;
    private final JLabel scaleLabel;
    private final SpritePanel palette;

    private LevelData levelData;
    private boolean modified;

    public LevelEditor(JFrame frame) {
        super(new BorderLayout());
        this.frame = frame;

        levelData = LevelData.forNewBlankCanvas(
                ImageSize.of(DEFAULT_CANVAS_TILES_X, DEFAULT_CANVAS_TILES_Y),
                ImageSize.of(DEFAULT_TILE_WIDTH, DEFAULT_TILE_HEIGHT),
                DEFAULT_INITIAL_LAYERS);

        // +- jframe (owning parent) --------------------------
        // | +- Level Editor (jpanel - this - border layout) --
        // | | +- scroll pane (default scroll layout) ---------
        // | | | +- jpanel (box layout, x axis + l/r hglue) ---
        // | | | |
        // | | | |     +- canvas (pref/min/max size) ----------
        // | | | |     |

        // build canvas
        canvas = new LevelCanvas(this, levelData, null);

        // build resizeable jpanel (box layout + horiz glue + pref/min/max size)
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.X_AXIS));
        inner.setBackground(Color.LIGHT_GRAY);
        inner.add(Box.createHorizontalGlue());
        inner.add(canvas);
        inner.add(Box.createHorizontalGlue());

        // build resizeable scroll pane
        JScrollPane scrollPane = new JScrollPane(inner);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(DEFAULT_SCROLL_UNIT);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(DEFAULT_SCROLL_UNIT);

        // LevelEditor (this - jpanel) -> add scroll pane
        this.add(scrollPane);

        // build and add status bar
        levelSizeLabel = new JLabel();
        setLevelSizeText();

        tileSizeLabel = new JLabel();
        setTileSizeText();

        layerInfoLabel = new JLabel();
        setLayerInfoText();

        scaleLabel = new JLabel();
        setScaleText();

        JPanel statusBar = new JPanel();
        statusBar.setLayout(new BoxLayout(statusBar, BoxLayout.X_AXIS));    // defaults left to right layout along X
        statusBar.setBorder(new BevelBorder(BevelBorder.LOWERED));
        statusBar.add(levelSizeLabel);
        statusBar.add(tileSizeLabel);
        statusBar.add(layerInfoLabel);
        statusBar.add(scaleLabel);
        this.add(statusBar, BorderLayout.SOUTH);

        palette = SpritePanel.createPanel(canvas);

        setTitle();

        frame.setLayout(new BorderLayout());
        frame.setJMenuBar(createMenuBar());
        frame.setContentPane(this);

        // setup to let the application handle closing event (and possibly override close)
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onFrameClosing();
            }
        });

        // size/locate/pack/display the window
        frame.setPreferredSize(new Dimension(1200, 700));
        frame.pack();
        frame.setLocationRelativeTo(null);  // must come after set-size and pack
    }

    // ------------------ MENU COMMAND HANDLERS ------------------

    public void cmdNewLevel() {
        saveLevelIfNeededAndThen(this::clearCanvas);
    }

    public void cmdOpenLevel() {
        saveLevelIfNeededAndThen(this::getNameAndOpenLevel);
    }

    public void cmdOpenPalette() {
        File paletteFile = openPaletteDialog();
        if (paletteFile != null) {
            try {
                levelData.setPaletteFile(paletteFile);  // last possible throw point
                palette.setFrameVisible(false);
                palette.resetFromLevelData(levelData);
                canvas.onLevelLoaded(levelData, palette);
                palette.setFrameLocationRelativeTo(this.frame);
                palette.invalidate();
                palette.setFrameVisible(true);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, e.getMessage(),
                        "Error reading palette file", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void cmdSaveLevel() {
        saveLevelInternal();
    }

    public void cmdSaveLevelAs() {
        File levelFile = saveLevelDialog();
        if (levelFile != null) {
            saveLevel(levelFile);
        }
    }

    public void cmdSetTileSize() {
        if (saveLevelInternal()) {
            ImageSize tileSize = setTileSizeDialog();
            if (tileSize != null) {
                levelData.setTileSize(tileSize);
                if (palette != null) {
                    palette.setTileSize(levelData.getTileSize());
                    palette.invalidate();
                    palette.repaint();
                }
                clearCanvas();
                setTileSizeText();
            }
        }
    }

    private void cmdSetScale() {
        canvas.setScale(setScaleDialog());
        onScaleChange();
    }

    public void cmdSelectLayer() {
        canvas.setCurrentLayer(selectLayerDialog());
        setLayerInfoText();
    }

    public void cmdAddLayer() {
        levelData.addLayer();
        canvas.onLayerAdded();
        setLayerInfoText();
    }

    public void cmdSetLevelSizeInTiles() {
        if (saveLevelInternal()) {
            ImageSize levelSizeInTiles = setLevelSizeDialog();
            if (levelSizeInTiles != null) {
                ImageSize tileSize = levelData.getTileSize();
                levelData.setDataSize(levelSizeInTiles.multipleOf(tileSize));
                clearCanvas();
                setLevelSizeText();
                frame.pack();
                canvas.invalidate();
            }
        }
    }

    public void cmdExit() {
        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    }

    // ------------------ Custom dialogs for setting layer, level, and tile sizes

    private ImageSize setTileSizeDialog() {
        RequestSizeDialog rsDialog = new RequestSizeDialog(frame, "Tile Size (in Pixels)");
        rsDialog.pack();
        rsDialog.setLocationRelativeTo(frame);      // must come after pack
        rsDialog.setVisible(true);
        return rsDialog.getEnteredSizes();
    }

    private ImageSize setLevelSizeDialog() {
        RequestSizeDialog rsDialog = new RequestSizeDialog(frame, "Level Size (in Tiles)");
        rsDialog.pack();
        rsDialog.setLocationRelativeTo(frame);       // must come after pack
        rsDialog.setVisible(true);
        return rsDialog.getEnteredSizes();
    }

    private int selectLayerDialog() {
        int oldLayer = canvas.getCurrentLayer() + 1;
        try {
            String newLayerStr = JOptionPane.showInputDialog(this,
                    "Enter a layer to work on:", "Select Layer",
                    JOptionPane.QUESTION_MESSAGE);
            int newLayer = Integer.parseInt(newLayerStr);
            if (newLayer < 1 || newLayer > levelData.getLayers()) {
                throw new NumberFormatException();
            }
            return newLayer - 1;
        } catch (NumberFormatException nfx) {
            int maxLevels = levelData.getLayers();
            JOptionPane.showMessageDialog(this, "Invalid level number entered.\n" +
                    "Enter a value between 1 and " + maxLevels, "Error", JOptionPane.ERROR_MESSAGE);
            return oldLayer;
        }
    }

    private double setScaleDialog() {
        double oldScale = canvas.getScale();
        try {
            String newScaleStr = JOptionPane.showInputDialog(this,
                    "Enter a magnification scale factor:", "Scale",
                    JOptionPane.QUESTION_MESSAGE);
            double newScale = Double.parseDouble(newScaleStr);
            if (newScale < 0.1d || newScale > 5.0d) {
                throw new NumberFormatException();
            }
            return newScale;
        } catch (NumberFormatException nfx) {
            JOptionPane.showMessageDialog(this, "Invalid magnification value entered.\nEnter a "
                    + "decimal value between 0.1 and 5.0", "Error", JOptionPane.ERROR_MESSAGE);
            return oldScale;
        }
    }

    // ------------------ Level Management

    private void clearCanvas() {
        levelData.clearLevelFile();
        canvas.onLevelLoaded(levelData, palette);
        setModified(false);
    }

    private void getNameAndOpenLevel() {
        File levelFile = openLevelDialog();
        if (levelFile != null) {
            openLevel(levelFile);
        }
    }

    private void saveLevelIfNeededAndThen(Runnable doThis) {
        if (modified) {
            Boolean saveLevel = querySaveLevel();
            if (saveLevel != null) {
                if (saveLevel) {
                    File levelFile = levelData.getLevelFile();
                    if (levelFile == null) {
                        levelFile = saveLevelDialog();
                    }
                    if (levelFile != null) {
                        saveLevel(levelFile);
                    }
                } else {
                    setModified(false);
                    levelData.clearLevelFile();
                }
            }
        }
        if (!modified) {
            doThis.run();
        }
    }

    private boolean saveLevelInternal() {
        if (!modified) {
            return true;
        }
        File levelFile = levelData.getLevelFile();
        if (levelFile == null) {
            levelFile = saveLevelDialog();
            if (levelFile == null) {
                return false;
            }
        }
        saveLevel(levelFile);
        return true;
    }

    private File openLevelDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open level");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.addChoosableFileFilter(FNX_FILTER_LEVEL);
        fileChooser.setAcceptAllFileFilterUsed(false);
        int state = fileChooser.showOpenDialog(frame);
        return switch (state) {
            case JFileChooser.ERROR_OPTION, JFileChooser.CANCEL_OPTION -> null;
            default -> fileChooser.getSelectedFile();
        };
    }

    private File saveLevelDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save level");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.addChoosableFileFilter(FNX_FILTER_LEVEL);
        fileChooser.setAcceptAllFileFilterUsed(false);
        int state = fileChooser.showSaveDialog(frame);
        return switch (state) {
            case JFileChooser.ERROR_OPTION, JFileChooser.CANCEL_OPTION -> null;
            default -> ensureExtension(fileChooser.getSelectedFile(), FNX_FILTER_LEVEL.getExtensions()[0]);
        };
    }

    private File ensureExtension(File file, String ext) {
        String absPath = file.getAbsolutePath();
        if (!absPath.substring(absPath.lastIndexOf('.') + 1).equals(ext)) {
            absPath += "." + ext;
            return new File(absPath);
        }
        return file;
    }

    private void openLevel(File levelFile) {
        try {
            levelData = LevelData.forLoadingALevel(levelFile);
            palette.setFrameVisible(false);
            palette.resetFromLevelData(levelData);
            canvas.onLevelLoaded(levelData, palette);
            palette.setFrameLocationRelativeTo(this.frame);
            palette.invalidate();
            palette.setFrameVisible(true);
            setTitle();
            setLevelSizeText();
            setTileSizeText();
            setLayerInfoText();
            setScaleText();
            setModified(false);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(),
                    "Error reading level file", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Boolean querySaveLevel() {
        String[] buttonLabels = new String[] { "Yes", "No", "Cancel" };
        String defaultOption = buttonLabels[0];
        int option = JOptionPane.showOptionDialog(frame, "Level modified. Save first?","Warning",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, buttonLabels, defaultOption);
        return switch(option) {
            case JOptionPane.YES_OPTION -> true;
            case JOptionPane.NO_OPTION -> false;
            default -> null;
        };
    }

    private void saveLevel(File levelFile) {
        try {
            levelData.saveXmlLevelFile(levelFile);
            setTitle();
            setModified(false);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(),
                    "Error writing level file", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --------------- Palette Management

    private void closePalette() {
        if (palette != null) {
            palette.close();
        }
    }

    private File openPaletteDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Open palette");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG Files", "png"));
        fileChooser.setAcceptAllFileFilterUsed(false);
        int state = fileChooser.showOpenDialog(frame);
        return switch (state) {
            case JFileChooser.ERROR_OPTION, JFileChooser.CANCEL_OPTION -> null;
            default -> fileChooser.getSelectedFile();
        };
    }

    // ------------------ Frame Management

    private void setTitle() {
        //String nameOnly = fileName != null ? Paths.get(fileName).getFileName().toString() : null;
        File file = levelData.getLevelFile();
        frame.setTitle(APP_NAME + (file != null ? (" | " + file + (modified ? " *" : "")) : ""));
    }

    private void setLevelSizeText() {
        ImageSize dataSize = levelData.getDataSize();
        levelSizeLabel.setText(" Level size: " + dataSize.width() + " x " + dataSize.height() + " tiles");
        levelSizeLabel.revalidate();
        levelSizeLabel.repaint();
    }

    private void setTileSizeText() {
        ImageSize tileSize = levelData.getTileSize();
        tileSizeLabel.setText("  |  Tile size: " + tileSize.width() + " x " + tileSize.height() + " pixels");
        tileSizeLabel.repaint();
    }

    private void setLayerInfoText() {
        int layers = levelData.getLayers();
        int currentLayer = canvas.getCurrentLayer();
        layerInfoLabel.setText("  |  Layer: " + (currentLayer + 1) + " of " + layers);
        layerInfoLabel.repaint();
    }

    private void setScaleText() {
        double scale = canvas.getScale();
        scaleLabel.setText("  |  Scale: " + scale + "x");
        scaleLabel.repaint();
    }

    public void onCanvasModified() {
        setModified(true);
    }

    private void onFrameClosing() {
        saveLevelIfNeededAndThen(() -> {
            closePalette();
            frame.dispose();
        });
    }

    private void setModified(boolean modified) {
        if (this.modified != modified) {
            this.modified = modified;
            setTitle();
        }
    }

    public void onScaleChange() {
        setScaleText();
        frame.pack();
        canvas.invalidate();
    }

    private class LevelEditorAction extends AbstractAction {
        static final String CMD_FILE = "File";                          // top-level File menu
        static final String CMD_NEW_LEVEL = "New Level";
        static final String CMD_OPEN = "Open";                          // mid-level Open menu
        static final String CMD_OPEN_LEVEL = "Existing Level...";
        static final String CMD_OPEN_PALETTE = "Palette";
        static final String CMD_SAVE_LEVEL = "Save Level";
        static final String CMD_SAVE_LEVEL_AS = "Save Level As...";
        static final String CMD_EXIT = "Exit";

        static final String CMD_EDIT = "Edit";                          // top-level Edit menu
        static final String CMD_LAYER = "Layer";                        // mid-level Layer menu
        static final String CMD_SELECT_LAYER = "Set Layer...";
        static final String CMD_ADD_LAYER = "Add Layer";
        static final String CMD_SET_LEVEL_SIZE = "Set Level Size...";
        static final String CMD_SET_TILE_SIZE = "Set Tile Size...";
        static final String CMD_SET_SCALE = "Set Scale...";

        @Override
        public void actionPerformed(ActionEvent e) {
            String command = e.getActionCommand();
            switch (command) {
                case CMD_NEW_LEVEL -> cmdNewLevel();
                case CMD_OPEN_LEVEL -> cmdOpenLevel();
                case CMD_OPEN_PALETTE -> cmdOpenPalette();
                case CMD_SAVE_LEVEL -> cmdSaveLevel();
                case CMD_SAVE_LEVEL_AS -> cmdSaveLevelAs();
                case CMD_EXIT -> cmdExit();
                case CMD_SELECT_LAYER -> cmdSelectLayer();
                case CMD_ADD_LAYER -> cmdAddLayer();
                case CMD_SET_LEVEL_SIZE -> cmdSetLevelSizeInTiles();
                case CMD_SET_TILE_SIZE -> cmdSetTileSize();
                case CMD_SET_SCALE -> cmdSetScale();
                default -> System.out.println("Unknown command '" + command + "'; ignoring.");
            }
        }
    }

    private JMenuBar createMenuBar() {
        Action menuItemAction = new LevelEditorAction();

        JMenuItem fileNewItem = new JMenuItem(menuItemAction);
        fileNewItem.setText(LevelEditorAction.CMD_NEW_LEVEL);
        fileNewItem.setMnemonic(KeyEvent.VK_N);
        fileNewItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));

        JMenuItem openLevelItem = new JMenuItem(menuItemAction);
        openLevelItem.setText(LevelEditorAction.CMD_OPEN_LEVEL);
        openLevelItem.setMnemonic(KeyEvent.VK_T);
        openLevelItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));

        JMenuItem openTileSheetItem = new JMenuItem(menuItemAction);
        openTileSheetItem.setText(LevelEditorAction.CMD_OPEN_PALETTE);
        openTileSheetItem.setMnemonic(KeyEvent.VK_P);
        openTileSheetItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));

        JMenu fileOpen = new JMenu(LevelEditorAction.CMD_OPEN);
        fileOpen.setMnemonic(KeyEvent.VK_O);
        fileOpen.add(openLevelItem);
        fileOpen.add(openTileSheetItem);

        JMenuItem fileSaveLevelItem = new JMenuItem(menuItemAction);
        fileSaveLevelItem.setText(LevelEditorAction.CMD_SAVE_LEVEL);
        fileSaveLevelItem.setMnemonic(KeyEvent.VK_S);
        fileSaveLevelItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));

        JMenuItem fileSaveLevelAsItem = new JMenuItem(menuItemAction);
        fileSaveLevelAsItem.setText(LevelEditorAction.CMD_SAVE_LEVEL_AS);
        fileSaveLevelAsItem.setMnemonic(KeyEvent.VK_A);
        fileSaveLevelAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));

        JMenuItem fileExitItem = new JMenuItem(menuItemAction);
        fileExitItem.setText(LevelEditorAction.CMD_EXIT);
        fileExitItem.setMnemonic(KeyEvent.VK_X);
        fileExitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));

        JMenu fileMenu = new JMenu(LevelEditorAction.CMD_FILE);
        fileMenu.setMnemonic(KeyEvent.VK_F);
        fileMenu.add(fileNewItem);
        fileMenu.add(fileOpen);
        fileMenu.addSeparator();
        fileMenu.add(fileSaveLevelItem);
        fileMenu.add(fileSaveLevelAsItem);
        fileMenu.addSeparator();
        fileMenu.add(fileExitItem);

        JMenuItem editSetTileSizeItem = new JMenuItem(menuItemAction);
        editSetTileSizeItem.setText(LevelEditorAction.CMD_SET_TILE_SIZE);
        editSetTileSizeItem.setMnemonic(KeyEvent.VK_T);
        editSetTileSizeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK));

        JMenuItem editSetLevelSizeItem = new JMenuItem(menuItemAction);
        editSetLevelSizeItem.setText(LevelEditorAction.CMD_SET_LEVEL_SIZE);
        editSetLevelSizeItem.setMnemonic(KeyEvent.VK_L);
        editSetLevelSizeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));

        JMenuItem editSetScaleItem = new JMenuItem(menuItemAction);
        editSetScaleItem.setText(LevelEditorAction.CMD_SET_SCALE);
        editSetScaleItem.setMnemonic(KeyEvent.VK_C);
        editSetScaleItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));

        JMenuItem editLayerSelectLayerItem = new JMenuItem(menuItemAction);
        editLayerSelectLayerItem.setText(LevelEditorAction.CMD_SELECT_LAYER);
        editLayerSelectLayerItem.setMnemonic(KeyEvent.VK_Y);
        editLayerSelectLayerItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));

        JMenuItem editLayerAddLayerItem = new JMenuItem(menuItemAction);
        editLayerAddLayerItem.setText(LevelEditorAction.CMD_ADD_LAYER);
        editLayerAddLayerItem.setMnemonic(KeyEvent.VK_D);
        editLayerAddLayerItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));

        JMenu editLayer = new JMenu(LevelEditorAction.CMD_LAYER);
        editLayer.setMnemonic(KeyEvent.VK_Y);
        editLayer.add(editLayerSelectLayerItem);
        editLayer.add(editLayerAddLayerItem);

        JMenu editMenu = new JMenu(LevelEditorAction.CMD_EDIT);
        editMenu.setMnemonic(KeyEvent.VK_E);
        editMenu.add(editSetTileSizeItem);
        editMenu.add(editSetLevelSizeItem);
        editMenu.add(editSetScaleItem);
        editMenu.addSeparator();
        editMenu.add(editLayer);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        return menuBar;
    }

    // ------------- Main

    private static void createAndShowGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            //UIManager.getDefaults().put("Button.showMnemonics", Boolean.TRUE);
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        // create and set up the window
        JFrame frame = new JFrame();
        new LevelEditor(frame);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LevelEditor::createAndShowGUI);
    }
}
