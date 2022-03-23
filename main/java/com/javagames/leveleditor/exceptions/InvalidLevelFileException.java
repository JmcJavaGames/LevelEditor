package com.javagames.leveleditor.exceptions;

import java.io.File;
import java.io.IOException;

public class InvalidLevelFileException extends IOException {
    public InvalidLevelFileException(File levelFile) {
        super("Invalid level file: " + levelFile);
    }

    public InvalidLevelFileException(File levelFile, Exception e) {
        super("Invalid level file: " + levelFile, e);
    }
}
