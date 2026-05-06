# SPECIFICATION-DRIVEN DEVELOPMENT — MS1: Identidad y Validación
# Sistema: CODE-CUP · UFPS · Facultad de Ingeniería de Sistemas
# Stack: Spring Boot 3.x · Spring Security · Spring Data JPA · 
#        OpenCSV · PostgreSQL (Supabase) · OAuth2 Client (Google)

## ROL
Eres un desarrollador backend senior trabajando en el microservicio MS1
del sistema CODE-CUP. Tu única fuente de verdad son las especificaciones
que se describen en este prompt. No inventes funcionalidades fuera de
ellas. Antes de escribir cualquier código, lee todas las especificaciones
completas.

---

## CONTEXTO DEL SISTEMA

CODE-CUP es un sistema de gestión de torneos de fútbol sala para la
Facultad de Ingeniería de Sistemas de la UFPS. Este microservicio (MS1)
es responsable exclusivamente de:

1. Mantener el registro de personas elegibles para participar en los
   torneos, cargado desde un CSV semestral proporcionado por la facultad.
2. Exponer un endpoint de consulta por cédula para que otros
   microservicios validen jugadores al momento de inscripción.
3. Gestionar las cuentas de los 3 actores autenticados del sistema:
   Administrador, Árbitro y Delegado.
4. Proveer autenticación mediante correo + contraseña y Google OAuth2.
5. Emitir tokens JWT que los demás microservicios consumirán para
   autorización.

MS1 NO gestiona lógica de torneos, equipos, partidos, finanzas ni
notificaciones. Esas responsabilidades pertenecen a otros microservicios.

---

## CONEXIÓN A BASE DE DATOS

Supabase PostgreSQL — usar exactamente esta configuración en
application.properties (NO hardcodear, usar variables de entorno):

  spring.datasource.url=jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres
  spring.datasource.username=postgres.jcuzocrgyomnuagvnwra
  spring.datasource.password=${DB_PASSWORD}
  spring.datasource.driver-class-name=org.postgresql.Driver
  spring.jpa.hibernate.ddl-auto=update
  spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
  spring.jpa.show-sql=true

La variable de entorno DB_PASSWORD se define localmente. Nunca debe
estar en el código fuente ni en archivos commiteados al repositorio.
Agregar application.properties al .gitignore.

---

## DEPENDENCIAS REQUERIDAS (pom.xml)

Incluir estas dependencias y ninguna más de las necesarias:

- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-security
- spring-boot-starter-oauth2-client
- spring-boot-starter-validation
- spring-security-oauth2-resource-server (con soporte JWT)
- com.opencsv:opencsv:5.9
- io.jsonwebtoken:jjwt-api, jjwt-impl, jjwt-jackson (versión 0.12.x)
- org.postgresql:postgresql
- org.projectlombok:lombok
- springdoc-openapi-starter-webmvc-ui (para Swagger UI)
- spring-boot-starter-test

---

## MODELO DE DOMINIO

### Tabla: jugadores
Registro de todas las personas elegibles para participar en torneos.
Poblada exclusivamente por carga del CSV semestral.

Campos:
- id: UUID, PK generado automáticamente
- cedula: VARCHAR(20), UNIQUE, NOT NULL — identificador universal
- nombre: VARCHAR(150), NOT NULL
- codigo_universitario: VARCHAR(20) — opcional, aplica principalmente
  para ESTUDIANTE y algunos GRADUADOS
- rol_jugador: ENUM('ESTUDIANTE', 'GRADUADO', 'PROFESOR', 'ADMINISTRATIVO')
- semestre: INTEGER — solo aplica si rol_jugador = ESTUDIANTE,
  nulo en todos los demás casos
- activo: BOOLEAN, default true
- fecha_actualizacion: TIMESTAMP — se actualiza en cada carga del CSV

Reglas de negocio:
  - La cédula es el identificador único universal para los 4 tipos.
  - El semestre solo tiene valor cuando rol_jugador = ESTUDIANTE;
    para cualquier otro rol debe guardarse como null aunque venga
    en el CSV.
  - Si en una carga del CSV ya existe la cédula → UPDATE del registro
    existente. Si no existe → INSERT. Nunca crear duplicados.
  - Los 4 tipos son elegibles para participar en torneos sin distinción.

