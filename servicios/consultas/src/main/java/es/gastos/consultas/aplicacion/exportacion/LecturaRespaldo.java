package es.gastos.consultas.aplicacion.exportacion;

import es.gastos.contratos.respaldo.v1.RespaldoV1;
import es.gastos.kernel.dominio.UsuarioId;

import java.time.LocalDate;

/**
 * Puerto de salida: todo lo vivo del usuario en formato v1. El catálogo va en orden de prioridad, que
 * es lo que permite que restaurar el respaldo deje las mismas prioridades.
 */
public interface LecturaRespaldo {

	RespaldoV1 de(UsuarioId usuario, LocalDate generadoEn);
}
