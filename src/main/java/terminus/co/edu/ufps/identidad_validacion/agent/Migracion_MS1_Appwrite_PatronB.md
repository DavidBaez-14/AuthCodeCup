# Migración MS1 → Appwrite + JWT propio (Patrón B) · CODE-CUP

> **Nota para quien implemente esto:** este documento es una guía concreta basada en el
> estado real de los repos `AuthCodeCup` (commit en `main` al momento de redacción) y
> `CodeCupFrontend`. Si al ejecutar encontrás que el código cambió, **no asumas, verificá**.
> Las rutas de archivos y los nombres de clases citados son exactos.

---

## 0. Resumen de la arquitectura objetivo

```
┌──────────────────────────┐
│  Frontend (React + Vite) │
│   codecup.games          │
└──────────┬───────────────┘
           │ 1. Login email/pwd o Google → Appwrite SDK (cliente)
           │
           │ 2. account.createJWT() → JWT corto Appwrite (15 min)
           │
           │ 3. POST /api/auth/exchange  (con JWT Appwrite)
           ▼
┌──────────────────────────┐         ┌──────────────────────────┐
│  MS1 · AuthCodeCup       │ ─────►  │  Appwrite Cloud          │
│  Spring Boot 3.3.5       │  GET    │  - Auth (sesiones)       │
│                          │ /v1/    │  - Database (perfiles,   │
│  - Verifica JWT Appwrite │ account │    jugadores opcional)   │
│  - Lee labels (rol)      │ ◄────   │  - Messaging (MS6)       │
│  - Emite JWT propio RS256│         └──────────────────────────┘
│    TTL = 14 h            │
│  - JWK Set público       │
└──────────┬───────────────┘
           │ 4. Devuelve JWT propio al frontend
           │
           │ 5. Frontend lo guarda y lo manda a TODOS los MS
           ▼
┌──────────────────────────────────────────────────────────────┐
│  MS2 · MS3 · MS4 · MS5 (Supabase Postgres)   MS6 (Appwrite)  │
│                                                              │
│  Cada uno valida el JWT propio LOCALMENTE con la clave       │
│  pública de MS1 (descubierta por JWK Set URI).               │
│  No hablan con Appwrite Auth ni con MS1 para autenticar.     │
└──────────────────────────────────────────────────────────────┘
```

**Reglas duras:**
- Solo MS1 conoce la API Key de Appwrite. Los demás microservicios nunca la ven.
- La cédula es el identificador universal entre microservicios. El `userId` de Appwrite y el `$id` de la colección son detalles internos de MS1.
- Los demás microservicios validan JWT offline con clave pública. Cero llamadas a Appwrite ni a MS1 para autenticar requests.

---

## 1. Configuración previa en Appwrite Cloud

> Pasos manuales en `cloud.appwrite.io`. No tienen código pero son prerrequisito.

### 1.1 Proyecto y plataforma

1. Crear cuenta en `cloud.appwrite.io` y reclamar el plan Education vía GitHub Student Pack
2. Crear proyecto `CODE-CUP`. Anotar `Project ID` y `API Endpoint` (típicamente `https://nyc.cloud.appwrite.io/v1` o similar según región)
3. En **Overview → Platforms**, agregar:
   - **Web App** con dominio `https://codecup.games` (producción)
   - **Web App** con dominio `http://localhost:5173` (desarrollo)

### 1.2 Auth · métodos habilitados

En **Auth → Settings**:
- Habilitar `Email/Password`
- **Session length**: subir a `30 días` (esto es para la sesión de Appwrite, no para el JWT propio — no confundir)
- Habilitar `Google OAuth`. En la consola de Google Cloud, agregar como redirect URI autorizado el callback de Appwrite que aparece en pantalla (algo como `https://cloud.appwrite.io/v1/account/sessions/oauth2/callback/google/<projectId>`). Listar el `client-id` y `client-secret` de Google.

### 1.3 SMTP para recuperación de contraseña y mensajería

En **Messaging → Providers**, agregar provider Email tipo SMTP. Recomendado: Resend (`smtp.resend.com`, puerto 465 TLS). Username `resend`, password = API Key de Resend. Esto cubre tanto:
- Correos automáticos de Appwrite (recuperación de contraseña, verificación de email)
- Correos custom desde MS6 vía `messaging.createEmail()`

### 1.4 Base de datos en Appwrite (solo para `perfiles`)

> **Decisión:** la tabla `jugadores` se queda en Supabase (relacional, ya validada, con CSV bulk insert funcional). La única colección que se crea en Appwrite es `perfiles`.

En **Databases**, crear database `ms1_identidad`. Adentro, una sola colección:

**Colección `perfiles`** (puente Appwrite userId ↔ cédula UFPS):

| Atributo | Tipo | Required | Notas |
|---|---|---|---|
| `appwrite_user_id` | String, 36 | sí | índice único |
| `cedula` | String, 20 | sí | índice (no único — puede coexistir admin y delegado del mismo CC en teoría) |
| `rol_solicitado` | Enum: ARBITRO, DELEGADO | sí | el admin no se auto-registra; lo crea el StartupRunner |
| `estado` | Enum: PENDIENTE, APROBADO, RECHAZADO | sí | default PENDIENTE |
| `fecha_solicitud` | DateTime | sí | |
| `fecha_resolucion` | DateTime | no | se llena al aprobar/rechazar |
| `motivo_rechazo` | String, 500 | no | |

**Permisos de la colección:**
- Read/Create/Update/Delete: solo la API Key del backend MS1
- (No exponer nada al cliente — todo va a través de MS1)

### 1.5 API Key

En **Overview → API Keys**, crear key `ms1-backend` con scopes:
- `users.read`, `users.write` (para crear cuentas, asignar labels, eliminar al rechazar)
- `databases.read`, `databases.write` (para la colección `perfiles`)
- `sessions.write` (necesario si en algún flujo MS1 quiere cerrar sesiones; opcional)

Guardar el secreto. **Nunca al frontend, nunca al repo público.**

### 1.6 Convención de Labels

Esta es la fuente de verdad de roles en el sistema. Se asignan vía API desde MS1.

| Label | Significado | Quién lo asigna |
|---|---|---|
| `administrador` | Admin del torneo | StartupRunner al crear el admin inicial |
| `arbitro` | Árbitro aprobado | MS1 cuando admin aprueba registro pendiente |
| `delegado` | Delegado aprobado | MS1 cuando admin aprueba registro pendiente |

