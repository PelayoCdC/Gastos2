package es.gastos.clasificacion.infraestructura.persistencia;

import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionRepositorio;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionTransaccion;
import es.gastos.clasificacion.dominio.clasificacion.OrigenAsignacion;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionId;
import es.gastos.kernel.dominio.ClaveConcepto;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de las clasificaciones con SQL explícito (docs/adr/0022). Las etiquetas de cada
 * transacción van en {@code clasificacion_etiqueta} con su posición y su origen; al guardar se
 * reescriben enteras. Los borrados del catálogo y de movimientos se aplican por conjuntos.
 */
@Repository
class ClasificacionRepositorioJdbc implements ClasificacionRepositorio {

	private static final String COLUMNAS = """
			id, usuario_id, documento_id, concepto, version_origen, categoria_id, origen_categoria, auto_clasificada, version""";

	private record Fila(UUID id, UUID usuario, UUID documento, String concepto, long versionOrigen, UUID categoria,
			String origenCategoria, boolean autoClasificada, long version) {
	}

	private final JdbcClient jdbc;

	ClasificacionRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void guardar(ClasificacionTransaccion clasificacion) {
		if (!clasificacion.tieneCambios()) {
			return;
		}
		Map<String, Object> parametros = new HashMap<>();
		parametros.put("id", clasificacion.id().valor());
		parametros.put("usuario", clasificacion.propietario().valor());
		parametros.put("documento", clasificacion.documentoId());
		parametros.put("concepto", clasificacion.concepto());
		parametros.put("clave", clasificacion.claveConcepto().valor());
		parametros.put("versionOrigen", clasificacion.versionOrigen());
		parametros.put("categoria", clasificacion.categoria() == null ? null : clasificacion.categoria().valor());
		parametros.put("origenCategoria", clasificacion.origenCategoria() == null ? null : clasificacion.origenCategoria().name());
		parametros.put("auto", clasificacion.autoClasificada());
		parametros.put("version", clasificacion.version());
		parametros.put("versionCargada", clasificacion.versionCargada());

		if (clasificacion.esNuevo()) {
			this.jdbc.sql("""
							INSERT INTO clasificacion_transaccion (id, usuario_id, documento_id, concepto, clave_concepto, version_origen,
							       categoria_id, origen_categoria, auto_clasificada, version)
							VALUES (:id, :usuario, :documento, :concepto, :clave, :versionOrigen, :categoria, :origenCategoria, :auto, :version)""")
					.params(parametros)
					.update();
		} else {
			int actualizadas = this.jdbc.sql("""
							UPDATE clasificacion_transaccion SET documento_id = :documento, concepto = :concepto, clave_concepto = :clave,
							       version_origen = :versionOrigen, categoria_id = :categoria, origen_categoria = :origenCategoria,
							       auto_clasificada = :auto, version = :version
							WHERE id = :id AND version = :versionCargada""")
					.params(parametros)
					.update();
			if (actualizadas == 0) {
				throw Conflicto.modificacionConcurrente("la clasificacion de la transaccion", clasificacion.id());
			}
			this.jdbc.sql("DELETE FROM clasificacion_etiqueta WHERE transaccion_id = :id").param("id", clasificacion.id().valor()).update();
		}

		int posicion = 0;
		for (Map.Entry<EtiquetaId, OrigenAsignacion> asignacion : clasificacion.etiquetas().entrySet()) {
			this.jdbc.sql("INSERT INTO clasificacion_etiqueta (transaccion_id, etiqueta_id, posicion, origen) VALUES (:id, :etiqueta, :posicion, :origen)")
					.param("id", clasificacion.id().valor())
					.param("etiqueta", asignacion.getKey().valor())
					.param("posicion", posicion++)
					.param("origen", asignacion.getValue().name())
					.update();
		}
	}

	@Override
	public Optional<ClasificacionTransaccion> buscar(TransaccionId id) {
		return this.cargar("id = :valor", Map.of("valor", id.valor())).stream().findFirst();
	}

	@Override
	public List<ClasificacionTransaccion> buscarVarias(UsuarioId usuario, Collection<TransaccionId> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return this.cargar("usuario_id = :usuario AND id IN (:ids)",
				Map.of("usuario", usuario.valor(), "ids", ids.stream().map(TransaccionId::valor).toList()));
	}

	@Override
	public List<ClasificacionTransaccion> delUsuario(UsuarioId usuario) {
		return this.cargar("usuario_id = :usuario", Map.of("usuario", usuario.valor()));
	}

