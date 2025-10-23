package area.server.AREA_Back.service.Webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordGatewayService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DiscordWebhookService discordWebhookService;

    @Value("${DISCORD_BOT_TOKEN:#{null}}")
    private String discordBotToken;

    private WebSocket webSocket;
    private final AtomicInteger sequenceNumber = new AtomicInteger(0);
    private final AtomicLong heartbeatInterval = new AtomicLong(45000); // Default 45 seconds
    private boolean connected = false;

    // Discord Gateway opcodes
    private static final int OPCODE_DISPATCH = 0;
    private static final int OPCODE_HEARTBEAT = 1;
    private static final int OPCODE_IDENTIFY = 2;
    private static final int OPCODE_RECONNECT = 7;
    private static final int OPCODE_INVALID_SESSION = 9;
    private static final int OPCODE_HELLO = 10;
    private static final int OPCODE_HEARTBEAT_ACK = 11;

    // Discord Gateway intents
    private static final int INTENT_GUILDS = 1;
    private static final int INTENT_GUILD_MESSAGES = 512;
    private static final int INTENT_GUILD_MESSAGE_REACTIONS = 1024;
    private static final int INTENT_MESSAGE_CONTENT = 32768;

    // WebSocket close codes
    private static final int CLOSE_CODE_NORMAL = 1000;

    @PostConstruct
    public void init() {
        if (discordBotToken == null || discordBotToken.trim().isEmpty()) {
            log.warn("Discord bot token not configured, Gateway service disabled");
            return;
        }

        log.info("Discord Gateway service initialized");
        connectToGateway();
    }

    @Scheduled(fixedRate = 30000)
    public void maintainConnection() {
        if (!connected && discordBotToken != null) {
            log.info("Attempting to reconnect to Discord Gateway");
            connectToGateway();
        }
    }

    private void connectToGateway() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(discordBotToken);
            headers.set("Authorization", "Bot " + discordBotToken);

            ResponseEntity<String> response = restTemplate.exchange(
                "https://discord.com/api/v10/gateway",
                HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to get Discord Gateway URL: {}", response.getStatusCode());
                return;
            }

            JsonNode gatewayInfo = objectMapper.readTree(response.getBody());
            String gatewayUrl = gatewayInfo.get("url").asText() + "?v=10&encoding=json";

            log.info("Connecting to Discord Gateway: {}", gatewayUrl);

            HttpClient client = HttpClient.newHttpClient();
            WebSocket.Builder webSocketBuilder = client.newWebSocketBuilder();

            CompletableFuture<WebSocket> wsFuture = webSocketBuilder
                .buildAsync(URI.create(gatewayUrl), new DiscordWebSocketListener());

            wsFuture.get();
            log.info("Successfully connected to Discord Gateway");

        } catch (Exception e) {
            log.error("Error connecting to Discord Gateway: {}", e.getMessage());
            this.connected = false;
        }
    }

    private final class DiscordWebSocketListener implements WebSocket.Listener {
        private StringBuilder messageBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            log.info("Discord Gateway WebSocket opened");
            DiscordGatewayService.this.webSocket = ws;
            DiscordGatewayService.this.connected = true;
            WebSocket.Listener.super.onOpen(ws);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            messageBuffer.append(data);

            if (last) {
                String message = messageBuffer.toString();
                messageBuffer = new StringBuilder();

                try {
                    handleGatewayMessage(message);
                } catch (Exception e) {
                    log.error("Error handling Gateway message: {}", e.getMessage(), e);
                }
            }

            return WebSocket.Listener.super.onText(ws, data, last);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.error("Discord Gateway WebSocket error: {}", error.getMessage());
            connected = false;
            WebSocket.Listener.super.onError(ws, error);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.info("Discord Gateway WebSocket closed: {} - {}", statusCode, reason);
            connected = false;
            return WebSocket.Listener.super.onClose(ws, statusCode, reason);
        }
    }

    private void handleGatewayMessage(String message) throws Exception {
        JsonNode payload = objectMapper.readTree(message);

        Integer op = payload.get("op").asInt();
        Integer s;
        if (payload.has("s") && !payload.get("s").isNull()) {
            s = payload.get("s").asInt();
        } else {
            s = null;
        }

        if (s != null) {
            sequenceNumber.set(s);
        }

        switch (op) {
            case OPCODE_DISPATCH:
                handleDispatchEvent(payload);
                break;
            case OPCODE_HEARTBEAT:
                sendHeartbeat();
                break;
            case OPCODE_RECONNECT:
                log.info("Discord requested reconnect");
                connected = false;
                connectToGateway();
                break;
            case OPCODE_INVALID_SESSION:
                log.warn("Invalid Discord session, reconnecting");
                connected = false;
                connectToGateway();
                break;
            case OPCODE_HELLO:
                handleHello(payload);
                break;
            case OPCODE_HEARTBEAT_ACK:
                log.debug("Heartbeat acknowledged");
                break;
            default:
                log.debug("Unhandled Gateway opcode: {}", op);
        }
    }

    private void handleHello(JsonNode payload) {
        heartbeatInterval.set(payload.get("d").get("heartbeat_interval").asLong());

        int intents = INTENT_GUILDS | INTENT_GUILD_MESSAGES | INTENT_GUILD_MESSAGE_REACTIONS | INTENT_MESSAGE_CONTENT;

        Map<String, Object> identifyPayload = new HashMap<>();
        identifyPayload.put("op", 2);
        identifyPayload.put("d", Map.of(
            "token", discordBotToken,
            "intents", intents,
            "properties", Map.of(
                "os", "linux",
                "browser", "area-bot",
                "device", "area-bot"
            )
        ));

        try {
            if (webSocket == null) {
                log.error("Cannot send identify - WebSocket is null");
                return;
            }
            String identifyJson = objectMapper.writeValueAsString(identifyPayload);
            webSocket.sendText(identifyJson, true);
            log.info("Sent Discord Gateway identify payload with intents: {} "
                + "(GUILDS + GUILD_MESSAGES + GUILD_MESSAGE_REACTIONS + MESSAGE_CONTENT)", intents);
        } catch (Exception e) {
            log.error("Failed to send identify payload: {}", e.getMessage());
        }

        startHeartbeat();
    }

    private void handleDispatchEvent(JsonNode payload) {
        String eventType = payload.get("t").asText();
        JsonNode eventData = payload.get("d");

        log.info("Received Discord Gateway event: {} - Data: {}", eventType, eventData);

        try {
            Map<String, Object> webhookPayload = new HashMap<>();
            webhookPayload.put("t", eventType);
            webhookPayload.put("d", objectMapper.convertValue(eventData, Map.class));
            webhookPayload.put("op", 0);

            discordWebhookService.processWebhook(webhookPayload, null, null);

        } catch (Exception e) {
            log.error("Failed to process Discord Gateway event {}: {}", eventType, e.getMessage());
        }
    }

    private void sendHeartbeat() {
        try {
            if (webSocket == null || !connected) {
                log.warn("Cannot send heartbeat - WebSocket not connected");
                return;
            }
            Map<String, Object> heartbeat = Map.of(
                "op", 1,
                "d", sequenceNumber.get()
            );
            String heartbeatJson = objectMapper.writeValueAsString(heartbeat);
            webSocket.sendText(heartbeatJson, true);
            log.debug("Sent Discord Gateway heartbeat");
        } catch (Exception e) {
            log.error("Failed to send heartbeat: {}", e.getMessage());
        }
    }

    private void startHeartbeat() {
        Thread heartbeatThread = new Thread(() -> {
            while (connected) {
                try {
                    Thread.sleep(heartbeatInterval.get());
                    sendHeartbeat();
                } catch (InterruptedException e) {
                    log.info("Heartbeat thread interrupted");
                    break;
                } catch (Exception e) {
                    log.error("Heartbeat error: {}", e.getMessage());
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        log.info("Started Discord Gateway heartbeat thread");
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(CLOSE_CODE_NORMAL, "Shutting down");
            connected = false;
        }
    }
}