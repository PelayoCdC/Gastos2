package es.gastos.consultas.infraestructura.persistencia;

import es.gastos.consultas.aplicacion.proyeccion.Proyeccion;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proyecciones con SQL explícito. Todas las escrituras siguen el mismo patrón:
 * <ol>
 *   <li>{@code INSERT IGNORE} del esqueleto (id y usuario), por si es el primer evento de esa fila;</li>
 *   <li>{@code UPDATE ... WHERE version < :version}: solo cambia si el evento es más nuevo que lo
 *   proyectado. Si no actualiza ninguna fila, el evento era repetido o atrasado y se ignora entero
 *   (también sus efectos de conjunto).</li>
 * </ol>
 */
@Repository
class ProyeccionJdbc implements Proyeccion {

	private final JdbcClient jdbc;

	private final JsonMapper json;

	ProyeccionJdbc(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public void guardarCuenta(UsuarioId usuario, Cuenta cuenta, long version) {
		this.esqueleto("cuenta_vista", cuenta.id(), usuario);
		this.jdbc.sql("""
						UPDATE cuenta_vista SET nombre = :nombre, banco = :banco, descripcion = :descripcion, eliminada = FALSE, version = :version
						WHERE id = :id AND version < :version""")
				.param("id", cuenta.id()).param("nombre", cuenta.nombre()).param("banco", cuenta.banco())
				.param("descripcion", cuenta.descripcion()).param("version", version)
				.update();
	}

	@Override
	public void eliminarCuenta(UsuarioId usuario, UUID cuentaId, long version) {
		// Si tenía movimientos, antes han llegado sus DocumentoEliminado y TransaccionEliminada
		this.lapida("cuenta_vista", "eliminada", cuentaId, usuario, version);
	}

	@Override
	public void registrarDocumento(UsuarioId usuario, Documento documento, long version) {
		this.esqueleto("documento_vista", documento.id(), usuario);
		this.jdbc.sql("""
						UPDATE documento_vista SET cuenta_id = :cuenta, nombre = :nombre, nombre_archivo = :archivo, fecha = :fecha, eliminado = FALSE,
						       version = :version
						WHERE id = :id AND version < :version""")
				.param("id", documento.id()).param("cuenta", documento.cuentaId()).param("nombre", documento.nombre())
				.param("archivo", documento.nombreArchivo()).param("fecha", documento.fecha()).param("version", version)
				.update();
	}

	@Override
	public void renombrarDocumento(UsuarioId usuario, UUID documentoId, String nombre, long version) {
		this.esqueleto("documento_vista", documentoId, usuario);
		this.jdbc.sql("UPDATE documento_vista SET nombre = :nombre, version = :version WHERE id = :id AND version < :version")
				.param("id", documentoId).param("nombre", nombre).param("version", version)
				.update();
	}

	@Override
	public void reasignarDocumento(UsuarioId usuario, UUID documentoId, UUID cuentaId, long version) {
		this.esqueleto("documento_vista", documentoId, usuario);
		int aplicado = this.jdbc.sql("UPDATE documento_vista SET cuenta_id = :cuenta, version = :version WHERE id = :id AND version < :version")
				.param("id", documentoId).param("cuenta", cuentaId).param("version", version)
				.update();
		if (aplicado > 0) {
			this.jdbc.sql("UPDATE transaccion_vista SET cuenta_id = :cuenta WHERE documento_id = :documento")
					.param("cuenta", cuentaId).param("documento", documentoId)
					.update();
		}
	}

	@Override
	public void eliminarDocumento(UsuarioId usuario, UUID documentoId, long version) {
		if (this.lapida("documento_vista", "eliminado", documentoId, usuario, version)) {
			this.jdbc.sql("UPDATE transaccion_vista SET eliminada = TRUE WHERE documento_id = :documento")
					.param("documento", documentoId)
					.update();
		}
	}

	@Override
	public void registrarTransaccion(UsuarioId usuario, Hechos hechos, Registro registro, long version) {
		this.esqueleto("transaccion_vista", hechos.id(), usuario);
		Map<String, Object> parametros = parametros(hechos, version);
		parametros.put("documento", registro.documentoId());
		parametros.put("saldo", registro.saldo());
		parametros.put("orden", registro.orden());
		parametros.put("ordenDocumento", registro.ordenDocumento());
		parametros.put("origen", registro.origen());
		this.jdbc.sql("""
						UPDATE transaccion_vista SET registrada = TRUE, eliminada = FALSE, cuenta_id = :cuenta, documento_id = :documento,
						       fecha_contable = :fechaContable, fecha_valor = :fechaValor, concepto = :concepto,
						       concepto_normalizado = :conceptoNormalizado, clave_concepto = :clave, nota = :nota, importe = :importe,
						       saldo = :saldo, orden = :orden, orden_documento = :ordenDocumento, origen = :origen,
						       version_movimientos = :version
						WHERE id = :id AND version_movimientos < :version""")
				.params(parametros)
				.update();
	}

	@Override
	public void corregirTransaccion(UsuarioId usuario, Hechos hechos, long version) {
		this.esqueleto("transaccion_vista", hechos.id(), usuario);
		this.jdbc.sql("""
						UPDATE transaccion_vista SET cuenta_id = :cuenta, fecha_contable = :fechaContable, fecha_valor = :fechaValor,
						       concepto = :concepto, concepto_normalizado = :conceptoNormalizado, clave_concepto = :clave, nota = :nota,
						       importe = :importe, version_movimientos = :version
						WHERE id = :id AND version_movimientos < :version""")
				.params(parametros(hechos, version))
				.update();
	}

	@Override
	public void eliminarTransaccion(UsuarioId usuario, UUID transaccionId, long version) {
		this.esqueleto("transaccion_vista", transaccionId, usuario);
		this.jdbc.sql("""
						UPDATE transaccion_vista SET eliminada = TRUE, version_movimientos = :version
						WHERE id = :id AND version_movimientos < :version""")
				.param("id", transaccionId).param("version", version)
				.update();
	}

	@Override
	public void clasificarTransaccion(UsuarioId usuario, UUID transaccionId, UUID categoriaId, List<UUID> etiquetaIds, long version) {
		this.esqueleto("transaccion_vista", transaccionId, usuario);
		int aplicado = this.jdbc.sql("""
						UPDATE transaccion_vista SET categoria_id = :categoria, version_clasificacion = :version
						WHERE id = :id AND version_clasificacion < :version""")
				.param("id", transaccionId).param("categoria", categoriaId).param("version", version)
				.update();
		if (aplicado == 0) {
			return;
		}
		this.jdbc.sql("DELETE FROM transaccion_etiqueta_vista WHERE transaccion_id = :id").param("id", transaccionId).update();
		for (int i = 0; i < etiquetaIds.size(); i++) {
			this.jdbc.sql("INSERT INTO transaccion_etiqueta_vista (transaccion_id, etiqueta_id, posicion) VALUES (:id, :etiqueta, :posicion)")
					.param("id", transaccionId).param("etiqueta", etiquetaIds.get(i)).param("posicion", i)
					.update();
		}
	}

	@Override
	public void guardarCategoria(UsuarioId usuario, Categoria categoria, long version) {
		this.esqueleto("categoria_vista", categoria.id(), usuario);
		this.jdbc.sql("""
						UPDATE categoria_vista SET nombre = :nombre, color = :color, descripcion = :descripcion, ocurrencias = :ocurrencias, eliminada = FALSE,
						       prioridad = :prioridad, version = :version
						WHERE id = :id AND version < :version""")
				.param("id", categoria.id()).param("nombre", categoria.nombre()).param("color", categoria.color())
				.param("descripcion", categoria.descripcion()).param("ocurrencias", this.json.writeValueAsString(categoria.ocurrencias()))
				.param("prioridad", categoria.prioridad()).param("version", version)
				.update();
	}

	@Override
	public void eliminarCategoria(UsuarioId usuario, UUID categoriaId, long version) {
		if (this.lapida("categoria_vista", "eliminada", categoriaId, usuario, version)) {
			this.jdbc.sql("UPDATE transaccion_vista SET categoria_id = NULL WHERE categoria_id = :categoria")
					.param("categoria", categoriaId).update();
			this.jdbc.sql("UPDATE etiqueta_vista SET categoria_id = NULL WHERE categoria_id = :categoria")
					.param("categoria", categoriaId).update();
		}
	}

	@Override
	public void guardarEtiqueta(UsuarioId usuario, Etiqueta etiqueta, long version) {
		this.esqueleto("etiqueta_vista", etiqueta.id(), usuario);
		this.jdbc.sql("""
						UPDATE etiqueta_vista SET nombre = :nombre, descripcion = :descripcion, ocurrencias = :ocurrencias, eliminada = FALSE,
						       categoria_id = :categoria, prioridad = :prioridad, version = :version
						WHERE id = :id AND version < :version""")
				.param("id", etiqueta.id()).param("nombre", etiqueta.nombre()).param("descripcion", etiqueta.descripcion())
				.param("ocurrencias", this.json.writeValueAsString(etiqueta.ocurrencias())).param("categoria", etiqueta.categoriaId())
				.param("prioridad", etiqueta.prioridad()).param("version", version)
				.update();
	}

	@Override
	public void eliminarEtiqueta(UsuarioId usuario, UUID etiquetaId, long version) {
		if (this.lapida("etiqueta_vista", "eliminada", etiquetaId, usuario, version)) {
			this.jdbc.sql("DELETE FROM transaccion_etiqueta_vista WHERE etiqueta_id = :etiqueta").param("etiqueta", etiquetaId).update();
		}
	}

	@Override
	public void guardarFormato(UsuarioId usuario, Formato formato, long version) {
		this.esqueleto("formato_vista", formato.id(), usuario);
		this.jdbc.sql("""
						UPDATE formato_vista SET nombre = :nombre, fila_inicio = :fila, columna_concepto = :concepto, columna_importe = :importe,
						       columna_fecha_contable = :fechaContable, columna_fecha_valor = :fechaValor, columna_saldo = :saldo,
						       formato_fecha = :formatoFecha, eliminado = FALSE, version = :version
						WHERE id = :id AND version < :version""")
				.param("id", formato.id()).param("nombre", formato.nombre()).param("fila", formato.filaInicio())
				.param("concepto", formato.columnaConcepto()).param("importe", formato.columnaImporte())
				.param("fechaContable", formato.columnaFechaContable()).param("fechaValor", formato.columnaFechaValor())
				.param("saldo", formato.columnaSaldo()).param("formatoFecha", formato.formatoFecha()).param("version", version)
				.update();
	}

	@Override
	public void eliminarFormato(UsuarioId usuario, UUID formatoId, long version) {
		this.lapida("formato_vista", "eliminado", formatoId, usuario, version);
	}

	@Override
	public void restaurarMovimientos(UsuarioId usuario) {
		this.jdbc.sql("UPDATE transaccion_vista SET registrada = FALSE, eliminada = TRUE, version_movimientos = 0 WHERE usuario_id = :usuario")
				.param("usuario", usuario.valor()).update();
		this.jdbc.sql("UPDATE documento_vista SET eliminado = TRUE, version = 0 WHERE usuario_id = :usuario")
				.param("usuario", usuario.valor()).update();
		this.jdbc.sql("UPDATE cuenta_vista SET eliminada = TRUE, version = 0 WHERE usuario_id = :usuario")
				.param("usuario", usuario.valor()).update();
	}

	@Override
	public void restaurarClasificacion(UsuarioId usuario) {
		this.jdbc.sql("""
						DELETE te FROM transaccion_etiqueta_vista te JOIN transaccion_vista t ON t.id = te.transaccion_id
						WHERE t.usuario_id = :usuario""")
				.param("usuario", usuario.valor()).update();
		this.jdbc.sql("UPDATE transaccion_vista SET categoria_id = NULL, version_clasificacion = 0 WHERE usuario_id = :usuario")
				.param("usuario", usuario.valor()).update();
		this.jdbc.sql("UPDATE etiqueta_vista SET eliminada = TRUE, version = 0 WHERE usuario_id = :usuario")
				.param("usuario", usuario.valor()).update();
		this.jdbc.sql("UPDATE categoria_vista SET eliminada = TRUE, version = 0 WHERE usuario_id = :usuario")
				.param("usuario", usuario.valor()).update();
	}

	@Override
	public void restaurarFormatos(UsuarioId usuario) {
		this.jdbc.sql("UPDATE formato_vista SET eliminado = TRUE, version = 0 WHERE usuario_id = :usuario")
				.param("usuario", usuario.valor()).update();
	}

	private void esqueleto(String tabla, UUID id, UsuarioId usuario) {
		this.jdbc.sql("INSERT IGNORE INTO " + tabla + " (id, usuario_id) VALUES (:id, :usuario)")
				.param("id", id).param("usuario", usuario.valor())
				.update();
	}

	/** Deja la lápida si el evento es nuevo; devuelve si se aplicó (para sus efectos de conjunto). */
	private boolean lapida(String tabla, String columna, UUID id, UsuarioId usuario, long version) {
		this.esqueleto(tabla, id, usuario);
		return this.jdbc.sql("UPDATE " + tabla + " SET " + columna + " = TRUE, version = :version WHERE id = :id AND version < :version")
				.param("id", id).param("version", version)
				.update() > 0;
	}

	private static Map<String, Object> parametros(Hechos hechos, long version) {
		Map<String, Object> parametros = new HashMap<>();
		parametros.put("id", hechos.id());
		parametros.put("cuenta", hechos.cuentaId());
		parametros.put("fechaContable", hechos.fechaContable());
		parametros.put("fechaValor", hechos.fechaValor());
		parametros.put("concepto", hechos.concepto());
		parametros.put("conceptoNormalizado", hechos.conceptoNormalizado());
		parametros.put("clave", hechos.claveConcepto());
		parametros.put("nota", hechos.nota());
		parametros.put("importe", hechos.importe());
		parametros.put("version", version);
		return parametros;
	}
}