Los usuarios sin label están en estado pendiente — la lógica de negocio lo detecta al ver que `perfiles.estado = PENDIENTE`.

---

## 2. Cambios en MS1 backend

### 2.1 Eliminar (en este orden)

Archivos a borrar completos:

```
src/main/java/.../ms1/model/Usuario.java
src/main/java/.../ms1/repository/UsuarioRepository.java
src/main/java/.../ms1/security/CustomUserDetailsService.java
src/main/java/.../ms1/security/JwtAuthenticationFilter.java
src/main/java/.../ms1/security/OAuth2LoginSuccessHandler.java
src/main/java/.../ms1/config/OAuth2Config.java
src/main/java/.../ms1/config/JwtConfig.java        ← está vacío, igual borrar
src/main/java/.../ms1/dto/LoginRequestDTO.java
src/main/java/.../ms1/dto/CrearUsuarioRequestDTO.java
src/main/java/.../ms1/dto/CrearUsuarioResponseDTO.java
src/main/java/.../ms1/dto/UsuarioDTO.java
src/main/java/.../ms1/dto/CambiarEstadoRequestDTO.java
src/main/java/.../ms1/dto/CambiarContrasenaRequestDTO.java
src/main/java/.../ms1/dto/RecuperarContrasenaRequestDTO.java
src/main/java/.../ms1/exception/CuentaBloqueadaException.java
src/main/java/.../ms1/controller/UsuarioController.java
src/main/java/.../ms1/service/UsuarioService.java
```

En `AuthService.java`: borrar los métodos `login()`, `loginGooglePorCorreo()`, `cambiarContrasena()`, `registrarEventoRecuperacion()`. Quedará vacío y se puede borrar también, o reutilizar la clase para los nuevos métodos del paso 2.4.

En `AuthController.java`: borrar `/login`, `/google`, `/recuperar-contrasena`, `/cambiar-contrasena`. Queda vacío y se reescribe en 2.4.

En `application.properties`: borrar `jwt.secret`, `jwt.expiration-seconds`, y todas las líneas `spring.security.oauth2.client.registration.google.*`.

En la base de datos Supabase: **dejar la tabla `usuarios` por ahora**. Borrar al final cuando todo el flujo nuevo esté en producción y haya pasado al menos una semana sin incidentes.

### 2.2 Agregar dependencia Nimbus para JWK

El `pom.xml` ya tiene JJWT y `spring-security-oauth2-resource-server`. Para exponer el JWK Set público y firmar con RS256 cómodamente, agregar:

```xml
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.40</version>
</dependency>
```

(Spring Security OAuth2 Resource Server ya trae Nimbus transitivamente, pero declararlo explícito asegura la versión que usaremos en MS1 para emitir).

### 2.3 Variables de entorno nuevas

`application.properties` queda así:

```properties
spring.application.name=AuthCodeCup
server.port=${SERVER_PORT:8081}

# Supabase: solo para tabla jugadores y nada mas (la tabla usuarios se borrara despues)
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Appwrite
appwrite.endpoint=${APPWRITE_ENDPOINT}
appwrite.project-id=${APPWRITE_PROJECT_ID}
appwrite.api-key=${APPWRITE_API_KEY}
appwrite.database-id=${APPWRITE_DATABASE_ID}
appwrite.collection-perfiles=${APPWRITE_COLLECTION_PERFILES}

# JWT propio (Patron B)
# La clave RSA se genera al primer arranque y se guarda en disco como PKCS8/SPKI base64.
# Si se prefiere persistencia externa, montar un volumen y apuntar aqui.
codecup.jwt.private-key-path=${JWT_PRIVATE_KEY_PATH:/var/lib/codecup/keys/jwt-private.pem}
codecup.jwt.public-key-path=${JWT_PUBLIC_KEY_PATH:/var/lib/codecup/keys/jwt-public.pem}
codecup.jwt.key-id=${JWT_KEY_ID:codecup-key-1}
codecup.jwt.issuer=${JWT_ISSUER:https://authcodecup-cykcc.ondigitalocean.app}
codecup.jwt.ttl-seconds=${JWT_TTL_SECONDS:50400}
# 50400 segundos = 14 horas. Cubre jornada de arbitro (8 AM - 10 PM).

app.frontend-url=${FRONTEND_URL:http://localhost:5173}
springdoc.swagger-ui.path=/swagger-ui/index.html
```

> **Nota sobre Digital Ocean App Platform:** las apps despliegan con FS efímero. Para que las claves RSA persistan entre redeploys hay dos opciones:
> 1. **Recomendado para tu escala**: cargar la pareja de claves desde variables de entorno (`JWT_PRIVATE_KEY_PEM` y `JWT_PUBLIC_KEY_PEM`) en lugar de leerlas de disco. Mapear así en `application.properties` y leer con `@Value`. Si llegan vacías, generar y loguear las nuevas para que las copies al panel de DO.
> 2. Migrar a Droplet con volumen persistente (más overhead, no vale la pena ahora).
>
> El código en 2.5 lo escribo asumiendo opción 1.

### 2.4 Nuevos endpoints de MS1

Reescribir `AuthController.java`:

```java
package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.*;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Llamado por el frontend tras login en Appwrite. Recibe el JWT corto de
     *  Appwrite y devuelve un JWT propio largo firmado con RS256. */
    @PostMapping("/exchange")
    public ResponseEntity<TokenResponseDTO> exchange(@Valid @RequestBody ExchangeRequestDTO req) {
        return ResponseEntity.ok(authService.exchange(req.getAppwriteJwt()));
    }

    /** Auto-registro: el usuario ya creó cuenta en Appwrite desde el frontend.
     *  Aquí solo registra la solicitud (cédula + rol_solicitado) en la
     *  colección `perfiles` con estado PENDIENTE. */
    @PostMapping("/registrar")
    public ResponseEntity<RegistroResponseDTO> registrar(@Valid @RequestBody RegistroRequestDTO req) {
        return ResponseEntity.ok(authService.registrar(req));
    }

    /** Refresh: emite un nuevo JWT propio si el actual aún es válido pero
     *  está cerca de expirar. No requiere ir a Appwrite. */
    @PostMapping("/refresh")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TokenResponseDTO> refresh(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.refresh(jwt));
    }
}
```

Nuevo `AdminRegistrosController.java`:

