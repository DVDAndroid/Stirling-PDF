package stirling.software.SPDF.utils;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

import io.github.pixee.security.BoundedLineReader;

public class P7MUtils {

    public static @NotNull ByteArrayOutputStream p7m(Path tempInputFile)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("/scripts/p7m");
        command.add(tempInputFile.toString());
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = BoundedLineReader.readLine(reader, 5_000_000)) != null) {
                out.append(line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("p7m failed with exit code: " + exitCode);
        }
        String output = out.toString();
        String outPdfFilePath = extractFilePath(output);
        if (outPdfFilePath == null) throw new RuntimeException("p7m errore path");
        ByteArrayOutputStream document = readFileToByteArrayOutputStream(outPdfFilePath);
        return document;
    }

    private static String extractFilePath(String text) {
        Pattern pattern = Pattern.compile("'/([^']+)'");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return "/" + matcher.group(1);
        }
        return null;
    }

    private static ByteArrayOutputStream readFileToByteArrayOutputStream(String filePath)
            throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (FileInputStream inputStream = new FileInputStream(filePath)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return outputStream;
    }
}
