package com.nightowl.nightowl;
import org.springframework.beans.factory.annotation.Value;
import com.google.gson.Gson;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class GenerateController {


    @Value("${GROQ_API_KEY}")
    private String GROQ_API_KEY;
    private final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final Gson gson = new Gson();

    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, String> request) {
        String notes = request.get("notes");
        String type = request.get("type");
        return generateFromText(notes, type);
    }

    private String buildPrompt(String notes, String type) {
        return switch (type) {
            case "flashcards" -> """
            Generate flashcards from these notes.
            Cover ALL important concepts — don't skip anything.
            Each flashcard should cover exactly one concept.
            Return ONLY a JSON array, no other text:
            [{"question":"...","answer":"..."}]
            Notes: """ + notes;
            case "quiz" -> """
            Generate multiple choice questions from these notes.
            Cover ALL important concepts — don't skip anything.
            Return ONLY a JSON array, no other text:
            [{"question":"...","options":["A)...","B)...","C)...","D)..."],"answer":"A"}]
            Notes: """ + notes;
            case "shortnotes" -> """
            Summarize these notes into concise bullet points covering ALL key concepts.
            Don't miss anything important. Group related points together.
            Return ONLY a JSON object, no other text:
            {"result":"• point1\\n• point2\\n• point3"}
            Notes: """ + notes;
            default -> throw new RuntimeException("Invalid type: " + type);
        };
    }
    public Map<String, Object> generateFromText(String notes, String type) {
        String prompt = buildPrompt(notes, type);
        String aiResponse = callGroq(prompt);
        return parseResponse(aiResponse, type);
    }
    private String callGroq(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_URL))
                    .header("Authorization", "Bearer " + GROQ_API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("STATUS: " + response.statusCode());
            System.out.println("BODY: " + response.body());

            Map responseMap = gson.fromJson(response.body(), Map.class);
            List choices = (List) responseMap.get("choices");
            Map firstChoice = (Map) choices.get(0);
            Map message = (Map) firstChoice.get("message");
            return (String) message.get("content");

        } catch (Exception e) {
            throw new RuntimeException("Groq API call failed: " + e.getMessage());
        }
    }

    private Map<String, Object> parseResponse(String content, String type) {
        try {
            String clean = content.trim();
            if (clean.contains("```json")) {
                clean = clean.substring(clean.indexOf("```json") + 7);
                clean = clean.substring(0, clean.lastIndexOf("```"));
            } else if (clean.contains("```")) {
                clean = clean.substring(clean.indexOf("```") + 3);
                clean = clean.substring(0, clean.lastIndexOf("```"));
            }
            clean = clean.trim();

            if (type.equals("flashcards")) {
                List<Map> flashcards = Arrays.asList(gson.fromJson(clean, Map[].class));
                return Map.of("flashcards", flashcards);
            } else if (type.equals("quiz")) {
                List<Map> questions = Arrays.asList(gson.fromJson(clean, Map[].class));
                return Map.of("quiz", questions);
            } else {
                Map result = gson.fromJson(clean, Map.class);
                return Map.of("result", result.get("result"));
            }
        } catch (Exception e) {
            return Map.of("result", content);
        }
    }
}

