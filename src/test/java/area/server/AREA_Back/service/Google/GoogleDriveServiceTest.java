package area.server.AREA_Back.service.Google;

import area.server.AREA_Back.service.Area.Services.Google.GoogleApiUtils;
import area.server.AREA_Back.service.Area.Services.Google.GoogleDriveService;
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
 * Unit tests for GoogleDriveService
 * Tests Drive operations: folder creation, file upload, sharing, and monitoring
 */
@ExtendWith(MockitoExtension.class)
class GoogleDriveServiceTest {

    @Mock
    private GoogleApiUtils googleApiUtils;

    @Mock
    private RestTemplate restTemplate;

    private GoogleDriveService googleDriveService;

    @BeforeEach
    void setUp() {
        googleDriveService = new GoogleDriveService(googleApiUtils);
        
        // Inject mocked RestTemplate using reflection
        try {
            var field = GoogleDriveService.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(googleDriveService, restTemplate);
        } catch (Exception e) {
            fail("Failed to inject RestTemplate: " + e.getMessage());
        }
    }

    @Test
    void testCreateDriveFolderSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Test Folder");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "folder-123");
        responseBody.put("name", "Test Folder");
        responseBody.put("webViewLink", "https://drive.google.com/folder-123");
        responseBody.put("createdTime", "2025-01-01T10:00:00Z");

        when(googleApiUtils.getRequiredParam(params, "name", String.class)).thenReturn("Test Folder");
        when(googleApiUtils.getOptionalParam(params, "parent_folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleDriveService.createDriveFolder(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("folder-123", result.get("folder_id"));
        assertEquals("Test Folder", result.get("name"));
        assertEquals("https://drive.google.com/folder-123", result.get("web_view_link"));
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testCreateDriveFolderWithParent() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Sub Folder");
        params.put("parent_folder_id", "parent-123");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "folder-456");
        responseBody.put("name", "Sub Folder");
        responseBody.put("webViewLink", "https://drive.google.com/folder-456");
        responseBody.put("createdTime", "2025-01-01T11:00:00Z");

        when(googleApiUtils.getRequiredParam(params, "name", String.class)).thenReturn("Sub Folder");
        when(googleApiUtils.getOptionalParam(params, "parent_folder_id", String.class, null)).thenReturn("parent-123");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleDriveService.createDriveFolder(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("folder-456", result.get("folder_id"));
    }

    @Test
    void testCreateDriveFolderFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Test Folder");

        when(googleApiUtils.getRequiredParam(params, "name", String.class)).thenReturn("Test Folder");
        when(googleApiUtils.getOptionalParam(params, "parent_folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.BAD_REQUEST));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleDriveService.createDriveFolder(token, input, params);
        });
    }