```java
package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.*;
import terminus.co.edu.ufps.identidad_validacion.ms1.service.RegistroService;
import java.util.List;

@RestController
@RequestMapping("/api/admin/registros")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminRegistrosController {

    private final RegistroService registroService;

    @GetMapping("/pendientes")
    public ResponseEntity<List<RegistroPendienteDTO>> listarPendientes() {
        return ResponseEntity.ok(registroService.listarPendientes());
    }

    @PostMapping("/{perfilId}/aprobar")
    public ResponseEntity<Void> aprobar(@PathVariable String perfilId) {
        registroService.aprobar(perfilId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{perfilId}/rechazar")
    public ResponseEntity<Void> rechazar(@PathVariable String perfilId,
                                         @RequestBody RechazoRequestDTO req) {
        registroService.rechazar(perfilId, req.getMotivo());
        return ResponseEntity.noContent().build();
    }
}
```

`JugadorController.java` se mantiene como está, pero el `@PreAuthorize("hasRole('ADMINISTRADOR')")` y `@PreAuthorize("hasAnyRole('ADMINISTRADOR','DELEGADO')")` siguen funcionando idéntico — el resource-server convierte el claim `roles` del JWT propio en authorities `ROLE_*`. Hablamos de eso en 2.6.

Nuevo endpoint **interno** en `JugadorController.java` para que MS6 pueda preguntar por el correo del delegado:

```java
@GetMapping("/{cedula}/contacto-delegado")
@PreAuthorize("hasAuthority('SCOPE_internal')")  // protegido por scope interno, no por rol
public ResponseEntity<ContactoDelegadoDTO> getContactoDelegado(@PathVariable String cedula) {
    return ResponseEntity.ok(jugadorService.buscarContactoDelegadoDeJugador(cedula));
}
```

(La parte de `SCOPE_internal` se resuelve en 5.3 cuando hablo de service-to-service.)

### 2.5 Componentes nuevos clave

**`AppwriteSessionVerifier.java`** (nuevo, en `ms1/security/`):

```java
package terminus.co.edu.ufps.identidad_validacion.ms1.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.AuthException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppwriteSessionVerifier {

    @Value("${appwrite.endpoint}")    private String endpoint;
    @Value("${appwrite.project-id}")  private String projectId;

    private final RestClient restClient = RestClient.create();

    /** Llama a GET /v1/account con el JWT corto de Appwrite. Si es válido,
     *  devuelve el dict del user (incluye $id, email, name, labels). */
    @SuppressWarnings("unchecked")
    public AppwriteUser verify(String appwriteJwt) {
        try {
            Map<String, Object> body = restClient.get()
                .uri(endpoint + "/account")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appwriteJwt)
                .header("X-Appwrite-Project", projectId)
                .header("X-Appwrite-Response-Format", "1.5.0")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);

            if (body == null) throw new AuthException("Respuesta vacía de Appwrite.");

            return new AppwriteUser(
                (String) body.get("$id"),
                (String) body.get("email"),
                (String) body.get("name"),
                (List<String>) body.getOrDefault("labels", List.of())
            );
        } catch (Exception ex) {
            log.warn("Verificación Appwrite falló: {}", ex.getMessage());
            throw new AuthException("Sesión de Appwrite inválida o expirada.");
        }
    }

    public record AppwriteUser(String id, String email, String name, List<String> labels) {}
}
```

**`JwtTokenProvider.java`** (reescrito, RS256 desde variables de entorno):

```java
package terminus.co.edu.ufps.identidad_validacion.ms1.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${codecup.jwt.private-key-pem:}")  private String privateKeyPem;
    @Value("${codecup.jwt.public-key-pem:}")   private String publicKeyPem;
    @Value("${codecup.jwt.key-id}")            private String keyId;
    @Value("${codecup.jwt.issuer}")            private String issuer;
    @Value("${codecup.jwt.ttl-seconds}")       private long   ttlSeconds;

    private RSAKey rsaJwk;

    @PostConstruct
    public void init() throws Exception {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            // Modo dev: generar par efímero y loguearlo para copiar al .env
            var gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            var pair = gen.generateKeyPair();
            this.rsaJwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(keyId)
                .build();
            log.warn("=== JWT KEYS NO CONFIGURADAS — generadas efímeras. Copiar al deployment ===");
            log.warn("JWT_PRIVATE_KEY_PEM=\n{}", toPem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            log.warn("JWT_PUBLIC_KEY_PEM=\n{}",  toPem("PUBLIC KEY",  pair.getPublic().getEncoded()));
            return;
        }
        var pubBytes  = Base64.getDecoder().decode(stripPem(publicKeyPem));
        var privBytes = Base64.getDecoder().decode(stripPem(privateKeyPem));
        var pub  = (RSAPublicKey)  KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubBytes));
        var priv = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privBytes));
        this.rsaJwk = new RSAKey.Builder(pub).privateKey(priv).keyID(keyId).build();
    }

    public String generarToken(String userId, String cedula, String email, String nombre, List<String> roles) {
        try {
            var now = Instant.now();
            var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(userId)                // Appwrite userId
                .claim("cedula", cedula)        // identificador universal entre microservicios
                .claim("email", email)
                .claim("nombre", nombre)
                .claim("roles", roles)          // ej: ["administrador"], ["arbitro"]
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .build();
            var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(),
                claims);
            jwt.sign(new RSASSASigner(rsaJwk.toPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo firmar el JWT", e);
        }
    }

    /** JWK público en formato JSON. Lo expone JwksController. */
    public String getJwksJson() {
        return new com.nimbusds.jose.jwk.JWKSet(rsaJwk.toPublicJWK()).toString();
    }

    public long getTtlSeconds() { return ttlSeconds; }

    private static String stripPem(String pem) {
        return pem.replaceAll("-----BEGIN [^-]+-----", "")
                  .replaceAll("-----END [^-]+-----", "")
                  .replaceAll("\\s", "");
    }
    private static String toPem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n"
             + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(bytes)
             + "\n-----END " + type + "-----\n";
    }
}
```

**`JwksController.java`** (nuevo, en `ms1/controller/`):

```java
package terminus.co.edu.ufps.identidad_validacion.ms1.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.JwtTokenProvider;

@RestController
@RequiredArgsConstructor
public class JwksController {
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() {
        return ResponseEntity.ok(jwtTokenProvider.getJwksJson());
    }
}
```

**`AppwriteUsersClient.java`** (nuevo, para asignar labels al aprobar y crear cuentas si fuera necesario):

