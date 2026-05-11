# Autenticación CODE-CUP · Guía de integración para MS2–MS6

> **A quién va dirigido:** quien implemente MS2 (Supercopa, ahora incluye lo que era MS3),
> MS4 (Finanzas), MS5 (Analytics) o MS6 (Notificaciones).
> MS1 ya está desplegado y emitiendo tokens. Esta guía explica cómo consumirlos.

**Nota de arquitectura:** MS2 y MS3 se unifican en un solo microservicio llamado **supercopa**.
Los endpoints de partidos y eventos viven en MS2, y los roles (arbitro/administrador)
solo restringen acceso al mismo controller.

---

## 0. Arquitectura en una frase

```
Frontend  →  [Appwrite login]  →  POST /api/auth/exchange  →  MS1
MS1       →  emite JWT propio RS256 (14 h)
Frontend  →  envía ese JWT en Authorization: Bearer <token>  →  MS2, MS4, MS5, MS6
Cada MS   →  valida el JWT LOCALMENTE usando la clave pública de MS1 (JWKS)
             SIN llamar a Appwrite ni a MS1 en cada request
```

```
┌─────────────────────────┐
│  Frontend (React/Vite)  │
│  codecup.games          │
└────────────┬────────────┘
             │  Authorization: Bearer <JWT propio>
             ▼
┌────────────────────────────────────────────────────────────────────┐
│  MS2 · MS4 · MS5 (Supabase Postgres)         MS6 (Appwrite Mail)  │
│                                                                    │
│  Cada uno descarga la clave pública de MS1 vía JWKS una sola vez  │
│  y la cachea. Todas las validaciones siguientes son offline.       │
└────────────────────────────────────────────────────────────────────┘
                ▲  solo la primera vez
                │  GET /.well-known/jwks.json
┌──────────────────────────┐
│  MS1 · AuthCodeCup       │
│  Spring Boot 3.3.5       │
│  DigitalOcean            │
│  https://authcodecup-    │
│  cykcc.ondigitalocean.app│
└──────────────────────────┘
```

**Reglas que nunca se rompen:**
- Solo MS1 tiene la API Key de Appwrite. MS2–MS6 nunca la ven.
- El identificador universal entre microservicios es la **cédula** (viene en el JWT).
- MS2, MS4 y MS5 nunca llaman a MS1 para autenticar un request. Solo validan el JWT localmente.
- MS6 es el único que habla con Appwrite (solo para enviar correos, no para autenticar).

---

## 1. Contrato real del JWT

