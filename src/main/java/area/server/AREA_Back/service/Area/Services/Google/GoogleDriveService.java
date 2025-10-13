package area.server.AREA_Back.service.Area.Services.Google;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling Google Drive operations.
 * Provides methods for creating folders, uploading/sharing files, and checking for new or modified files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveService {

    private final GoogleApiUtils utils;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Create a new folder in Google Drive.
     *
     * @param token Google OAuth token
     * @param input Input payload (unused)
     * @param params Parameters including name, parent_folder_id
     * @return Result map with folder_id, name, web_view_link, created_at
     */
    public Map<String, Object> createDriveFolder(String token, Map<String, Object> input, Map<String, Object> params) {
        String name = utils.getRequiredParam(params, "name", String.class);
        String parentFolderId = utils.getOptionalParam(params, "parent_folder_id", String.class, null);

        String url = GoogleApiUtils.DRIVE_API + "/files";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", name);
        requestBody.put("mimeType", "application/vnd.google-apps.folder");

        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            requestBody.put("parents", List.of(parentFolderId));
        }

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create Drive folder: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("folder_id", responseBody.get("id"));
        result.put("name", responseBody.get("name"));
        result.put("web_view_link", responseBody.get("webViewLink"));
        result.put("created_at", responseBody.get("createdTime"));

        return result;
    }

    /**
     * Upload a file to Google Drive with multipart upload.
     *
     * @param token Google OAuth token
     * @param input Input payload (unused)
     * @param params Parameters including name, content, mime_type, parent_folder_id
     * @return Result map with file_id, name, web_view_link, web_content_link, created_at, size
     */
    public Map<String, Object> uploadDriveFile(String token, Map<String, Object> input, Map<String, Object> params) {
        String name = utils.getRequiredParam(params, "name", String.class);
        String content = utils.getRequiredParam(params, "content", String.class);
        String mimeType = utils.getOptionalParam(params, "mime_type", String.class, "text/plain");
        String parentFolderId = utils.getOptionalParam(params, "parent_folder_id", String.class, null);

        String url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        metadata.put("mimeType", mimeType);

        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            metadata.put("parents", List.of(parentFolderId));
        }

        String boundary = "boundary_" + System.currentTimeMillis();
        StringBuilder multipartBody = new StringBuilder();

        multipartBody.append("--").append(boundary).append("\r\n");
        multipartBody.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");
        multipartBody.append(utils.mapToJson(metadata)).append("\r\n");

        multipartBody.append("--").append(boundary).append("\r\n");
        multipartBody.append("Content-Type: ").append(mimeType).append("\r\n\r\n");
        multipartBody.append(content).append("\r\n");
        multipartBody.append("--").append(boundary).append("--");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.parseMediaType("multipart/related; boundary=" + boundary));

        HttpEntity<String> request = new HttpEntity<>(multipartBody.toString(), headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to upload Drive file: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("file_id", responseBody.get("id"));
        result.put("name", responseBody.get("name"));
        result.put("web_view_link", responseBody.get("webViewLink"));
        result.put("web_content_link", responseBody.get("webContentLink"));
        result.put("created_at", responseBody.get("createdTime"));
        result.put("size", responseBody.get("size"));

        return result;
    }

    /**
     * Share a Drive file with a user.
     *
     * @param token Google OAuth token
     * @param input Input payload (unused)
     * @param params Parameters including file_id, email, role
     * @return Result map with permission_id, email, role, shared_at
     */
    public Map<String, Object> shareDriveFile(String token, Map<String, Object> input, Map<String, Object> params) {
        String fileId = utils.getRequiredParam(params, "file_id", String.class);
        String email = utils.getRequiredParam(params, "email", String.class);
        String role = utils.getOptionalParam(params, "role", String.class, "reader");

        String url = GoogleApiUtils.DRIVE_API + "/files/" + fileId + "/permissions";

        Map<String, Object> requestBody = Map.of(
            "type", "user",
            "role", role,
            "emailAddress", email
        );

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.POST, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to share Drive file: " + response.getStatusCode());
        }

        Map<String, Object> responseBody = response.getBody();
        Map<String, Object> result = new HashMap<>();
        result.put("permission_id", responseBody.get("id"));
        result.put("email", email);
        result.put("role", role);
        result.put("shared_at", LocalDateTime.now().toString());

        return result;
    }

    /**
     * Check for new files in Google Drive.
     *
     * @param token Google OAuth token
     * @param params Parameters including folder_id, file_type
     * @param lastCheck Timestamp of the last check
     * @return List of new files
     */
    public List<Map<String, Object>> checkNewDriveFiles(String token,
                                                          Map<String, Object> params,
                                                          LocalDateTime lastCheck) {
        String folderId = utils.getOptionalParam(params, "folder_id", String.class, null);
        String fileType = utils.getOptionalParam(params, "file_type", String.class, "any");

        StringBuilder query = new StringBuilder("trashed=false");

        if (folderId != null && !folderId.isEmpty()) {
            query.append(" and '").append(folderId).append("' in parents");
        }

        if (!"any".equals(fileType)) {
            String mimeType = getMimeTypeForFileType(fileType);
            if (mimeType != null) {
                if (mimeType.endsWith("/")) {
                    query.append(" and mimeType contains '").append(mimeType).append("'");
                } else {
                    query.append(" and mimeType='").append(mimeType).append("'");
                }
            }
        }

        String url = GoogleApiUtils.DRIVE_API + "/files?q=" + query + "&orderBy=createdTime desc&pageSize=10";

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch Drive files: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) responseBody.get("files");

        if (files == null) {
            return Collections.emptyList();
        }

        return files.stream()
            .map(this::parseDriveFile)
            .toList();
    }

    /**
     * Check for modified files in Google Drive.
     *
     * @param token Google OAuth token
     * @param params Parameters including folder_id, file_type
     * @param lastCheck Timestamp of the last check
     * @return List of modified files
     */
    public List<Map<String, Object>> checkModifiedDriveFiles(String token,
                                                               Map<String, Object> params,
                                                               LocalDateTime lastCheck) {
        String folderId = utils.getOptionalParam(params, "folder_id", String.class, null);
        String fileType = utils.getOptionalParam(params, "file_type", String.class, "any");

        StringBuilder query = new StringBuilder("trashed=false");

        String modifiedTime = lastCheck.format(DateTimeFormatter.ISO_INSTANT);
        query.append(" and modifiedTime > '").append(modifiedTime).append("'");

        if (folderId != null && !folderId.isEmpty()) {
            query.append(" and '").append(folderId).append("' in parents");
        }

        if (!"any".equals(fileType)) {
            String mimeType = getMimeTypeForFileType(fileType);
            if (mimeType != null) {
                if (mimeType.endsWith("/")) {
                    query.append(" and mimeType contains '").append(mimeType).append("'");
                } else {
                    query.append(" and mimeType='").append(mimeType).append("'");
                }
            }
        }

        String url = GoogleApiUtils.DRIVE_API + "/files?q=" + query + "&orderBy=modifiedTime desc&pageSize=10"
            + "&fields=files(id,name,mimeType,webViewLink,createdTime,modifiedTime,owners,version)";

        HttpHeaders headers = utils.createGoogleHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url, HttpMethod.GET, request,
            new ParameterizedTypeReference<>() { }
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Failed to fetch modified Drive files: {}", response.getStatusCode());
            return Collections.emptyList();
        }

        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) responseBody.get("files");

        if (files == null) {
            return Collections.emptyList();
        }

        return files.stream()
            .map(file -> {
                Map<String, Object> result = parseDriveFile(file);
                result.put("version", file.get("version"));
                result.put("event_type", "modified");
                return result;
            })
            .toList();
    }

    /**
     * Parse a Drive file from Google API response.
     *
     * @param file The file data from API
     * @return Parsed file map
     */
    private Map<String, Object> parseDriveFile(Map<String, Object> file) {
        Map<String, Object> result = new HashMap<>();
        result.put("file_id", file.get("id"));
        result.put("name", file.get("name"));
        result.put("mime_type", file.get("mimeType"));
        result.put("web_view_link", file.get("webViewLink"));
        result.put("created_time", file.get("createdTime"));
        result.put("modified_time", file.get("modifiedTime"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> owners = (List<Map<String, Object>>) file.get("owners");
        if (owners != null && !owners.isEmpty()) {
            result.put("owner", owners.get(0).get("emailAddress"));
        }

        return result;
    }

    /**
     * Get MIME type for a given file type.
     *
     * @param fileType The file type string
     * @return MIME type string or null
     */
    private String getMimeTypeForFileType(String fileType) {
        return switch (fileType) {
            case "document" -> "application/vnd.google-apps.document";
            case "spreadsheet" -> "application/vnd.google-apps.spreadsheet";
            case "presentation" -> "application/vnd.google-apps.presentation";
            case "folder" -> "application/vnd.google-apps.folder";
            case "pdf" -> "application/pdf";
            case "image" -> "image/";
            default -> null;
        };
    }
}
