package terminus.co.edu.ufps.identidad_validacion.ms1.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "cuenta_roles",
    uniqueConstraints = @UniqueConstraint(name = "cuenta_rol_unique", columnNames = {"cuenta_id", "rol"})
)
public class CuentaRol {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private EstadoRol estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_jugador", length = 20)
    private RolJugador rolJugador;

    @Column(name = "codigo_universitario", length = 20)
    private String codigoUniversitario;

    private Integer semestre;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "motivo_solicitud", length = 500)
    private String motivoSolicitud;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;
}
