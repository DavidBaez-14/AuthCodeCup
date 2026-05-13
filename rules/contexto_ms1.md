# MS1 · Identidad y Validación — Contexto del Microservicio

> **Proyecto:** CODE-CUP · UFPS · Facultad de Ingeniería de Sistemas
> **Stack:** Spring Boot · AppWrite · PostgreSQL
> **Sprint actual:** Sprint 2 (29 Abr – 20 May 2026)
> **Estado del MS:** ✅ COMPLETO — todas las HU de este microservicio están implementadas

---

## ¿Qué hace este microservicio?

MS1 es la puerta de entrada al sistema. Gestiona la identidad de todos los usuarios: valida que pertenezcan a la facultad, controla los roles, administra el ciclo de vida de las cuentas y expone los endpoints de autenticación que usan los demás microservicios.

**AppWrite** se usa como backend de autenticación (sesiones, OAuth, tokens JWT). La base de datos de miembros de la facultad (CSV oficial) se mantiene en PostgreSQL y se consulta durante el registro y la aprobación.

---

## Actores del sistema

| Actor | Descripción |
|-------|-------------|
| `Admin` | Administrador general del torneo. Aprueba cuentas, carga el CSV oficial. |
| `Árbitro` | Registra eventos de partido. Solo puede operar si tiene cuenta aprobada. |
| `Delegado` | Representa a un equipo. Puede tener múltiples equipos, pero solo uno activo por edición del torneo. |
| `Jugador` | Miembro de la facultad inscrito en un equipo. Edita su perfil y solicita unirse a equipos. |
| `Público` | Visitante sin cuenta. Solo accede a vistas públicas (no gestionadas por este MS). |

---

## Historias de Usuario

### ✅ Completadas

| HU | Descripción | Pts | Horas |
|----|-------------|-----|-------|
| HU01 | Carga masiva de jugadores desde CSV oficial de la facultad | 5 | 12 h |
| HU02 | Consulta automática de datos por cédula al momento del registro | 3 | 6 h |
| HU03 `[MOD]` | Aprobar y rechazar solicitudes de acceso al sistema | 5 | 10 h |
| HU04 `[MOD]` | Autenticación por rol: login con correo + Google OAuth | 5 | 12 h |
| HU40 `[NUEVA]` | Auto-registro público y solicitud de rol en la plataforma | 5 | 12 h |
| HU41 `[NUEVA]` | Jugador edita su perfil personal (nombre, foto, datos de contacto) | 2 | 4 h |

**Total MS1:** 6 HU · 25 pts · 56 h — **todo completado**

---

## Flujos implementados

### Registro y aprobación
1. Un usuario nuevo llega a la pantalla de auto-registro (`HU40`).
2. El sistema consulta la cédula contra el CSV cargado por el Admin (`HU01`, `HU02`). Si existe, prellenan los campos.
3. El usuario elige el rol que solicita (Jugador, Delegado, Árbitro).
4. La solicitud queda en estado `PENDIENTE` hasta que el Admin la aprueba o rechaza (`HU03`).
5. Al aprobar, AppWrite crea la sesión y asigna el rol correspondiente.

### Autenticación
- Login con correo institucional o Google OAuth (`HU04`).
- Los tokens JWT generados por AppWrite son validados por los demás microservicios (MS2, MS3, MS4, MS5).
- Cada token incluye el `rol` y el `userId` como claims.

### Perfil de jugador
- El jugador puede editar nombre, foto de perfil y datos de contacto (`HU41`).
- El correo y la cédula no son editables (datos oficiales del CSV).

---

## Reglas de negocio críticas

- Un usuario solo puede tener **un rol activo** a la vez. El cambio de rol requiere aprobación del Admin.
- Un **delegado puede registrar múltiples equipos**, pero solo uno puede estar activo por edición del torneo.
- El CSV oficial de la facultad es la **fuente de verdad** de identidad. No se puede registrar un usuario cuya cédula no esté en el CSV.
- Las cuentas rechazadas pueden volver a solicitar acceso con una justificación.
- Google OAuth solo está disponible para correos del dominio institucional (`@ufps.edu.co`).

---

## Contratos de API relevantes para otros MS

Todos los demás microservicios dependen de este MS para validar identidad. Los endpoints clave que deben estar disponibles:

```
GET  /api/v1/users/{userId}          → datos básicos del usuario (nombre, rol, cédula)
GET  /api/v1/users/{userId}/role     → rol activo del usuario
POST /api/v1/auth/validate-token     → valida JWT y retorna claims
GET  /api/v1/members/cedula/{cedula} → consulta si una cédula está en el CSV oficial
```

---

## Dependencias

| Dirección | MS | Motivo |
|-----------|----|--------|
| MS1 → (ninguno) | — | MS1 no consume otros microservicios |
| MS2 → MS1 | MS2 llama a MS1 para verificar identidad de jugadores y delegados |
| MS3 → MS1 | MS3 llama a MS1 para asociar multas y pagos al userId correcto |
| MS4 → MS1 | MS4 llama a MS1 para construir perfiles de rendimiento |
| MS5 → MS1 | MS5 llama a MS1 para obtener correos a los que notificar |

---

## Infraestructura

- **AppWrite:** Gestión de sesiones, OAuth, roles como atributos de usuario.
- **PostgreSQL:** Tabla `faculty_members` con el CSV oficial. Tabla `access_requests` para solicitudes pendientes.
- **MS5 (Notificaciones):** Cuando una solicitud es aprobada o rechazada, MS1 emite un evento para que MS5 envíe el correo de confirmación al usuario.

---

## Estado para agentes de IA

> ✅ Este microservicio está **completamente implementado**. No hay historias de usuario pendientes.
> Si encuentras código incompleto, es un bug — no una HU faltante.
> Cualquier nueva funcionalidad de identidad debe evaluarse como una HU nueva en el backlog.