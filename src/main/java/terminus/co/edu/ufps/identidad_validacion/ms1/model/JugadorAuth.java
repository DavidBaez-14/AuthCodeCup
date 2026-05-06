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
@Table(name = "jugadores_auth")
public class JugadorAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "appwrite_user_id", nullable = false, unique = true, length = 36)
    private String appwriteUserId;

    @Column(nullable = false, unique = true, length = 20)
    private String cedula;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_jugador", nullable = false, length = 20)
    private RolJugador rolJugador;

    @Column(name = "codigo_universitario", length = 20)
    private String codigoUniversitario;

    @Column(name = "semestre")
    private Integer semestre;

    @Column(length = 150)
    private String nombre;

    @Column(length = 150)
    private String correo;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;
}
