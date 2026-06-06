# MS1 · Evolución y Decisiones Técnicas

> **Propósito:** Registro consolidado de los cambios, nuevas funcionalidades y decisiones de diseño aplicadas al MS1 (Identidad y Validación) durante la consolidación del módulo de gestión de usuarios y la alineación con el modelo multi-microservicio del proyecto.
>
> Este documento NO reemplaza al `contexto_ms1.md` (que sigue siendo la fuente sobre QUÉ hace el MS); aquí queda registro de POR QUÉ se tomaron ciertas decisiones, para que futuros mantenedores no las reviertan sin entender el contexto.

---

## 1. Nuevo módulo: Gestión de cuentas y handover de admins

### Problema que resolvió

El módulo "Solicitudes pendientes" solo permitía aprobar/rechazar solicitudes de usuarios que ya se habían registrado. Faltaban dos capacidades críticas para que un administrador pudiera operar el sistema en producción y entregárselo a su sucesor (docente):

1. **Crear cuentas para personas que no saben registrarse** (caso del árbitro no técnico).
2. **Promover a otro usuario como administrador y eliminar el admin saliente** (handover).

### Endpoints nuevos

| Método | Path | Función |
|---|---|---|
| `GET` | `/api/admin/cuentas?q=&rol=&page=&size=` | Listado paginado de todas las cuentas con búsqueda libre y filtro por rol APROBADO |
| `POST` | `/api/admin/cuentas` | Crear cuenta manual con `nombre, correo, cedula, rolInicial, motivo`. Devuelve **una sola vez** una `passwordTemporal` aleatoria de 16 chars |
| `POST` | `/api/admin/roles/asignar` | Asignar cualquier rol (ADMINISTRADOR, DELEGADO, ARBITRO) sin esperar solicitud previa. JUGADOR queda fuera (se gestiona por padrón + flujo normal) |
| `POST` | `/api/admin/roles/revocar` | Quitar un rol específico sin borrar la cuenta. Guards: no auto-revocarse admin, no revocar al último admin |
| `DELETE` | `/api/admin/roles/cuenta/{cedula}` | Eliminar cuenta completa (BD + Appwrite). Guards: no auto-eliminarse, no eliminar al último admin |

### Decisión: `asignar` genérico en lugar de `promover-admin` específico

La primera iteración tenía `/api/admin/roles/promover-admin` (solo admins). Se refactorizó a `/asignar` genérico que admite cualquier rol con `motivo` obligatorio. Razón: cubre los tres casos (delegado/árbitro/admin) sin duplicar código, y la UI maneja la confirmación extra para admin a nivel de modal (escribir literal `CONFIRMAR`).

### Decisión: rechazo de solicitud = eliminación total

Al rechazar una `CuentaRol`, ahora se borra el registro Y, si era el único rol vivo de esa cuenta, también se borra la `Cuenta` y el usuario Appwrite. **No se conservan registros "muertos" con estado `RECHAZADO`.**

**Por qué:**
- Antes: el `existsByCuentaIdAndRol(...)` bloqueaba al usuario para volver a solicitar el mismo rol, atrapándolo (la unique constraint `(cuenta_id, rol)` no permitía reaplicar).
- Ahora: si fue rechazado y vuelve a intentar registro, el sistema lo trata como cuenta nueva.
- Trazabilidad: los rechazos quedan en log estructurado `[AUDIT][SOLICITUD_RECHAZADA]` con cédula, rol, motivo y flag `cuentaEliminada`.

**Implicación de schema:** ya no se generan filas con `estado = RECHAZADO`. Histórico previo limpiado vía SQL manual.

---

## 2. Padrón preview público en signup

### Problema

El formulario de signup pedía al usuario digitar `rolJugador`, `codigoUniversitario` y `semestre` aunque la cédula ya estuviera en el padrón con esos datos oficiales. Esto:

- Era redundante (la info ya existe en BD).
- Permitía manipulación: usuario podía digitar `PROFESOR` cuando el padrón dice `ESTUDIANTE`.
- Hacía el formulario innecesariamente largo para usuarios oficiales.

### Solución

**Endpoint nuevo público:**

