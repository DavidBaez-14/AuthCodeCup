package terminus.co.edu.ufps.identidad_validacion.ms1.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingNotificacionPublisher implements NotificacionPublisher {

    @Override
    public void notificarAsignacionRol(String cedula, String correo, String nombre, String rol) {
        log.info("[NOTIF][ROLE_ASSIGNED] cedula={} correo={} nombre={} rol={}",
                cedula, correo, nombre, rol);
    }

    @Override
    public void notificarRevocacionRol(String cedula, String correo, String nombre, String rol, String motivo) {
        log.info("[NOTIF][ROLE_REVOKED] cedula={} correo={} nombre={} rol={} motivo={}",
                cedula, correo, nombre, rol, motivo);
    }

    @Override
    public void notificarRechazoSolicitud(String cedula, String correo, String nombre, String rol, String motivo, boolean cuentaEliminada) {
        log.info("[NOTIF][REQUEST_REJECTED] cedula={} correo={} nombre={} rol={} motivo={} cuentaEliminada={}",
                cedula, correo, nombre, rol, motivo, cuentaEliminada);
    }

    @Override
    public void notificarCreacionCuenta(String cedula, String correo, String nombre, String rolInicial) {
        log.info("[NOTIF][ACCOUNT_CREATED] cedula={} correo={} nombre={} rolInicial={}",
                cedula, correo, nombre, rolInicial);
    }

    @Override
    public void notificarEliminacionCuenta(String cedula, String correo, String nombre, String motivo) {
        log.info("[NOTIF][ACCOUNT_DELETED] cedula={} correo={} nombre={} motivo={}",
                cedula, correo, nombre, motivo);
    }
}
