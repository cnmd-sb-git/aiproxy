package com.example.aiproxy.common.audio;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioHelper {

    private static final Logger LOGGER = Logger.getLogger(AudioHelper.class.getName());
    public static final String ERR_AUDIO_DURATION_NAN_MSG = "Audio duration is N/A";

    // Corresponds to regexp.MustCompile(`time=(\d+:\d+:\d+\.\d+)`)
    private static final Pattern FFMPEG_TIME_PATTERN = Pattern.compile("time=(\\d{2}:\\d{2}:\\d{2}\\.\\d+)");

    // Placeholder for config.FfmpegEnabled
    // In a real application, this would be loaded from a configuration file/service.
    private static boolean ffmpegEnabled = true; // Assume true if class is used.

    public static void setFfmpegEnabled(boolean enabled) {
        ffmpegEnabled = enabled;
    }

    public static boolean isFfmpegEnabled() {
        return ffmpegEnabled;
    }


    /**
     * Gets the duration of an audio stream using ffprobe.
     * If the input stream does not support mark/reset, it will be consumed by the first attempt
     * and will not be available for the fallback if ffprobe fails to determine duration directly.
     * The caller is responsible for providing a stream that can be read multiple times if needed (e.g., ByteArrayInputStream,
     * or a FileInputStream from a file, or a stream that is buffered and supports mark/reset).
     *
     * @param audioInputStream The audio stream.
     * @return The duration in seconds.
     * @throws IOException           If an I/O error occurs or ffprobe/ffmpeg command fails.
     * @throws AudioDurationException If the duration cannot be determined.
     */
    public static double getAudioDuration(InputStream audioInputStream) throws IOException, AudioDurationException {
        if (!ffmpegEnabled) {
            LOGGER.info("FFmpeg is not enabled. Skipping audio duration check.");
            return 0.0;
        }

        // Try with ffprobe first (direct duration query)
        ProcessBuilder ffprobeBuilder = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "stream=duration", // For streams
                "-of", "default=noprint_wrappers=1:nokey=1",
                "-i", "-" // Input from stdin
        );

        String outputString;
        Process ffprobeProcess = null;
        ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream(); // To capture stderr for debugging

        try {
            ffprobeProcess = ffprobeBuilder.start();

            // Write audio data to ffprobe's stdin in a separate thread
            // to avoid deadlocks if the audio stream is large.
            Thread inputThread = new Thread(() -> {
                try (OutputStream processStdin = ffprobeProcess.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                        processStdin.write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error writing to ffprobe stdin: " + e.getMessage(), e);
                }
            });
            inputThread.start();

            // Read ffprobe's stdout
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffprobeProcess.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(ffprobeProcess.getErrorStream()))) {
                StringBuilder outputBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line);
                }
                outputString = outputBuilder.toString().trim();

                // Capture stderr
                StringBuilder errorOutputBuilder = new StringBuilder();
                 while ((line = errorReader.readLine()) != null) {
                    errorOutputBuilder.append(line).append("\n");
                }
                if(errorOutputBuilder.length() > 0){
                    LOGGER.fine("ffprobe stderr: " + errorOutputBuilder.toString());
                }
            }
            
            inputThread.join(5000); // Wait for input thread to finish or timeout

            boolean exited = ffprobeProcess.waitFor(10, TimeUnit.SECONDS); // Wait for ffprobe to exit
            if (!exited) {
                ffprobeProcess.destroyForcibly();
                throw new IOException("ffprobe command timed out.");
            }
            if (ffprobeProcess.exitValue() != 0 && outputString.isEmpty()) {
                 // If exit code is non-zero AND output is empty, it's likely an error
                throw new IOException("ffprobe command failed with exit code " + ffprobeProcess.exitValue() + ". Stderr: " + errorBuffer.toString(StandardCharsets.UTF_8));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffprobe command interrupted.", e);
        } finally {
            if (ffprobeProcess != null && ffprobeProcess.isAlive()) {
                ffprobeProcess.destroyForcibly();
            }
            // Ensure the input stream is closed by the caller or a try-with-resources if it was opened here.
        }


        if (outputString.isEmpty() || "N/A".equalsIgnoreCase(outputString)) {
            LOGGER.info("ffprobe could not determine duration directly or returned N/A. Trying fallback with ffmpeg.");
            // The original Go code attempts to seeker.Seek(0, io.SeekStart).
            // This requires the InputStream to support mark/reset.
            if (audioInputStream.markSupported()) {
                try {
                    audioInputStream.reset(); // Attempt to rewind
                    return getAudioDurationFallback(audioInputStream);
                } catch (IOException e) {
                    LOGGER.warning("Failed to reset input stream for ffmpeg fallback: " + e.getMessage() + ". Fallback might fail or read from an empty stream.");
                    // Proceed, but it might fail if stream can't be re-read.
                     return getAudioDurationFallback(audioInputStream); // Try anyway, might be partially read
                }
            } else {
                LOGGER.warning("InputStream does not support mark/reset. Fallback with ffmpeg might not work as expected if stream is already consumed.");
                // If we absolutely need to retry, the stream should have been copied to a buffer first.
                // For now, we'll throw, as the Go code implies rewinding is possible.
                throw new AudioDurationException("ffprobe failed and input stream cannot be reset for ffmpeg fallback.");
            }
        }

        try {
            return Double.parseDouble(outputString);
        } catch (NumberFormatException e) {
            throw new AudioDurationException("Failed to parse duration from ffprobe output: " + outputString, e);
        }
    }


    private static double getAudioDurationFallback(InputStream audioInputStream) throws IOException, AudioDurationException {
        if (!ffmpegEnabled) {
            return 0.0;
        }
        LOGGER.fine("Executing ffmpeg fallback for stream.");
        ProcessBuilder ffmpegBuilder = new ProcessBuilder(
                "ffmpeg",
                "-i", "-",       // Input from stdin
                "-f", "null", "-" // No output file
        );

        Process ffmpegProcess = null;
        StringBuilder stderrBuilder = new StringBuilder();

        try {
            ffmpegProcess = ffmpegBuilder.start();

            // Write audio data to ffmpeg's stdin in a separate thread
            Thread inputThread = new Thread(() -> {
                try (OutputStream processStdin = ffmpegProcess.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                        processStdin.write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                     LOGGER.log(Level.WARNING, "Error writing to ffmpeg stdin: " + e.getMessage(), e);
                }
            });
            inputThread.start();
            
            // ffmpeg writes progress/duration info to stderr
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(ffmpegProcess.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    stderrBuilder.append(line).append("\n");
                }
            }
            
            inputThread.join(5000); // Wait for input thread to finish

            boolean exited = ffmpegProcess.waitFor(15, TimeUnit.SECONDS); // Increased timeout for ffmpeg
             if (!exited) {
                ffmpegProcess.destroyForcibly();
                throw new IOException("ffmpeg command timed out.");
            }

            // ffmpeg can return non-0 exit code even if it processes the file and prints duration
            // (e.g. if input stream ends unexpectedly but enough was read to determine format/duration)
            // So, we parse stderr regardless of exit code, unless it's a catastrophic failure.
            if (ffmpegProcess.exitValue() != 0 && stderrBuilder.length() == 0) {
                throw new IOException("ffmpeg command failed with exit code " + ffmpegProcess.exitValue() + " and no stderr output.");
            }
            
            LOGGER.fine("ffmpeg stderr output:\n" + stderrBuilder.toString());
            return parseTimeFromFfmpegOutput(stderrBuilder.toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg command interrupted.", e);
        } finally {
            if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
                ffmpegProcess.destroyForcibly();
            }
        }
    }

    public static double getAudioDurationFromFilePath(String filePath) throws IOException, AudioDurationException {
        if (!ffmpegEnabled) {
            LOGGER.info("FFmpeg is not enabled. Skipping audio duration check for file: " + filePath);
            return 0.0;
        }

        ProcessBuilder ffprobeBuilder = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "format=duration", // For files, format=duration is often more reliable
                "-of", "default=noprint_wrappers=1:nokey=1",
                "-i", filePath
        );

        String outputString;
        Process ffprobeProcess = null;
        try {
            ffprobeProcess = ffprobeBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffprobeProcess.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(ffprobeProcess.getErrorStream()))) {
                StringBuilder outputBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line);
                }
                outputString = outputBuilder.toString().trim();

                StringBuilder errorOutputBuilder = new StringBuilder();
                 while ((line = errorReader.readLine()) != null) {
                    errorOutputBuilder.append(line).append("\n");
                }
                if(errorOutputBuilder.length() > 0){
                    LOGGER.fine("ffprobe stderr for file " + filePath + ": " + errorOutputBuilder.toString());
                }
            }
            
            boolean exited = ffprobeProcess.waitFor(10, TimeUnit.SECONDS);
            if (!exited) {
                ffprobeProcess.destroyForcibly();
                throw new IOException("ffprobe command for file " + filePath + " timed out.");
            }
            if (ffprobeProcess.exitValue() != 0 && outputString.isEmpty()) {
                throw new IOException("ffprobe command for file " + filePath + " failed with exit code " + ffprobeProcess.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffprobe command for file " + filePath + " interrupted.", e);
        } finally {
             if (ffprobeProcess != null && ffprobeProcess.isAlive()) {
                ffprobeProcess.destroyForcibly();
            }
        }


        if (outputString.isEmpty() || "N/A".equalsIgnoreCase(outputString)) {
            LOGGER.info("ffprobe could not determine duration for file " + filePath + " or returned N/A. Trying fallback with ffmpeg.");
            return getAudioDurationFromFilePathFallback(filePath);
        }

        try {
            return Double.parseDouble(outputString);
        } catch (NumberFormatException e) {
            throw new AudioDurationException("Failed to parse duration from ffprobe output for file " + filePath + ": " + outputString, e);
        }
    }

    private static double getAudioDurationFromFilePathFallback(String filePath) throws IOException, AudioDurationException {
         if (!ffmpegEnabled) {
            return 0.0;
        }
        LOGGER.fine("Executing ffmpeg fallback for file: " + filePath);
        ProcessBuilder ffmpegBuilder = new ProcessBuilder(
                "ffmpeg",
                "-i", filePath,
                "-f", "null", "-"
        );

        Process ffmpegProcess = null;
        StringBuilder stderrBuilder = new StringBuilder();
        try {
            ffmpegProcess = ffmpegBuilder.start();
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(ffmpegProcess.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    stderrBuilder.append(line).append("\n");
                }
            }
             boolean exited = ffmpegProcess.waitFor(15, TimeUnit.SECONDS); // Increased timeout
            if (!exited) {
                ffmpegProcess.destroyForcibly();
                throw new IOException("ffmpeg command for file " + filePath + " timed out.");
            }
             if (ffmpegProcess.exitValue() != 0 && stderrBuilder.length() == 0) {
                throw new IOException("ffmpeg command for file " + filePath + " failed with exit code " + ffmpegProcess.exitValue() + " and no stderr output.");
            }
            LOGGER.fine("ffmpeg -i " + filePath + " -f null -\n" + stderrBuilder.toString());
            return parseTimeFromFfmpegOutput(stderrBuilder.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg command for file " + filePath + " interrupted.", e);
        } finally {
            if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
                ffmpegProcess.destroyForcibly();
            }
        }
    }

    private static double parseTimeFromFfmpegOutput(String output) throws AudioDurationException {
        Matcher matcher = FFMPEG_TIME_PATTERN.matcher(output);
        List<String> timeMatches = new ArrayList<>();
        while (matcher.find()) {
            timeMatches.add(matcher.group(1));
        }

        if (timeMatches.isEmpty()) {
            throw new AudioDurationException(ERR_AUDIO_DURATION_NAN_MSG + ". No time found in ffmpeg output.");
        }

        // Get the last time match
        String timeStr = timeMatches.get(timeMatches.size() - 1);
        String[] parts = timeStr.split(":");
        if (parts.length != 3) {
            throw new AudioDurationException("Invalid time format in ffmpeg output: " + timeStr);
        }

        try {
            double hours = Double.parseDouble(parts[0]);
            double minutes = Double.parseDouble(parts[1]);
            double seconds = Double.parseDouble(parts[2]);
            return hours * 3600 + minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            throw new AudioDurationException("Failed to parse time components from ffmpeg output: " + timeStr, e);
        }
    }

    // Custom exception for audio duration errors
    public static class AudioDurationException extends Exception {
        public AudioDurationException(String message) {
            super(message);
        }

        public AudioDurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    private AudioHelper() {} // Private constructor for utility class
}
