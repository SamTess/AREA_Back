package area.server.AREA_Back.converter;

import area.server.AREA_Back.entity.enums.DedupStrategy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DedupStrategyConverter implements AttributeConverter<DedupStrategy, String> {

    @Override
    public String convertToDatabaseColumn(final DedupStrategy dedupStrategy) {
        if (dedupStrategy == null) {
            return null;
        }
        return dedupStrategy.toString(); // This will use our custom toString() method
    }

    @Override
    public DedupStrategy convertToEntityAttribute(final String dbData) {
        if (dbData == null) {
            return null;
        }
        
        switch (dbData.toLowerCase()) {
            case "none":
                return DedupStrategy.NONE;
            case "by_payload_hash":
                return DedupStrategy.BY_PAYLOAD_HASH;
            case "by_external_id":
                return DedupStrategy.BY_EXTERNAL_ID;
            default:
                throw new IllegalArgumentException("Unknown dedup strategy: " + dbData);
        }
    }
}