package com.rebeautifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static MockHttpSession session;
    private static UUID bookId;
    private static UUID chapterId;
    private static UUID sectionId;
    private static UUID noteIdForAi;

    @Test
    @Order(1)
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.version").exists());

        mockMvc.perform(get("/api/v1/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.version").exists());
    }

    @Test
    @Order(2)
    void registerLoginAndCreateHierarchy() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"user_%s",
                                  "email":"user_%s@example.com",
                                  "password":"password123",
                                  "displayName":"Test User"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("user_" + suffix))
                .andReturn();

        session = (MockHttpSession) register.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Test User"));

        MvcResult bookResult = mockMvc.perform(post("/api/v1/books")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Integration Book","description":"Persisted hierarchy"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Integration Book"))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andReturn();

        bookId = UUID.fromString(objectMapper.readTree(bookResult.getResponse().getContentAsString()).get("id").asText());

        MvcResult chapterResult = mockMvc.perform(post("/api/v1/books/" + bookId + "/chapters")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Chapter One"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        chapterId = UUID.fromString(objectMapper.readTree(chapterResult.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/v1/books/" + bookId + "/chapters")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Chapter Two"}
                                """))
                .andExpect(status().isCreated());

        MvcResult sectionResult = mockMvc.perform(post("/api/v1/chapters/" + chapterId + "/sections")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Section A"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        sectionId = UUID.fromString(objectMapper.readTree(sectionResult.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(get("/api/v1/books/" + bookId + "/chapters").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Chapter One"))
                .andExpect(jsonPath("$[1].title").value("Chapter Two"));

        mockMvc.perform(get("/api/v1/chapters/" + chapterId + "/sections").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sectionId.toString()));
    }

    @Test
    @Order(3)
    void reorderChapters() throws Exception {
        MvcResult chapters = mockMvc.perform(get("/api/v1/books/" + bookId + "/chapters").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode array = objectMapper.readTree(chapters.getResponse().getContentAsString());
        UUID first = UUID.fromString(array.get(0).get("id").asText());
        UUID second = UUID.fromString(array.get(1).get("id").asText());

        mockMvc.perform(put("/api/v1/books/" + bookId + "/chapters/reorder")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("orderedIds", List.of(second, first)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second.toString()))
                .andExpect(jsonPath("$[1].id").value(first.toString()));
    }

    @Test
    @Order(4)
    void updateAndDeleteSection() throws Exception {
        mockMvc.perform(patch("/api/v1/sections/" + sectionId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Section A renamed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Section A renamed"));

        mockMvc.perform(delete("/api/v1/sections/" + sectionId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(5)
    void createUpdateAndVersionNotes() throws Exception {
        // Recreate a section after order-4 delete so section notes still have a parent.
        MvcResult sectionResult = mockMvc.perform(post("/api/v1/chapters/" + chapterId + "/sections")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Notes Section"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        sectionId = UUID.fromString(objectMapper.readTree(sectionResult.getResponse().getContentAsString()).get("id").asText());

        MvcResult bookNote = mockMvc.perform(post("/api/v1/books/" + bookId + "/notes")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attachType":"BOOK",
                                  "title":"Book thought",
                                  "bodyRich":"<p>Original capture</p>",
                                  "status":"SAVED",
                                  "tags":["seed","book"],
                                  "pageStart":2,
                                  "pageEnd":4
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Book thought"))
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.bodyOriginal").value("<p>Original capture</p>"))
                .andExpect(jsonPath("$.contentVersion").value(1))
                .andExpect(jsonPath("$.tags[0]").value("seed"))
                .andReturn();
        UUID bookNoteId = UUID.fromString(objectMapper.readTree(bookNote.getResponse().getContentAsString()).get("id").asText());
        noteIdForAi = bookNoteId;

        mockMvc.perform(post("/api/v1/books/" + bookId + "/notes")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attachType":"SECTION",
                                  "chapterId":"%s",
                                  "sectionId":"%s",
                                  "title":"Section draft",
                                  "bodyRich":"<p>Still forming</p>",
                                  "status":"DRAFT",
                                  "tags":["draft"]
                                }
                                """.formatted(chapterId, sectionId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachType").value("SECTION"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.bodyOriginal").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(patch("/api/v1/notes/" + bookNoteId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedContentVersion":1,
                                  "bodyRich":"<p>Working edit</p>",
                                  "status":"SAVED",
                                  "tags":["seed","edited"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyRich").value("<p>Working edit</p>"))
                .andExpect(jsonPath("$.bodyOriginal").value("<p>Original capture</p>"))
                .andExpect(jsonPath("$.contentVersion").value(2));

        mockMvc.perform(patch("/api/v1/notes/" + bookNoteId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedContentVersion":1,
                                  "bodyRich":"<p>Stale write</p>"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/books/" + bookId + "/notes").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Order(6)
    void viewerCannotMutateNotes() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult viewerRegister = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"viewer_%s",
                                  "email":"viewer_%s@example.com",
                                  "password":"password123",
                                  "displayName":"Viewer"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession viewerSession = (MockHttpSession) viewerRegister.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/books/" + bookId + "/members")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"viewer_%s","role":"VIEWER"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/books/" + bookId + "/notes").session(viewerSession))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/books/" + bookId + "/notes")
                        .session(viewerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attachType":"BOOK","title":"Nope","bodyRich":"<p>x</p>","status":"DRAFT"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void refineAcceptAndStaleWithMockOllama() throws Exception {
        mockMvc.perform(get("/api/v1/system/ai-status").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.model").value("llama3.2:1b"));

        MvcResult refine = mockMvc.perform(post("/api/v1/notes/" + noteIdForAi + "/refinements")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"GRAMMAR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.mode").value("GRAMMAR"))
                .andExpect(jsonPath("$.resultRich").isNotEmpty())
                .andReturn();
        UUID revisionId = UUID.fromString(objectMapper.readTree(refine.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/v1/notes/" + noteIdForAi + "/refinements")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"SUMMARY"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("SUMMARY"));

        mockMvc.perform(get("/api/v1/notes/" + noteIdForAi + "/refinements").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", org.hamcrest.Matchers.greaterThanOrEqualTo(2)));

        // Change note so prior revision becomes stale on accept path
        mockMvc.perform(patch("/api/v1/notes/" + noteIdForAi)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedContentVersion":2,
                                  "bodyRich":"<p>Changed after refine</p>",
                                  "status":"SAVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentVersion").value(3));

        mockMvc.perform(post("/api/v1/refinements/" + revisionId + "/accept")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVISION_STALE"));

        MvcResult freshRefine = mockMvc.perform(post("/api/v1/notes/" + noteIdForAi + "/refinements")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"TAKEAWAYS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn();
        UUID freshId = UUID.fromString(objectMapper.readTree(freshRefine.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/v1/refinements/" + freshId + "/accept")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"editedBodyRich":"<p>Accepted takeaways</p>"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyRich").value("<p>Accepted takeaways</p>"))
                .andExpect(jsonPath("$.bodyOriginal").value("<p>Original capture</p>"));

        mockMvc.perform(post("/api/v1/notes/" + noteIdForAi + "/refinements")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"ORGANIZE"}
                                """))
                .andExpect(status().isCreated());

        MvcResult qa = mockMvc.perform(post("/api/v1/notes/" + noteIdForAi + "/refinements")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"QA","question":"What changed?"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("QA"))
                .andReturn();
        UUID qaId = UUID.fromString(objectMapper.readTree(qa.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/v1/refinements/" + qaId + "/reject")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(post("/api/v1/refinements/" + qaId + "/regenerate")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("QA"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    @Order(8)
    void sharedEditorCanRefineButViewerCannot() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult editorRegister = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"ed_%s",
                                  "email":"ed_%s@example.com",
                                  "password":"password123",
                                  "displayName":"Shared Editor"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession editorSession = (MockHttpSession) editorRegister.getRequest().getSession(false);

        MvcResult viewerRegister = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"vw_%s",
                                  "email":"vw_%s@example.com",
                                  "password":"password123",
                                  "displayName":"Shared Viewer"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        MockHttpSession viewerSession = (MockHttpSession) viewerRegister.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/books/" + bookId + "/members")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"ed_%s","role":"EDITOR"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/books/" + bookId + "/members")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail":"vw_%s","role":"VIEWER"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/notes/" + noteIdForAi + "/refinements").session(editorSession))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/notes/" + noteIdForAi + "/refinements")
                        .session(editorSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"GRAMMAR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/v1/notes/" + noteIdForAi + "/refinements").session(viewerSession))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/notes/" + noteIdForAi + "/refinements")
                        .session(viewerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"SUMMARY"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void searchExportAndImport() throws Exception {
        mockMvc.perform(get("/api/v1/search").session(session).param("q", "a"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/search").session(session).param("q", "Accepted takeaways"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.hits[0].type").value("NOTE"));

        // Minimal 1x1 PNG
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/books/" + bookId + "/cover")
                        .file(new MockMultipartFile("file", "cover.png", "image/png", png))
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").isNotEmpty());

        MvcResult mdExport = mockMvc.perform(get("/api/v1/books/" + bookId + "/export")
                        .session(session)
                        .param("format", "markdown"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(mdExport.getResponse().getContentAsString()).contains("# Integration Book");
        assertThat(mdExport.getResponse().getContentAsString()).contains("![Cover](data:image/png;base64,");
        assertThat(mdExport.getResponse().getContentAsString()).doesNotContain("JSON export");
        MvcResult jsonExport = mockMvc.perform(get("/api/v1/books/" + bookId + "/export")
                        .session(session)
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andReturn();
        String exportBody = jsonExport.getResponse().getContentAsString();
        assertThat(exportBody).contains("rebeautifier.book-backup");
        assertThat(exportBody).containsPattern("\"schemaVersion\"\\s*:\\s*2");
        assertThat(exportBody).contains("coverBase64");
        assertThat(exportBody).contains("image/png");

        mockMvc.perform(post("/api/v1/books/import")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schema\":\"wrong\",\"schemaVersion\":1,\"book\":{\"title\":\"X\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_INVALID"));

        // Legacy v1 (no cover) still imports
        mockMvc.perform(post("/api/v1/books/import")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schema":"rebeautifier.book-backup","schemaVersion":1,"book":{"title":"Legacy V1 Book","chapters":[],"notes":[]}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Legacy V1 Book"));

        MvcResult imported = mockMvc.perform(post("/api/v1/books/import")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exportBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Integration Book"))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.coverUrl").isNotEmpty())
                .andReturn();
        assertThat(imported.getResponse().getContentAsString()).contains("/api/v1/covers/");

        // In-place replace on the original book
        mockMvc.perform(put("/api/v1/books/" + bookId + "/import")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exportBody.replace("Integration Book", "Integration Book Replaced")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Integration Book Replaced"))
                .andExpect(jsonPath("$.coverUrl").isNotEmpty());

        mockMvc.perform(get("/api/v1/books").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    @Order(10)
    void changePasswordAndAiMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/system/ai-status").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricsAttempts").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.metricsSucceeded").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.metricsFailed").exists())
                .andExpect(jsonPath("$.metricsAverageLatencyMs").exists());

        mockMvc.perform(patch("/api/v1/auth/me")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Tester\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Tester"));

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Tester"));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrong-password","newPassword":"password456"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"password123","newPassword":"password456"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk());

        // restore default password for later tests
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"password456","newPassword":"password123"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(11)
    void transferOwnershipToExistingUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"heir_%s",
                                  "email":"heir_%s@example.com",
                                  "password":"password123",
                                  "displayName":"Heir"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/books/" + bookId + "/transfer")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"heir_%s\"}".formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("EDITOR"));

        mockMvc.perform(get("/api/v1/books/" + bookId + "/members").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", org.hamcrest.Matchers.hasItem("heir_" + suffix)))
                .andExpect(jsonPath("$[*].role", org.hamcrest.Matchers.hasItem("OWNER")));
    }

    @Test
    @Order(12)
    void logoutClearsSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}