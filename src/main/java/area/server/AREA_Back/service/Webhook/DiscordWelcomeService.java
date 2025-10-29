package area.server.AREA_Back.service.Webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordWelcomeService {

    private final RestTemplate restTemplate;

    @Value("${DISCORD_BOT_TOKEN:#{null}}")
    private String discordBotToken;

    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";

    /**
     * Sends welcome messages to guilds the bot was just added to via OAuth
     */
    public void sendWelcomeMessagesToNewGuilds(String userAccessToken) {
        if (discordBotToken == null || discordBotToken.trim().isEmpty()) {
            log.warn("Discord bot token not configured, cannot send welcome messages");
            return;
        }

        try {
            List<Map<String, Object>> userGuilds = fetchUserGuilds(userAccessToken);

            log.info("Found {} guilds for user", userGuilds.size());

            for (Map<String, Object> guild : userGuilds) {
                String guildId = (String) guild.get("id");
                String guildName = (String) guild.get("name");

                Object permissionsObj = guild.get("permissions");
                if (permissionsObj != null) {
                    try {
                        sendWelcomeMessageToGuild(guildId, guildName);
                    } catch (Exception e) {
                        log.debug("Could not send welcome message to guild {} ({}): {}",
                            guildName, guildId, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error sending welcome messages to Discord guilds: {}", e.getMessage(), e);
        }
    }

    /**
     * Fetch guilds the user has access to using their OAuth token
     */
    private List<Map<String, Object>> fetchUserGuilds(String userAccessToken) {
        String guildsUrl = DISCORD_API_BASE + "/users/@me/guilds";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + userAccessToken);
        headers.set("Content-Type", "application/json");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
            guildsUrl,
            HttpMethod.GET,
            request,
            new ParameterizedTypeReference<List<Map<String, Object>>>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to fetch user's Discord guilds");
        }

        return response.getBody();
    }

    /**
     * Send welcome message to a specific guild
     */
    private void sendWelcomeMessageToGuild(String guildId, String guildName) {
        String channelsUrl = String.format("%s/guilds/%s/channels", DISCORD_API_BASE, guildId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bot " + discordBotToken);
        headers.set("Content-Type", "application/json");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<List<Map<String, Object>>> channelsResponse = restTemplate.exchange(
            channelsUrl,
            HttpMethod.GET,
            request,
            new ParameterizedTypeReference<List<Map<String, Object>>>() { }
        );

        if (!channelsResponse.getStatusCode().is2xxSuccessful() || channelsResponse.getBody() == null) {
            log.warn("Failed to fetch channels for guild {}", guildId);
            return;
        }

        String targetChannelId = null;
        for (Map<String, Object> channel : channelsResponse.getBody()) {
            Integer channelType = (Integer) channel.get("type");
            if (channelType != null && channelType == 0) {
                targetChannelId = (String) channel.get("id");
                break;
            }
        }

        if (targetChannelId == null) {
            log.warn("No text channel found in guild {} to send welcome message", guildId);
            return;
        }

        sendMessage(targetChannelId, guildId, guildName);
    }

    /**
     * Send a message to a Discord channel
     */
    private void sendMessage(String channelId, String guildId, String guildName) {
        String messageUrl = String.format("%s/channels/%s/messages", DISCORD_API_BASE, channelId);

        String welcomeMessage = "👋 **Welcome to AREA Bot!**\n\n"
            + "Thank you for adding me to **" + guildName + "**!\n\n"
            + "**Quick Setup Guide:**\n\n"
            + "During the OAuth process, you were asked to grant me permissions. "
            + "If you granted **Administrator** permissions, I'm all set! 🎉\n\n"
            + "If not, here's how to give me the permissions I need:\n\n"
            + "**Option 1: Grant Administrator (Recommended)**\n"
            + "1. Go to **Server Settings** → **Roles**\n"
            + "2. Find the **AREA** role (created automatically)\n"
            + "3. Enable **Administrator** permission\n\n"
            + "**Option 2: Grant Specific Permissions**\n"
            + "At minimum, I need:\n"
            + "• **Send Messages** - To communicate in channels\n"
            + "• **Manage Messages** - To react to messages\n"
            + "• **Read Message History** - To check for events\n"
            + "• **Add Reactions** - To add reactions to messages\n"
            + "• **Manage Channels** - To create channels (if needed)\n\n"
            + "Once configured, I'll be ready to automate your workflows! 🚀\n\n"
            + "_Need help? Check our documentation or contact support._";

        Map<String, Object> messageBody = new HashMap<>();
        messageBody.put("content", welcomeMessage);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bot " + discordBotToken);
        headers.set("Content-Type", "application/json");

        HttpEntity<Map<String, Object>> messageRequest = new HttpEntity<>(messageBody, headers);

        try {
            ResponseEntity<Map<String, Object>> messageResponse = restTemplate.exchange(
                messageUrl,
                HttpMethod.POST,
                messageRequest,
                new ParameterizedTypeReference<Map<String, Object>>() { }
            );

            if (messageResponse.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully sent welcome message to guild {} ({}) in channel {}",
                    guildName, guildId, channelId);
            } else {
                log.warn("Failed to send welcome message to guild {}: {}",
                    guildId, messageResponse.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("Error sending welcome message to guild {} ({}): {}",
                guildName, guildId, e.getMessage());
        }
    }
}