### Tabla: usuarios
Cuentas de acceso al sistema para los actores operativos.
Un usuario NO necesariamente existe en la tabla jugadores
(ejemplo: un árbitro externo no tiene código de la facultad).

Campos:
- id: PK generado automáticamente
- correo: VARCHAR(150), UNIQUE, NOT NULL
- contrasena: VARCHAR(255) — nulo si usa solo Google OAuth
- rol_sistema: ENUM('ADMINISTRADOR', 'ARBITRO', 'DELEGADO')
- nombre: VARCHAR(150), NOT NULL
- cedula: VARCHAR(20) — opcional, referencia lógica a jugadores
- activo: BOOLEAN, default true
- proveedor_auth: ENUM('LOCAL', 'GOOGLE'), default 'LOCAL'
- google_id: VARCHAR(100) — nulo si proveedor_auth = LOCAL
- intentos_fallidos: INTEGER, default 0
- bloqueado_hasta: TIMESTAMP — nulo si la cuenta no está bloqueada
- fecha_creacion: TIMESTAMP
- fecha_ultimo_acceso: TIMESTAMP

Reglas de negocio:
  - El correo es el identificador de login universal.
  - Un ARBITRO puede ser externo: no requiere cédula en jugadores.
  - Para DELEGADO se recomienda correo @ufps.edu.co pero el sistema
    acepta cualquier correo válido.
  - Solo el ADMINISTRADOR puede crear, activar y desactivar cuentas.
  - Máximo 5 intentos de login fallidos antes de bloquear la cuenta
    por 15 minutos.
  - El token JWT expira a los 60 minutos.

---

## ESPECIFICACIONES DE API — ENDPOINTS A IMPLEMENTAR

Implementar exactamente estos endpoints. No crear endpoints adicionales.

### Grupo 1: Carga del CSV (requiere rol ADMINISTRADOR)

POST /api/jugadores/cargar-csv
  - Recibe: MultipartFile con archivo CSV
  - Estructura esperada del CSV (en este orden de columnas):
    nombre, cedula, codigo_universitario, rol_jugador, semestre
  - Comportamiento:
    · Valida que el archivo sea .csv
    · Valida que las columnas requeridas existan:
      nombre, cedula, rol_jugador
    · codigo_universitario y semestre son opcionales en el CSV
    · Para cada fila: si la cédula ya existe → UPDATE,
      si no existe → INSERT
    · Si rol_jugador != ESTUDIANTE → semestre se guarda como null
      aunque venga con valor en el CSV
    · Registra fecha_actualizacion = NOW() en cada registro procesado
    · Un error en una fila no interrumpe el procesamiento del resto;
      se acumula en la lista de errores de la respuesta
  - Respuesta 200:
    {
      "importados": N,
      "actualizados": N,
      "rechazados": N,
      "errores": [ { "fila": N, "cedula": "...", "razon": "..." } ]
    }
  - Respuesta 400: si el archivo no es .csv o falta columna requerida

GET /api/jugadores
  - Parámetros opcionales de query: rol_jugador, activo, page, size
  - Respuesta 200: página de JugadorDTO

### Grupo 2: Consulta por cédula
(requiere rol ADMINISTRADOR o DELEGADO)

GET /api/jugadores/{cedula}
  - Busca un jugador por cédula exacta
  - Respuesta 200: JugadorDTO
    {
      "cedula": "...",
      "nombre": "...",
      "codigo_universitario": "...",
      "rol_jugador": "ESTUDIANTE|GRADUADO|PROFESOR|ADMINISTRATIVO",
      "semestre": null o número entero,
      "activo": true|false
    }
  - Respuesta 404:
    { "mensaje": "Cédula no encontrada en la base de la facultad." }

### Grupo 3: Autenticación

