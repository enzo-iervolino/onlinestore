package com.delta.store.product.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class VideoGenerationService {

    private static final long FFMPEG_TIMEOUT_SECONDS = 45;

    @Value("${app.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.ffmpeg.webm-duration-seconds:3}")
    private int durationSeconds;

    @Value("${app.ffmpeg.webm-fps:15}")
    private int fps;

    @Value("${app.ffmpeg.webm-bitrate:700k}")
    private String bitrate;

    public boolean isImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            return false;
        }

        String lower = originalFilename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif");
    }

    public Path generateWebmFromImage(Path imagePath, Path outputDirectory) {
        if (imagePath == null) {
            throw new IllegalArgumentException("Il file immagine sorgente è obbligatorio.");
        }
        if (outputDirectory == null) {
            throw new IllegalArgumentException("La cartella di destinazione è obbligatoria.");
        }
        if (!Files.exists(imagePath)) {
            throw new IllegalArgumentException("File immagine non trovato: " + imagePath);
        }

        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Impossibile creare la cartella per il video WebM: " + outputDirectory, e);
        }

        String baseName = cleanBaseName(imagePath.getFileName() != null ? imagePath.getFileName().toString() : "media");
        Path outputPath = outputDirectory.resolve(baseName + "-" + UUID.randomUUID().toString().replace("-", "") + ".webm");

        int safeDuration = Math.max(1, durationSeconds);
        int safeFps = Math.max(10, fps);
        int totalFrames = safeDuration * safeFps;
        String safeBitrate = StringUtils.hasText(bitrate) ? bitrate : "700k";

        String animatedProductFilter = "scale=960:960:force_original_aspect_ratio=decrease,"
                + "pad=960:960:(ow-iw)/2:(oh-ih)/2:color=white,"
                + "zoompan=z='min(zoom+0.0018,1.07)':d=" + totalFrames + ":s=960x960:fps=" + safeFps + ","
                + "format=yuv420p";

        ProcessBuilder processBuilder = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-loop", "1",
                "-i", imagePath.toAbsolutePath().normalize().toString(),
                "-t", String.valueOf(safeDuration),
                "-vf", animatedProductFilter,
                "-c:v", "libvpx",
                "-deadline", "realtime",
                "-cpu-used", "8",
                "-b:v", safeBitrate,
                "-an",
                outputPath.toAbsolutePath().normalize().toString()
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = processBuilder.start();
            boolean completed = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException("Timeout durante la generazione del video WebM con FFmpeg.");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("FFmpeg non ha generato il video WebM. Verifica che FFmpeg sia installato e supporti il codec libvpx.");
            }

            if (!Files.exists(outputPath)) {
                throw new RuntimeException("Video WebM non creato: " + outputPath);
            }

            return outputPath;
        } catch (IOException e) {
            throw new RuntimeException("FFmpeg non risulta eseguibile. Verifica app.ffmpeg.path o installa FFmpeg sul server.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Generazione WebM interrotta.", e);
        }
    }

    private String cleanBaseName(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "media" : filename);
        int dotIndex = cleaned.lastIndexOf('.');
        if (dotIndex > 0) {
            cleaned = cleaned.substring(0, dotIndex);
        }
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9._-]", "-");
        if (!StringUtils.hasText(cleaned)) {
            cleaned = "media";
        }
        return cleaned;
    }
}
