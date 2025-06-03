package com.example.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GeminiPdfAnalyzerServer {

    private static final Logger LOGGER = Logger.getLogger(GeminiPdfAnalyzerServer.class.getName());
    private static final String GEMINI_CHAT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    private static final String GEMINI_CONTENT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}";

    // Configuration from environment variables
    private static final String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY");
    private static final String GEMINI_MODEL_NAME = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-2.0-flash");

    // Available models list (can be overridden by environment variable)
    private static final List<String> AVAILABLE_MODELS = Arrays.asList(
            "gemini-2.5-flash-preview-05-20",
            "gemini-2.5-flash-preview-04-17",
            "gemini-2.5-pro-preview-05-06",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash",
            "gemini-1.5-flash-8b",
            "gemini-1.5-pro",
            "gemma-3-27b-it",
            "gemma-3-12b-it",
            "gemma-3-4b-it",
            "gemma-3-1b-it"
    );

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder().build();

    public static void main(String[] args) {
        LOGGER.info("Starting Gemini PDF Analyzer MCP Server...");

        // Validate API key
        if (GEMINI_API_KEY == null || GEMINI_API_KEY.isEmpty()) {
            LOGGER.severe("GEMINI_API_KEY environment variable is not set!");
            System.exit(1);
        }

        LOGGER.info("Using default Gemini model: " + GEMINI_MODEL_NAME);

        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(new ObjectMapper());

        McpSyncServer mcpServer = McpServer.sync(transportProvider)
                .serverInfo("gemini-pdf-analyzer", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .build();

        // Tool 1: Analyze PDF with Gemini
        String analyzeToolSchema = """
                {
                  "type": "object",
                  "properties": {
                    "pdfResourceUri": {
                      "type": "string",
                      "description": "The file URI of the PDF resource provided by the client (e.g., file:///path/to/your.pdf)"
                    },
                    "originalPrompt": {
                      "type": "string",
                      "description": "The original prompt Claude received which should be used to analyze the PDF content with Gemini."
                    },
                    "modelOverride": {
                      "type": "string",
                      "description": "Optional: Override the default Gemini model for this request"
                    }
                  },
                  "required": ["pdfResourceUri", "originalPrompt"]
                }
                """;

        Tool analyzeWithGeminiTool = new Tool(
                "CLD_Gemini_PDF_Analyzer",
                "Extracts text from a given PDF, sends it with an original prompt to Gemini API for analysis, and returns Gemini's response.",
                analyzeToolSchema
        );

        // Tool 2: List available models
        String listModelsSchema = """
                {
                  "type": "object",
                  "properties": {}
                }
                """;

        Tool listModelsTool = new Tool(
                "listGoogleModels",
                "Lists all available Gemini models that can be used with the PDF analyzer",
                listModelsSchema
        );

        // Implementation for analyzePdfWithGemini
        McpServerFeatures.SyncToolSpecification analyzeWithGeminiSpec =
                new McpServerFeatures.SyncToolSpecification(
                        analyzeWithGeminiTool,
                        (exchange, arguments) -> {
                            String pdfUriString = (String) arguments.get("pdfResourceUri");
                            String originalPrompt = (String) arguments.get("originalPrompt");
                            String modelOverride = (String) arguments.get("modelOverride");

                            // Use override model if provided, otherwise use default
                            String modelToUse = (modelOverride != null && !modelOverride.isEmpty())
                                    ? modelOverride : GEMINI_MODEL_NAME;

                            LOGGER.info("Tool 'CLD_Gemini_PDF_Analyzer' called with URI: " + pdfUriString);
                            LOGGER.info("Using model: " + modelToUse);

                            try {
                                // Get PDF file
                                File pdfFile = getPdfFileFromUri(pdfUriString);

                                if (!pdfFile.exists() || !pdfFile.canRead()) {
                                    LOGGER.warning("PDF file does not exist or cannot be read: " + pdfFile.getAbsolutePath());
                                    return new CallToolResult(List.of(new TextContent("Error: PDF file not found or not readable at: " + pdfFile.getAbsolutePath())), true);
                                }

                                // Try direct PDF upload first
                                LOGGER.info("Attempting direct PDF upload to Gemini...");
                                try {
                                    String response = sendPdfDirectlyToGemini(pdfFile, originalPrompt, modelToUse);
                                    return new CallToolResult(List.of(new TextContent(response)), false);
                                } catch (Exception e) {
                                    LOGGER.warning("Direct PDF upload failed, falling back to text extraction: " + e.getMessage());
                                    // Fall back to text extraction method
                                    String response = sendExtractedTextToGemini(pdfFile, originalPrompt, modelToUse);
                                    return new CallToolResult(List.of(new TextContent(response)), false);
                                }

                            } catch (Exception e) {
                                LOGGER.log(Level.SEVERE, "An error occurred", e);
                                return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
                            }
                        });

        // Implementation for listGeminiModels
        McpServerFeatures.SyncToolSpecification listModelsSpec =
                new McpServerFeatures.SyncToolSpecification(
                        listModelsTool,
                        (exchange, arguments) -> {
                            LOGGER.info("Tool 'listGoogleModels' called");

                            Map<String, Object> modelsInfo = new HashMap<>();
                            modelsInfo.put("defaultModel", GEMINI_MODEL_NAME);
                            modelsInfo.put("availableModels", AVAILABLE_MODELS);
                            Map<String, String> modelCapabilities = Map.ofEntries(
                                    Map.entry("gemini-2.5-flash-preview-05-20", "Latest preview with high RPM (10 RPM, 250K TPM)"),
                                    Map.entry("gemini-2.5-flash-preview-04-17", "Latest preview with high RPM (10 RPM, 250K TPM)"),
                                    Map.entry("gemini-2.5-pro-preview-05-06", "Pro version with advanced capabilities"),
                                    Map.entry("gemini-2.0-flash", "Stable, fast model (15 RPM)"),
                                    Map.entry("gemini-2.0-flash-lite", "Lightweight version (30 RPM)"),
                                    Map.entry("gemini-1.5-flash", "Stable general-purpose model"),
                                    Map.entry("gemini-1.5-flash-8b", "Lightweight 8B parameter model"),
                                    Map.entry("gemini-1.5-pro", "Pro version for complex tasks"),
                                    Map.entry("gemma-3-27b-it", "Open model, 27B parameters"),
                                    Map.entry("gemma-3-12b-it", "Open model, 12B parameters"),
                                    Map.entry("gemma-3-4b-it", "Open model, 4B parameters"),
                                    Map.entry("gemma-3-1b-it", "Open model, 1B parameters")
                            );
                            modelsInfo.put("modelCapabilities", modelCapabilities);

                            String jsonResponse = null;
                            try {
                                jsonResponse = objectMapper.writeValueAsString(modelsInfo);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                            return new CallToolResult(List.of(new TextContent(jsonResponse)), false);
                        });

        mcpServer.addTool(analyzeWithGeminiSpec);
        mcpServer.addTool(listModelsSpec);

        LOGGER.info("Tools registered: 'CLD_Gemini_PDF_Analyzer' and 'listGoogleModels'");
        LOGGER.info("Gemini PDF Analyzer MCP Server is ready and waiting for client connection.");
    }

    /**
     * Send PDF directly to Gemini using multimodal API
     */
    private static String sendPdfDirectlyToGemini(File pdfFile, String prompt, String model) throws IOException, InterruptedException {
        // Read PDF as bytes
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);

        // Create multimodal request
        Map<String, Object> filePart = Map.of(
                "inline_data", Map.of(
                        "mime_type", "application/pdf",
                        "data", base64Pdf
                )
        );

        Map<String, Object> textPart = Map.of("text", prompt);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(textPart, filePart)
                )),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 8192
                )
        );

        String requestBodyJson = objectMapper.writeValueAsString(requestBody);

        // Build URL with model and API key
        String url = GEMINI_CONTENT_ENDPOINT
                .replace("{model}", model)
                .replace("{key}", GEMINI_API_KEY);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
            throw new IOException("Unexpected response format from Gemini");
        } else {
            throw new IOException("Gemini API error: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Fallback method: Extract text and send to Gemini
     */
    private static String sendExtractedTextToGemini(File pdfFile, String prompt, String model) throws IOException, InterruptedException {
        LOGGER.info("Using text extraction fallback method...");

        String pdfText;
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            pdfText = stripper.getText(document);
        }
        LOGGER.info("Successfully extracted text from PDF. Length: " + pdfText.length());

        String combinedPromptForGemini = prompt + "\n\n--- PDF Content ---\n" + pdfText;

        Map<String, Object> message = Map.of("role", "user", "content", combinedPromptForGemini);
        Map<String, Object> requestBodyMap = Map.of(
                "model", model,
                "messages", List.of(message),
                "temperature", 0.7
        );
        String requestBodyJson = objectMapper.writeValueAsString(requestBodyMap);

        HttpRequest geminiRequest = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_CHAT_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer    " + GEMINI_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        HttpResponse<String> geminiResponse = httpClient.send(geminiRequest, HttpResponse.BodyHandlers.ofString());

        if (geminiResponse.statusCode() >= 200 && geminiResponse.statusCode() < 300) {
            Map<String, Object> responseMap = objectMapper.readValue(geminiResponse.body(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> firstChoice = choices.get(0);
                Map<String, Object> geminiMessage = (Map<String, Object>) firstChoice.get("message");
                return (String) geminiMessage.get("content");
            }
        }

        throw new IOException("Gemini API error: " + geminiResponse.statusCode() + " - " + geminiResponse.body());
    }

    /**
     * Simplified method to handle file URI conversion
     */
    private static File getPdfFileFromUri(String uriString) throws URISyntaxException {
        // Handle different URI formats
        if (uriString.startsWith("file:///")) {
            // Remove file:/// prefix and handle Windows paths
            String path = uriString.substring(8);

            // On Windows, file URIs might have an extra slash before the drive letter
            if (path.matches("^/[a-zA-Z]:.*")) {
                path = path.substring(1);
            }

            // Replace forward slashes with system-appropriate separators
            path = path.replace('/', File.separatorChar);

            // Handle URL-encoded spaces and special characters
            try {
                path = java.net.URLDecoder.decode(path, "UTF-8");
            } catch (Exception e) {
                LOGGER.warning("Failed to decode path: " + e.getMessage());
            }

            return new File(path);
        } else if (uriString.startsWith("file://")) {
            // Handle file:// (two slashes)
            String path = uriString.substring(7);
            path = path.replace('/', File.separatorChar);
            try {
                path = java.net.URLDecoder.decode(path, "UTF-8");
            } catch (Exception e) {
                LOGGER.warning("Failed to decode path: " + e.getMessage());
            }
            return new File(path);
        } else {
            // Try to parse as standard URI
            URI uri = new URI(uriString);
            return new File(uri);
        }
    }
}