    @Test
    void testUploadDriveFileSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("name", "document.txt");
        params.put("content", "Hello World");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "file-123");
        responseBody.put("name", "document.txt");
        responseBody.put("webViewLink", "https://drive.google.com/file-123");
        responseBody.put("webContentLink", "https://drive.google.com/download/file-123");
        responseBody.put("createdTime", "2025-01-01T10:00:00Z");
        responseBody.put("size", "11");

        when(googleApiUtils.getRequiredParam(params, "name", String.class)).thenReturn("document.txt");
        when(googleApiUtils.getRequiredParam(params, "content", String.class)).thenReturn("Hello World");
        when(googleApiUtils.getOptionalParam(params, "mime_type", String.class, "text/plain")).thenReturn("text/plain");
        when(googleApiUtils.getOptionalParam(params, "parent_folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.mapToJson(any())).thenReturn("{\"name\":\"document.txt\",\"mimeType\":\"text/plain\"}");
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleDriveService.uploadDriveFile(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("file-123", result.get("file_id"));
        assertEquals("document.txt", result.get("name"));
        assertEquals("11", result.get("size"));
    }

    @Test
    void testUploadDriveFileWithParent() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("name", "report.pdf");
        params.put("content", "PDF content");
        params.put("mime_type", "application/pdf");
        params.put("parent_folder_id", "folder-123");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "file-456");
        responseBody.put("name", "report.pdf");
        responseBody.put("webViewLink", "https://drive.google.com/file-456");
        responseBody.put("webContentLink", "https://drive.google.com/download/file-456");
        responseBody.put("createdTime", "2025-01-01T11:00:00Z");
        responseBody.put("size", "1024");

        when(googleApiUtils.getRequiredParam(params, "name", String.class)).thenReturn("report.pdf");
        when(googleApiUtils.getRequiredParam(params, "content", String.class)).thenReturn("PDF content");
        when(googleApiUtils.getOptionalParam(params, "mime_type", String.class, "text/plain")).thenReturn("application/pdf");
        when(googleApiUtils.getOptionalParam(params, "parent_folder_id", String.class, null)).thenReturn("folder-123");
        when(googleApiUtils.mapToJson(any())).thenReturn("{\"name\":\"report.pdf\",\"mimeType\":\"application/pdf\",\"parents\":[\"folder-123\"]}");
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleDriveService.uploadDriveFile(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("file-456", result.get("file_id"));
    }

    @Test
    void testUploadDriveFileFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("name", "document.txt");
        params.put("content", "Hello World");

        when(googleApiUtils.getRequiredParam(params, "name", String.class)).thenReturn("document.txt");
        when(googleApiUtils.getRequiredParam(params, "content", String.class)).thenReturn("Hello World");
        when(googleApiUtils.getOptionalParam(params, "mime_type", String.class, "text/plain")).thenReturn("text/plain");
        when(googleApiUtils.getOptionalParam(params, "parent_folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.mapToJson(any())).thenReturn("{\"name\":\"document.txt\"}");
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.FORBIDDEN));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleDriveService.uploadDriveFile(token, input, params);
        });
    }

    @Test
    void testShareDriveFileSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("file_id", "file-123");
        params.put("email", "user@example.com");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "permission-123");

        when(googleApiUtils.getRequiredParam(params, "file_id", String.class)).thenReturn("file-123");
        when(googleApiUtils.getRequiredParam(params, "email", String.class)).thenReturn("user@example.com");
        when(googleApiUtils.getOptionalParam(params, "role", String.class, "reader")).thenReturn("reader");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleDriveService.shareDriveFile(token, input, params);

        // Then
        assertNotNull(result);
        assertEquals("permission-123", result.get("permission_id"));
        assertEquals("user@example.com", result.get("email"));
        assertEquals("reader", result.get("role"));
    }

    @Test
    void testShareDriveFileWithWriterRole() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("file_id", "file-456");
        params.put("email", "editor@example.com");
        params.put("role", "writer");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", "permission-456");

        when(googleApiUtils.getRequiredParam(params, "file_id", String.class)).thenReturn("file-456");
        when(googleApiUtils.getRequiredParam(params, "email", String.class)).thenReturn("editor@example.com");
        when(googleApiUtils.getOptionalParam(params, "role", String.class, "reader")).thenReturn("writer");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        Map<String, Object> result = googleDriveService.shareDriveFile(token, input, params);

        // Then
        assertEquals("writer", result.get("role"));
    }

    @Test
    void testShareDriveFileFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("file_id", "file-123");
        params.put("email", "user@example.com");

        when(googleApiUtils.getRequiredParam(params, "file_id", String.class)).thenReturn("file-123");
        when(googleApiUtils.getRequiredParam(params, "email", String.class)).thenReturn("user@example.com");
        when(googleApiUtils.getOptionalParam(params, "role", String.class, "reader")).thenReturn("reader");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.FORBIDDEN));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            googleDriveService.shareDriveFile(token, input, params);
        });
    }

    @Test
    void testCheckNewDriveFilesSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> file1 = new HashMap<>();
        file1.put("id", "file-1");
        file1.put("name", "document1.txt");
        file1.put("mimeType", "text/plain");
        file1.put("webViewLink", "https://drive.google.com/file-1");
        file1.put("createdTime", "2025-01-01T10:00:00Z");
        file1.put("modifiedTime", "2025-01-01T10:30:00Z");
        file1.put("size", "1024");

        Map<String, Object> file2 = new HashMap<>();
        file2.put("id", "file-2");
        file2.put("name", "document2.txt");
        file2.put("mimeType", "text/plain");
        file2.put("webViewLink", "https://drive.google.com/file-2");
        file2.put("createdTime", "2025-01-01T10:15:00Z");
        file2.put("modifiedTime", "2025-01-01T10:45:00Z");
        file2.put("size", "2048");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("files", Arrays.asList(file1, file2));

        when(googleApiUtils.getOptionalParam(params, "folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "file_type", String.class, "any")).thenReturn("any");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleDriveService.checkNewDriveFiles(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("file-1", result.get(0).get("file_id"));
        assertEquals("document1.txt", result.get(0).get("name"));
    }

    @Test
    void testCheckNewDriveFilesWithFolder() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("folder_id", "folder-123");
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("files", Collections.emptyList());

        when(googleApiUtils.getOptionalParam(params, "folder_id", String.class, null)).thenReturn("folder-123");
        when(googleApiUtils.getOptionalParam(params, "file_type", String.class, "any")).thenReturn("any");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleDriveService.checkNewDriveFiles(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewDriveFilesWithFileType() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("file_type", "document");
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("files", Collections.emptyList());

        when(googleApiUtils.getOptionalParam(params, "folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "file_type", String.class, "any")).thenReturn("document");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleDriveService.checkNewDriveFiles(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewDriveFilesFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        when(googleApiUtils.getOptionalParam(params, "folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "file_type", String.class, "any")).thenReturn("any");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.UNAUTHORIZED));

        // When
        List<Map<String, Object>> result = googleDriveService.checkNewDriveFiles(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckNewDriveFilesNullFiles() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.now().minusMinutes(30);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("files", null);

        when(googleApiUtils.getOptionalParam(params, "folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "file_type", String.class, "any")).thenReturn("any");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleDriveService.checkNewDriveFiles(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckModifiedDriveFilesSuccess() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.of(2025, 1, 1, 9, 0, 0);

        Map<String, Object> file1 = new HashMap<>();
        file1.put("id", "file-1");
        file1.put("name", "modified-doc.txt");
        file1.put("mimeType", "text/plain");
        file1.put("webViewLink", "https://drive.google.com/file-1");
        file1.put("createdTime", "2025-01-01T09:00:00Z");
        file1.put("modifiedTime", "2025-01-01T10:00:00Z");
        file1.put("size", "512");
        file1.put("version", "1");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("files", Collections.singletonList(file1));

        when(googleApiUtils.getOptionalParam(params, "folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "file_type", String.class, "any")).thenReturn("any");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleDriveService.checkModifiedDriveFiles(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("file-1", result.get(0).get("file_id"));
        assertEquals("modified-doc.txt", result.get(0).get("name"));
    }

    @Test
    void testCheckModifiedDriveFilesWithFolder() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        params.put("folder_id", "folder-456");
        LocalDateTime lastCheck = LocalDateTime.of(2025, 1, 1, 9, 0, 0);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("files", Collections.emptyList());

        when(googleApiUtils.getOptionalParam(params, "folder_id", String.class, null)).thenReturn("folder-456");
        when(googleApiUtils.getOptionalParam(params, "file_type", String.class, "any")).thenReturn("any");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleDriveService.checkModifiedDriveFiles(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckModifiedDriveFilesFailure() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.of(2025, 1, 1, 9, 0, 0);

        when(googleApiUtils.getOptionalParam(params, "folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "file_type", String.class, "any")).thenReturn("any");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        // When
        List<Map<String, Object>> result = googleDriveService.checkModifiedDriveFiles(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckModifiedDriveFilesNullFiles() {
        // Given
        String token = "valid-token";
        Map<String, Object> params = new HashMap<>();
        LocalDateTime lastCheck = LocalDateTime.of(2025, 1, 1, 9, 0, 0);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("files", null);

        when(googleApiUtils.getOptionalParam(params, "folder_id", String.class, null)).thenReturn(null);
        when(googleApiUtils.getOptionalParam(params, "file_type", String.class, "any")).thenReturn("any");
        when(googleApiUtils.createGoogleHeaders(token)).thenReturn(new org.springframework.http.HttpHeaders());
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // When
        List<Map<String, Object>> result = googleDriveService.checkModifiedDriveFiles(token, params, lastCheck);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