POST /api/auth/login
  - Body: { "correo": "...", "contrasena": "..." }
  - Comportamiento:
    · Busca el usuario por correo en la tabla usuarios
    · Si cuenta inactiva → 403 con mensaje: "Cuenta desactivada."
    · Si bloqueado_hasta > NOW() → 429 con mensaje:
      "Cuenta bloqueada temporalmente. Intenta en X minutos."
    · Si contrasena incorrecta → incrementa intentos_fallidos;
      si llega a 5 → establece bloqueado_hasta = NOW() + 15 minutos
    · Si exitoso → resetea intentos_fallidos = 0,
      actualiza fecha_ultimo_acceso = NOW()
  - Respuesta 200:
    {
      "token": "JWT",
      "tipo": "Bearer",
      "expira_en": 3600,
      "rol": "ADMINISTRADOR|ARBITRO|DELEGADO",
      "nombre": "...",
      "correo": "..."
    }
  - Respuesta 401: { "mensaje": "Credenciales incorrectas." }
  - Respuesta 403: { "mensaje": "Cuenta desactivada." }
  - Respuesta 429: { "mensaje": "Cuenta bloqueada. Intenta en X minutos." }

GET /api/auth/google
  - Inicia el flujo OAuth2 con Google
  - Spring Security maneja la redirección automáticamente

GET /api/auth/google/callback
  - Spring Security maneja el callback de Google
  - Si el correo del token de Google existe en usuarios → emite JWT
    con la misma estructura de respuesta que /api/auth/login
  - Si no existe → 403:
    { "mensaje": "No tienes una cuenta registrada en el sistema." }
  - El sistema NO auto-registra usuarios desde Google; el admin
    debe crear la cuenta primero con ese correo.

POST /api/auth/recuperar-contrasena
  - Body: { "correo": "..." }
  - Comportamiento: registra el evento en log del servidor.
    El envío del correo es responsabilidad de MS6 (Notificaciones);
    MS1 solo expone el evento para que MS6 lo consuma.
  - Respuesta 200 siempre, independientemente de si el correo existe,
    para no revelar información sobre cuentas registradas.

### Grupo 4: Gestión de cuentas (requiere rol ADMINISTRADOR)

POST /api/usuarios
  - Body:
    {
      "correo": "...",
      "nombre": "...",
      "cedula": "...",         (opcional)
      "rol_sistema": "...",
      "proveedor_auth": "LOCAL|GOOGLE"
    }
  - Si proveedor_auth = LOCAL → genera contrasena temporal aleatoria,
    la hashea con BCrypt y la incluye en texto plano solo en esta
    respuesta (única vez que se expone)
  - Respuesta 201: UsuarioDTO (sin contrasena)
  - Validaciones: correo único, rol_sistema válido, correo con formato
    válido

GET /api/usuarios
  - Parámetros opcionales: rol_sistema, activo, page, size
  - Respuesta 200: página de UsuarioDTO

GET /api/usuarios/{id}
  - Respuesta 200: UsuarioDTO
  - Respuesta 404 si no existe

PATCH /api/usuarios/{id}/estado
  - Body: { "activo": true|false }
  - Cambia el estado activo de la cuenta
  - Respuesta 200: UsuarioDTO actualizado

---

## ESTRUCTURA DE PAQUETES

com.codecup.ms1
├── config
│   ├── SecurityConfig.java
│   ├── JwtConfig.java
│   ├── OAuth2Config.java
│   └── SwaggerConfig.java
├── controller
│   ├── AuthController.java
│   ├── JugadorController.java
│   └── UsuarioController.java
├── service
│   ├── AuthService.java
│   ├── CsvService.java
│   ├── JugadorService.java
│   └── UsuarioService.java
├── repository
│   ├── JugadorRepository.java
│   └── UsuarioRepository.java
├── model
│   ├── Jugador.java
│   ├── Usuario.java
│   ├── RolJugador.java         — enum
│   └── RolSistema.java         — enum
├── dto
│   ├── JugadorDTO.java
│   ├── UsuarioDTO.java
│   ├── LoginRequestDTO.java
│   ├── LoginResponseDTO.java
│   └── CsvResultadoDTO.java
├── exception
│   ├── GlobalExceptionHandler.java
│   ├── CedulaNotFoundException.java
│   └── CuentaBloqueadaException.java
└── security
    ├── JwtTokenProvider.java
    ├── JwtAuthenticationFilter.java
    └── CustomUserDetailsService.java

---

## SEGURIDAD Y JWT