```java
package terminus.co.edu.ufps.identidad_validacion.ms1.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AppwriteUsersClient {

    @Value("${appwrite.endpoint}")   private String endpoint;
    @Value("${appwrite.project-id}") private String projectId;
    @Value("${appwrite.api-key}")    private String apiKey;

    private final RestClient client = RestClient.create();

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

    private void serverHeaders(HttpHeaders h) {
        h.add("X-Appwrite-Project", projectId);
        h.add("X-Appwrite-Key",     apiKey);
        h.add("X-Appwrite-Response-Format", "1.5.0");
    }
}
```

**`AuthService.java`** (reescrito):

```java
package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.*;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.AuthException;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteSessionVerifier;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.JwtTokenProvider;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppwriteSessionVerifier sessionVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final PerfilRepository perfilRepository;  // ver 2.6

    @Transactional(readOnly = true)
    public TokenResponseDTO exchange(String appwriteJwt) {
        var user = sessionVerifier.verify(appwriteJwt);

        // Buscar perfil del usuario
        var perfil = perfilRepository.findByAppwriteUserId(user.id())
            .orElseThrow(() -> new AuthException(
                "No tienes un perfil registrado. Completa tu inscripción."));

        if (perfil.getEstado() != EstadoRegistro.APROBADO) {
            throw new AuthException("Tu cuenta está pendiente de aprobación.");
        }

        // Filtrar labels conocidos (defensa contra labels manuales en Appwrite)
        var rolesValidos = user.labels().stream()
            .filter(l -> List.of("administrador","arbitro","delegado").contains(l))
            .toList();

        if (rolesValidos.isEmpty()) {
            throw new AuthException("Tu cuenta no tiene rol asignado.");
        }

        var token = jwtTokenProvider.generarToken(
            user.id(), perfil.getCedula(), user.email(), user.name(), rolesValidos);

        return TokenResponseDTO.builder()
            .token(token)
            .expiraEn(jwtTokenProvider.getTtlSeconds())
            .roles(rolesValidos)
            .nombre(user.name())
            .correo(user.email())
            .cedula(perfil.getCedula())
            .build();
    }

    @Transactional
    public RegistroResponseDTO registrar(RegistroRequestDTO req) {
        // Verifica que la cuenta de Appwrite exista validando el JWT
        var user = sessionVerifier.verify(req.getAppwriteJwt());

        if (perfilRepository.findByAppwriteUserId(user.id()).isPresent()) {
            throw new AuthException("Ya enviaste una solicitud de registro.");
        }

        var perfil = Perfil.builder()
            .appwriteUserId(user.id())
            .cedula(req.getCedula().trim())
            .rolSolicitado(req.getRolSolicitado())
            .estado(EstadoRegistro.PENDIENTE)
            .fechaSolicitud(LocalDateTime.now())
            .build();
        perfilRepository.save(perfil);

        return RegistroResponseDTO.builder()
            .estado("PENDIENTE")
            .mensaje("Tu solicitud fue enviada. El administrador la revisará pronto.")
            .build();
    }

    public TokenResponseDTO refresh(Jwt jwt) {
        // Re-emite con los mismos claims si el JWT actual aún no expiró.
        // No vuelve a Appwrite — todo queda local.
        var userId = jwt.getSubject();
        var cedula = jwt.getClaimAsString("cedula");
        var email  = jwt.getClaimAsString("email");
        var nombre = jwt.getClaimAsString("nombre");
        var roles  = jwt.getClaimAsStringList("roles");

        var token = jwtTokenProvider.generarToken(userId, cedula, email, nombre, roles);
        return TokenResponseDTO.builder()
            .token(token)
            .expiraEn(jwtTokenProvider.getTtlSeconds())
            .roles(roles)
            .nombre(nombre)
            .correo(email)
            .cedula(cedula)
            .build();
    }
}
```

**`RegistroService.java`** (nuevo):

```java
package terminus.co.edu.ufps.identidad_validacion.ms1.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.identidad_validacion.ms1.dto.RegistroPendienteDTO;
import terminus.co.edu.ufps.identidad_validacion.ms1.exception.ResourceNotFoundException;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteUsersClient;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistroService {

    private final PerfilRepository perfilRepository;
    private final AppwriteUsersClient appwriteUsersClient;
    private final JugadorRepository jugadorRepository;

    @Transactional(readOnly = true)
    public List<RegistroPendienteDTO> listarPendientes() {
        return perfilRepository.findByEstado(EstadoRegistro.PENDIENTE)
            .stream()
            .map(p -> RegistroPendienteDTO.from(p,
                jugadorRepository.findByCedula(p.getCedula()).orElse(null)))
            .toList();
    }

    @Transactional
    public void aprobar(String perfilId) {
        var perfil = perfilRepository.findById(perfilId)
            .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado."));
        // Asignar label en Appwrite
        var label = perfil.getRolSolicitado().name().toLowerCase(); // "arbitro" / "delegado"
        appwriteUsersClient.asignarLabels(perfil.getAppwriteUserId(), List.of(label));

        perfil.setEstado(EstadoRegistro.APROBADO);
        perfil.setFechaResolucion(LocalDateTime.now());
        perfilRepository.save(perfil);
    }

    @Transactional
    public void rechazar(String perfilId, String motivo) {
        var perfil = perfilRepository.findById(perfilId)
            .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado."));
        // Eliminar la cuenta en Appwrite así no puede volver a entrar
        appwriteUsersClient.eliminarUsuario(perfil.getAppwriteUserId());

        perfil.setEstado(EstadoRegistro.RECHAZADO);
        perfil.setMotivoRechazo(motivo);
        perfil.setFechaResolucion(LocalDateTime.now());
        perfilRepository.save(perfil);
    }
}
```

### 2.6 Nueva entidad `Perfil` y repositorio

> **Decisión pragmática:** la colección `perfiles` está en Appwrite Database según 1.4, PERO el código aquí asume que la persistes en Supabase como entidad JPA. ¿Por qué? Porque el flujo `JOIN perfiles + jugadores` es trivial en SQL y se necesita en `RegistroService.listarPendientes()`. Si la dejas en Appwrite, vas a tener que hacer dos queries y unirlas en código.
>
> **Recomendación:** mover `perfiles` también a Supabase como tabla simple. La razón "todo lo de identidad va a Appwrite" es menos importante que la simplicidad del JOIN.
>
> Si insistes en tenerla en Appwrite, reemplaza `PerfilRepository` por un cliente Appwrite tipo `AppwriteUsersClient` que use el endpoint `/databases/{dbId}/collections/{collId}/documents`. Es factible pero más código.

