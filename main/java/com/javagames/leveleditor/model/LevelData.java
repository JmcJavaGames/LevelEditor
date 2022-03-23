package com.javagames.leveleditor.model;

import com.javagames.leveleditor.exceptions.InvalidLevelFileException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.Date;

public class LevelData {
    private static final String LEVEL_ELEMENT = "Level";
    private static final String MODIFIED_ATTRIBUTE = "modified";
    private static final String LAYERS_ATTRIBUTE = "layers";
    private static final String PALETTE_ELEMENT = "Palette";
    private static final String TILE_WIDTH_ATTRIBUTE = "tileWidth";
    private static final String TILE_HEIGHT_ATTRIBUTE = "tileHeight";

    private File levelFile;
    private File dataFile;
    private File paletteFile;
    private ImageSize dataSize;             // size of one layer of multi-layer canvas
    private ImageSize tileSize;             // size in pixels of one tile
    private int layers;                     // number of layers
    private int[][] data;                   // pixel data for data image layers - e.g.: data[layer][pixel]
    private BufferedImage paletteImage;     // png file - sprite sheet

    private record LevelFileFields(File dataFile, File paletteFile, ImageSize tileSize, int layers) {}

    // ----------------- Constructors

    // base constructor used for parsing an xml level file
    private LevelData(File levelFile) throws IOException {
        LevelFileFields fields = parseXmlLevelFile(levelFile);
        this.levelFile = levelFile;
        dataFile = fields.dataFile;
        paletteFile = fields.paletteFile;
        tileSize = fields.tileSize;
        layers = fields.layers;
        BufferedImage dataImage = ImageIO.read(dataFile);
        if (dataImage == null) {
            throw new IOException("data image file is corrupted: " + dataFile);
        }
        int dataSizeHeight = dataImage.getHeight() / layers;
        data = new int[layers][];
        for (int i = 0; i < layers; i++) {
            data[i] = dataImage.getRGB(0, i * dataSizeHeight, dataImage.getWidth(), dataSizeHeight,
                    null, 0, dataImage.getWidth());
        }
        dataSize = ImageSize.of(dataImage.getWidth(), dataSizeHeight);
        try {
            paletteImage = ImageIO.read(paletteFile);
        } catch (IOException e) {
            throw new IOException("Can't read palette file!", e);
        }
        if (paletteImage == null) {
            throw new IOException("palette image file is corrupted: " + paletteFile);
        }
    }

    // base constructor used for new blank canvas with or without a palette file on startup or clear
    private LevelData(File paletteFile, ImageSize dataSize, ImageSize tileSize, int layers) throws IOException {
        this.paletteFile = paletteFile;
        this.dataSize = dataSize;
        this.tileSize = tileSize;
        this.levelFile = null;
        this.dataFile = null;
        this.layers = layers;
        this.data = new int[layers][];
        for (int i = 0; i < layers; i++) {
            this.data[i] = new int[dataSize.width() * dataSize.height()];
        }
        this.paletteImage = paletteFile != null ? imageFromPngFileIfNotNull(paletteFile) : null;
    }

    // ----------------- Static Factory Methods

    // used to load a saved level file (and palette) - called from LevelEditor when a level file is opened
    public static LevelData forLoadingALevel(File levelFile) throws IOException {
        return new LevelData(levelFile);
    }

    // used to specify a blank new image file - called from LevelEditor on startup
    public static LevelData forNewBlankCanvas(ImageSize dataSize, ImageSize tileSize, int layers) {
        try {
            return new LevelData(null, dataSize, tileSize, layers);
        } catch (IOException ignored) {}    // can't happen when passing null for paletteFile
        return null;                        // we'll never get here
    }

    // ----------------- Static Helpers

    private static BufferedImage imageFromPngFileIfNotNull(File imageFile) throws IOException {
        BufferedImage image = imageFile != null ? ImageIO.read(imageFile) : null;
        if (image == null) {
            throw new IOException("image file is corrupted: " + imageFile);
        }
        return image;
    }

    /*  XML Format:
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <Level modified="Thu Mar 17 16:00:27 MDT 2022" layers="2">
          <Palette tileHeight="16" tileWidth="16">C:\...\LevelEditor\test\resources\palette_16x16.png</Palette>
        </Level>
     */
    private static LevelFileFields parseXmlLevelFile(File xmlLevelFile) throws IOException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(xmlLevelFile);
            doc.getDocumentElement().normalize();

            Node levelNode = doc.getElementsByTagName(LEVEL_ELEMENT).item(0);
            if (levelNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new InvalidLevelFileException(xmlLevelFile);
            }
            Element level = (Element) levelNode;
            Node paletteNode = level.getElementsByTagName(PALETTE_ELEMENT).item(0);
            if (paletteNode.getNodeType() != Node.ELEMENT_NODE) {
                throw new InvalidLevelFileException(xmlLevelFile);
            }
            String layersStr = level.getAttribute(LAYERS_ATTRIBUTE);
            int layers = !layersStr.isEmpty() ? Integer.parseInt(layersStr) : 1;
            Element palette = (Element) paletteNode;

            // get parent path of xml level descriptor file
            File absXmlLevelFile = xmlLevelFile.getAbsoluteFile();
            File xmlLevelFileParent = absXmlLevelFile.getParentFile();