Esto es exactamente lo que `JwtTokenProvider.generarToken()` pone en el token.
Verificable decodificando cualquier token en [jwt.io](https://jwt.io).

```json
{
  "iss": "https://authcodecup-cykcc.ondigitalocean.app",
  "sub": "68229e5400013b0b4588",
  "cedula": "1090123456",
  "email": "usuario@ufps.edu.co",
  "nombre": "Juan Pérez",
  "roles": ["delegado", "jugador"],
  "iat": 1746000000,
  "exp": 1746050400
}
```

| Claim | Tipo | Descripción |
|---|---|---|
| `sub` | String | Appwrite userId. Identificador interno de MS1. Los demás MS no lo necesitan. |
| `cedula` | String | **Identificador universal.** Úsalo para relacionar datos entre microservicios. |
| `email` | String | Correo del usuario. |
| `nombre` | String | Nombre completo. |
| `roles` | String[] | Lista de roles aprobados en **minúscula**. Puede tener más de uno. |
| `iat` / `exp` | Long | Emisión y expiración (Unix timestamp). TTL = 14 horas (50 400 segundos). |

---

## 2. Sistema de roles — un usuario puede tener varios

Este es el cambio más importante respecto al documento de migración original.
En MS1, la entidad que gestiona los roles es `CuentaRol`, no `Perfil`.
Una misma `Cuenta` puede tener múltiples `CuentaRol` aprobados.

**Ejemplo real:** un estudiante que también es delegado de su equipo tiene:

```json
"roles": ["delegado", "jugador"]
```

Los roles posibles son:

| Valor en el JWT | Constante en Spring | Actor |
|---|---|---|
| `"administrador"` | `ROLE_ADMINISTRADOR` | Organiza el torneo |
| `"arbitro"` | `ROLE_ARBITRO` | Opera en cancha |
| `"delegado"` | `ROLE_DELEGADO` | Gestiona su equipo |
| `"jugador"` | `ROLE_JUGADOR` | Consulta su perfil e historial |

### Cómo se convierten los roles en Spring Security

El `JwtAuthenticationConverter` que usa MS1 (y que **copiarás exacto** a cada MS)
mapea el array `roles` del JWT a authorities `ROLE_*`:

```
"roles": ["delegado", "jugador"]
    → ROLE_DELEGADO
    → ROLE_JUGADOR
```

Por lo tanto, `@PreAuthorize("hasRole('DELEGADO')")` es verdadero para ese usuario,
y también `@PreAuthorize("hasRole('JUGADOR')")`. Ambos al mismo tiempo.

### Caso de uso multi-rol en un endpoint

Si un endpoint debe ser accesible por cualquier usuario autenticado con al menos uno
de varios roles:

```java
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DELEGADO')")
```

Si necesitas saber **qué roles concretos** tiene el usuario en la lógica de negocio:

```java
@GetMapping("/mi-perfil")
public ResponseEntity<?> miPerfil(@AuthenticationPrincipal Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    // roles = ["delegado", "jugador"]
    boolean esDelegado = roles.contains("delegado");
    // ...
}
```

---

## 3. Qué agregar a cada microservicio (MS2, MS4, MS5)

Son exactamente 3 pasos: dependencia, properties y SecurityConfig.
Nada de Appwrite, nada de llamadas a MS1.

### 3.1 Dependencia Maven

Agregar al `pom.xml`. Si el proyecto ya tiene `spring-boot-starter-web`,
solo necesitas las dos primeras:

```xml
<!-- Validación de JWT vía JWKS (descubre la clave pública de MS1 automáticamente) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- Spring Security base -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Web (probablemente ya lo tenés) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### 3.2 `application.properties` — solo estas 3 líneas de auth

```properties
# URL de producción de MS1 (cambiar a http://localhost:8081 para desarrollo local)
codecup.jwt.issuer=https://authcodecup-cykcc.ondigitalocean.app

# Spring descarga la clave pública de MS1 la primera vez y la cachea
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${codecup.jwt.issuer}/.well-known/jwks.json
spring.security.oauth2.resourceserver.jwt.issuer-uri=${codecup.jwt.issuer}
```

> **Desarrollo local:** si MS1 corre en `http://localhost:8081`, usar ese valor
> para `codecup.jwt.issuer`. El JWKS endpoint es `http://localhost:8081/.well-known/jwks.json`.

### 3.3 `SecurityConfig.java` — copiar exacto, solo cambiar los matchers públicos

Crear `src/main/java/.../config/SecurityConfig.java`.
El único cambio entre microservicios es la lista de endpoints públicos
dentro de `authorizeHttpRequests`.

```java
package <tu.paquete>.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
                // ── ENDPOINTS PÚBLICOS DE ESTE MS ──────────────────────────────
                // Swagger (quitar en producción si se prefiere proteger)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Preflight CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // ── PONER AQUÍ LOS ENDPOINTS PÚBLICOS DEL MS ──────────────────
                // Ejemplo para MS5: .requestMatchers(HttpMethod.GET, "/api/analytics/**").permitAll()
                // Ejemplo para MS2: .requestMatchers(HttpMethod.GET, "/api/partidos/*/eventos").permitAll()
                // ── TODO LO DEMÁS REQUIERE JWT VÁLIDO ─────────────────────────
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    /**
     * Convierte el claim `roles` del JWT en authorities ROLE_*.
     * También convierte el claim `scope` en SCOPE_* (para tokens internos de servicio).
     * Esta lógica es idéntica en todos los microservicios.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthorityPrefix("SCOPE_");

        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();

            // SCOPE_* para tokens de servicio interno (ej: MS6 llamando a MS1)
            var scopeAuthorities = scopesConverter.convert(jwt);
            if (scopeAuthorities != null) {
                authorities.addAll(scopeAuthorities);
            }

            // ROLE_* desde el claim "roles" del JWT propio de MS1
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.stream()
                    .map(String::toUpperCase)
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .forEach(authorities::add);
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
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

---

## 4. Cómo leer el JWT en un controller

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/partidos")
public class PartidoController {

    @PostMapping
    @PreAuthorize("hasRole('ARBITRO')")
    public ResponseEntity<PartidoDTO> iniciar(
            @AuthenticationPrincipal Jwt jwt,   // ← inyección automática
            @RequestBody IniciarPartidoRequest req) {

        // Claims disponibles en todos los tokens
        String cedulaArbitro = jwt.getClaimAsString("cedula");
        String nombreArbitro = jwt.getClaimAsString("nombre");
        String correoArbitro = jwt.getClaimAsString("email");
        List<String> roles   = jwt.getClaimAsStringList("roles");

        // Lógica de negocio usando la cédula como identificador
        return ResponseEntity.ok(partidoService.iniciar(req, cedulaArbitro));
    }
}
```

---

## 5. Patrones `@PreAuthorize` por microservicio

### MS2 · Super-Copa

```java
// Solo el administrador configura torneos
@PreAuthorize("hasRole('ADMINISTRADOR')")
public ResponseEntity<?> crearEdicionTorneo(...) { }

// Delegado registra su equipo; admin también puede hacerlo
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'DELEGADO')")
public ResponseEntity<?> registrarEquipo(...) { }

// Delegado gestiona su propio plantel
// (validar dentro del servicio que el equipo le pertenece por cédula)
@PreAuthorize("hasRole('DELEGADO')")
public ResponseEntity<?> agregarJugadorAPlantel(...) { }

// Cronograma público: sin auth (poner en permitAll del SecurityConfig)
// GET /api/supercopa/cronograma
```

### MS3 · Partido en Tiempo Real

```java
// Solo el árbitro asignado opera durante el partido
@PreAuthorize("hasRole('ARBITRO')")
public ResponseEntity<?> registrarEvento(...) { }

@PreAuthorize("hasRole('ARBITRO')")
public ResponseEntity<?> verificarElegibilidad(...) { }

@PreAuthorize("hasRole('ARBITRO')")
public ResponseEntity<?> cerrarPartido(...) { }

// El admin puede habilitar jugadores también
@PreAuthorize("hasAnyRole('ARBITRO', 'ADMINISTRADOR')")
public ResponseEntity<?> habilitarJugadorEnCampo(...) { }

// Eventos en tiempo real: público (permitAll)
// GET /api/partidos/{id}/eventos
// GET /api/partidos/{id}/marcador
```

### MS4 · Finanzas y Multas

```java
// Delegado sube comprobantes de su equipo
@PreAuthorize("hasRole('DELEGADO')")
public ResponseEntity<?> subirComprobante(...) { }

// Admin aprueba o rechaza
@PreAuthorize("hasRole('ADMINISTRADOR')")
public ResponseEntity<?> aprobarRecaudo(...) { }

// El sistema genera la multa automáticamente desde MS3
// (endpoint interno con SCOPE_internal, llamado por MS3)
@PreAuthorize("hasAuthority('SCOPE_internal')")
public ResponseEntity<?> generarMultaAutomatica(...) { }
```

### MS5 · Analytics y Reportes

```java
// Todas las vistas de consulta son públicas (poner en permitAll)
// GET /api/analytics/posiciones
// GET /api/analytics/goleadores
// GET /api/analytics/resultados
// GET /api/analytics/salon-de-la-fama

// Solo el admin genera el reporte semestral
@PreAuthorize("hasRole('ADMINISTRADOR')")
public ResponseEntity<?> generarReportePdf(...) { }

// Un jugador puede consultar su propio historial
@PreAuthorize("hasRole('JUGADOR')")
public ResponseEntity<?> miHistorial(@AuthenticationPrincipal Jwt jwt) {
    String cedula = jwt.getClaimAsString("cedula"); // su propia cédula
    return ResponseEntity.ok(analyticsService.historialJugador(cedula));
}
```

---

## 6. Comunicación entre microservicios

### Regla general: eventos enriquecidos (sin llamadas extra)

Cuando MS3 registra una tarjeta, en vez de que MS4 y MS6 llamen a MS1
para buscar datos del jugador o del delegado, MS3 publica el evento
con **todos los datos necesarios** ya incluidos:

```json
{
  "evento_id": "uuid-v4",
  "tipo": "TARJETA_REGISTRADA",
  "partido_id": "abc123",
  "cedula_jugador": "1090123456",
  "nombre_jugador": "Angel Vesga",
  "tipo_tarjeta": "ROJA",
  "cedula_delegado": "1090111222",
  "correo_delegado": "delegado@ufps.edu.co",
  "nombre_delegado": "Sebastian Padilla",
  "partidos_suspension": 1,
  "monto_multa_cop": 15000
}
```

MS3 obtiene `correo_delegado` al cargar la alineación (HU19), consultando
`GET /api/jugadores/{cedula}` en MS1, que devuelve `cedulaDelegado`,
`correoDelegado` y `nombreDelegado` (ya están en `JugadorDTO`).

MS4 y MS6 consumen el evento y tienen todo lo que necesitan sin llamadas extra.

### Si un MS necesita llamar a MS1 directamente (caso excepcional)

MS1 puede emitir tokens de servicio con `scope: internal`.
Cada MS los guarda como variable de entorno `MS1_SERVICE_TOKEN` y los envía así:

```
Authorization: Bearer ${MS1_SERVICE_TOKEN}
```

Los endpoints internos de MS1 están protegidos con:
```java
@PreAuthorize("hasAuthority('SCOPE_internal')")
```

Ejemplo de endpoint interno disponible en MS1:
```
GET /api/jugadores/{cedula}/contacto-delegado
→ devuelve: { cedulaDelegado, correoDelegado, nombreDelegado }
```

**Para la escala actual (6 microservicios), priorizar siempre eventos enriquecidos.**
Los tokens de servicio son un fallback para casos donde enriquecer el evento
no sea posible.

---

## 7. MS6 · Notificaciones — caso especial

MS6 es el único que habla con Appwrite, pero **solo para enviar correos**,
no para autenticar. Su configuración es:

```properties
# Igual que los demás MS para validar JWT
codecup.jwt.issuer=https://authcodecup-cykcc.ondigitalocean.app
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${codecup.jwt.issuer}/.well-known/jwks.json
spring.security.oauth2.resourceserver.jwt.issuer-uri=${codecup.jwt.issuer}

# Adicional: credenciales de Appwrite para enviar mensajes
appwrite.endpoint=https://sfo.cloud.appwrite.io/v1
appwrite.project-id=${APPWRITE_PROJECT_ID}
appwrite.api-key=${APPWRITE_API_KEY_MS6}   # API Key distinta a la de MS1, scope: messaging.write

# Pub/Sub (si se usa Google Cloud para el bus de eventos)
google.pubsub.project=${GOOGLE_PUBSUB_PROJECT}
google.pubsub.subscription=ms6-notificaciones
```

MS6 consume eventos del bus (Google Pub/Sub o el mecanismo elegido por el equipo),
extrae el `correo_delegado` o `correo_jugador` del evento enriquecido, y llama
a Appwrite Messaging para enviar el correo. No necesita resolver nada en MS1.

```java
// Ejemplo de llamada a Appwrite Messaging desde MS6
messaging.createEmail(
    ID.unique(),
    "Sanción registrada: " + nombreJugador,
    bodyHtml,
    List.of(),                        // topics
    List.of(appwriteUserIdDelegado),  // targets — viene en el evento enriquecido
    ...
);
```

> **Acuerdo del equipo:** MS3 incluye `appwrite_user_id_delegado` en cada evento
> de tarjeta para que MS6 lo use directamente como target. Cero llamadas extra.

---

## 8. Variables de entorno por microservicio

### MS2, MS3, MS4, MS5

```env
# Puerto (asignar uno distinto por MS)
SERVER_PORT=8082          # MS2
# SERVER_PORT=8083        # MS3
# SERVER_PORT=8084        # MS4
# SERVER_PORT=8085        # MS5

# Auth — apuntar a MS1
CODECUP_JWT_ISSUER=https://authcodecup-cykcc.ondigitalocean.app

# Base de datos — cada MS tiene su propio schema o DB en Supabase
DB_URL=jdbc:postgresql://aws-1-us-east-1.pooler.supabase.com:5432/postgres
DB_USERNAME=postgres.<schema-del-ms>
DB_PASSWORD=<password>

# CORS
FRONTEND_URL=https://codecup.games
```

> **Desarrollo local:** cambiar `CODECUP_JWT_ISSUER=http://localhost:8081`
> si MS1 corre localmente. El JWKS se descargará de ahí.

### MS6

```env
SERVER_PORT=8086

# Auth (igual que los demás)
CODECUP_JWT_ISSUER=https://authcodecup-cykcc.ondigitalocean.app

# Appwrite — API Key separada solo con scope messaging.write
APPWRITE_ENDPOINT=https://sfo.cloud.appwrite.io/v1
APPWRITE_PROJECT_ID=authcodecup
APPWRITE_API_KEY=<api-key-solo-para-ms6>

# Bus de eventos
GOOGLE_PUBSUB_PROJECT=<project-id>
GOOGLE_PUBSUB_SUBSCRIPTION=ms6-notificaciones
```

---

## 9. Cómo probar la integración antes de tener frontend

### Paso 1 — Obtener un JWT real de MS1

Con Postman o curl, hacer login en Appwrite y luego el exchange:

```bash
# 1. Login en Appwrite (obtener JWT corto)
curl -X POST https://sfo.cloud.appwrite.io/v1/account/sessions/email \
  -H "X-Appwrite-Project: authcodecup" \
  -H "Content-Type: application/json" \
  -d '{"email":"tu@correo.com","password":"tupassword"}'
# Guardar la sesión (cookie o sessionId)

# 2. Crear JWT corto de Appwrite
curl -X POST https://sfo.cloud.appwrite.io/v1/account/jwt \
  -H "X-Appwrite-Project: authcodecup" \
  -H "X-Appwrite-Session: <sessionId>"
# Respuesta: { "jwt": "<jwt-corto>" }

# 3. Exchange con MS1 → JWT propio
curl -X POST https://authcodecup-cykcc.ondigitalocean.app/api/auth/exchange \
  -H "Content-Type: application/json" \
  -d '{"appwriteJwt":"<jwt-corto>"}'
# Respuesta: { "token": "<JWT-propio>", "roles": [...], "cedula": "..." }
```

### Paso 2 — Usar el JWT en el MS que estés probando

```bash
curl -X GET http://localhost:8082/api/supercopa/torneos \
  -H "Authorization: Bearer <JWT-propio>"
```

### Paso 3 — Verificar que el rol correcto permite o deniega

```bash
# Con un token de DELEGADO intentando un endpoint de ADMINISTRADOR → debe dar 403
curl -X POST http://localhost:8082/api/supercopa/torneos \
  -H "Authorization: Bearer <JWT-delegado>" \
  -H "Content-Type: application/json" \
  -d '{...}'
# Esperado: 403 Forbidden
```

---

## 10. Checklist de integración por MS

Para dar por integrada la autenticación en un microservicio:

- [ ] `spring-boot-starter-oauth2-resource-server` está en el `pom.xml`
- [ ] Las 3 líneas de `codecup.jwt.*` están en `application.properties`
- [ ] `SecurityConfig.java` copiado y los endpoints públicos del MS definidos en `permitAll()`
- [ ] Al arrancar el MS, los logs muestran que Spring descargó el JWKS de MS1
  (buscar `JwkSetUriJwtDecoderBuilder` o similar en el arranque)
- [ ] `GET /.well-known/jwks.json` en MS1 responde con la clave pública
- [ ] Un request sin token a un endpoint protegido devuelve `401`
- [ ] Un request con token de rol incorrecto devuelve `403`
- [ ] Un request con token válido y rol correcto devuelve `200`
- [ ] `jwt.getClaimAsString("cedula")` devuelve la cédula esperada
- [ ] `jwt.getClaimAsStringList("roles")` devuelve la lista correcta
- [ ] Si el usuario tiene dos roles (`delegado` + `jugador`), ambos endpoints funcionan con el mismo token