Voy a documentar la **opción simple** (Postgres):

```java
// ms1/model/Perfil.java
@Entity
@Table(name = "perfiles")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Perfil {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "appwrite_user_id", nullable = false, unique = true, length = 36)
    private String appwriteUserId;

    @Column(nullable = false, length = 20)
    private String cedula;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_solicitado", nullable = false, length = 20)
    private RolSolicitado rolSolicitado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRegistro estado;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;
}

// ms1/model/RolSolicitado.java
public enum RolSolicitado { ARBITRO, DELEGADO }

// ms1/model/EstadoRegistro.java
public enum EstadoRegistro { PENDIENTE, APROBADO, RECHAZADO }

// ms1/repository/PerfilRepository.java
public interface PerfilRepository extends JpaRepository<Perfil, String> {
    Optional<Perfil> findByAppwriteUserId(String appwriteUserId);
    List<Perfil> findByEstado(EstadoRegistro estado);
}
```

`JugadorRepository` necesita método nuevo:

```java
Optional<Jugador> findByCedula(String cedula);
```

### 2.7 Reescribir `SecurityConfig.java`

```java
package terminus.co.edu.ufps.identidad_validacion.ms1.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos
                .requestMatchers("/.well-known/jwks.json").permitAll()
                .requestMatchers("/api/auth/exchange", "/api/auth/registrar").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Todo lo demás requiere JWT propio válido
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    /** Convierte el claim `roles` del JWT en authorities ROLE_*. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) return List.of();
            return roles.stream()
                .map(String::toUpperCase)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .map(a -> (org.springframework.security.core.GrantedAuthority) a)
                .toList();
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend-url}") String frontendUrl) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

Y agregar a `application.properties` la configuración del resource-server (MS1 también valida sus propios JWTs cuando llega un request a `/api/admin/*` o cualquier endpoint protegido):

```properties
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${codecup.jwt.issuer}/.well-known/jwks.json
spring.security.oauth2.resourceserver.jwt.issuer-uri=${codecup.jwt.issuer}
```

### 2.8 Adaptar `StartupRunner.java`

Ya no crea `Usuario`. Ahora el flujo es:
1. Verifica conexión a DB
2. Verifica que exista al menos un `Perfil` con label `administrador` en Appwrite
3. Si no existe ninguno, **lo crea programáticamente en Appwrite** usando `AppwriteUsersClient.crearUsuario()` (método que tendrías que agregar al cliente, similar a `asignarLabels`) con email/password de variables de entorno, le asigna label `administrador` y crea su `Perfil` aprobado.

### 2.9 Tests

El test actual `AuthCodeCupApplicationTests.java` solo verifica que el contexto Spring carga. Va a romperse al quitar las clases viejas. **Hay que actualizarlo**, no eliminarlo. Mock del `AppwriteSessionVerifier` y del `AppwriteUsersClient` con `@MockBean` para que el contexto cargue sin llamadas reales a Appwrite.

---

## 3. Cambios en el frontend

### 3.1 Instalar SDK

```bash
npm install appwrite
```

### 3.2 Nuevo archivo `src/lib/appwrite.js`

```javascript
import { Client, Account, ID, OAuthProvider } from 'appwrite';

const client = new Client()
  .setEndpoint(import.meta.env.VITE_APPWRITE_ENDPOINT)
  .setProject(import.meta.env.VITE_APPWRITE_PROJECT_ID);

export const account = new Account(client);
export { ID, OAuthProvider };
```

Variables nuevas en `.env`:
```
VITE_APPWRITE_ENDPOINT=https://cloud.appwrite.io/v1
VITE_APPWRITE_PROJECT_ID=<tu project id>
```

### 3.3 Reescribir `src/api/auth.js`

```javascript
import { account, ID, OAuthProvider } from '../lib/appwrite';
import { requestJson } from './http';

const FRONTEND_ORIGIN = window.location.origin;

/** Login email/password.
 *  1) Crea sesión en Appwrite.
 *  2) Obtiene JWT corto de Appwrite.
 *  3) Lo intercambia con MS1 por un JWT propio largo. */
export async function login(correo, contrasena) {
  await account.createEmailPasswordSession(correo, contrasena);
  const { jwt } = await account.createJWT();
  return requestJson('/api/auth/exchange', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ appwriteJwt: jwt }),
  });
}

/** Inicia OAuth con Google. Appwrite redirige al callback de Google,
 *  Google vuelve a Appwrite, Appwrite redirige a una URL que tú decides.
 *  En esa URL llamas exchangeAfterGoogle(). */
export function startGoogleLogin() {
  account.createOAuth2Session(
    OAuthProvider.Google,
    `${FRONTEND_ORIGIN}/auth/google/callback`, // success
    `${FRONTEND_ORIGIN}/login?oauthError=google`, // failure
  );
}

/** Llamar desde GoogleCallbackPage tras volver de Appwrite. */
export async function exchangeAfterGoogle() {
  const { jwt } = await account.createJWT();
  return requestJson('/api/auth/exchange', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ appwriteJwt: jwt }),
  });
}

/** Auto-registro: el usuario crea cuenta en Appwrite y registra
 *  su solicitud de rol en MS1. */
export async function registrarse(correo, contrasena, nombre, cedula, rolSolicitado) {
  await account.create(ID.unique(), correo, contrasena, nombre);
  await account.createEmailPasswordSession(correo, contrasena);
  const { jwt } = await account.createJWT();
  return requestJson('/api/auth/registrar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ appwriteJwt: jwt, cedula, rolSolicitado }),
  });
}

/** Recuperación de contraseña: 100% en Appwrite, sin backend. */
export async function recuperarContrasena(correo) {
  return account.createRecovery(correo, `${FRONTEND_ORIGIN}/reset-password`);
}

export async function confirmarRecuperacion(userId, secret, nuevaContrasena) {
  return account.updateRecovery(userId, secret, nuevaContrasena);
}

/** Logout: cierra sesión en Appwrite Y limpia el JWT propio. */
export async function logout() {
  try { await account.deleteSession('current'); } catch { /* ignore */ }
}

/** Refresh del JWT propio si está cerca de expirar. No habla con Appwrite. */
export async function refresh(token) {
  return requestJson('/api/auth/refresh', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });
}
```

### 3.4 Adaptar `src/utils/session.js`

Mínimos cambios — agregar la fecha de expiración para poder hacer refresh proactivo:

```javascript
const TOKEN_KEY = 'codecup_token';
const ROL_KEY = 'codecup_rol';
const NOMBRE_KEY = 'codecup_nombre';
const CORREO_KEY = 'codecup_correo';
const CEDULA_KEY = 'codecup_cedula';
const EXP_KEY = 'codecup_token_exp';

