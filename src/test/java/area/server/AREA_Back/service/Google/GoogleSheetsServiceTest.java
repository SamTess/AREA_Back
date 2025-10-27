package area.server.AREA_Back.service.Google;

import area.server.AREA_Back.service.Area.Services.Google.GoogleApiUtils;
import area.server.AREA_Back.service.Area.Services.Google.GoogleSheetsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoogleSheetsService
 * Tests Sheets operations: creating spreadsheets, adding rows, updating cells, and checking for new rows
 */
@ExtendWith(MockitoExtension.class)
class GoogleSheetsServiceTest {

    @Mock
    private GoogleApiUtils googleApiUtils;

    @Mock
    private RestTemplate restTemplate;

    private GoogleSheetsService googleSheetsService;

    @BeforeEach
    void setUp() {
        googleSheetsService = new GoogleSheetsService(googleApiUtils);
        
        // Inject mocked RestTemplate
        try {
            var field = GoogleSheetsService.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(googleSheetsService, restTemplate);
        } catch (Exception e) {
            fail("Failed to inject RestTemplate: " + e.getMessage());
        }
    }

    @Test
    void testAddSheetRowSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("spreadsheet_id", "sheet-123");
        params.put("values", Arrays.asList("Value1", "Value2", "Value3"));

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("updates", "Sheet1!A2:C2");

        when(googleApiUtils.getRequiredParam(params, "spreadsheet_id", String.class)).thenReturn("sheet-123");
        when(googleApiUtils.getOptionalParam(params, "sheet_name", String.class, "Sheet1")).thenReturn("Sheet1");
        when(googleApiUtils.getRequiredParam(eq(params), eq("values"), eq(List.class))).thenReturn(Arrays.asList("Value1", "Value2", "Value3"));
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleSheetsService.addSheetRow(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("sheet-123", result.get("spreadsheet_id"));
        assertEquals("Sheet1", result.get("sheet_name"));
        assertEquals(1, result.get("updated_rows"));
        assertNotNull(result.get("updated_at"));
    }

    @Test
    void testAddSheetRowFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("spreadsheet_id", "sheet-123");
        params.put("values", Arrays.asList("Value1", "Value2"));

