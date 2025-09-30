package area.server.AREA_Back.converter;

import area.server.AREA_Back.entity.Service;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AuthTypeConverter implements AttributeConverter<Service.AuthType, Object> {

    @Override
    public Object convertToDatabaseColumn(final Service.AuthType authType) {
        if (authType == null) {
            return null;
        }
        return authType.toString();
    }

    @Override
    public Service.AuthType convertToEntityAttribute(final Object dbData) {
        if (dbData == null) {
            return null;
        }

        String value = dbData.toString();
        switch (value.toLowerCase()) {
            case "oauth2":
                return Service.AuthType.OAUTH2;
            case "apikey":
                return Service.AuthType.APIKEY;
            case "none":
                return Service.AuthType.NONE;
            default:
                throw new IllegalArgumentException("Unknown auth type: " + value);
        }
    }
}