export function setSession({ token, roles, nombre, correo, cedula, expiraEn }) {
  // expiraEn = TTL en segundos, lo convertimos a timestamp absoluto
  const expAt = Date.now() + (expiraEn * 1000);
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(ROL_KEY, roles[0]?.toUpperCase() || ''); // primer rol como principal
  localStorage.setItem(NOMBRE_KEY, nombre);
  localStorage.setItem(CORREO_KEY, correo);
  localStorage.setItem(CEDULA_KEY, cedula);
  localStorage.setItem(EXP_KEY, String(expAt));
}

export function clearSession() {
  [TOKEN_KEY, ROL_KEY, NOMBRE_KEY, CORREO_KEY, CEDULA_KEY, EXP_KEY]
    .forEach(k => localStorage.removeItem(k));
}

export function getToken() { return localStorage.getItem(TOKEN_KEY); }
export function getRol()   { return localStorage.getItem(ROL_KEY); }
export function getNombre() { return localStorage.getItem(NOMBRE_KEY); }
export function hasSession() {
  const exp = Number(localStorage.getItem(EXP_KEY) || 0);
  return Boolean(getToken()) && Date.now() < exp;
}

/** Devuelve true si el token expira en < 30 minutos. */
export function shouldRefresh() {
  const exp = Number(localStorage.getItem(EXP_KEY) || 0);
  return exp > 0 && (exp - Date.now()) < 30 * 60 * 1000;
}
```

> Nota: el campo `mustChangePassword` se elimina. Appwrite maneja eso solo con el flujo de recuperación.

### 3.5 Adaptar `src/api/http.js` para refresh proactivo

```javascript
import { getToken, setSession, clearSession, shouldRefresh } from '../utils/session';
import { refresh } from './auth';

const BACKEND_ORIGIN = import.meta.env.VITE_BACKEND_ORIGIN || 'https://authcodecup-cykcc.ondigitalocean.app';

async function ensureFreshToken() {
  if (shouldRefresh()) {
    try {
      const data = await refresh(getToken());
      setSession({
        token: data.token,
        roles: data.roles,
        nombre: data.nombre,
        correo: data.correo,
        cedula: data.cedula,
        expiraEn: data.expiraEn,
      });
    } catch {
      clearSession();
      window.location.href = '/login';
    }
  }
}

async function parseBody(response) {
  const ct = response.headers.get('content-type') || '';
  if (ct.includes('application/json')) return response.json();
  const text = await response.text();
  return text ? { mensaje: text } : {};
}

export async function requestJson(path, options = {}) {
  await ensureFreshToken();
  const url = /^https?:\/\//i.test(path) ? path : `${BACKEND_ORIGIN}${path}`;
  // Inyectar Authorization si hay token y no lo trae
  const token = getToken();
  const headers = { ...(options.headers || {}) };
  if (token && !headers.Authorization) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(url, { ...options, headers });
  const body = await parseBody(response);
  if (!response.ok) {
    const error = new Error(body?.mensaje || body?.error || 'Error en la solicitud.');
    error.status = response.status;
    error.body = body;
    if (response.status === 401) { clearSession(); window.location.href = '/login'; }
    throw error;
  }
  return body;
}
```

### 3.6 Actualizar `LoginPage.jsx`

Cambios mínimos: el resultado de `login()` ahora viene con `roles` (array) en lugar de `rol` (string), y sin `debeCambiarContrasena`. El handler queda:

```javascript
const data = await login(correo, contrasena);
setSession({
  token: data.token,
  roles: data.roles,
  nombre: data.nombre,
  correo: data.correo,
  cedula: data.cedula,
  expiraEn: data.expiraEn,
});
const principal = data.roles[0].toUpperCase();
navigate(ROLE_ROUTE[principal] || '/login', { replace: true });
```

### 3.7 Reescribir `GoogleCallbackPage.jsx`

Ya no recibe el JWT por query string — Appwrite ya estableció la sesión, y aquí solo se hace el exchange con MS1:

```javascript
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { exchangeAfterGoogle } from '../api/auth';
import { setSession } from '../utils/session';

const ROLE_ROUTE = {
  ADMINISTRADOR: '/dashboard/admin',
  ARBITRO: '/dashboard/arbitro',
  DELEGADO: '/dashboard/delegado',
};

function GoogleCallbackPage() {
  const navigate = useNavigate();
  const [error, setError] = useState(null);

  useEffect(() => {
    (async () => {
      try {
        const data = await exchangeAfterGoogle();
        setSession({
          token: data.token, roles: data.roles, nombre: data.nombre,
          correo: data.correo, cedula: data.cedula, expiraEn: data.expiraEn,
        });
        const principal = data.roles[0].toUpperCase();
        navigate(ROLE_ROUTE[principal] || '/login', { replace: true });
      } catch (e) { setError(e.message); }
    })();
  }, [navigate]);

  if (error) return (
    <main className="page login-page"><section className="auth-card">
      <h1>No fue posible completar Google Login</h1>
      <p className="auth-subtitle">{error}</p>
      <Link className="primary-button" to="/login">Volver al login</Link>
    </section></main>
  );
  return (
    <main className="page login-page"><section className="auth-card">
      <h1>Autenticando con Google...</h1>
    </section></main>
  );
}

export default GoogleCallbackPage;
```

### 3.8 Crear nueva página `RegistroPage.jsx`

Pantalla nueva (no existe en el frontend actual) con formulario: nombre, correo, contraseña, cédula, rol solicitado (radio: árbitro / delegado). Llama a `registrarse()`. Después muestra "Solicitud enviada, espera aprobación del admin". Agregar ruta `/registro` en `App.jsx`.

### 3.9 Actualizar `ProtectedRoute.jsx`

Borrar la línea de `mustChangePassword`:

```javascript
import { Navigate, useLocation } from 'react-router-dom';
import { getRol, hasSession } from '../utils/session';

