package terminus.co.edu.ufps.identidad_validacion.ms1.notification;

/**
 * Contrato hacia MS5 (Notificaciones). MS5 aún no existe; la implementación
 * actual sólo deja traza en log. Cuando MS5 esté operativo se sustituye este
 * bean por un publisher real (HTTP/cola) sin tocar el resto del código.
 */
public interface NotificacionPublisher {

    void notificarAsignacionRol(String cedula, String correo, String nombre, String rol);

    void notificarRevocacionRol(String cedula, String correo, String nombre, String rol, String motivo);

    void notificarRechazoSolicitud(String cedula, String correo, String nombre, String rol, String motivo, boolean cuentaEliminada);

    void notificarCreacionCuenta(String cedula, String correo, String nombre, String rolInicial);

    void notificarEliminacionCuenta(String cedula, String correo, String nombre, String motivo);
}
