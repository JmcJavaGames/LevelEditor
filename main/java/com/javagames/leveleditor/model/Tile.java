package com.javagames.leveleditor.model;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.WritableRaster;

public class Tile {
    public static final int EMPTY_CODE = 0x00FFFFFF;
    public static final Tile EMPTY_TILE = new Tile(EMPTY_CODE, null);

    private final int code;
    private final BufferedImage image;

    private Tile(int code, BufferedImage image) {
        this.code = code;
        this.image = image != null ? copyImage(image) : null;
    }

    public static Tile of(int code, BufferedImage image) {
        return new Tile(code, image);
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getCode() {
        return code;
    }

    public void render(Graphics2D g2d, int x, int y, ImageObserver observer) {
        if (image != null) {
            g2d.drawImage(image, x, y, observer);
        }
    }

    private static BufferedImage copyImage(BufferedImage image) {
        ColorModel cm = image.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = image.copyData(image.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }
}