function ProtectedRoute({ children, allowedRoles }) {
  if (!hasSession()) return <Navigate to="/login" replace />;
  if (allowedRoles?.length && !allowedRoles.includes(getRol())) {
    return <Navigate to="/login" replace />;
  }
  return children;
}
export default ProtectedRoute;
```

### 3.10 Sección "Pendientes de aprobación" en `AdminDashboard.jsx`

Cuando el admin entra al dashboard, llamar a `GET /api/admin/registros/pendientes`. Mostrar tabla con nombre, correo, cédula, rol solicitado, y botones aprobar/rechazar que llaman a `POST /api/admin/registros/{id}/aprobar` y `/rechazar`.

### 3.11 `ChangePasswordPage.jsx`

Convertirla en `ResetPasswordPage` para el flujo de Appwrite (`/reset-password?userId=...&secret=...`). Llama a `confirmarRecuperacion(userId, secret, nuevaContrasena)`.

---

## 4. Despliegue: claves RSA en Digital Ocean

1. Localmente: arrancar MS1 sin las variables `JWT_PRIVATE_KEY_PEM` y `JWT_PUBLIC_KEY_PEM`. La aplicación va a generar el par y loguearlo en consola al arranque.
2. Copiar las dos PEM enteras (incluyendo `-----BEGIN/END-----`).
3. En Digital Ocean App Platform → Settings → Environment Variables, agregar `JWT_PRIVATE_KEY_PEM` y `JWT_PUBLIC_KEY_PEM` (marcadas como "secret"). Pegar las PEM con saltos de línea reales.
4. Redeploy.
5. Verificar que `https://authcodecup-cykcc.ondigitalocean.app/.well-known/jwks.json` devuelve un JSON válido con la clave pública.

---

## 5. Cómo MS2, MS3, MS4, MS5 usan MS1

Esta es la parte que importa para el resto del equipo. Cada microservicio de Spring Boot que se cree va a seguir el mismo patrón. **Cero código de Appwrite**, cero llamadas a MS1 para autenticar. Solo validación local del JWT propio.

### 5.1 Dependencia Maven (única que necesitan)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### 5.2 Configuración

`application.properties`:

```properties
codecup.jwt.issuer=https://authcodecup-cykcc.ondigitalocean.app
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${codecup.jwt.issuer}/.well-known/jwks.json
spring.security.oauth2.resourceserver.jwt.issuer-uri=${codecup.jwt.issuer}
```

Spring descubre automáticamente la clave pública vía JWKS y la cachea. Si MS1 rota la clave, los demás MS la recogen al siguiente intento.

`SecurityConfig.java` (idéntica a la de MS1 sección 2.7, ajustando los matchers a los endpoints públicos del MS en cuestión).

### 5.3 Cómo leer al usuario en un controller

```java
@RestController
@RequestMapping("/api/partidos")
public class PartidoController {

    @PostMapping
    @PreAuthorize("hasRole('ARBITRO')")
    public ResponseEntity<Partido> crear(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PartidoRequest req) {
        var cedulaArbitro = jwt.getClaimAsString("cedula");
        var nombreArbitro = jwt.getClaimAsString("nombre");
        // ... lógica de negocio
        return ResponseEntity.ok(partidoService.crear(req, cedulaArbitro));
    }
}
```

Los `@PreAuthorize("hasRole('XXX')")` funcionan sin más configuración porque la `JwtAuthenticationConverter` (heredada via copy/paste o módulo común) convierte el claim `roles` en authorities `ROLE_*`.

### 5.4 Comunicación service-to-service (interna)

Cuando MS6 necesita el correo del delegado de un jugador para mandarle la notificación de sanción:

**Opción simple (recomendada):** los eventos publicados en Pub/Sub ya van **enriquecidos** con todos los datos. El árbitro registra una tarjeta en MS3, MS3 publica:

```json
{
  "evento_id": "uuid-v4",
  "tipo": "JUGADOR_EXPULSADO",
  "cedula_jugador": "1090123456",
  "nombre_jugador": "Angel Vesga",
  "cedula_delegado": "1090111222",
  "correo_delegado": "delegado@ufps.edu.co",
  "nombre_delegado": "Sebastian Padilla",
  "partido_id": "abc",
  "partidos_suspension": 1
}
```

MS3 obtiene `correo_delegado` al cargar la alineación en HU19 (consulta a MS1: `GET /api/jugadores/{cedula}` ya regresa esos datos). MS6 consume el evento, extrae `correo_delegado`, y llama a Appwrite Messaging:

```java
messaging.createEmail(
    ID.unique(),
    "Sanción de tu jugador: " + nombreJugador,
    bodyHtml,
    List.of(),                           // topics
    List.of(perfilDelegadoUserId),       // targets (userId del delegado en Appwrite)
    /* resto de params */
);
```

**Si en algún caso un MS sí necesita llamar a MS1**, debe usar un token de servicio. El patrón:

1. MS1 expone un endpoint admin `POST /api/admin/service-tokens` (autenticado con rol ADMINISTRADOR) que emite un JWT con scope `internal` y TTL largo (30 días).
2. Cada MS guarda ese token como variable de entorno `MS1_SERVICE_TOKEN`.
3. Para llamar a MS1: `Authorization: Bearer ${MS1_SERVICE_TOKEN}`.
4. En MS1, los endpoints internos están anotados con `@PreAuthorize("hasAuthority('SCOPE_internal')")` y la `JwtAuthenticationConverter` también mapea el claim `scope` a authorities.

Para 6 microservicios con tu escala, esto es overkill. Empieza con eventos enriquecidos. Solo agrega tokens de servicio si te encuentras con un caso donde no puedas enriquecer.

### 5.5 MS6 (Notificaciones) — caso especial

MS6 es el único que habla con Appwrite Messaging. Su configuración:

- Misma validación de JWT que los demás MS (resource-server con JWKS de MS1)
- Adicional: `RestClient` apuntando a Appwrite con la API Key (lectura de targets, escritura de mensajes)
- Necesita el `userId` de Appwrite del delegado para mandarle email. Ese `userId` viaja en el JWT (`sub`) cuando es el propio usuario, pero cuando MS6 manda a un tercero (el delegado de un jugador sancionado), necesita resolver `cedula → appwrite_user_id`. Eso lo expone MS1 con un endpoint interno: `GET /api/internal/perfiles/by-cedula/{cedula}` (protegido con `SCOPE_internal`), o se enriquece en el evento (preferible).

Acuerdo con el equipo: **siempre enriquecer eventos**. MS3 al publicar `JUGADOR_EXPULSADO` ya incluye `appwrite_user_id_delegado`. MS6 usa ese userId directamente como target en Appwrite Messaging. Cero llamadas extra.

---

## 6. Orden sugerido de ejecución

