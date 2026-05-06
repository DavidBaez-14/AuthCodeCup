package terminus.co.edu.ufps.identidad_validacion.ms1.security;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class AppwriteUsersClient {

    @Value("${appwrite.endpoint}")
    private String endpoint;

    @Value("${appwrite.project-id}")
    private String projectId;

    @Value("${appwrite.api-key}")
    private String apiKey;

    private final RestClient client = RestClient.create();

    public String crearUsuario(String email, String password, String nombre) {
        Map<String, Object> body = client.post()
                .uri(endpoint + "/users")
                .headers(this::serverHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "userId", UUID.randomUUID().toString(),
                        "email", email,
                        "password", password,
                        "name", nombre))
                .retrieve()
                .body(Map.class);

        if (body == null) {
            throw new IllegalStateException("Appwrite user create returned empty body.");
        }

        return (String) body.get("$id");
    }

    public void asignarLabels(String userId, List<String> labels) {
        client.put()
                .uri(endpoint + "/users/" + userId + "/labels")
                .headers(this::serverHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("labels", labels))
                .retrieve()
                .toBodilessEntity();
    }

    public void eliminarUsuario(String userId) {
        client.delete()
                .uri(endpoint + "/users/" + userId)
                .headers(this::serverHeaders)
                .retrieve()
                .toBodilessEntity();
    }

    private void serverHeaders(HttpHeaders headers) {
        headers.add("X-Appwrite-Project", projectId);
        headers.add("X-Appwrite-Key", apiKey);
        headers.add("X-Appwrite-Response-Format", "1.5.0");
    }
}
