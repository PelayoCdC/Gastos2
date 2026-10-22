package es.gastos.consultas.infraestructura.persistencia;

import es.gastos.consultas.aplicacion.proyeccion.EstadoProyeccion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalInt;

@Repository
class EstadoProyeccionJdbc implements EstadoProyeccion {

	/** Todas las tablas que se rellenan con eventos; las de Flyway, outbox e inbox no se tocan. */
	private static final List<String> TABLAS = List.of("transaccion_etiqueta_vista", "transaccion_vista", "documento_vista",
			"cuenta_vista", "etiqueta_vista", "categoria_vista", "proyeccion_posicion");

	private final JdbcClient jdbc;

	private final Clock reloj;

	EstadoProyeccionJdbc(JdbcClient jdbc, Clock reloj) {
		this.jdbc = jdbc;
		this.reloj = reloj;
	}

	@Override
	public OptionalInt version() {
		return this.jdbc.sql("SELECT version FROM proyeccion_estado WHERE id = 1").query(Integer.class).optional()
				.map(OptionalInt::of).orElse(OptionalInt.empty());
	}

	@Override
	public void vaciar() {
		// DELETE y no TRUNCATE: TRUNCATE confirma la transacción en MariaDB y el vaciado debe ir junto
		// con el cambio de versión
		TABLAS.forEach(tabla -> this.jdbc.sql("DELETE FROM " + tabla).update());
	}

	@Override
	public void fijarVersion(int version) {
		this.jdbc.sql("""
						INSERT INTO proyeccion_estado (id, version, actualizada_en) VALUES (1, :version, :ahora)
						ON DUPLICATE KEY UPDATE version = VALUES(version), actualizada_en = VALUES(actualizada_en)""")
				.param("version", version).param("ahora", LocalDateTime.now(this.reloj))
				.update();
	}
}
