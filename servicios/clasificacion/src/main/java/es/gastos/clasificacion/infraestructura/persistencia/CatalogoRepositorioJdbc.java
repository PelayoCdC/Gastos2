package es.gastos.clasificacion.infraestructura.persistencia;

import es.gastos.clasificacion.dominio.catalogo.CatalogoRepositorio;
import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Color;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.catalogo.NombreCatalogo;
import es.gastos.clasificacion.dominio.catalogo.Ocurrencia;
import es.gastos.clasificacion.dominio.catalogo.Ocurrencias;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia del catálogo con SQL explícito (docs/adr/0022). Las ocurrencias van en tablas hijas
 * con su posición; al guardar se reescriben enteras (son pocas y el agregado es la unidad).
 */
@Repository
class CatalogoRepositorioJdbc implements CatalogoRepositorio {

	private static final String ORDEN = " ORDER BY prioridad, nombre_normalizado";

	private record FilaCategoria(UUID id, UUID usuario, String nombre, String color, String descripcion, int prioridad, long version) {
	}

	private record FilaEtiqueta(UUID id, UUID usuario, String nombre, String descripcion, UUID categoria, int prioridad, long version) {
	}

	private final JdbcClient jdbc;

	CatalogoRepositorioJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void guardar(Categoria categoria) {
		if (!categoria.tieneCambios()) {
			return;
		}
		Map<String, Object> parametros = new HashMap<>();
		parametros.put("id", categoria.id().valor());
		parametros.put("usuario", categoria.propietario().valor());
		parametros.put("nombre", categoria.nombre().valor());
		parametros.put("normalizado", categoria.nombre().normalizado());
		parametros.put("color", categoria.color() == null ? null : categoria.color().valor());
		parametros.put("descripcion", categoria.descripcion());
		parametros.put("prioridad", categoria.prioridad());
		parametros.put("version", categoria.version());
		parametros.put("versionCargada", categoria.versionCargada());

		this.escribir(categoria.esNuevo(), "una categoria", categoria.nombre().valor(), "la categoria", categoria.id(),
				"""
						INSERT INTO categoria (id, usuario_id, nombre, nombre_normalizado, color, descripcion, prioridad, version)
						VALUES (:id, :usuario, :nombre, :normalizado, :color, :descripcion, :prioridad, :version)""",
				"""
						UPDATE categoria SET nombre = :nombre, nombre_normalizado = :normalizado, color = :color,
						       descripcion = :descripcion, prioridad = :prioridad, version = :version
						WHERE id = :id AND version = :versionCargada""",
				parametros);
		this.reescribirOcurrencias("categoria_ocurrencia", "categoria_id", categoria.id().valor(), categoria.ocurrencias());
	}

	@Override
	public void guardar(Etiqueta etiqueta) {
		if (!etiqueta.tieneCambios()) {
			return;
		}
		Map<String, Object> parametros = new HashMap<>();
		parametros.put("id", etiqueta.id().valor());
		parametros.put("usuario", etiqueta.propietario().valor());
		parametros.put("nombre", etiqueta.nombre().valor());
		parametros.put("normalizado", etiqueta.nombre().normalizado());
		parametros.put("descripcion", etiqueta.descripcion());
		parametros.put("categoria", etiqueta.categoriaId() == null ? null : etiqueta.categoriaId().valor());
		parametros.put("prioridad", etiqueta.prioridad());
		parametros.put("version", etiqueta.version());
		parametros.put("versionCargada", etiqueta.versionCargada());

		this.escribir(etiqueta.esNuevo(), "una etiqueta", etiqueta.nombre().valor(), "la etiqueta", etiqueta.id(),
				"""
						INSERT INTO etiqueta (id, usuario_id, nombre, nombre_normalizado, descripcion, categoria_id, prioridad, version)
						VALUES (:id, :usuario, :nombre, :normalizado, :descripcion, :categoria, :prioridad, :version)""",
				"""
						UPDATE etiqueta SET nombre = :nombre, nombre_normalizado = :normalizado, descripcion = :descripcion,
						       categoria_id = :categoria, prioridad = :prioridad, version = :version
						WHERE id = :id AND version = :versionCargada""",
				parametros);
		this.reescribirOcurrencias("etiqueta_ocurrencia", "etiqueta_id", etiqueta.id().valor(), etiqueta.ocurrencias());
	}