            // get palette file; if it's null or relative, rebuild it against the xml file path
            File paletteFile = new File(palette.getTextContent());
            File parentPaletteFile = paletteFile.getParentFile();
            if (parentPaletteFile == null || !parentPaletteFile.isAbsolute()) {
                paletteFile = Path.of(xmlLevelFileParent.getPath(), paletteFile.getPath()).toFile();
            }

            // build data file name from xml file name
            String absXmlLevelFileStr = absXmlLevelFile.toString();
            int dotIndex = absXmlLevelFileStr.lastIndexOf('.');
            if (dotIndex >= 0) {
                absXmlLevelFileStr = absXmlLevelFileStr.substring(0, dotIndex);
            }
            File dataFile = new File(absXmlLevelFileStr + ".png");

            String tileWidthStr = palette.getAttribute(TILE_WIDTH_ATTRIBUTE);
            int tileWidth = Integer.parseInt(tileWidthStr);

            String tileHeightStr = palette.getAttribute(TILE_HEIGHT_ATTRIBUTE);
            int tileHeight = Integer.parseInt(tileHeightStr);

            return new LevelFileFields(dataFile, paletteFile, ImageSize.of(tileWidth, tileHeight), layers);
        } catch (NumberFormatException | ParserConfigurationException | SAXException e) {
            throw new InvalidLevelFileException(xmlLevelFile, e);
        }
    }

    // ----------------- Public interface

    public void saveXmlLevelFile(File xmlLevelFile) throws IOException {
        try {
            // save the level data png (image) file
            Path parentXmlLevelFile = xmlLevelFile.toPath();
            String parentXmlLevelFileStr = parentXmlLevelFile.toString();
            int dotIndex = parentXmlLevelFileStr.lastIndexOf('.');
            if (dotIndex >= 0) {
                parentXmlLevelFileStr = parentXmlLevelFileStr.substring(0, dotIndex);
            }
            File levelDataFile = new File(parentXmlLevelFileStr + ".png");
            BufferedImage dataImage = new BufferedImage(dataSize.width(),
                    dataSize.height() * layers, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < layers; i++) {
                dataImage.setRGB(0, i * dataSize.height(), dataSize.width(),
                        dataSize.height(), data[i], 0, dataSize.width());
            }
            ImageIO.write(dataImage, "png", levelDataFile);

            // build the xml descriptor DOM
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            Element levelElement = doc.createElement(LEVEL_ELEMENT);
            levelElement.setAttribute(MODIFIED_ATTRIBUTE, new Date().toString());
            levelElement.setAttribute(LAYERS_ATTRIBUTE, Integer.toString(layers));
            doc.appendChild(levelElement);
            Element paletteElement = doc.createElement(PALETTE_ELEMENT);
            paletteElement.setAttribute(TILE_WIDTH_ATTRIBUTE, Integer.toString(tileSize.width()));
            paletteElement.setAttribute(TILE_HEIGHT_ATTRIBUTE, Integer.toString(tileSize.height()));
            Path palettePath = paletteFile.toPath();
            if (!palettePath.isAbsolute()) {
                palettePath = parentXmlLevelFile.getParent().relativize(palettePath);
            }
            paletteElement.appendChild(doc.createTextNode(palettePath.toString()));
            levelElement.appendChild(paletteElement);

            // write the xml file from DOM
            try (OutputStream os = new FileOutputStream(xmlLevelFile);
                 BufferedOutputStream bos = new BufferedOutputStream(os)) {
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer transformer = tf.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                DOMSource src = new DOMSource(doc);
                StreamResult result = new StreamResult(bos);
                transformer.transform(src, result);
            }
        } catch (TransformerException | ParserConfigurationException e) {
            throw new IOException(e);
        }
    }

    // ------------------ level info

    public File getLevelFile() {
        return levelFile;
    }

    public void clearLevelFile() {
        levelFile = null;
        dataFile = null;
        data = new int[layers][];
        for (int i = 0; i < layers; i++) {
            data[i] = new int[dataSize.width() * dataSize.height()];
        }
    }

    public int getLayers() {
        return layers;
    }

    public void addLayer() {
        int layers = this.layers + 1;
        int[][] data = new int[layers][];
        System.arraycopy(this.data, 0, data, 0, this.layers);
        data[this.layers] = new int[dataSize.width() * dataSize.height()];
        this.layers = layers;
        this.data = data;
    }

    // ------------------ data info

    public File getDataFile() {
        return dataFile;
    }

    public ImageSize getDataSize() {
        return dataSize;
    }

    public void setDataSize(ImageSize dataSize) {
        this.dataSize = dataSize;
        data = new int[layers][];
        for (int i = 0; i < layers; i++) {
            data[i] = new int[dataSize.width() * dataSize.height()];
        }
        dataFile = null;
    }

    public int[][] getData() {
        return data;
    }

    // ------------------ palette info

    public File getPaletteFile() {
        return paletteFile;
    }

    public void setPaletteFile(File paletteFile) throws IOException {
        paletteImage = imageFromPngFileIfNotNull(paletteFile);
        this.paletteFile = paletteFile;
    }

    public BufferedImage getPaletteImage() {
        return paletteImage;
    }

    // ------------------ tile info

    public ImageSize getTileSize() {
        return tileSize;
    }

    public void setTileSize(ImageSize tileSize) {
        this.tileSize = tileSize;
    }
}
