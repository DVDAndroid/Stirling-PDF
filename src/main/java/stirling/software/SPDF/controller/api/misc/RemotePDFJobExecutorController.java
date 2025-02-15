package stirling.software.SPDF.controller.api.misc;

import static stirling.software.SPDF.utils.P7MUtils.p7m;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.pixee.security.Filenames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import stirling.software.SPDF.utils.WebResponseUtils;

@RestController
@RequestMapping("/api/v1/misc")
@Tag(name = "Misc", description = "Miscellaneous APIs")
public class RemotePDFJobExecutorController {

    private static final List<String> ALLOWED_JOBS = Arrays.asList("remove-cert-sign");

    @GetMapping("/remotePDFJobExecutor")
    @Operation(
            summary = "Runs a job on a remote PDF",
            description = "This endpoints runs a job on a remote PDF")
    public ResponseEntity<byte[]> remotePDFJob(HttpServletRequest req, String job, String url)
            throws Exception {
        if (!ALLOWED_JOBS.contains(job)) {
            return ResponseEntity.badRequest().body("Invalid job".getBytes());
        }

        Path tempDir = Files.createTempDirectory("remotejob_process");
        Path tempInputFile = tempDir.resolve("input.pdf");
        Files.createDirectories(tempDir);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .build();

        HttpResponse<Void> response =
                client.send(httpRequest, HttpResponse.BodyHandlers.discarding());

        String contentDisposition =
                response.headers().firstValue("content-disposition").orElse(null);

        String filename = "pdf";
        if (contentDisposition != null) {
            Pattern pattern = Pattern.compile("filename=\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(contentDisposition);
            filename = matcher.find() ? matcher.group(1) : "pdf";
        }
        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, tempInputFile, StandardCopyOption.REPLACE_EXISTING);
        }

        return WebResponseUtils.boasToWebResponse(
                p7m(tempInputFile),
                Filenames.toSimpleFileName(filename).replaceFirst("[.][^.]+$", "")
                        + "_unsigned.pdf");
    }
}
