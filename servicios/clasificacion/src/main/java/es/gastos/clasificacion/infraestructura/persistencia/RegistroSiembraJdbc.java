package es.gastos.clasificacion.infraestructura.persistencia;

import es.gastos.clasificacion.aplicacion.siembra.RegistroSiembra;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * {@code INSERT IGNORE} sobre la clave primaria: si dos peticiones del mismo usuario llegan a la
 * vez, InnoDB hace esperar a la segunda hasta que la primera confirma y entonces no inserta nada,
 * así que solo una siembra.
 */
@Repository
class RegistroSiembraJdbc implements RegistroSiembra {

	private final JdbcClient jdbc;

	private final Clock reloj;

	RegistroSiembraJdbc(JdbcClient jdbc, Clock reloj) {
		this.jdbc = jdbc;
		this.reloj = reloj;
	}

	@Override
	public boolean marcar(UsuarioId usuario) {
		return this.jdbc.sql("INSERT IGNORE INTO catalogo_sembrado (usuario_id, sembrado_en) VALUES (:usuario, :ahora)")
				.param("usuario", usuario.valor())
				.param("ahora", LocalDateTime.ofInstant(this.reloj.instant(), ZoneOffset.UTC))
				.update() == 1;
	}
}
