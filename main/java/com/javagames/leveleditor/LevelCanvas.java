package com.javagames.leveleditor;

import com.javagames.leveleditor.model.ImageSize;
import com.javagames.leveleditor.model.LevelData;
import com.javagames.leveleditor.model.Tile;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

public class LevelCanvas extends JPanel {
    private static final String CURSOR_IMAGE_PATH = "images/cursor.png";
    private static final double DEFAULT_SCALE = 1.0d;

    private final LevelEditor editor;
    private final BufferedImage cursorImage;

    private LevelData data;
    private Tile selectedTile;  // the currently selected tile (paint tool)
    private Tile[][] tiles;     // the multi-layer tile array to be rendered
    private Point buttonTile;   // the tile that was under the cursor when a button was pressed (in tiles, not pixels)
    private boolean dropping;   // true if dropping tiles onto canvas; false if clearing them
    private int currentLayer;   // the zero-based index of the current editing layer
    private double scale;       // the current scale factor of the canvas - used during rendering

    public LevelCanvas(LevelEditor editor, LevelData data, SpritePanel palette) {
        super(new BorderLayout(), true);

        this.editor = editor;
        this.scale = DEFAULT_SCALE;

        this.cursorImage = loadCursorImage();

        setBackground(Color.WHITE);
        setFocusable(true);
        requestFocusInWindow();

        onLevelLoaded(data, palette);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                if (selectedTile != null) {
                    onMousePressedWithTileSelected(e);
                }
            }
        });
        addMouseWheelListener(e -> {
            if (!e.isAltDown() && e.isControlDown() && !e.isShiftDown()) {
                LevelCanvas.this.onCtrlMouseScroll(e);
            } else {
                getParent().dispatchEvent(e);
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                super.mouseDragged(e);
                if (selectedTile != null) {
                    onMouseDraggedWithTileSelected(e);
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);
                if (e.getKeyChar() == 27) {  // escape character
                    onEscapeTyped();
                }
            }
        });
    }

    private BufferedImage loadCursorImage() {
        try (InputStream ios = this.getClass().getClassLoader().getResourceAsStream(CURSOR_IMAGE_PATH)) {
            return ios != null ? ImageIO.read(ios) : null;
        } catch (IOException e) {
            System.out.println("can't read cursor image from resource fork");
        }
        return null;
    }

    public int getCurrentLayer() {
        return currentLayer;
    }

    public void setCurrentLayer(int layer) {
        currentLayer = layer;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
        repaint();
    }

    public void onLevelLoaded(LevelData data, SpritePanel palette) {
        this.data = data;
        ImageSize canvasSize = data.getDataSize();
        int layers = data.getLayers();
        tiles = new Tile[layers][];
        int[][] pixels = data.getData();
        assert tiles.length == pixels.length : "tile layers is not the same as data layers";
        for (int layer = 0; layer < layers; layer++) {
            tiles[layer] = new Tile[canvasSize.width() * canvasSize.height()];
            if (palette == null || data.getDataFile() == null) {
                Arrays.fill(tiles[layer], Tile.EMPTY_TILE);         // fill tile array with EMPTY_TILE
                Arrays.fill(pixels[layer], Tile.EMPTY_CODE);        // fill pixel array with EMPTY_CODE
            } else {                                                // fill tile array with loaded pixel data
                assert tiles[layer].length == pixels[layer].length : "for layer " + layer
                        + ", tile array is not the same size as data array";
                for (int i = 0; i < tiles[layer].length; i++) {
                    int code = pixels[layer][i] & Tile.EMPTY_CODE;  // & 0x00FFFFFF - mask off the alpha byte
                    tiles[layer][i] = code != Tile.EMPTY_CODE
                            ? Tile.of(code, palette.getImageAt(code))
                            : Tile.EMPTY_TILE;
                }
            }
        }
        repaint();
    }

    public void onLayerAdded() {
        int layers = data.getLayers();
        int[][] pixels = data.getData();
        Tile[][] prev = tiles;
        tiles = new Tile[layers][];
        System.arraycopy(prev, 0, tiles, 0, layers - 1);
        ImageSize dataSize = data.getDataSize();
        tiles[layers - 1] = new Tile[dataSize.width() * dataSize.height()];
        Arrays.fill(tiles[layers - 1], Tile.EMPTY_TILE);
        Arrays.fill(pixels[layers - 1], Tile.EMPTY_CODE);
    }

    private Dimension getTargetSize() {
        ImageSize tileSize = data.getTileSize();
        ImageSize dataSize = data.getDataSize();
        return new Dimension((int) (dataSize.width() * tileSize.width() * scale),
                (int) (dataSize.height() * tileSize.height() * scale));
    }

    @Override
    public Dimension getPreferredSize() {
        return getTargetSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return getTargetSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getTargetSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        ImageSize dataSize = data.getDataSize();
        ImageSize tileSize = data.getTileSize();
        int layers = data.getLayers();
        int levelTilesWide = dataSize.width();
        int canvasWidth = dataSize.width() * tileSize.width();
        int canvasHeight = dataSize.height() * tileSize.height();
        Graphics2D g2d = (Graphics2D) g;
        g2d.scale(scale, scale);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, canvasWidth, canvasHeight);
        for (int layer = 0; layer < layers; layer++) {
            for (int i = 0; i < tiles[layer].length; i++) {
                int xPosPixels = (i % levelTilesWide) * tileSize.width();
                int yPosPixels = (i / levelTilesWide) * tileSize.height();
                tiles[layer][i].render(g2d, xPosPixels, yPosPixels, this);
            }
        }
        g.dispose();
    }

    public void onPaletteTileSelected(Tile selected) {
        this.selectedTile = selected;   // store off newly selected tile

        // Windows limits cursor size to 32 x 32, but java scales down anything larger
        // to that size, so don't bother trying to create a scaled up cursor tile.

        BufferedImage ci = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = ci.createGraphics();
        g2d.drawImage(selected.getImage(), 0, 0, 32, 32, null);
        g2d.drawImage(cursorImage, 0, 0, cursorImage.getWidth(), cursorImage.getHeight(), null);
        g2d.dispose();
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Cursor c = toolkit.createCustomCursor(ci, new Point(1, 1), "cursor");
        setCursor(c);
        requestFocus();
    }

    private void dropOrClearTileAt(boolean drop, int xTile, int yTile, int layer, ImageSize tileSize) {
        // update tile and data array content at specified array position
        ImageSize dataSize = data.getDataSize();
        int index = xTile + yTile * dataSize.width();
        Tile selected = drop ? selectedTile : Tile.EMPTY_TILE;
        tiles[layer][index] = selected;
        data.getData()[layer][index] = selected.getCode();
        editor.onCanvasModified();

        // repaint the region containing the new tile
        int xDrop = (int) (xTile * tileSize.width() * scale);
        int yDrop = (int) (yTile * tileSize.height() * scale);
        int xSize = (int) (tileSize.width() * scale);
        int ySize = (int) (tileSize.height() * scale);
        repaint(xDrop, yDrop, xSize, ySize);
    }

    private void onMousePressedWithTileSelected(MouseEvent e) {
        int button = e.getButton();
        if (button == MouseEvent.BUTTON1 || button == MouseEvent.BUTTON3) {
            // calculate the tile at button press (save it off)
            ImageSize tileSize = data.getTileSize();
            Point p = e.getPoint();
            int xTile = p.x / (int) (tileSize.width() * scale);
            int yTile = p.y / (int) (tileSize.height() * scale);
            buttonTile = new Point(xTile, yTile);
            dropOrClearTileAt(dropping = button == MouseEvent.BUTTON1, xTile, yTile, currentLayer, tileSize);
        }
    }

    private void onMouseDraggedWithTileSelected(MouseEvent e) {
        // calculate the tile at button press (save it off)
        ImageSize tileSize = data.getTileSize();
        Point p = e.getPoint();
        int xTile = p.x / (int) (tileSize.width() * scale);
        int yTile = p.y / (int) (tileSize.height() * scale);
        Point buttonTile = new Point(xTile, yTile);
        if (!Objects.equals(this.buttonTile, buttonTile)) {
            this.buttonTile = buttonTile;
            dropOrClearTileAt(dropping, xTile, yTile, currentLayer, tileSize);
        }
    }

    private void onCtrlMouseScroll(MouseWheelEvent e) {
        int count = e.getWheelRotation();  // -1 is up one click; 1 is down one click;
        int newScale = (int) (scale * 10) - count;
        if (newScale > 0) {
            scale = (double) newScale / 10.0d;
            editor.onScaleChange();
        }
    }

    private void onEscapeTyped() {
        selectedTile = null;
        setCursor(Cursor.getDefaultCursor());
    }
}
