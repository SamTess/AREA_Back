package area.server.AREA_Back.dto;

import java.util.Objects;

import area.server.AREA_Back.service.OAuthService;

public final class OAuthProvider {
    private final String providerKey;
    private final String providerLabel;
    private final String providerLogoUrl;
    private final String userAuthUrl;
    private final String clientId;

    public OAuthProvider(
        String providerKey,
        String providerLabel,
        String providerLogoUrl,
        String userAuthUrl,
        String clientId) {

        this.providerKey = providerKey;
        this.providerLabel = providerLabel;
        this.providerLogoUrl = providerLogoUrl;
        this.userAuthUrl = userAuthUrl;
        this.clientId = clientId;
    }

    public String getProviderKey() { return providerKey; }
    public String getProviderLabel() { return providerLabel; }
    public String getProviderLogoUrl() { return providerLogoUrl; }
    public String getUserAuthUrl() { return userAuthUrl; }
    public String getClientId() { return clientId; }

    public static OAuthProvider fromService(OAuthService service) {
        if (service == null) return null;
        return new OAuthProvider(
            service.getProviderKey(),
            service.getProviderLabel(),
            service.getProviderLogoUrl(),
            service.getUserAuthUrl(),
            service.getClientId()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OAuthProvider that = (OAuthProvider) o;
        return Objects.equals(providerKey, that.providerKey) &&
            Objects.equals(providerLabel, that.providerLabel) &&
            Objects.equals(providerLogoUrl, that.providerLogoUrl) &&
            Objects.equals(userAuthUrl, that.userAuthUrl) &&
            Objects.equals(clientId, that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerKey, providerLabel, providerLogoUrl, userAuthUrl, clientId);
    }
}
