package area.server.AREA_Back.service.Area.Services.Google;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling Google Sheets operations.
 * Provides methods for creating spreadsheets, adding rows, updating cells, and checking for new rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsService {

    private final GoogleApiUtils utils;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Add a row to a Google Sheet.
     *
     * @param token Google OAuth token
     * @param input Input payload (unused)
     * @param params Parameters including spreadsheet_id, sheet_name, values
     * @return Result map with spreadsheet_id, sheet_name, updated_range, updated_rows, updated_at
     */
    public Map<String, Object> addSheetRow(String token, Map<String, Object> input, Map<String, Object> params) {
        String spreadsheetId = utils.getRequiredParam(params, "spreadsheet_id", String.class);
        String sheetName = utils.getOptionalParam(params, "sheet_name", String.class, "Sheet1");

        @SuppressWarnings("unchecked")
        List<String> values = utils.getRequiredParam(params, "values", List.class);

        String range = sheetName + "!A:Z";
        String url = GoogleApiUtils.SHEETS_API + "/spreadsheets/" + spreadsheetId + "/values/" + range
            + ":append?valueInputOption=USER_ENTERED";

        Map<String, Object> requestBody = Map.of(
            "values", List.of(values)
        );

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to add row to Sheet: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("spreadsheet_id", spreadsheetId);
        result.put("sheet_name", sheetName);
        result.put("updated_range", responseBody.get("updates"));
        result.put("updated_rows", 1);
        result.put("updated_at", LocalDateTime.now().toString());

        return result;
    }

    /**
     * Update a specific cell in a Google Sheet.
     *
     * @param token Google OAuth token
     * @param input Input payload (unused)
     * @param params Parameters including spreadsheet_id, sheet_name, cell, value
     * @return Result map with spreadsheet_id, updated_range, updated_cells, updated_at
     */
    public Map<String, Object> updateSheetCell(String token, Map<String, Object> input, Map<String, Object> params) {
        String spreadsheetId = utils.getRequiredParam(params, "spreadsheet_id", String.class);
        String sheetName = utils.getOptionalParam(params, "sheet_name", String.class, "Sheet1");
        String cell = utils.getRequiredParam(params, "cell", String.class);
        String value = utils.getRequiredParam(params, "value", String.class);

        String range = sheetName + "!" + cell;
        String url = GoogleApiUtils.SHEETS_API + "/spreadsheets/" + spreadsheetId + "/values/" + range
            + "?valueInputOption=USER_ENTERED";

        Map<String, Object> requestBody = Map.of(
            "values", List.of(List.of(value))
        );

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.PUT, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to update Sheet cell: " + response.getStatusCode());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("spreadsheet_id", spreadsheetId);
        result.put("updated_range", range);
        result.put("updated_cells", 1);
        result.put("updated_at", LocalDateTime.now().toString());

        return result;
    }

    /**
     * Create a new Google Spreadsheet.
     *
     * @param token Google OAuth token
     * @param input Input payload (unused)
     * @param params Parameters including title, sheet_names
     * @return Result map with spreadsheet_id, spreadsheet_url, created_at
     */
    public Map<String, Object> createSpreadsheet(String token, Map<String, Object> input, Map<String, Object> params) {
        String title = utils.getRequiredParam(params, "title", String.class);

        @SuppressWarnings("unchecked")
        List<String> sheetNames = utils.getOptionalParam(params, "sheet_names", List.class, List.of("Sheet1"));

        String url = GoogleApiUtils.SHEETS_API + "/spreadsheets";

        List<Map<String, Object>> sheets = new ArrayList<>();
        for (String sheetName : sheetNames) {
            sheets.add(Map.of("properties", Map.of("title", sheetName)));
        }

        Map<String, Object> requestBody = Map.of(
            "properties", Map.of("title", title),
            "sheets", sheets
        );

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create Spreadsheet: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("spreadsheet_id", responseBody.get("spreadsheetId"));
        result.put("spreadsheet_url", responseBody.get("spreadsheetUrl"));
        result.put("created_at", LocalDateTime.now().toString());

        return result;
    }

    /**
     * Check for new rows added to a Google Sheet.
     *
     * @param token Google OAuth token
     * @param params Parameters including spreadsheet_id, sheet_name, last_row_count
     * @param lastCheck Timestamp of the last check (unused for sheets)
     * @return List of new rows
     */
    public List<Map<String, Object>> checkNewSheetRows(String token,
                                                         Map<String, Object> params,
                                                         LocalDateTime lastCheck) {
        String spreadsheetId = utils.getRequiredParam(params, "spreadsheet_id", String.class);
        String sheetName = utils.getOptionalParam(params, "sheet_name", String.class, "Sheet1");
        Integer lastKnownRowCount = utils.getOptionalParam(params, "last_row_count", Integer.class, 0);

        String range = sheetName + "!A:Z";
        String url = GoogleApiUtils.SHEETS_API + "/spreadsheets/" + spreadsheetId + "/values/" + range;

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch Sheet data: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<List<Object>> values = (List<List<Object>>) responseBody.get("values");

        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        int currentRowCount = values.size();

        if (currentRowCount <= lastKnownRowCount) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> newRows = new ArrayList<>();
        for (int i = lastKnownRowCount; i < currentRowCount; i++) {
            Map<String, Object> rowEvent = new HashMap<>();
            rowEvent.put("spreadsheet_id", spreadsheetId);
            rowEvent.put("sheet_name", sheetName);
            rowEvent.put("row_number", i + 1);
            rowEvent.put("row_data", values.get(i));
            rowEvent.put("detected_at", LocalDateTime.now().toString());
            rowEvent.put("current_row_count", currentRowCount);
            newRows.add(rowEvent);
        }

        final int maxNewRows = 20;
        if (newRows.size() > maxNewRows) {
            return newRows.stream().limit(maxNewRows).toList();
        }

        return newRows;
    }
}
