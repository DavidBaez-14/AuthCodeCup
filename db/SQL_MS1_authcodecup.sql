-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.jugadores (
  id uuid NOT NULL,
  activo boolean NOT NULL,
  cedula character varying NOT NULL UNIQUE,
  codigo_universitario character varying,
  fecha_actualizacion timestamp without time zone NOT NULL,
  nombre character varying NOT NULL,
  rol_jugador character varying NOT NULL CHECK (rol_jugador::text = ANY (ARRAY['ESTUDIANTE'::character varying, 'GRADUADO'::character varying, 'PROFESOR'::character varying, 'ADMINISTRATIVO'::character varying]::text[])),
  semestre integer,
  cedula_delegado character varying,
  correo_delegado character varying,
  nombre_delegado character varying,
  CONSTRAINT jugadores_pkey PRIMARY KEY (id)
);
CREATE TABLE public.cuenta_roles (
  id uuid NOT NULL,
  codigo_universitario character varying,
  estado character varying NOT NULL CHECK (estado::text = ANY (ARRAY['PENDIENTE'::character varying, 'PENDIENTE_VALIDACION'::character varying, 'APROBADO'::character varying, 'RECHAZADO'::character varying]::text[])),
  fecha_resolucion timestamp without time zone,
  fecha_solicitud timestamp without time zone NOT NULL,
  motivo_rechazo character varying,
  motivo_solicitud character varying,
  rol character varying NOT NULL CHECK (rol::text = ANY (ARRAY['ADMINISTRADOR'::character varying, 'ARBITRO'::character varying, 'DELEGADO'::character varying, 'JUGADOR'::character varying]::text[])),
  rol_jugador character varying CHECK (rol_jugador::text = ANY (ARRAY['ESTUDIANTE'::character varying, 'GRADUADO'::character varying, 'PROFESOR'::character varying, 'ADMINISTRATIVO'::character varying]::text[])),
  semestre integer,
  cuenta_id uuid NOT NULL,
  CONSTRAINT cuenta_roles_pkey PRIMARY KEY (id),
  CONSTRAINT fk4jjxgmwggnl000j8p7a4qu4pt FOREIGN KEY (cuenta_id) REFERENCES public.cuentas(id)
);
CREATE TABLE public.cuentas (
  id uuid NOT NULL,
  appwrite_user_id character varying NOT NULL UNIQUE,
  cedula character varying NOT NULL UNIQUE,
  correo character varying UNIQUE,
  fecha_creacion timestamp without time zone NOT NULL,
  nombre character varying,
  CONSTRAINT cuentas_pkey PRIMARY KEY (id)
);