	private void escribir(boolean nuevo, String tipo, String nombre, String recurso, Object id, String insert, String update,
			Map<String, Object> parametros) {
		try {
			if (nuevo) {
				this.jdbc.sql(insert).params(parametros).update();
				return;
			}
			if (this.jdbc.sql(update).params(parametros).update() == 0) {
				throw Conflicto.modificacionConcurrente(recurso, id);
			}
		} catch (DuplicateKeyException e) {
			throw Conflicto.nombreDuplicado(tipo, nombre);
		}
	}

	private void reescribirOcurrencias(String tabla, String columna, UUID id, Ocurrencias ocurrencias) {
		this.jdbc.sql("DELETE FROM " + tabla + " WHERE " + columna + " = :id").param("id", id).update();
		List<Ocurrencia> lista = ocurrencias.lista();
		for (int i = 0; i < lista.size(); i++) {
			this.jdbc.sql("INSERT INTO " + tabla + " (" + columna + ", posicion, texto) VALUES (:id, :posicion, :texto)")
					.param("id", id).param("posicion", i).param("texto", lista.get(i).texto()).update();
		}
	}

	@Override
	public Optional<Categoria> buscarCategoria(CategoriaId id) {
		return this.categoriasDonde("id = :valor", id.valor()).stream().findFirst();
	}

	@Override
	public Optional<Etiqueta> buscarEtiqueta(EtiquetaId id) {
		return this.etiquetasDonde("id = :valor", id.valor()).stream().findFirst();
	}

	@Override
	public List<Categoria> categorias(UsuarioId usuario) {
		return this.categoriasDonde("usuario_id = :valor", usuario.valor());
	}

	@Override
	public List<Etiqueta> etiquetas(UsuarioId usuario) {
		return this.etiquetasDonde("usuario_id = :valor", usuario.valor());
	}

	@Override
	public List<Etiqueta> etiquetasDeCategoria(CategoriaId categoria) {
		return this.etiquetasDonde("categoria_id = :valor", categoria.valor());
	}

	@Override
	public Optional<Categoria> categoriaPorNombre(UsuarioId usuario, String nombreNormalizado) {
		return this.categorias(usuario).stream().filter(c -> c.nombre().normalizado().equals(nombreNormalizado)).findFirst();
	}

	@Override
	public Optional<Etiqueta> etiquetaPorNombre(UsuarioId usuario, String nombreNormalizado) {
		return this.etiquetas(usuario).stream().filter(e -> e.nombre().normalizado().equals(nombreNormalizado)).findFirst();
	}

	@Override
	public int siguientePrioridadCategoria(UsuarioId usuario) {
		return this.siguientePrioridad("categoria", usuario);
	}

	@Override
	public int siguientePrioridadEtiqueta(UsuarioId usuario) {
		return this.siguientePrioridad("etiqueta", usuario);
	}

	private int siguientePrioridad(String tabla, UsuarioId usuario) {
		return this.jdbc.sql("SELECT COALESCE(MAX(prioridad), 0) + 1 FROM " + tabla + " WHERE usuario_id = :usuario")
				.param("usuario", usuario.valor()).query(Integer.class).single();
	}

	@Override
	public void eliminar(Categoria categoria) {
		this.jdbc.sql("DELETE FROM categoria WHERE id = :id").param("id", categoria.id().valor()).update();
	}

	@Override
	public void eliminar(Etiqueta etiqueta) {
		this.jdbc.sql("DELETE FROM etiqueta WHERE id = :id").param("id", etiqueta.id().valor()).update();
	}

