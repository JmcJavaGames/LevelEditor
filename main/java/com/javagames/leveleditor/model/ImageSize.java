package com.javagames.leveleditor.model;

public record ImageSize(int width, int height) {
    public static ImageSize of(int width, int height) {
        return new ImageSize(width, height);
    }

    public ImageSize multipleOf(ImageSize other) {
        return ImageSize.of(width * other.width, height * other.height);
    }
}
