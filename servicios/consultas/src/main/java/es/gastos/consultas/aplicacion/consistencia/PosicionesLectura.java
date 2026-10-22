package es.gastos.consultas.aplicacion.consistencia;

import es.gastos.kernel.dominio.UsuarioId;

import java.util.Map;

/**
 * Puerto de salida: hasta qué secuencia ha proyectado consultas los eventos de cada usuario en cada
 * contexto. Basta con la posición por usuario porque los eventos de un usuario van todos a la misma
 * partición (la clave del mensaje es el usuario, docs/adr/0004) y llegan en orden.
 */
public interface PosicionesLectura {

	/** Avanza la posición; nunca la hace retroceder (un lote reprocesado no la baja). */
	void avanzar(UsuarioId usuario, String contexto, long secuencia);

	Map<String, Long> de(UsuarioId usuario);

	/** Si consultas proyecta los eventos de ese contexto. */
	boolean proyecta(String contexto);
}