	@Override
	public List<ClasificacionTransaccion> similares(UsuarioId usuario, ClaveConcepto clave, TransaccionId excepto) {
		return this.cargar("usuario_id = :usuario AND clave_concepto = :clave AND id <> :excepto",
				Map.of("usuario", usuario.valor(), "clave", clave.valor(), "excepto", excepto.valor()));
	}

	@Override
	public void olvidar(TransaccionId id) {
		this.jdbc.sql("DELETE FROM clasificacion_transaccion WHERE id = :id").param("id", id.valor()).update();
	}

	@Override
	public int olvidarDocumento(UUID documentoId) {
		return this.jdbc.sql("DELETE FROM clasificacion_transaccion WHERE documento_id = :documento").param("documento", documentoId).update();
	}

	@Override
	public int desasignarCategoria(CategoriaId categoria) {
		return this.jdbc.sql("""
						UPDATE clasificacion_transaccion SET categoria_id = NULL, origen_categoria = NULL, version = version + 1
						WHERE categoria_id = :categoria""")
				.param("categoria", categoria.valor())
				.update();
	}

	/** Las etiquetas asignadas se van en cascada. */
	@Override
	public void olvidarDelUsuario(UsuarioId usuario) {
		this.jdbc.sql("DELETE FROM clasificacion_transaccion WHERE usuario_id = :usuario").param("usuario", usuario.valor()).update();
	}

	@Override
	public boolean algunaDeOtroUsuario(UsuarioId usuario, Collection<UUID> ids) {
		return !ids.isEmpty() && this.jdbc.sql("SELECT COUNT(*) FROM clasificacion_transaccion WHERE id IN (:ids) AND usuario_id <> :usuario")
				.param("ids", ids).param("usuario", usuario.valor()).query(Long.class).single() > 0;
	}

	@Override
	public int quitarEtiqueta(EtiquetaId etiqueta) {
		this.jdbc.sql("""
						UPDATE clasificacion_transaccion SET version = version + 1
						WHERE id IN (SELECT transaccion_id FROM clasificacion_etiqueta WHERE etiqueta_id = :etiqueta)""")
				.param("etiqueta", etiqueta.valor())
				.update();
		return this.jdbc.sql("DELETE FROM clasificacion_etiqueta WHERE etiqueta_id = :etiqueta")
				.param("etiqueta", etiqueta.valor())
				.update();
	}

	private List<ClasificacionTransaccion> cargar(String condicion, Map<String, Object> parametros) {
		List<Fila> filas = this.jdbc.sql("SELECT " + COLUMNAS + " FROM clasificacion_transaccion WHERE " + condicion + " ORDER BY id")
				.params(parametros)
				.query((rs, n) -> new Fila(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class),
						rs.getObject("documento_id", UUID.class), rs.getString("concepto"), rs.getLong("version_origen"),
						rs.getObject("categoria_id", UUID.class), rs.getString("origen_categoria"), rs.getBoolean("auto_clasificada"),
						rs.getLong("version")))
				.list();
		if (filas.isEmpty()) {
			return List.of();
		}

		Map<UUID, LinkedHashMap<EtiquetaId, OrigenAsignacion>> etiquetas = new HashMap<>();
		this.jdbc.sql("""
						SELECT ce.transaccion_id, ce.etiqueta_id, ce.origen FROM clasificacion_etiqueta ce
						JOIN clasificacion_transaccion ct ON ct.id = ce.transaccion_id
						WHERE ct.""" + condicion + " ORDER BY ce.transaccion_id, ce.posicion")
				.params(parametros)
				.query(rs -> {
					etiquetas.computeIfAbsent(rs.getObject("transaccion_id", UUID.class), k -> new LinkedHashMap<>())
							.put(new EtiquetaId(rs.getObject("etiqueta_id", UUID.class)), OrigenAsignacion.valueOf(rs.getString("origen")));
				});

		return filas.stream().map(f -> ClasificacionTransaccion.reconstituir(new TransaccionId(f.id()), new UsuarioId(f.usuario()),
				f.documento(), f.concepto(), f.versionOrigen(), f.categoria() == null ? null : new CategoriaId(f.categoria()),
				f.origenCategoria() == null ? null : OrigenAsignacion.valueOf(f.origenCategoria()),
				etiquetas.getOrDefault(f.id(), new LinkedHashMap<>()), f.autoClasificada(), f.version())).toList();
	}
}