	/** Las ocurrencias se van en cascada; primero las etiquetas, que apuntan a las categorías. */
	@Override
	public void eliminarDelUsuario(UsuarioId usuario) {
		this.jdbc.sql("DELETE FROM etiqueta WHERE usuario_id = :usuario").param("usuario", usuario.valor()).update();
		this.jdbc.sql("DELETE FROM categoria WHERE usuario_id = :usuario").param("usuario", usuario.valor()).update();
	}

	@Override
	public boolean algunoDeOtroUsuario(UsuarioId usuario, Collection<UUID> ids) {
		return !ids.isEmpty() && this.jdbc.sql("""
						SELECT (SELECT COUNT(*) FROM categoria WHERE id IN (:ids) AND usuario_id <> :usuario)
						     + (SELECT COUNT(*) FROM etiqueta WHERE id IN (:ids) AND usuario_id <> :usuario)""")
				.param("ids", ids).param("usuario", usuario.valor()).query(Long.class).single() > 0;
	}

	private List<Categoria> categoriasDonde(String condicion, UUID valor) {
		List<FilaCategoria> filas = this.jdbc.sql("""
						SELECT id, usuario_id, nombre, color, descripcion, prioridad, version FROM categoria WHERE """ + " " + condicion + ORDEN)
				.param("valor", valor)
				.query((rs, n) -> new FilaCategoria(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class),
						rs.getString("nombre"), rs.getString("color"), rs.getString("descripcion"), rs.getInt("prioridad"),
						rs.getLong("version")))
				.list();
		Map<UUID, List<Ocurrencia>> ocurrencias = this.ocurrencias("categoria_ocurrencia", "categoria_id",
				filas.stream().map(FilaCategoria::id).toList());
		return filas.stream().map(f -> Categoria.reconstituir(new CategoriaId(f.id()), new UsuarioId(f.usuario()),
				new NombreCatalogo(f.nombre()), f.color() == null ? null : new Color(f.color()), f.descripcion(),
				new Ocurrencias(ocurrencias.getOrDefault(f.id(), List.of())), f.prioridad(), f.version())).toList();
	}

	private List<Etiqueta> etiquetasDonde(String condicion, UUID valor) {
		List<FilaEtiqueta> filas = this.jdbc.sql("""
						SELECT id, usuario_id, nombre, descripcion, categoria_id, prioridad, version FROM etiqueta WHERE """ + " " + condicion + ORDEN)
				.param("valor", valor)
				.query((rs, n) -> new FilaEtiqueta(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class),
						rs.getString("nombre"), rs.getString("descripcion"), rs.getObject("categoria_id", UUID.class),
						rs.getInt("prioridad"), rs.getLong("version")))
				.list();
		Map<UUID, List<Ocurrencia>> ocurrencias = this.ocurrencias("etiqueta_ocurrencia", "etiqueta_id",
				filas.stream().map(FilaEtiqueta::id).toList());
		return filas.stream().map(f -> Etiqueta.reconstituir(new EtiquetaId(f.id()), new UsuarioId(f.usuario()),
				new NombreCatalogo(f.nombre()), f.descripcion(), new Ocurrencias(ocurrencias.getOrDefault(f.id(), List.of())),
				f.categoria() == null ? null : new CategoriaId(f.categoria()), f.prioridad(), f.version())).toList();
	}

	private Map<UUID, List<Ocurrencia>> ocurrencias(String tabla, String columna, List<UUID> ids) {
		Map<UUID, List<Ocurrencia>> resultado = new HashMap<>();
		if (ids.isEmpty()) {
			return resultado;
		}
		this.jdbc.sql("SELECT " + columna + " AS duenio, texto FROM " + tabla + " WHERE " + columna + " IN (:ids) ORDER BY "
						+ columna + ", posicion")
				.param("ids", ids)
				.query(rs -> {
					resultado.computeIfAbsent(rs.getObject("duenio", UUID.class), k -> new ArrayList<>())
							.add(new Ocurrencia(rs.getString("texto")));
				});
		return resultado;
	}
}
