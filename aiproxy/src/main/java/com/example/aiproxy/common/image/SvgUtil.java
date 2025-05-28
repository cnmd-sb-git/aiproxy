package com.example.aiproxy.common.image;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for handling SVG images.
 * <p>
 * The original Go code used `oksvg` for parsing and `rasterx` for rendering,
 * and registered SVG as a format with the standard `image` package.
 * This Java version provides:
 * - Dimension extraction (equivalent to `DecodeConfig`) using standard XML parsing.
 * - A placeholder for SVG rendering (equivalent to `Decode`) which would require
 *   a dedicated SVG rendering library like Apache Batik.
 * - Standard Java `ImageIO` does not natively support SVG rasterization or registration
 *   in the same way as Go's `image.RegisterFormat` without custom ImageIO plugins.
 * </p>
 */
public class SvgUtil {

    private static final Logger LOGGER = Logger.getLogger(SvgUtil.class.getName());

    /**
     * Represents basic configuration information for an image, similar to Go's `image.Config`.
     */
    public static class ImageConfig {
        public final int width;
        public final int height;
        public final ColorModel colorModel; // Java's ColorModel

        public ImageConfig(int width, int height, ColorModel colorModel) {
            this.width = width;
            this.height = height;
            this.colorModel = colorModel;
        }
    }

    /**
     * Extracts image configuration (dimensions, color model) from an SVG input stream.
     * This is analogous to `DecodeConfig` in the Go version.
     *
     * @param inputStream The input stream containing SVG data.
     * @return An ImageConfig object with width, height, and a default RGB/RGBA color model.
     * @throws IOException If parsing fails or SVG data is invalid.
     */
    public static ImageConfig decodeConfig(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be null.");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Prevent XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream); // InputSource can be used for encoding

            Element svgRoot = doc.getDocumentElement();
            if (!"svg".equalsIgnoreCase(svgRoot.getTagName())) {
                throw new IOException("Input is not a valid SVG: root element is not <svg>");
            }

            int width = 0;
            int height = 0;

            // Try to get width and height attributes directly
            String widthStr = svgRoot.getAttribute("width");
            String heightStr = svgRoot.getAttribute("height");

            // viewBox attribute: "minX minY width height"
            String viewBoxStr = svgRoot.getAttribute("viewBox");
            
            if (widthStr != null && !widthStr.isEmpty() && !widthStr.endsWith("%")) {
                 try { width = (int) Float.parseFloat(removeUnits(widthStr)); } catch (NumberFormatException e) { /* ignore, try viewBox */ }
            }
            if (heightStr != null && !heightStr.isEmpty() && !heightStr.endsWith("%")) {
                 try { height = (int) Float.parseFloat(removeUnits(heightStr)); } catch (NumberFormatException e) { /* ignore, try viewBox */ }
            }

            // If width/height are not absolute or not present, use viewBox
            if ((width <= 0 || height <= 0) && viewBoxStr != null && !viewBoxStr.isEmpty()) {
                String[] viewBoxParts = viewBoxStr.trim().split("\\s+|,\\s*");
                if (viewBoxParts.length == 4) {
                    try {
                        if (width <= 0) width = (int) Float.parseFloat(viewBoxParts[2]);
                        if (height <= 0) height = (int) Float.parseFloat(viewBoxParts[3]);
                    } catch (NumberFormatException e) {
                        throw new IOException("Could not parse width/height from viewBox: " + viewBoxStr, e);
                    }
                } else {
                     throw new IOException("Invalid viewBox attribute format: " + viewBoxStr);
                }
            }
            
            if (width <= 0 || height <= 0) {
                 LOGGER.warning("Could not determine valid width/height from SVG attributes or viewBox. Using default 0,0.");
                 // throw new IOException("Could not determine valid width/height from SVG attributes or viewBox.");
            }


            // Default to an RGBA color model, as SVGs can have transparency.
            // This is a simplification; true SVG color model can be complex.
            ColorModel colorModel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getColorModel();

            return new ImageConfig(width, height, colorModel);

        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse SVG XML: " + e.getMessage(), e);
        }
    }
    
    private static String removeUnits(String value) {
        if (value == null) return null;
        // Basic unit removal (px, pt, em, etc.)
        return value.replaceAll("[a-zA-Z%]+$", "").trim();
    }


    /**
     * Decodes an SVG input stream into a raster image (BufferedImage).
     * NOTE: This is a placeholder. Standard Java SE does not provide a built-in
     * SVG rasterizer. A library like Apache Batik is required for this functionality.
     *
     * @param inputStream The input stream containing SVG data.
     * @return A BufferedImage representing the rasterized SVG.
     * @throws IOException If an error occurs during decoding (e.g., library not available).
     */
    public static BufferedImage decode(InputStream inputStream) throws IOException {
        LOGGER.severe("SVG rasterization (decode to BufferedImage) is not implemented. " +
                      "This requires an SVG rendering library like Apache Batik.");
        throw new UnsupportedOperationException(
                "SVG rasterization to BufferedImage requires a library like Apache Batik. " +
                "This method is a placeholder.");
        // Example with Batik (if it were added as a dependency):
        // try {
        //     TranscoderInput input = new TranscoderInput(inputStream);
        //     ImageTranscoder transcoder = new PNGTranscoder(); // or JPEGTranscoder, etc.
        //     // Set transcoding hints if needed (e.g., for dimensions, background color)
        //     // Example: To use dimensions from SVG:
        //     // ImageConfig config = decodeConfig(new TeeInputStream(inputStream, new ByteArrayOutputStream())); // Need to re-read or buffer inputStream
        //     // if (config.width > 0) transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) config.width);
        //     // if (config.height > 0) transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) config.height);
        //
        //     BufferedImageRenderingTranscoder t = new BufferedImageRenderingTranscoder();
        //     t.transcode(input, null);
        //     return t.getBufferedImage();
        // } catch (TranscoderException e) {
        //     throw new IOException("Failed to transcode SVG: " + e.getMessage(), e);
        // }
    }
    
    /**
     * Registers this SVG handler with ImageIO.
     * Note: This is a simplified conceptual placeholder. True ImageIO registration
     * requires implementing ImageReaderSpi and ImageReader, which is complex.
     * Libraries like TwelveMonkeys ImageIO provide such SPIs for SVG (often using Batik).
     */
    public static void registerWithImageIO() {
        LOGGER.info("Conceptual registration for SVG with ImageIO. " + 
                    "Actual ImageIO SPI implementation for SVG is complex and typically provided by external libraries.");
        // Example of what an SPI registration might look like (but this is not a complete SPI):
        // ImageReaderSpi svgSpi = new SvgImageReaderSpi(); // This class would need to be fully implemented
        // IIORegistry registry = IIORegistry.getDefaultInstance();
        // registry.registerServiceProvider(svgSpi);
    }


    private SvgUtil() {
        // Private constructor for utility class
    }
}
