package com.nightowl.nightowl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FileController {

    @Autowired
    private GenerateController generateController;

    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {

        try {
            String extractedText = extractText(file);

            if (extractedText.isBlank()) {
                return Map.of("error", "Could not extract text from this file.");
            }

            // Limit text to 8000 characters to avoid overwhelming the AI
            if (extractedText.length() > 8000) {
                extractedText = extractedText.substring(0, 8000);
            }

            System.out.println("Extracted " + extractedText.length() + " characters from file");

            return generateController.generateFromText(extractedText, type);

        } catch (Exception e) {
            return Map.of("error", "File processing failed: " + e.getMessage());
        }
    }
    private String extractText(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename().toLowerCase();

        if (filename.endsWith(".pdf")) {
            PDDocument doc = org.apache.pdfbox.Loader.loadPDF(file.getBytes());
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            doc.close();
            return text;
        }

        throw new Exception("Unsupported file type. Please upload a PDF file.");
    }
}
