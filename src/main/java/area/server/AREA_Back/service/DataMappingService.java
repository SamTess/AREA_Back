package area.server.AREA_Back.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DataMappingService {

    @SuppressWarnings("unchecked")
    public Map<String, Object> applyMapping(Map<String, Object> input, Map<String, Object> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return input;
        }

        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, Object> mappingEntry : mapping.entrySet()) {
            String targetField = mappingEntry.getKey();
            Object sourcePathObj = mappingEntry.getValue();

            if (sourcePathObj instanceof String) {
                String sourcePath = (String) sourcePathObj;
                Object value = extractValueByPath(input, sourcePath);
                result.put(targetField, value);
            } else if (sourcePathObj instanceof Map) {
                Map<String, Object> transform = (Map<String, Object>) sourcePathObj;
                Object value = applyTransformation(input, transform);
                result.put(targetField, value);
            } else {
                result.put(targetField, sourcePathObj);
            }
        }

        return result;
    }

    public boolean evaluateCondition(Map<String, Object> data, Map<String, Object> condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        String operator = (String) condition.get("operator");
        if (operator == null) {
            operator = "and";
        }

        switch (operator.toLowerCase()) {
            case "and":
                return evaluateAndCondition(data, condition);
            case "or":
                return evaluateOrCondition(data, condition);
            case "not":
                return evaluateNotCondition(data, condition);
            default:
                return evaluateSimpleCondition(data, condition);
        }
    }

    private Object extractValueByPath(Map<String, Object> data, String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        if (path.startsWith("{{") && path.endsWith("}}")) {
            path = path.substring(2, path.length() - 2);
        }

        String[] parts = path.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
            } else {
                log.warn("Cannot navigate path '{}' - object is not a Map at part '{}'", path, part);
                return null;
            }
        }

        return current;
    }

    private Object applyTransformation(Map<String, Object> input, Map<String, Object> transform) {
        String type = (String) transform.get("type");
        Object sourceValue = extractValueByPath(input, (String) transform.get("source"));

        if (sourceValue == null) {
            return transform.get("default");
        }

        String transformType;
        if (type != null) {
            transformType = type.toLowerCase();
        } else {
            transformType = "direct";
        }

        switch (transformType) {
            case "string":
                return String.valueOf(sourceValue);
            case "number":
                return convertToNumber(sourceValue);
            case "boolean":
                return convertToBoolean(sourceValue);
            case "template":
                return applyTemplate((String) transform.get("template"), input);
            case "format":
                return applyFormat(sourceValue, (String) transform.get("format"));
            default:
                return sourceValue;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateAndCondition(Map<String, Object> data, Map<String, Object> condition) {
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) condition.get("conditions");
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        for (Map<String, Object> subCondition : conditions) {
            if (!evaluateCondition(data, subCondition)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateOrCondition(Map<String, Object> data, Map<String, Object> condition) {
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) condition.get("conditions");
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }

        for (Map<String, Object> subCondition : conditions) {
            if (evaluateCondition(data, subCondition)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateNotCondition(Map<String, Object> data, Map<String, Object> condition) {
        Map<String, Object> subCondition = (Map<String, Object>) condition.get("condition");
        return !evaluateCondition(data, subCondition);
    }

    private boolean evaluateSimpleCondition(Map<String, Object> data, Map<String, Object> condition) {
        String field = (String) condition.get("field");
        String operator = (String) condition.get("operator");
        Object expectedValue = condition.get("value");

        Object actualValue = extractValueByPath(data, field);

        if (operator == null) {
            operator = "equals";
        }

        switch (operator.toLowerCase()) {
            case "equals":
                return objectsEqual(actualValue, expectedValue);
            case "not_equals":
                return !objectsEqual(actualValue, expectedValue);
            case "contains":
                return stringContains(actualValue, expectedValue);
            case "not_contains":
                return !stringContains(actualValue, expectedValue);
            case "starts_with":
                return stringStartsWith(actualValue, expectedValue);
            case "ends_with":
                return stringEndsWith(actualValue, expectedValue);
            case "regex":
                return stringMatchesRegex(actualValue, expectedValue);
            case "greater_than":
                return compareNumbers(actualValue, expectedValue) > 0;
            case "less_than":
                return compareNumbers(actualValue, expectedValue) < 0;
            case "greater_equal":
                return compareNumbers(actualValue, expectedValue) >= 0;
            case "less_equal":
                return compareNumbers(actualValue, expectedValue) <= 0;
            case "exists":
                return actualValue != null;
            case "not_exists":
                return actualValue == null;
            default:
                log.warn("Unknown condition operator: {}", operator);
                return false;
        }
    }


    private boolean objectsEqual(Object actual, Object expected) {
        if (actual == null && expected == null) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        return actual.toString().equals(expected.toString());
    }

    private boolean stringContains(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return actual.toString().contains(expected.toString());
    }

    private boolean stringStartsWith(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return actual.toString().startsWith(expected.toString());
    }

    private boolean stringEndsWith(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return actual.toString().endsWith(expected.toString());
    }

    private boolean stringMatchesRegex(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }
        try {
            Pattern pattern = Pattern.compile(expected.toString());
            return pattern.matcher(actual.toString()).matches();
        } catch (Exception e) {
            log.warn("Invalid regex pattern: {}", expected, e);
            return false;
        }
    }

    private int compareNumbers(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return 0;
        }
        try {
            double actualNum = convertToNumber(actual).doubleValue();
            double expectedNum = convertToNumber(expected).doubleValue();
            return Double.compare(actualNum, expectedNum);
        } catch (Exception e) {
            return actual.toString().compareTo(expected.toString());
        }
    }

    private Number convertToNumber(Object value) {
        if (value instanceof Number) {
            return (Number) value;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot convert to number: " + value);
        }
    }

    private Boolean convertToBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String str = ((String) value).toLowerCase();
            return "true".equals(str) || "yes".equals(str) || "1".equals(str);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        return false;
    }

    private String applyTemplate(String template, Map<String, Object> data) {
        if (template == null) {
            return null;
        }

        String result = template;
        Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(template);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = extractValueByPath(data, placeholder);
            String replacement;
            if (value != null) {
                replacement = value.toString();
            } else {
                replacement = "";
            }
            result = result.replace("{{" + placeholder + "}}", replacement);
        }

        return result;
    }

    private String applyFormat(Object value, String format) {
        if (value == null || format == null) {
            return String.valueOf(value);
        }

        try {
            switch (format.toLowerCase()) {
                case "uppercase":
                    return value.toString().toUpperCase();
                case "lowercase":
                    return value.toString().toLowerCase();
                case "trim":
                    return value.toString().trim();
                default:
                    return String.format(format, value);
            }
        } catch (Exception e) {
            log.warn("Failed to apply format '{}' to value '{}': {}", format, value, e.getMessage());
            return String.valueOf(value);
        }
    }
}