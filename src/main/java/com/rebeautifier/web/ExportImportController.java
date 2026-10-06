package com.rebeautifier.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rebeautifier.service.ExportImportService;
import com.rebeautifier.web.dto.BookBackupDocument;
import com.rebeautifier.web.dto.BookResponse;
import com.rebeautifier.web.error.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ExportImportController {

    private final ExportImportService exportImportService;
    private final ObjectMapper objectMapper;

    public ExportImportController(ExportImportService exportImportService, ObjectMapper objectMapper) {
        this.exportImportService = exportImportService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/books/{bookId}/export")
    public ResponseEntity<byte[]> exportBook(@PathVariable UUID bookId,
                                             @RequestParam(defaultValue = "json") String format) {
        if ("markdown".equalsIgnoreCase(format) || "md".equalsIgnoreCase(format)) {
            String markdown = exportImportService.exportMarkdown(bookId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"book-" + bookId + ".md\"")
                    .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                    .body(markdown.getBytes(StandardCharsets.UTF_8));
        }
        if ("json".equalsIgnoreCase(format)) {
            try {
                BookBackupDocument document = exportImportService.exportJson(bookId);
                byte[] body = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"book-" + bookId + ".json\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body);
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                throw ApiException.badRequest("Could not serialize export");
            }
        }
        throw ApiException.badRequest("format must be json or markdown");
    }

    @PostMapping(value = "/books/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse importBook(@RequestBody String rawJson) {
        return exportImportService.importJson(rawJson);
    }
}
