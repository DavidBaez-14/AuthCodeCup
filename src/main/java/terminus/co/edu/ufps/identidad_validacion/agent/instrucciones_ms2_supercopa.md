# MS2 Supercopa - Instrucciones de implementacion

## 1) Objetivo
Implementar el microservicio MS2 Supercopa para que un jugador autenticado pueda ver su perfil con:
- partidos jugados
- goles anotados
- tarjetas amarillas, azules y rojas
- titulos o campeonatos obtenidos
- equipos donde ha jugado

## 2) Flujo de autenticacion existente (resumen)
1. Frontend inicia sesion con Appwrite (email/pwd o Google).
2. Frontend genera un JWT corto de Appwrite.
3. Frontend envia ese JWT corto a MS1: POST /api/auth/exchange.
4. MS1 valida la sesion en Appwrite y emite un JWT propio RS256.
5. Frontend guarda el JWT propio y lo envia a TODOS los microservicios en `Authorization: Bearer <token>`.

Reglas duras:
- MS2 NO usa API Key de Appwrite.
- MS2 NO llama a MS1 para autenticar.
- El identificador universal entre servicios es la cedula (claim del JWT propio).

## 3) Contrato del JWT propio
El JWT que emite MS1 incluye al menos:
- sub: appwrite user id
- cedula
- email
- nombre
- roles (labels de Appwrite)

Este JWT se firma con RS256 y se valida usando el JWK Set publico de MS1.

## 4) Pre-requisitos para rol jugador
Para que un jugador pueda entrar a MS2:
- Debe existir un label `jugador` en Appwrite asignado al usuario.
- MS1 debe aceptar `jugador` como rol valido al hacer exchange.
- Debe existir un flujo para crear el perfil del jugador (o seed administrativo) que deje el perfil APROBADO.
- El frontend debe poder reconocer el rol `JUGADOR` para enrutar y proteger vistas.

Si esto no se completa, el exchange de MS1 rechazara el usuario o no tendra roles validos.

## 5) Seguridad en MS2 (Spring Security)
MS2 debe validar el JWT propio via JWK Set de MS1.

Configuracion recomendada:
- Agregar dependencia `spring-boot-starter-oauth2-resource-server`.
- Configurar `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` apuntando a `https://<ms1-host>/.well-known/jwks.json`.

Usar un `JwtAuthenticationConverter` que mapee el claim `roles` a `ROLE_<ROL>` en mayusculas.
Ejemplo de regla:
- rol `jugador` -> `ROLE_JUGADOR`

Proteger el endpoint del perfil con:
- `@PreAuthorize("hasRole('JUGADOR')")`

## 6) Diseno de datos sugerido (MS2)
Tablas minimas recomendadas:

- jugadores
  - cedula (PK)
  - nombre
  - correo (opcional)

- equipos
  - id
  - nombre

- jugador_equipo
  - id
  - cedula
  - equipo_id
  - fecha_inicio
  - fecha_fin (nullable)

- partidos
  - id
  - torneo_id
  - fecha
  - equipo_local_id
  - equipo_visitante_id
  - estado (PROGRAMADO, JUGADO)

- partido_jugador
  - id
  - partido_id
  - cedula
  - equipo_id
  - goles
  - jugo (bool)

- tarjetas
  - id
  - partido_id
  - cedula
  - tipo (AMARILLA, AZUL, ROJA)

- titulos
  - id
  - torneo_id
  - equipo_id
  - puesto (CAMPEON, SUBCAMPEON, TERCERO)
  - fecha

Relacion para titulos del jugador:
- Un jugador obtiene un titulo si estuvo en el equipo en la temporada del torneo.

## 7) Endpoint principal (historia de usuario)
GET /api/supercopa/mi-perfil

- Requiere JWT propio valido.
- Toma la cedula desde el claim `cedula` (no usar path param).

Respuesta ejemplo:
{
  "cedula": "1090000001",
  "nombre": "Juan Perez",
  "equipos": [
    { "id": "E1", "nombre": "Bug Hunters FC", "desde": "2024-01-10", "hasta": null }
  ],
  "resumen": {
    "partidosJugados": 18,
    "goles": 12,
    "tarjetas": { "amarillas": 2, "azules": 1, "rojas": 0 },
    "titulos": 1
  },
  "titulos": [
    { "torneo": "Supercopa 2024", "equipo": "Bug Hunters FC", "puesto": "CAMPEON" }
  ],
  "partidos": [
    {
      "id": "P100",
      "fecha": "2024-05-10",
      "equipo": "Bug Hunters FC",
      "rival": "Runtime Error FC",
      "goles": 1,
      "tarjetas": ["AMARILLA"]
    }
  ]
}

## 8) Logica de negocio (resumen)
- Partidos jugados = contar partidos donde `partido_jugador.jugo = true`.
- Goles = suma de `partido_jugador.goles`.
- Tarjetas = conteo por tipo desde `tarjetas`.
- Equipos = historial en `jugador_equipo`.
- Titulos = unir `titulos` con `jugador_equipo` por equipo y fecha.

## 9) Errores recomendados
- 401 si el JWT es invalido o expirado.
- 403 si el rol no es JUGADOR.
- 404 si la cedula no tiene perfil registrado en MS2 (o devolver perfil vacio con resumen en cero).

## 10) Integracion con frontend
El frontend ya guarda el JWT propio en localStorage y lo envia en Authorization.
MS2 solo debe consumir ese token.

Se recomienda crear un cliente en frontend:
- GET /api/supercopa/mi-perfil con header Authorization.

## 11) Checklist de implementacion
1. Configurar seguridad JWT con JWK de MS1.
2. Definir modelo de datos y migraciones.
3. Implementar repositorios y servicios de agregacion.
4. Crear endpoint /api/supercopa/mi-perfil.
5. Probar con JWT real emitido por MS1.
6. Conectar frontend al nuevo endpoint. 