1. **Día 1**: configurar Appwrite Cloud (sección 1 completa). Sin tocar código todavía.
2. **Día 2**: en MS1, agregar dependencia Nimbus, crear `JwtTokenProvider` RS256, `JwksController`, generar par RSA, levantar localmente y verificar que `/.well-known/jwks.json` responde.
3. **Día 3**: crear `AppwriteSessionVerifier`, `AppwriteUsersClient`, entidad `Perfil`, `RegistroService`, `AuthService` reescrito.
4. **Día 4**: reescribir `SecurityConfig`, `AuthController`, crear `AdminRegistrosController`. Eliminar archivos viejos. Probar el flujo completo de login con Postman: crear usuario en Appwrite Console manualmente → llamar `/exchange` con JWT corto → recibir JWT propio.
5. **Día 5**: frontend. Instalar SDK, crear `lib/appwrite.js`, reescribir `api/auth.js`, adaptar `LoginPage`, `GoogleCallbackPage`, `ProtectedRoute`, `session.js`, `http.js`. Crear `RegistroPage`.
6. **Día 6**: dashboard del admin con sección de pendientes. Pruebas end-to-end del flujo de aprobación.
7. **Día 7**: deploy a Digital Ocean con las claves RSA en variables de entorno. Smoke test en producción.

Si vas en equipo de 2-3, tiempo total razonable: 3-4 días calendario.

---

## 7. Checklist de validación final

Antes de dar por cerrada la migración, verificar:

- [ ] `/.well-known/jwks.json` devuelve JSON con la clave pública
- [ ] `POST /api/auth/exchange` con JWT Appwrite válido retorna JWT propio
- [ ] El JWT propio dura 14 horas (decodificarlo en jwt.io, ver `exp - iat`)
- [ ] Login email/password funciona end-to-end
- [ ] Login Google OAuth funciona end-to-end (sin pasar por backend, todo Appwrite)
- [ ] Recuperación de contraseña por correo funciona (sin backend involucrado)
- [ ] Auto-registro deja al usuario en estado PENDIENTE y no le permite acceso
- [ ] Aprobación del admin asigna label en Appwrite y `Perfil.estado = APROBADO`
- [ ] Después de aprobado, el usuario puede hacer login y recibir el JWT propio
- [ ] Rechazar elimina la cuenta de Appwrite (verificar en consola)
- [ ] El árbitro puede mantener sesión durante 14 horas sin re-login
- [ ] El JWT propio se renueva automáticamente cuando faltan < 30 min
- [ ] Endpoint protegido con `@PreAuthorize("hasRole('ARBITRO')")` rechaza JWT con rol distinto
- [ ] Tabla `usuarios` de Supabase puede borrarse sin que rompa nada (esperar 1 semana antes de hacerlo en producción)

---

## 8. Decisiones diferidas (para conversar con el equipo)

- **¿Mover `jugadores` a Appwrite Database?** El `propuesta_appwrite_codecup.md` lo planteaba. Mi recomendación: **dejarla en Supabase**. Razones: bulk insert por CSV en SQL es más rápido, JOINs con `perfiles` son triviales, el dominio es relacional. Appwrite Database es documental y no aporta ventaja aquí.
- **¿Storage de comprobantes en Appwrite o en Supabase Storage?** Appwrite Storage es mejor (transformaciones de imagen, URLs firmadas, encriptación). Cuando llegues a HU25/HU27 (Sprint 2) lo decides.
- **¿API Gateway?** No lo necesitas para 6 servicios. Si en el futuro escalas, Spring Cloud Gateway se agrega sin tocar el patrón actual.
- **¿Service Discovery?** No para tu caso. URLs fijas en variables de entorno.

---

## 9. Variables de entorno consolidadas

### MS1

```
SERVER_PORT=8081
DB_URL=jdbc:postgresql://...
DB_USERNAME=...
DB_PASSWORD=...
APPWRITE_ENDPOINT=https://nyc.cloud.appwrite.io/v1
APPWRITE_PROJECT_ID=...
APPWRITE_API_KEY=...
APPWRITE_DATABASE_ID=ms1_identidad
APPWRITE_COLLECTION_PERFILES=perfiles      # solo si decides usar Appwrite DB
JWT_PRIVATE_KEY_PEM="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
JWT_PUBLIC_KEY_PEM="-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
JWT_KEY_ID=codecup-key-1
JWT_ISSUER=https://authcodecup-cykcc.ondigitalocean.app
JWT_TTL_SECONDS=50400
ADMIN_EMAIL=admin@codecup.games
ADMIN_PASSWORD=...
FRONTEND_URL=https://codecup.games
```

### MS2-MS5

```
CODECUP_JWT_ISSUER=https://authcodecup-cykcc.ondigitalocean.app
DB_URL=jdbc:postgresql://...   # cada uno con su schema o DB en Supabase
DB_USERNAME=...
DB_PASSWORD=...
```

### MS6

```
CODECUP_JWT_ISSUER=https://authcodecup-cykcc.ondigitalocean.app
APPWRITE_ENDPOINT=https://nyc.cloud.appwrite.io/v1
APPWRITE_PROJECT_ID=...
APPWRITE_API_KEY=...                       # API Key separada con scope messaging.write
GOOGLE_PUBSUB_PROJECT=...
GOOGLE_PUBSUB_SUBSCRIPTION=ms6-notificaciones
```

### Frontend

```
VITE_BACKEND_ORIGIN=https://authcodecup-cykcc.ondigitalocean.app
VITE_APPWRITE_ENDPOINT=https://nyc.cloud.appwrite.io/v1
VITE_APPWRITE_PROJECT_ID=...
```

---

## 10. Preguntas abiertas

Si al implementar encuentras alguna de estas situaciones, conversar antes de proceder:

- ¿La región de Appwrite Cloud que elijas (NYC, Frankfurt, Singapur) afecta latencia desde Cúcuta? Hacer ping y elegir.
- ¿El plan Education tiene límite de usuarios totales? Verificar antes de cargar la base completa de 300 jugadores (aunque aquí los jugadores no son usuarios de Appwrite, conviene confirmar).
- ¿Qué pasa si el admin se equivoca aprobando un usuario? Falta un endpoint `POST /api/admin/registros/{id}/revertir-aprobacion` que quite el label y vuelva a PENDIENTE. Decidir si vale la pena ahora.
- ¿La HU38 (notificación de sanción) se cambia oficialmente para que vaya solo al delegado? Actualizar el documento de HUs antes de implementar.
