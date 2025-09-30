package area.server.AREA_Back.service;

public abstract class OAuthService {
    protected final String providerKey;
    protected final String clientId;
    protected final String clientSecret;

    protected OAuthService(String providerKey, String clientId, String clientSecret) {
        if (clientId == null || clientId.isEmpty() ||
            clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalStateException(providerKey + " OAuth2 client ID and secret must be set in environment variables.");
        }
        this.providerKey = providerKey;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getProviderKey() { return providerKey; }
    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }

    public abstract String exchangeToken(String authorizationCode);
}
