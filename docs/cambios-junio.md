# MS1 · Changelog Junio 2026

> **Versión:** Pre-release sprint de handover + gestión de cuentas
> **Periodo cubierto:** Cambios pendientes de commit a fecha de hoy (no incluidos en `main`)
> **Documentos relacionados:** [`docs/evolucion_y_decisiones.md`](evolucion_y_decisiones.md) (razones técnicas detrás de cada decisión), [`docs/contexto_ms1.md`](contexto_ms1.md) (qué hace MS1 en general).

---

## TL;DR para el equipo

Tres bloques de trabajo principales:

1. **Módulo de gestión de cuentas para administradores** — el admin ahora puede crear cuentas a mano, asignar/revocar roles a cualquier persona y eliminar cuentas. Necesario para el handover al docente al final del semestre.
2. **Signup más inteligente** — si la cédula del jugador ya está en el padrón oficial, no se le piden datos académicos (los toma el backend automáticamente).
3. **Endurecimiento del flujo de rechazo** — al rechazar una solicitud, ahora se borra la cuenta completa (BD + Appwrite) si no quedan otros roles activos. Esto desbloquea el problema de "cuentas zombi" que impedían reintentar el registro.

Más bugs arreglados, audit logs estructurados y un contrato de notificaciones listo para cuando MS5 esté desplegado.

---

## 1. Funcionalidades nuevas

### 1.1 Módulo de gestión de cuentas (handover de admins)

Antes: el admin solo podía aprobar/rechazar solicitudes existentes. No podía crear cuentas, ni promover a otros usuarios, ni eliminar cuentas. Eso bloqueaba dos casos: árbitros que no saben registrarse, y la entrega del sistema al docente al final del proyecto.

Ahora hay un nuevo controller `AdminCuentasController` (`/api/admin/cuentas/**`) y endpoints adicionales en `AdminRolesController` (`/api/admin/roles/**`):

| Método | Path | Función |
|---|---|---|
| `GET` | `/api/admin/cuentas?q=&rol=&page=&size=` | Listado paginado de cuentas con búsqueda libre y filtro por rol APROBADO |
| `POST` | `/api/admin/cuentas` | Crear cuenta manual. Devuelve **una sola vez** una `passwordTemporal` aleatoria de 16 chars |
| `POST` | `/api/admin/roles/asignar` | Asignar cualquier rol (ADMINISTRADOR, DELEGADO, ARBITRO) sin esperar solicitud previa |
| `POST` | `/api/admin/roles/revocar` | Quitar un rol específico sin borrar la cuenta. Guard: no auto-revocar admin, no revocar al último admin |
| `DELETE` | `/api/admin/roles/cuenta/{cedula}` | Eliminar cuenta completa (BD + Appwrite). Guards: no auto-eliminarse, no eliminar al último admin |

Archivos nuevos:
- `controller/AdminCuentasController.java`
- `service/AdminCuentasService.java`
- `dto/CrearCuentaRequestDTO.java`, `dto/CrearCuentaResponseDTO.java`
- `dto/AsignarRolRequestDTO.java`, `dto/RevocarRolRequestDTO.java`, `dto/EliminarCuentaRequestDTO.java`
- `dto/CuentaAdminDTO.java`, `dto/RolEstadoDTO.java`
- `security/PasswordGenerator.java` (genera passwords aleatorias de 16 chars sin caracteres ambiguos como `I`, `l`, `0`, `O`, `1`)

Archivos modificados:
- `controller/AdminRolesController.java` — sumó endpoints `/asignar`, `/revocar`, `/cuenta/{cedula}`
- `service/AdminRolesService.java` — sumó `asignarRol`, `revocarRol`, `eliminarCuenta`
- `repository/CuentaRolRepository.java` — sumó `countByRolAndEstado` para el guard del "último admin"

### 1.2 Padrón preview público en signup

Endpoint nuevo: `GET /api/auth/padron-preview/{cedula}` (público, sin auth).

Devuelve solo lo mínimo necesario para que el frontend decida si esconder los campos académicos:
```json
{ "enPadron": true, "esEstudiante": true, "nombre": "Christian López" }
```

**No expone** semestre ni código universitario por privacidad. Si la cédula está en padrón, el backend al recibir `solicitarRol` **ignora** los campos académicos que mande el frontend y copia directamente del padrón.

Archivos nuevos:
- `dto/PadronPreviewDTO.java`

Archivos modificados:
- `controller/AuthController.java` — sumó `GET /api/auth/padron-preview/{cedula}`
- `service/AuthService.java` — método `previewPadron(cedula)` + refactor de `solicitarRol` (padrón = fuente única de verdad)
- `config/SecurityConfig.java` — whitelist del endpoint público

