package stirling.software.SPDF.controller.api.security;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.pixee.security.BoundedLineReader;
import io.github.pixee.security.Filenames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import stirling.software.SPDF.model.api.PDFFile;
import stirling.software.SPDF.service.CustomPDDocumentFactory;
import stirling.software.SPDF.utils.WebResponseUtils;

@RestController
@RequestMapping("/api/v1/security")
@Tag(name = "Security", description = "Security APIs")
public class RemoveCertSignController {

    private final CustomPDDocumentFactory pdfDocumentFactory;

    @Autowired
    public RemoveCertSignController(CustomPDDocumentFactory pdfDocumentFactory) {
        this.pdfDocumentFactory = pdfDocumentFactory;
    }

    @PostMapping(consumes = "multipart/form-data", value = "/remove-cert-sign")
    @Operation(
            summary = "Remove digital signature from PDF",
            description =
                    "This endpoint accepts a PDF file and returns the PDF file without the digital signature. Input:PDF, Output:PDF Type:SISO")
    public ResponseEntity<byte[]> removeCertSignPDF(@ModelAttribute PDFFile request)
            throws Exception {
        MultipartFile pdf = request.getFileInput();

        String originalFilename = pdf.getOriginalFilename();
        if (originalFilename.endsWith(".pdf.p7m")) {
            Path tempDir = Files.createTempDirectory("p7m_process");
            Path tempInputFile = tempDir.resolve("input.pdf.p7m");
            Files.createDirectories(tempDir);
            pdf.transferTo(tempInputFile.toFile());

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
            return WebResponseUtils.boasToWebResponse(
                    document,
                    Filenames.toSimpleFileName(originalFilename).replaceFirst("[.][^.]+$", "")
                            + "_unsigned.pdf");
        }

        // Load the PDF document
        PDDocument document = pdfDocumentFactory.load(pdf);

        // Get the document catalog
        PDDocumentCatalog catalog = document.getDocumentCatalog();

        // Get the AcroForm
        PDAcroForm acroForm = catalog.getAcroForm();
        if (acroForm != null) {
            // Remove signature fields safely
            List<PDField> fieldsToRemove =
                    acroForm.getFields().stream()
                            .filter(field -> field instanceof PDSignatureField)
                            .collect(Collectors.toList());

            if (!fieldsToRemove.isEmpty()) {
                acroForm.flatten(fieldsToRemove, false);
            }
        }
        // Return the modified PDF as a response
        return WebResponseUtils.pdfDocToWebResponse(
                document,
                Filenames.toSimpleFileName(originalFilename).replaceFirst("[.][^.]+$", "")
                        + "_unsigned.pdf");
    }

    private static String extractFilePath(String text) {
        Pattern pattern = Pattern.compile("'/([^']+)'");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return "/" + matcher.group(1);
        }
        return null;
    }

    public static ByteArrayOutputStream readFileToByteArrayOutputStream(String filePath)
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