- El JWT incluye en el payload: sub (correo), rol, nombre, iat, exp
- La clave secreta para firmar se lee desde variable de entorno
  JWT_SECRET (mínimo 256 bits)
- Endpoints protegidos (requieren token válido):
  /api/jugadores/** y /api/usuarios/**
- Endpoints públicos (sin token):
  /api/auth/**  y  /swagger-ui/** y /v3/api-docs/**
- La autorización por rol se implementa con @PreAuthorize
  en los controllers, no con lógica manual en los servicios

---

## SWAGGER

Configurar springdoc-openapi para que Swagger UI quede disponible en:
  http://localhost:8081/swagger-ui/index.html

Agregar anotaciones @Operation y @Tag en cada controller para que
los endpoints queden documentados con descripción, parámetros y
ejemplos de respuesta en el Swagger UI.

Configurar Swagger para que permita enviar el header
Authorization: Bearer <token> desde la interfaz.

---

## MANEJO DE ERRORES

Todos los errores retornan este formato consistente desde
GlobalExceptionHandler (@ControllerAdvice):

{
  "timestamp": "ISO-8601",
  "status": 400|401|403|404|429|500,
  "error": "descripción corta",
  "mensaje": "mensaje legible para el frontend",
  "path": "/api/..."
}

Nunca exponer stack traces ni mensajes internos de Hibernate
en la respuesta al cliente.

---

## REGLAS DE IMPLEMENTACIÓN

1. Usar Lombok (@Data, @Builder, @RequiredArgsConstructor) en
   entidades y DTOs para reducir boilerplate.
2. Las entidades JPA NO se exponen directamente en los controllers;
   siempre usar DTOs.
3. El mapeo entidad ↔ DTO se hace con métodos estáticos fromEntity()
   en cada DTO. No usar MapStruct.
4. CsvService procesa cada fila con try-catch individual: un error
   en una fila no interrumpe el procesamiento del resto.
5. El upsert en CsvService se resuelve verificando existencia por
   cédula antes de decidir INSERT o UPDATE para cada fila.
6. Las contraseñas se hashean con BCryptPasswordEncoder (strength 12).
7. No implementar lógica de envío de correo en este microservicio.
   MS1 solo registra el evento; el envío es responsabilidad de MS6.
8. Crear un CommandLineRunner que al iniciar:
   a) Verifique la conexión a la base de datos. Si falla, el
      microservicio termina con mensaje de error claro.
   b) Si no existe ningún ADMINISTRADOR en la tabla usuarios,
      cree uno con credenciales leídas desde las variables de
      entorno ADMIN_EMAIL y ADMIN_PASSWORD.

---

## VARIABLES DE ENTORNO REQUERIDAS

DB_PASSWORD=
JWT_SECRET=
ADMIN_EMAIL=
ADMIN_PASSWORD=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
SERVER_PORT=8081

Crear un archivo .env.example en la raíz del proyecto con estas
claves sin valores para documentar lo que se debe configurar.
Este archivo sí se commitea. El .env real nunca se commitea.

---

## ORDEN DE IMPLEMENTACIÓN

Implementar estrictamente en este orden:

1. Configuración del proyecto: application.properties, variables
   de entorno y verificación de conexión a Supabase.
2. Entidades JPA (Jugador, Usuario) y creación de tablas vía
   ddl-auto=update.
3. Repositorios Spring Data JPA.
4. DTOs con sus métodos fromEntity().
5. CsvService + JugadorService + JugadorController.
   → Punto de verificación: probar en Swagger la carga del CSV
     y la consulta por cédula antes de continuar.
6. UsuarioService + UsuarioController.
7. JwtTokenProvider + CustomUserDetailsService.
8. SecurityConfig + JwtAuthenticationFilter.
9. AuthController: login local.
10. OAuth2Config + flujo Google OAuth2.
11. GlobalExceptionHandler con todos los casos de error.
12. CommandLineRunner: verificación de DB + seed del administrador.
13. SwaggerConfig y anotaciones @Operation en todos los controllers.

Antes de pasar al siguiente paso, confirma que el anterior compila
y el endpoint correspondiente responde correctamente en Swagger.
Si algo es ambiguo en la especificación, pregunta antes de asumir.