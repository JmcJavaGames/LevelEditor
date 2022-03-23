package com.javagames.leveleditor;

import com.javagames.leveleditor.model.ImageSize;
import com.javagames.leveleditor.model.LevelData;
import com.javagames.leveleditor.model.Tile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;

public class SpritePanel extends JPanel {
    private static final Stroke GRID_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_SQUARE,
            BasicStroke.JOIN_MITER, 10.0f, new float[] {1.0f, 2.0f}, 0.0f);
    private static final Color GRID_COLOR = new Color(0, 0, 0, 0x7f);   // 50% transparent

    private final JFrame frame;
    private final LevelCanvas canvas;
    private final BufferedImage backgroundTile;

    private File file;
    private BufferedImage image;
    private double scale;
    private int imageWidthPixelsScaled;
    private int imageHeightPixelsScaled;
    private Dimension dimension;
    private int tileWidthPixelsScaled;
    private int tileHeightPixelsScaled;
    private int imageWidthTiles;
    private Tile[] tiles;
    private Tile currentTile;
    private Point curHoverPoint;

    public SpritePanel(JFrame frame, LevelCanvas canvas) {
        this.frame = frame;
        this.canvas = canvas;
        this.frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.frame.setContentPane(this);
        this.frame.setResizable(false);
        this.backgroundTile = makeScaledBackgroundTile();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                onMouseClicked(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
                onMouseExited();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                super.mouseMoved(e);
                onMouseMoved(e);
            }
        });
    }

    private static BufferedImage makeScaledBackgroundTile() {
        int boxesPerTile = 12;
        int boxSize = 8;
        int width = boxesPerTile * boxSize;
        int height = boxesPerTile * boxSize;
        BufferedImage bimage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED);
        Graphics2D g2d = bimage.createGraphics();
        boolean white = false;
        Color[] colors = new Color[] { Color.WHITE, Color.LIGHT_GRAY };
        for (int y = 0; y < boxesPerTile; y++) {
            for (int x = 0; x < boxesPerTile; x++) {
                white = !white;
                g2d.setColor(colors[white ? 0 : 1]);
                g2d.fillRect(x * boxSize, y * boxSize, boxSize, boxSize);
            }
            white = !white;
        }
        g2d.dispose();
        return bimage;
    }

    public static SpritePanel createPanel(LevelCanvas editor) {
        JFrame frame = new JFrame();
        return new SpritePanel(frame, editor);
    }

    @Override
    public Dimension getPreferredSize() {
        return dimension;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (file != null) {
            Graphics2D g2d = (Graphics2D) g.create();
            drawPanelBackground(g2d);
            g2d.setClip(0, 0, imageWidthPixelsScaled, imageHeightPixelsScaled);
            drawImageBackground(g2d);
            drawImageAtScale(g2d);
            drawGridAtScale(g2d);
            g2d.dispose();
        }
    }

    private void drawPanelBackground(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawImageBackground(Graphics2D g2d) {
        int wBkg = backgroundTile.getWidth();
        int hBkg = backgroundTile.getHeight();
        for (int y = 0; y < imageHeightPixelsScaled; y += hBkg) {
            for (int x = 0; x < imageWidthPixelsScaled; x += wBkg) {
                g2d.drawImage(backgroundTile, x, y, this);
            }
        }
    }

    private void drawImageAtScale(Graphics2D g2d) {
        g2d.scale(scale, scale);
        g2d.drawImage(image, 0, 0, this);
        g2d.scale(1.0 / scale, 1.0 / scale);
    }

    private void drawGridAtScale(Graphics2D g2d) {
        g2d.setColor(GRID_COLOR);
        g2d.setStroke(GRID_STROKE);
        int xInterval = tileWidthPixelsScaled;
        int yInterval = tileHeightPixelsScaled;
        for (int y = yInterval; y < imageHeightPixelsScaled; y += yInterval) {
            g2d.drawLine(0, y, imageWidthPixelsScaled, y);
        }
        for (int x = xInterval; x < imageWidthPixelsScaled; x += xInterval) {
            g2d.drawLine(x, 0, x, imageHeightPixelsScaled);
        }
    }

    public void setTileSize(ImageSize spriteSize) {
        int width = spriteSize.width();
        int height = spriteSize.height();
        double scale = width < 32 ? 2.0 : 1.0;
        this.tileWidthPixelsScaled = (int) (width * scale);
        this.tileHeightPixelsScaled = (int) (height * scale);
        this.scale = scale;
        this.imageWidthTiles = image.getWidth() / width;
        int imageHeightTiles = image.getHeight() / height;
        this.imageWidthPixelsScaled = imageWidthTiles * tileWidthPixelsScaled;
        this.imageHeightPixelsScaled = imageHeightTiles * tileHeightPixelsScaled;
        this.dimension = new Dimension(imageWidthPixelsScaled, imageHeightPixelsScaled);
        this.tiles = new Tile[imageWidthTiles * imageHeightTiles];
        for (int y = 0; y < imageHeightTiles; y++) {
            for (int x = 0; x < imageWidthTiles; x++) {
                int index = x + y * imageWidthTiles;
                this.tiles[index] = Tile.of(index, image.getSubimage(x * width, y * height, width, height));
            }
        }
        frame.pack();
    }

    public void resetFromLevelData(LevelData levelData) {
        file = levelData.getPaletteFile();
        image = levelData.getPaletteImage();
        setTileSize(levelData.getTileSize());
        frame.setTitle(file.getName());
    }

    public BufferedImage getImageAt(int index) {
        return tiles[index].getImage();
    }

    public void setFrameVisible(boolean show) {
        frame.setVisible(show);
    }

    public void setFrameLocationRelativeTo(JFrame otherFrame) {
        frame.setLocationRelativeTo(otherFrame);
    }

    public void onMouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            Point p = e.getPoint();
            int index = (p.x / tileWidthPixelsScaled) + (p.y / tileHeightPixelsScaled) * imageWidthTiles;
            currentTile = tiles[index];
            canvas.onPaletteTileSelected(currentTile);
        }
    }

    public void onMouseMoved(MouseEvent e) {
        Point newHoverPoint = e.getPoint();
        if (curHoverPoint == null || !sameTile(newHoverPoint,
                curHoverPoint, tileWidthPixelsScaled, tileHeightPixelsScaled)) {
            Graphics2D g2d = (Graphics2D) this.getGraphics();
            g2d.setXORMode(Color.RED);
            highlightMouseOverTileAtScale(g2d, curHoverPoint);
            curHoverPoint = newHoverPoint;
            highlightMouseOverTileAtScale(g2d, curHoverPoint);
            g2d.dispose();
        }
    }

    private void onMouseExited() {
        if (curHoverPoint != null) {
            Graphics2D g2d = (Graphics2D) this.getGraphics();
            g2d.setXORMode(Color.RED);
            highlightMouseOverTileAtScale(g2d, curHoverPoint);
            g2d.dispose();
            curHoverPoint = null;
        }
    }

    private boolean sameTile(Point p1, Point p2, int width, int height) {
        return p1.x / width == p2.x / width && p1.y / height == p2.y / height;
    }

    private void highlightMouseOverTileAtScale(Graphics2D g2d, Point p) {
        if (p != null) {
            int w = tileWidthPixelsScaled;
            int h = tileHeightPixelsScaled;
            int x = p.x / w * w;   // truncate to start of last xgrid position
            int y = p.y / h * h;   // truncate to start of last ygrid position
            g2d.fillRect(x, y, w, h);
        }
    }

    public void close() {
        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    }
}