```
GET /api/auth/padron-preview/{cedula}  →  { enPadron, esEstudiante, nombre }
```

Sin auth (whitelisted en `SecurityConfig`). Devuelve solo lo mínimo necesario para que el frontend decida si esconder los campos académicos. **No expone** código universitario ni semestre por privacidad.

**Cambio en `AuthService.solicitarRol`:** cuando rol = JUGADOR y la cédula está en padrón, **ignora** los campos académicos enviados por el frontend y copia directamente del padrón al `CuentaRol`. Esto garantiza una sola fuente de verdad.

### Decisión clave

El frontend NO es la fuente de verdad de los datos académicos. Aunque el formulario los siga aceptando, el backend los descarta si el padrón ya los tiene. Esto cierra el vector de manipulación silenciosa.

---

## 3. Listado de cuentas: bug del "rol fantasma"

### Síntoma observado

La tabla del módulo Usuarios mostraba a Strickland con chip "ÁRBITRO" pero el filtro por rol árbitro no lo incluía.

### Causa

`AdminCuentasService.toDto` traía TODOS los `CuentaRol` de la cuenta sin filtrar por estado, incluyendo los `RECHAZADO`. El `RoleChips.jsx` no diferenciaba `RECHAZADO`, así que los pintaba con el color del rol normal.

### Fix

Una línea en `AdminCuentasService.toDto`:
```java
.filter(cr -> cr.getEstado() != EstadoRol.RECHAZADO)
```

Los registros `RECHAZADO` se preservan en BD (auditoría) pero no viajan al frontend en este endpoint.

**Nota relacionada:** después del cambio del rechazo (§1) ya no se generan registros `RECHAZADO` nuevos; este filtro queda como salvaguarda para los existentes históricos.

---

## 4. Bug 500 en `GET /api/admin/cuentas` (Postgres + Hibernate)

### Síntoma

Spring logs: `ERROR: function lower(bytea) does not exist` al consultar el endpoint sin filtros.

### Causa

La JPQL en `CuentaRepository.buscar` usaba `(:q IS NULL OR LOWER(c.cedula) LIKE LOWER(CONCAT('%', :q, '%')))`. Cuando `:q` era null, Hibernate inyectaba el parámetro sin tipo declarado al driver JDBC. Postgres por defecto interpretaba el `?` no tipado como `bytea` y `lower(bytea)` no existe.

### Fix

`CAST(:q AS string)` en JPQL → Hibernate emite cast SQL explícito a `varchar`. Postgres ya sabe que el parámetro es texto incluso si es null. Aplicado a las 3 ramas del OR (cedula/nombre/correo).

Aprovechado también para reemplazar el literal `'APROBADO'` por el enum tipado `terminus.co.edu.ufps.identidad_validacion.ms1.model.EstadoRol.APROBADO`.

### Lección para futuras queries

En PostgreSQL con Hibernate 6, cualquier comparación `(:param IS NULL OR ...)` con parámetros string opcionales debe usar `CAST(:param AS string)` para evitar `bytea` ambiguity.

---

## 5. Audit logs estructurados

Cada acción sensible del módulo de cuentas emite un log con prefijo identificable para auditoría futura (cuando exista MS5 o un agregador de logs):

| Prefijo | Cuándo |
|---|---|
| `[AUDIT][ROL_ASIGNADO]` | Admin asigna rol a una cuenta |
| `[AUDIT][ROL_REVOCADO]` | Admin revoca un rol específico |
| `[AUDIT][CUENTA_CREADA]` | Admin crea cuenta manualmente |
| `[AUDIT][CUENTA_ELIMINADA]` | Admin elimina cuenta completa |
| `[AUDIT][SOLICITUD_RECHAZADA]` | Admin rechaza solicitud (incluye `cuentaEliminada=true/false`) |

Estructura uniforme: `caller, cedula, rol/campo, motivo, ...`. Fáciles de grep en logs.

---

## 6. `NotificacionPublisher` (contrato hacia futuro MS5)

Se introdujo una interfaz `NotificacionPublisher` con métodos por cada evento publicable:

- `notificarAsignacionRol`
- `notificarRevocacionRol`
- `notificarRechazoSolicitud` (con flag `cuentaEliminada`)
- `notificarCreacionCuenta` (sin password, por seguridad)
- `notificarEliminacionCuenta`

**Implementación actual:** `LoggingNotificacionPublisher` que emite `[NOTIF][...]` en logs. Cuando MS5 esté desplegado como componente D en DO, se sustituye este bean por un `HttpNotificacionPublisher` que haga HTTP `@Async` hacia MS5 (ver `modelo_despliegue.md` §4.7) sin tocar el resto del código.

**Decisión consciente:** la password temporal de `ACCOUNT_CREATED` **nunca** viaja en la notificación. Solo se devuelve al admin en la respuesta del endpoint, una sola vez.

---

## 7. Constraints de BD agregadas

Cambios aplicados manualmente en Supabase (mantener documentado para que futuros despliegues los repliquen):

- `ALTER TABLE cuentas ADD CONSTRAINT cuentas_correo_unique UNIQUE (correo);`
- *(Pendiente — opcional)* Agregar `ON DELETE CASCADE` a la FK de `cuenta_roles.cuenta_id` para evitar el borrado manual en dos pasos que hace hoy `eliminarCuenta`.

---

## 8. `ConflictException` + handler HTTP 409

Antes el handler global solo mapeaba 400, 401, 403, 404 y 500. Se agregó:

- `terminus.co.edu.ufps.identidad_validacion.ms1.exception.ConflictException`
- Handler en `GlobalExceptionHandler` que devuelve **HTTP 409**.

Se usa para:
- "Ya tiene ese rol APROBADO" (asignar idempotente bloqueado).
- "Ese correo/cédula ya está en uso" (crear cuenta manual con datos duplicados).
- "No se puede eliminar al único administrador del sistema".

---

## 9. Decisiones que se evaluaron y se descartaron (o se difirieron)

### Cambio de correo del usuario (Nice to Have)

Apperitura: Appwrite tiene `account.updateEmail`. El plan diseñado fue:
1. Frontend ejecuta `account.updateEmail` en Appwrite.
2. Frontend llama a `POST /api/auth/sync-email` con su JWT.
3. Backend hace `users.get(appwriteUserId)` (admin API) y trae el email real desde Appwrite (no del body del cliente). Actualiza `cuentas.correo`.

**No implementado en esta iteración**. Razón: no es bloqueante y agrega un endpoint nuevo. Cuando se necesite, el patrón ya está pensado: ir a Appwrite a leer la verdad, nunca confiar en el body.

### Limpieza de cuentas Appwrite huérfanas (signup incompleto)

Si un usuario hace signup en Appwrite y cierra la pestaña antes de completar `solicitarRol`, queda un usuario Appwrite sin `Cuenta` en BD. Se propusieron dos enfoques:

- **Reactivo**: pestaña admin "Appwrite huérfanos" → descartado (agrega complejidad UI).
- **Preventivo**: endpoint `POST /api/auth/abort-signup` que el frontend llama en el catch del signup para borrar el Appwrite user antes de hacer logout.

**No implementado**. Se asume que los huérfanos son raros y un admin puede limpiarlos manualmente vía Appwrite Console.

### Mensaje de login para cuentas "muertas"

Cuando un usuario logueado tiene su último rol revocado, el siguiente intento de `exchange` devuelve `"Cuenta sin roles aprobados"` (mensaje genérico). Se discutió mejorarlo a algo más específico ("Tu solicitud fue rechazada. Vuelve a registrarte si es necesario."). **No urgente** después del cambio del §1 porque las cuentas rechazadas ya no existen.

---

## 10. Próximos cambios sugeridos

- **Bug pendiente del Render warning:** `PageImpl serialization not supported`. Se puede arreglar con `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` en `AuthCodeCupApplication`, pero requiere adaptar el frontend para el nuevo shape del JSON.
- **Implementación real del `NotificacionPublisher`** cuando MS5 esté en producción (DO componente D). El esqueleto del `HttpNotificacionPublisher` ya está pensado en `modelo_despliegue.md` §4.7.
- **Endpoint inter-MS para datos del padrón** (`SCOPE_internal`) cuando MS4 lo necesite sin propagar JWT de usuario.