### 1.3 Contrato hacia MS5 (Notificaciones)

Nueva interfaz `NotificacionPublisher` con métodos para cada evento que MS5 consumirá cuando esté desplegado:

```java
void notificarAsignacionRol(...)
void notificarRevocacionRol(...)
void notificarRechazoSolicitud(...)
void notificarCreacionCuenta(...)
void notificarEliminacionCuenta(...)
```

Implementación actual: `LoggingNotificacionPublisher` que solo emite `[NOTIF][...]` en los logs. Cuando MS5 esté en producción, basta con sustituir este bean por un `HttpNotificacionPublisher` (HTTP `@Async` hacia el componente DO de MS5) sin tocar el resto del código.

Archivos nuevos:
- `notification/NotificacionPublisher.java`
- `notification/LoggingNotificacionPublisher.java`

> Decisión consciente: la `passwordTemporal` de `notificarCreacionCuenta` **NO** viaja en el evento. Solo se devuelve al admin en la respuesta del endpoint, una sola vez.

---

## 2. Cambios de comportamiento importantes

### 2.1 Rechazo de solicitud = eliminación total

**Antes:** al rechazar una `CuentaRol`, el registro se marcaba como `RECHAZADO` pero seguía en BD. El usuario quedaba bloqueado para reaplicar al mismo rol porque la unique constraint `(cuenta_id, rol)` no permitía duplicados.

**Ahora:** al rechazar, se borra el `CuentaRol`. Si era el único rol vivo de esa cuenta, también se borran la `Cuenta` y el usuario de Appwrite. Trazabilidad vía log `[AUDIT][SOLICITUD_RECHAZADA]` con flag `cuentaEliminada=true/false`.

**Implicación:** ya no se generan filas con estado `RECHAZADO`. El histórico previo se limpió manualmente vía SQL.

Archivo afectado: `service/AdminRolesService.java`.

### 2.2 `solicitarRol` con cédula en padrón ignora datos del cliente

`AuthService.solicitarRol` ahora detecta si la cédula está en el padrón:
- **Sí está** → copia `rolJugador`, `codigoUniversitario`, `semestre` desde el padrón, ignora lo que mande el frontend, estado `APROBADO` inmediato.
- **No está** → comportamiento anterior (campos auto-reportados, estado `PENDIENTE_VALIDACION`, admin valida).

Esto cierra el vector de manipulación donde un usuario podía digitar "PROFESOR" en el formulario aunque el padrón dijera "ESTUDIANTE".

### 2.3 Listado de cuentas filtra estados RECHAZADO

`AdminCuentasService.toDto` ahora hace `.filter(cr -> cr.getEstado() != EstadoRol.RECHAZADO)` antes de mapear los roles al DTO. Los registros RECHAZADO existentes (de antes del cambio §2.1) ya no aparecen en la UI como roles fantasma.

---

## 3. Bugs arreglados

### 3.1 Error 500 `function lower(bytea) does not exist` en `GET /api/admin/cuentas`

Causa: la JPQL en `CuentaRepository.buscar` usaba `(:q IS NULL OR LOWER(c.cedula) LIKE ...)` con `:q` opcional. Cuando llegaba null, Hibernate inyectaba el parámetro sin tipo y Postgres lo interpretaba como `bytea`. `lower(bytea)` no existe → 500.

Fix: `CAST(:q AS string)` en las 3 ramas del OR + reemplazar el literal `'APROBADO'` por el enum tipado.

Archivo: `repository/CuentaRepository.java`.

### 3.2 Rol fantasma en la tabla de Usuarios

Strickland aparecía con chip "ÁRBITRO" en la UI pero el filtro por rol árbitro no lo incluía. Causa: el backend devolvía todos los `CuentaRol` (incluyendo RECHAZADO) sin filtrar.

Fix: ver §2.3.

---

## 4. Plumbing y soporte

### 4.1 `ConflictException` + HTTP 409

Antes el handler global solo mapeaba 400, 401, 403, 404 y 500. Se agregó `ConflictException` para devolver HTTP 409 en casos como "ya tiene ese rol APROBADO", "ese correo/cédula ya está en uso", "no se puede eliminar al único admin".

Archivos nuevos: `exception/ConflictException.java`.
Archivos modificados: `exception/GlobalExceptionHandler.java`.

### 4.2 Audit logs estructurados

Cada acción sensible emite un log con prefijo grepable para auditoría:

