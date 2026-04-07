package terminus.co.edu.ufps.identidad_validacion.ms1.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    @Column(nullable = false, length = 255)
    private String contrasena;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_sistema", nullable = false, length = 20)
    private RolSistema rolSistema;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 20)
    private String cedula;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "debe_cambiar_contrasena", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean debeCambiarContrasena = false;

    @Builder.Default
    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_ultimo_acceso")
    private LocalDateTime fechaUltimoAcceso;
}

