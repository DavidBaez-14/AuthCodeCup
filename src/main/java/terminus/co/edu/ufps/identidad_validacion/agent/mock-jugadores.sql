CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO public.usuarios (
activo,
bloqueado_hasta,
cedula,
contrasena,
correo,
debe_cambiar_contrasena,
fecha_creacion,
fecha_ultimo_acceso,
intentos_fallidos,
nombre,
rol_sistema
)
VALUES
(
true,
NULL,
'1098765432',
crypt('Delegado123!', gen_salt('bf', 12)),
'delegado.prueba2@ufps.edu.co',
false,
NOW(),
NULL,
0,
'Delegado Prueba 2',
'DELEGADO'
),
(
true,
NULL,
NULL,
crypt('Arbitro123!', gen_salt('bf', 12)),
'arbitro.prueba1@externo.com',
false,
NOW(),
NULL,
0,
'Arbitro Prueba 1',
'ARBITRO'
),
(
true,
NULL,
'9988776655',
crypt('Arbitro123!', gen_salt('bf', 12)),
'arbitro.prueba2@externo.com',
false,
NOW(),
NULL,
0,
'Arbitro Prueba 2',
'ARBITRO'
)
ON CONFLICT (correo) DO UPDATE
SET
activo = EXCLUDED.activo,
bloqueado_hasta = EXCLUDED.bloqueado_hasta,
cedula = EXCLUDED.cedula,
contrasena = EXCLUDED.contrasena,
debe_cambiar_contrasena = EXCLUDED.debe_cambiar_contrasena,
fecha_creacion = EXCLUDED.fecha_creacion,
fecha_ultimo_acceso = EXCLUDED.fecha_ultimo_acceso,
intentos_fallidos = EXCLUDED.intentos_fallidos,
nombre = EXCLUDED.nombre,
rol_sistema = EXCLUDED.rol_sistema;

INSERT INTO public.jugadores (
id,
activo,
cedula,
codigo_universitario,
fecha_actualizacion,
nombre,
rol_jugador,
semestre
)
VALUES
(gen_random_uuid(), true, '2000000001', '11554001', NOW(), 'Juan Perez', 'ESTUDIANTE', 3),
(gen_random_uuid(), true, '2000000002', '11554002', NOW(), 'Laura Gomez', 'ESTUDIANTE', 5),
(gen_random_uuid(), true, '2000000003', '11554003', NOW(), 'Santiago Rojas', 'ESTUDIANTE', 8),
(gen_random_uuid(), true, '2000000004', 'GRA001', NOW(), 'Camila Duarte', 'GRADUADO', NULL),
(gen_random_uuid(), true, '2000000005', NULL, NOW(), 'Andres Vera', 'GRADUADO', NULL),
(gen_random_uuid(), true, '2000000006', NULL, NOW(), 'Carlos Pabon', 'PROFESOR', NULL),
(gen_random_uuid(), true, '2000000007', NULL, NOW(), 'Diana Quintero', 'PROFESOR', NULL),
(gen_random_uuid(), true, '2000000008', NULL, NOW(), 'Martha Cardenas', 'ADMINISTRATIVO', NULL),
(gen_random_uuid(), true, '2000000009', '11554009', NOW(), 'Felipe Torres', 'ESTUDIANTE', 2),
(gen_random_uuid(), true, '2000000010', NULL, NOW(), 'Patricia Suarez', 'ADMINISTRATIVO', NULL)
ON CONFLICT (cedula) DO UPDATE
SET
activo = EXCLUDED.activo,
codigo_universitario = EXCLUDED.codigo_universitario,
fecha_actualizacion = EXCLUDED.fecha_actualizacion,
nombre = EXCLUDED.nombre,
rol_jugador = EXCLUDED.rol_jugador,
semestre = EXCLUDED.semestre;

Credenciales de prueba (si quieres login local):

Delegado: delegado.prueba2@ufps.edu.co / Delegado123!
Arbitro 1: arbitro.prueba1@externo.com / Arbitro123!
Arbitro 2: arbitro.prueba2@externo.com / Arbitro123!