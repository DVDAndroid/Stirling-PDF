package stirling.software.SPDF.controller.api.security;

import static stirling.software.SPDF.utils.P7MUtils.p7m;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

            return WebResponseUtils.boasToWebResponse(
                    p7m(tempInputFile),
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
}
