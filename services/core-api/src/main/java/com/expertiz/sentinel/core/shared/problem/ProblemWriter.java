package com.expertiz.sentinel.core.shared.problem;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Écrit un Problem Details hors de Spring MVC : filtres, point d'entrée et gestionnaire d'accès de Spring Security. */
@Component
public class ProblemWriter {

    private final JsonMapper json;

    public ProblemWriter(JsonMapper json) {
        this.json = json;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ProblemType type, String detail)
            throws IOException {
        ProblemDetail problem = Problems.create(type, detail, List.of(), request);
        response.setStatus(type.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), body(problem));
    }

    private static Map<String, Object> body(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }
}
