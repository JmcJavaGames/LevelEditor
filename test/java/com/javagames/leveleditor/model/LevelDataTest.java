package com.javagames.leveleditor.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class LevelDataTest {
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String TEST_FILE = "test-level.xml";
    private static final String TEST_DATA_FILE = TEST_FILE.replace(".xml", ".png");
    private static final File IN_LEVEL_PATH = new File("test/resources/" + TEST_FILE);
    private static final File OUT_LEVEL_PATH = new File(TEMP_DIR + TEST_FILE);

    @Test
    void load_fromXmlLevelFile_works() throws IOException {
        LevelData ld = LevelData.forLoadingALevel(IN_LEVEL_PATH);
        Assertions.assertNotNull(ld);
    }

    @Test
    void save_toXmlLevelFile_works() throws IOException {
        LevelData ld = LevelData.forLoadingALevel(IN_LEVEL_PATH);
        ld.saveXmlLevelFile(OUT_LEVEL_PATH);
        Files.delete(Path.of(TEMP_DIR, TEST_FILE));
        Files.delete(Path.of(TEMP_DIR, TEST_DATA_FILE));
    }
}