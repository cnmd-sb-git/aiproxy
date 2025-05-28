package com.example.aiproxy.common.image;

import com.example.aiproxy.common.LimitedReader; // Assumes this path from previous translation

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageUtil {

    private static final Logger LOGGER = Logger.getLogger(ImageUtil.class.getName());
    private static final Pattern DATA_URL_PATTERN = Pattern.compile("data:image/([^;]+);base64,(.*)");
    // Regex to strip the "data:image/...;base64," part, similar to reg.ReplaceAllString(encoded, "")
    private static final Pattern DATA_URL_PREFIX_PATTERN = Pattern.compile("^data:image/[^;]+;base64,");


    public static final long MAX_IMAGE_SIZE_BYTES = 1024 * 1024 * 5; // 5MB
    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(10); // General timeout for HTTP requests

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Checks if the HTTP response content type indicates an image.
     *
     * @param httpResponse The HTTP response.
     * @return True if the Content-Type starts with "image/", false otherwise.
     */
    public static boolean isImageResponse(HttpResponse<?> httpResponse) {
        return httpResponse.headers().firstValue("Content-Type")
                .map(contentType -> contentType.toLowerCase().startsWith("image/"))
                .orElse(false);
    }

    /**
     * Represents image dimensions.
     */
    public static class ImageSize {
        public final int width;
        public final int height;

        public ImageSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ImageSize imageSize = (ImageSize) o;
            return width == imageSize.width && height == imageSize.height;
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height);
        }

        @Override
        public String toString() {
            return "ImageSize{" + "width=" + width + ", height=" + height + '}';
        }
    }
    
    /**
     * Custom exception for image-related errors.
     */
    public static class ImageProcessingException extends IOException {
        public ImageProcessingException(String message) {
            super(message);
        }
        public ImageProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }


    /**
     * Gets image dimensions from a given URL (HTTP/HTTPS or data URL).
     *
     * @param imageUrl The URL of the image.
     * @return ImageSize containing width and height.
     * @throws IOException If fetching or decoding the image config fails.
     * @throws ImageProcessingException For specific image processing errors.
     */
    public static ImageSize getImageSize(String imageUrl) throws IOException, ImageProcessingException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IllegalArgumentException("Image URL cannot be null or empty.");
        }

        if (imageUrl.startsWith("data:image/")) {
            return getImageSizeFromBase64(imageUrl);
        } else {
            return getImageSizeFromUrl(imageUrl);
        }
    }


    private static ImageSize getImageSizeFromUrl(String urlString) throws IOException, ImageProcessingException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .timeout(DEFAULT_HTTP_TIMEOUT)
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request to fetch image from URL was interrupted: " + urlString, e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch image from URL: " + urlString + ". Status code: " + response.statusCode());
        }

        if (!isImageResponse(response)) {
            throw new ImageProcessingException("URL content type is not an image: " + urlString +
                                               " (Content-Type: " + response.headers().firstValue("Content-Type").orElse("N/A") + ")");
        }

        try (InputStream responseBody = response.body();
             ImageInputStream iis = ImageIO.createImageInputStream(responseBody)) {
            if (iis == null) {
                throw new ImageProcessingException("Could not create ImageInputStream from URL: " + urlString + ". Possibly unsupported image format or corrupt data.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis, true, true); // true for seekForwardOnly, true for ignoreMetadata
                    return new ImageSize(reader.getWidth(0), reader.getHeight(0));
                } finally {
                    reader.dispose();
                }
            } else {
                throw new ImageProcessingException("No ImageReader found for the image format from URL: " + urlString);
            }
        }
    }

    private static ImageSize getImageSizeFromBase64(String dataUrl) throws IOException, ImageProcessingException {
        Matcher matcher = DATA_URL_PREFIX_PATTERN.matcher(dataUrl);
        String base64Data = matcher.replaceFirst(""); // Strip the prefix

        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw new ImageProcessingException("Invalid Base64 data in data URL.", e);
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(decodedBytes);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
            if (iis == null) {
                throw new ImageProcessingException("Could not create ImageInputStream from Base64 data. Possibly unsupported image format or corrupt data.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis, true, true);
                    return new ImageSize(reader.getWidth(0), reader.getHeight(0));
                } finally {
                    reader.dispose();
                }
            } else {
                throw new ImageProcessingException("No ImageReader found for the image format from Base64 data.");
            }
        }
    }
    
    /**
     * Represents the result of fetching an image from a URL.
     */
    public static class ImageFetchResult {
        public final String contentType;
        public final String base64Data;

        public ImageFetchResult(String contentType, String base64Data) {
            this.contentType = contentType;
            this.base64Data = base64Data;
        }
    }

    /**
     * Fetches an image from a URL (HTTP/HTTPS or data URL) and returns its content type and Base64 encoded data.
     *
     * @param imageUrl The URL of the image.
     * @return ImageFetchResult containing content type and Base64 data.
     * @throws IOException If fetching or processing the image fails.
     * @throws ImageProcessingException For specific image processing errors.
     */
    public static ImageFetchResult getImageFromURL(String imageUrl) throws IOException, ImageProcessingException {
         if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IllegalArgumentException("Image URL cannot be null or empty.");
        }
        Matcher dataUrlMatcher = DATA_URL_PATTERN.matcher(imageUrl);
        if (dataUrlMatcher.matches()) {
            String imageType = "image/" + dataUrlMatcher.group(1);
            String base64Content = dataUrlMatcher.group(2);
            return new ImageFetchResult(imageType, base64Content);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(DEFAULT_HTTP_TIMEOUT)
                .GET()
                .build();
        
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request to fetch image from URL was interrupted: " + imageUrl, e);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch image from URL: " + imageUrl + ". Status code: " + response.statusCode());
        }
        
        String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
        if (!contentType.toLowerCase().startsWith("image/")) {
             throw new ImageProcessingException("URL content type is not an image: " + imageUrl + " (Content-Type: " + contentType + ")");
        }

        try (InputStream responseBody = response.body();
             LimitedReader limitedInputStream = new LimitedReader(responseBody, MAX_IMAGE_SIZE_BYTES);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = limitedInputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            
            if (limitedInputStream.hasExceeded()) {
                 throw new ImageProcessingException("Image size exceeds maximum limit of " + MAX_IMAGE_SIZE_BYTES + " bytes from URL: " + imageUrl, 
                                          new IOException(LimitedReader.ERR_LIMITED_READER_EXCEEDED));
            }
            byte[] imageBytes = baos.toByteArray();
            return new ImageFetchResult(contentType, Base64.getEncoder().encodeToString(imageBytes));

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains(LimitedReader.ERR_LIMITED_READER_EXCEEDED)) {
                 throw new ImageProcessingException("Image size exceeds maximum limit of " + MAX_IMAGE_SIZE_BYTES + " bytes from URL: " + imageUrl, e);
            }
            LOGGER.log(Level.WARNING, "Failed to read image data from URL: " + imageUrl, e);
            throw new IOException("Failed to read image data from URL: " + imageUrl, e);
        }
    }

    private ImageUtil() {
        // Private constructor for utility class
    }
}
