package com.example.bajaj;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class BajajWebhookSubmitterApplication {

    public static void main(String[] args) {
        SpringApplication.run(BajajWebhookSubmitterApplication.class, args);
    }

    @Value("${app.name}")
    private String name;

    @Value("${app.regNo}")
    private String regNo;

    @Value("${app.email}")
    private String email;

    @Value("${app.generateUrl}")
    private String generateUrl;

    @Value("${app.submitUrl}")
    private String submitUrl;

    @Value("${app.finalQuery}")
    private String finalQuery;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .filter((request, next) -> {
                    System.out.println("Calling: " + request.url());
                    return next.exchange(request);
                })
                .build();
    }

    @Bean
    public CommandLineRunner run(WebClient webClient) {
        return args -> {
            try {
                // 1) Generate webhook
                Map<?, ?> genResponse = webClient.post()
                        .uri(generateUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "name", name,
                                "regNo", regNo,
                                "email", email
                        ))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(30))
                        .block();

                System.out.println("Generate response: " + genResponse);

                if (genResponse == null) {
                    System.err.println("No response from generateWebhook API.");
                    return;
                }

                // 2) Extract webhook and token safely (no generic issues)
                Object webhookObj = null;
                Object accessTokenObj = null;

                if (genResponse.containsKey("webhook")) {
                    webhookObj = genResponse.get("webhook");
                } else if (genResponse.containsKey("webHook")) {
                    webhookObj = genResponse.get("webHook");
                } else if (genResponse.containsKey("webHookUrl")) {
                    webhookObj = genResponse.get("webHookUrl");
                }

                if (genResponse.containsKey("accessToken")) {
                    accessTokenObj = genResponse.get("accessToken");
                } else if (genResponse.containsKey("access_token")) {
                    accessTokenObj = genResponse.get("access_token");
                }

                String webhook = webhookObj != null ? webhookObj.toString() : "";
                String accessToken = accessTokenObj != null ? accessTokenObj.toString() : "";

                System.out.println("Webhook URL: " + webhook);
                System.out.println("Access Token: " + accessToken);

                // 3) Determine question based on last two digits of regNo
                int lastTwo = extractLastTwoDigits(regNo);
                boolean odd = (lastTwo % 2 == 1);
                System.out.println("RegNo last two digits: " + lastTwo + " → " + (odd ? "ODD (Question 1)" : "EVEN (Question 2)"));

                // 4) Validate SQL query before submission
                if (finalQuery == null || finalQuery.trim().isEmpty()
                        || finalQuery.contains("PUT YOUR FINAL SQL QUERY HERE")) {
                    System.err.println("❌ ERROR: app.finalQuery not set. Paste your final SQL in application.properties (app.finalQuery).");
                    return;
                }

                // 5) Submit final query
                Map<String, Object> body = Map.of("finalQuery", finalQuery);

                Map<?, ?> submitResponse = webClient.post()
                        .uri(submitUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", accessToken)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(30))
                        .block();

                System.out.println("✅ Submit response: " + submitResponse);
                System.out.println("✅ Done. Save this console output and the JAR file for submission.");

            } catch (Exception ex) {
                System.err.println("❌ Exception during flow: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                System.exit(0);
            }
        };
    }

    private static int extractLastTwoDigits(String regNo) {
        String digits = regNo.replaceAll("\\D+", "");
        if (digits.length() >= 2) {
            String lastTwo = digits.substring(digits.length() - 2);
            try {
                return Integer.parseInt(lastTwo);
            } catch (NumberFormatException ignored) {}
        }
        if (!digits.isEmpty()) {
            return Integer.parseInt(digits.substring(digits.length() - 1));
        }
        return 0;
    }
}
