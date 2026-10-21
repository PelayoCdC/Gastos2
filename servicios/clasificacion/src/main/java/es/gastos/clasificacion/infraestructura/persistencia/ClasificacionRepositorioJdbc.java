package es.gastos.clasificacion.infraestructura.persistencia;

import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionRepositorio;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Persistencia de las clasificaciones de transacciones. */
@Repository
class ClasificacionRepositorioJdbc implements ClasificacionRepositorio {

	private final JdbcClient jdbc;

	ClasificacionRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public int desasignarCategoria(CategoriaId categoria) {
		return this.jdbc.sql("""
						UPDATE clasificacion_transaccion SET categoria_id = NULL, origen_categoria = NULL, version = version + 1
						WHERE categoria_id = :categoria""")
				.param("categoria", categoria.valor())
				.update();
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
}