| Prefijo | Cuándo |
|---|---|
| `[AUDIT][ROL_ASIGNADO]` | Admin asigna rol |
| `[AUDIT][ROL_REVOCADO]` | Admin revoca rol |
| `[AUDIT][CUENTA_CREADA]` | Admin crea cuenta manualmente |
| `[AUDIT][CUENTA_ELIMINADA]` | Admin elimina cuenta |
| `[AUDIT][SOLICITUD_RECHAZADA]` | Solicitud rechazada (incluye `cuentaEliminada=true/false`) |

### 4.3 Constraint UNIQUE en `cuentas.correo`

Aplicado manualmente en Supabase:
```sql
ALTER TABLE cuentas ADD CONSTRAINT cuentas_correo_unique UNIQUE (correo);
```

Esto cierra una condición de carrera potencial donde dos requests simultáneos podrían crear cuentas con el mismo correo.

### 4.4 Cambios menores

- `config/SwaggerConfig.java` — actualizaciones de presentación en Swagger UI (paths nuevos del módulo de cuentas).
- `config/SecurityConfig.java` — además del whitelist de `padron-preview`, ajustes menores de CORS.

---

## 5. Reorganización de documentación

### 5.1 Mover `rules/` → `docs/`

La carpeta `rules/` se eliminó. Sus dos documentos quedaron consolidados bajo `docs/`:

- `rules/Auth_Integracion_MS2_MS6.md` → `docs/Auth_Integracion_MS2_MS6.md`
- `rules/contexto_ms1.md` → `docs/contexto_ms1.md`

Razón: unificar todo el contenido descriptivo del MS bajo un solo árbol (`docs/`) para que sea fácil de encontrar.

### 5.2 Nuevo `docs/evolucion_y_decisiones.md`

Documento extenso con el POR QUÉ de cada decisión técnica de este sprint. Sirve como referencia para futuros mantenedores que se pregunten "¿por qué se hizo así?". Incluye:

- Decisiones que se evaluaron y se descartaron (ej. cambio de correo del usuario, limpieza de Appwrite huérfanos).
- Bugs históricos con su causa raíz para que no se repitan.
- Lecciones generales (ej. para Postgres + Hibernate: usar `CAST(:param AS string)` cuando se compara con null).
- Próximos cambios sugeridos.

### 5.3 Este archivo (`docs/cambios-junio.md`)

Changelog del sprint para que el equipo entienda qué hay nuevo sin tener que leer todos los commits.

---

## 6. Cómo probar todo esto manualmente

1. **Levantar MS1** desde Spring Boot Dashboard (`AuthCodeCup`).
2. **Login como administrador** (tu cuenta sembrada por `StartupRunner`).
3. **Probar módulo Usuarios** (necesita frontend levantado — ver changelog del Frontend):
   - Ver listado de cuentas.
   - Crear un usuario manual → copiar la `passwordTemporal` que aparece UNA vez.
   - Asignar/revocar rol con motivo obligatorio.
   - Intentar eliminar tu propia cuenta → debe responder 400.
   - Intentar revocarte admin → debe responder 400.
4. **Probar padrón preview** desde Swagger:
   ```
   GET /api/auth/padron-preview/1152789
   → { "enPadron": true, "esEstudiante": true, "nombre": "Christian López" }
   ```
5. **Probar signup smart** desde el frontend con una cédula que esté en padrón. No deben aparecer los campos académicos.

---

## 7. Resumen para el commit

```
feat(ms1): módulo de gestión de cuentas + padrón preview + handover

- Nuevo módulo /api/admin/cuentas para listar, crear, asignar/revocar
  roles y eliminar cuentas. Habilita el handover del sistema al docente.
- Endpoint público /api/auth/padron-preview/{cedula} para signup smart.
- AuthService.solicitarRol ahora copia datos académicos desde el padrón
  cuando la cédula está oficialmente registrada (cierra vector de
  manipulación de rol/semestre desde el cliente).
- Rechazo de solicitud ahora elimina cuenta completa si era el único
  rol vivo, desbloqueando el reintento del usuario.
- Audit logs estructurados [AUDIT][...] y contrato NotificacionPublisher
  listo para integrar con MS5 cuando esté en producción.
- Fix 500 en GET /api/admin/cuentas (CAST(:q AS string) para evitar
  función lower(bytea) inexistente en Postgres).
- Fix UI rol fantasma: filtrar RECHAZADO al construir CuentaAdminDTO.
- Reorganización de docs: rules/ → docs/ + nuevos cambios-junio.md y
  evolucion_y_decisiones.md.
```
