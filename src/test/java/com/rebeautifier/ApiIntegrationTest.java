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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    @Order(1)
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
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
    void logoutClearsSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}