        when(googleApiUtils.getRequiredParam(params, "spreadsheet_id", String.class)).thenReturn("sheet-123");
        when(googleApiUtils.getOptionalParam(params, "sheet_name", String.class, "Sheet1")).thenReturn("Sheet1");
        when(googleApiUtils.getRequiredParam(eq(params), eq("values"), eq(List.class))).thenReturn(Arrays.asList("Value1", "Value2"));
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.FORBIDDEN));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleSheetsService.addSheetRow(token, input, params);
        });
    }

    @Test
    void testUpdateSheetCellSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("spreadsheet_id", "sheet-456");
        params.put("cell", "B5");
        params.put("value", "Updated Value");

        Map<String, Object> responseBody = new HashMap<>();

        when(googleApiUtils.getRequiredParam(params, "spreadsheet_id", String.class)).thenReturn("sheet-456");
        when(googleApiUtils.getOptionalParam(params, "sheet_name", String.class, "Sheet1")).thenReturn("Sheet1");
        when(googleApiUtils.getRequiredParam(params, "cell", String.class)).thenReturn("B5");
        when(googleApiUtils.getRequiredParam(params, "value", String.class)).thenReturn("Updated Value");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleSheetsService.updateSheetCell(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("sheet-456", result.get("spreadsheet_id"));
        assertEquals("Sheet1!B5", result.get("updated_range"));
        assertEquals(1, result.get("updated_cells"));
        assertNotNull(result.get("updated_at"));
    }

    @Test
    void testUpdateSheetCellFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("spreadsheet_id", "sheet-456");
        params.put("cell", "B5");
        params.put("value", "New Value");

        when(googleApiUtils.getRequiredParam(params, "spreadsheet_id", String.class)).thenReturn("sheet-456");
        when(googleApiUtils.getOptionalParam(params, "sheet_name", String.class, "Sheet1")).thenReturn("Sheet1");
        when(googleApiUtils.getRequiredParam(params, "cell", String.class)).thenReturn("B5");
        when(googleApiUtils.getRequiredParam(params, "value", String.class)).thenReturn("New Value");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleSheetsService.updateSheetCell(token, input, params);
        });
    }

    @Test
    void testCreateSpreadsheetSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("title", "My Spreadsheet");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("spreadsheetId", "new-sheet-789");
        responseBody.put("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/new-sheet-789");

        when(googleApiUtils.getRequiredParam(params, "title", String.class)).thenReturn("My Spreadsheet");
        when(googleApiUtils.getOptionalParam(eq(params), eq("sheet_names"), eq(List.class), anyList()))
            .thenReturn(List.of("Sheet1"));
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleSheetsService.createSpreadsheet(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("new-sheet-789", result.get("spreadsheet_id"));
        assertEquals("https://docs.google.com/spreadsheets/d/new-sheet-789", result.get("spreadsheet_url"));
        assertNotNull(result.get("created_at"));
    }

    @Test
    void testCreateSpreadsheetWithMultipleSheets() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("title", "Multi-Sheet");
        params.put("sheet_names", Arrays.asList("Data", "Analysis", "Summary"));

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("spreadsheetId", "multi-sheet-101");
        responseBody.put("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/multi-sheet-101");

        when(googleApiUtils.getRequiredParam(params, "title", String.class)).thenReturn("Multi-Sheet");
        when(googleApiUtils.getOptionalParam(eq(params), eq("sheet_names"), eq(List.class), anyList()))
            .thenReturn(Arrays.asList("Data", "Analysis", "Summary"));
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleSheetsService.createSpreadsheet(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("multi-sheet-101", result.get("spreadsheet_id"));
    }

    @Test
    void testCreateSpreadsheetFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("title", "Failed Sheet");

        when(googleApiUtils.getRequiredParam(params, "title", String.class)).thenReturn("Failed Sheet");
        when(googleApiUtils.getOptionalParam(eq(params), eq("sheet_names"), eq(List.class), anyList()))
            .thenReturn(List.of("Sheet1"));
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.UNAUTHORIZED));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleSheetsService.createSpreadsheet(token, input, params);
        });
    }

    @Test
    void testCheckNewSheetRowsSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("spreadsheet_id", "sheet-123");
        params.put("last_row_count", 5);
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("values", Arrays.asList(
            Arrays.asList("Row1Col1", "Row1Col2"),
            Arrays.asList("Row2Col1", "Row2Col2"),
            Arrays.asList("Row3Col1", "Row3Col2"),
            Arrays.asList("Row4Col1", "Row4Col2"),
            Arrays.asList("Row5Col1", "Row5Col2"),
            Arrays.asList("Row6Col1", "Row6Col2"),
            Arrays.asList("Row7Col1", "Row7Col2")
        ));

        when(googleApiUtils.getRequiredParam(params, "spreadsheet_id", String.class)).thenReturn("sheet-123");
        when(googleApiUtils.getOptionalParam(params, "sheet_name", String.class, "Sheet1")).thenReturn("Sheet1");
        when(googleApiUtils.getOptionalParam(params, "last_row_count", Integer.class, 0)).thenReturn(5);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleSheetsService.checkNewSheetRows(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("sheet-123", result.get(0).get("spreadsheet_id"));
        assertEquals(6, result.get(0).get("row_number"));
        assertEquals(7, result.get(1).get("row_number"));
    }

    @Test
    void testCheckNewSheetRowsNoNewRows() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("spreadsheet_id", "sheet-456");
        params.put("last_row_count", 10);
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("values", Arrays.asList(
            Arrays.asList("Row1", "Data1"),
            Arrays.asList("Row2", "Data2"),
            Arrays.asList("Row3", "Data3")
        ));

        when(googleApiUtils.getRequiredParam(params, "spreadsheet_id", String.class)).thenReturn("sheet-456");
        when(googleApiUtils.getOptionalParam(params, "sheet_name", String.class, "Sheet1")).thenReturn("Sheet1");
        when(googleApiUtils.getOptionalParam(params, "last_row_count", Integer.class, 0)).thenReturn(10);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleSheetsService.checkNewSheetRows(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewSheetRowsFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("spreadsheet_id", "sheet-789");
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        when(googleApiUtils.getRequiredParam(params, "spreadsheet_id", String.class)).thenReturn("sheet-789");
        when(googleApiUtils.getOptionalParam(params, "sheet_name", String.class, "Sheet1")).thenReturn("Sheet1");
        when(googleApiUtils.getOptionalParam(params, "last_row_count", Integer.class, 0)).thenReturn(0);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.NOT_FOUND));

        // When
        List<Map<String, Object>> result = googleSheetsService.checkNewSheetRows(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewSheetRowsNullValues() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("spreadsheet_id", "sheet-null");
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("values", null);

        when(googleApiUtils.getRequiredParam(params, "spreadsheet_id", String.class)).thenReturn("sheet-null");
        when(googleApiUtils.getOptionalParam(params, "sheet_name", String.class, "Sheet1")).thenReturn("Sheet1");
        when(googleApiUtils.getOptionalParam(params, "last_row_count", Integer.class, 0)).thenReturn(0);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleSheetsService.checkNewSheetRows(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewSheetRowsEmptyValues() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("spreadsheet_id", "sheet-empty");
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("values", Collections.emptyList());

        when(googleApiUtils.getRequiredParam(params, "spreadsheet_id", String.class)).thenReturn("sheet-empty");
        when(googleApiUtils.getOptionalParam(params, "sheet_name", String.class, "Sheet1")).thenReturn("Sheet1");
        when(googleApiUtils.getOptionalParam(params, "last_row_count", Integer.class, 0)).thenReturn(0);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleSheetsService.checkNewSheetRows(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewSheetRowsMaxLimit() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("spreadsheet_id", "sheet-large");
        params.put("last_row_count", 0);
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        List<List<Object>> largeList = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            largeList.add(Arrays.asList("Row" + i, "Data" + i));
        }

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("values", largeList);

        when(googleApiUtils.getRequiredParam(params, "spreadsheet_id", String.class)).thenReturn("sheet-large");
        when(googleApiUtils.getOptionalParam(params, "sheet_name", String.class, "Sheet1")).thenReturn("Sheet1");
        when(googleApiUtils.getOptionalParam(params, "last_row_count", Integer.class, 0)).thenReturn(0);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleSheetsService.checkNewSheetRows(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(20, result.size()); // Max limit is 20
    }
}
