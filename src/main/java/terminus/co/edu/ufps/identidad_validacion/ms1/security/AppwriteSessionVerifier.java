package terminus.co.edu.ufps.identidad_validacion.ms1.security;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.AuthException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppwriteSessionVerifier {

    @Value("${appwrite.endpoint}")
    private String endpoint;

    @Value("${appwrite.project-id}")
    private String projectId;

    private final RestClient restClient = RestClient.create();

    @SuppressWarnings("unchecked")
    public AppwriteUser verify(String appwriteJwt) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(endpoint + "/account")
                    .header("X-Appwrite-JWT", appwriteJwt)
                    .header("X-Appwrite-Project", projectId)
                    .header("X-Appwrite-Response-Format", "1.5.0")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);

            if (body == null) {
                throw new AuthException("Empty response from Appwrite.");
            }

            return new AppwriteUser(
                    (String) body.get("$id"),
                    (String) body.get("email"),
                    (String) body.get("name"),
                    (List<String>) body.getOrDefault("labels", List.of()));
        } catch (Exception ex) {
            log.warn("Appwrite session verification failed: {}", ex.getMessage());
            throw new AuthException("Invalid or expired Appwrite session.");
        }
    }

    public record AppwriteUser(String id, String email, String name, List<String> labels) {
    }
}
