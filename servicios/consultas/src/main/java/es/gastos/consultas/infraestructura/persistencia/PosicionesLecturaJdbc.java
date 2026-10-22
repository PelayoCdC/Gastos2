package es.gastos.consultas.infraestructura.persistencia;

import es.gastos.compartido.mensajeria.PropiedadesMensajeria;
import es.gastos.consultas.aplicacion.consistencia.PosicionesLectura;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
class PosicionesLecturaJdbc implements PosicionesLectura {

	private final JdbcClient jdbc;

	/** Los contextos cuyos topics consume ({@code gastos.mensajeria.consume}: "movimientos.eventos" → "movimientos"). */
	private final Set<String> contextos;

	PosicionesLecturaJdbc(JdbcClient jdbc, PropiedadesMensajeria propiedades) {
		this.jdbc = jdbc;
		this.contextos = propiedades.consume().stream()
				.map(topic -> topic.substring(0, topic.indexOf('.')))
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public void avanzar(UsuarioId usuario, String contexto, long secuencia) {
		this.jdbc.sql("""
						INSERT INTO proyeccion_posicion (usuario_id, contexto, secuencia) VALUES (:usuario, :contexto, :secuencia)
						ON DUPLICATE KEY UPDATE secuencia = GREATEST(secuencia, VALUES(secuencia))""")
				.param("usuario", usuario.valor()).param("contexto", contexto).param("secuencia", secuencia)
				.update();
	}

	@Override
	public Map<String, Long> de(UsuarioId usuario) {
		Map<String, Long> posiciones = new HashMap<>();
		this.jdbc.sql("SELECT contexto, secuencia FROM proyeccion_posicion WHERE usuario_id = :usuario")
				.param("usuario", usuario.valor())
				.query(rs -> {
					posiciones.put(rs.getString("contexto"), rs.getLong("secuencia"));
				});
		return posiciones;
	}

	@Override
	public boolean proyecta(String contexto) {
		return this.contextos.contains(contexto);
	}
}
