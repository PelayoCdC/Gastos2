package es.gastos.movimientos.infraestructura.persistencia;

import es.gastos.kernel.aplicacion.Pagina;
import es.gastos.kernel.aplicacion.PeticionPagina;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.aplicacion.cuentas.LecturaCuentas;
import es.gastos.movimientos.aplicacion.cuentas.ResumenCuenta;
import es.gastos.movimientos.aplicacion.documentos.LecturaDocumentos;
import es.gastos.movimientos.aplicacion.documentos.ResumenDocumento;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.documento.DocumentoId;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptadores de los puertos de lectura de cuentas y documentos: SQL directo, sin cargar agregados. */
@Repository
class LecturasJdbc implements LecturaCuentas, LecturaDocumentos {

	/**
	 * Saldo = el del último movimiento que trae saldo: fecha contable más reciente y, a igual fecha,
	 * mayor orden (fila del extracto); el id desempata para que el resultado sea determinista.
	 */
	private static final String RESUMEN_CUENTA = """
			SELECT c.id, c.nombre, c.banco, c.descripcion,
			       (SELECT t.saldo FROM transaccion t
			        WHERE t.cuenta_id = c.id AND t.saldo IS NOT NULL
			        ORDER BY t.fecha_contable DESC, t.orden DESC, t.id DESC LIMIT 1) AS saldo,
			       (SELECT COUNT(*) FROM transaccion t WHERE t.cuenta_id = c.id) AS numero
			FROM cuenta c
			WHERE c.usuario_id = :usuario""";

	private static final RowMapper<ResumenCuenta> MAPEO_CUENTA = (rs, n) -> new ResumenCuenta(
			rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("banco"), rs.getString("descripcion"),
			rs.getBigDecimal("saldo"), rs.getLong("numero"));

	private static final String RESUMEN_DOCUMENTO = """
			SELECT d.id, d.nombre, d.nombre_archivo, d.fecha, d.cuenta_id, c.nombre AS cuenta_nombre,
			       (SELECT COUNT(*) FROM transaccion t WHERE t.documento_id = d.id) AS numero
			FROM documento d LEFT JOIN cuenta c ON c.id = d.cuenta_id
			WHERE d.usuario_id = :usuario""";

	private static final RowMapper<ResumenDocumento> MAPEO_DOCUMENTO = (rs, n) -> {
		UUID cuenta = rs.getObject("cuenta_id", UUID.class);
		return new ResumenDocumento(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("nombre_archivo"),
				Columnas.fecha(rs, "fecha"),
				cuenta == null ? null : new ResumenDocumento.CuentaResumida(cuenta, rs.getString("cuenta_nombre")),
				rs.getLong("numero"));
	};

	private final JdbcClient jdbc;

	LecturasJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ResumenCuenta> listar(UsuarioId usuario) {
		return this.jdbc.sql(RESUMEN_CUENTA + " ORDER BY c.nombre_normalizado")
				.param("usuario", usuario.valor())
				.query(MAPEO_CUENTA)
				.list();
	}

	@Override
	public Optional<ResumenCuenta> resumen(UsuarioId usuario, CuentaId cuenta) {
		return this.jdbc.sql(RESUMEN_CUENTA + " AND c.id = :cuenta")
				.param("usuario", usuario.valor())
				.param("cuenta", cuenta.valor())
				.query(MAPEO_CUENTA)
				.optional();
	}

	@Override
	public Pagina<ResumenDocumento> pagina(UsuarioId usuario, CuentaId cuenta, PeticionPagina peticion) {
		String filtro = cuenta == null ? "" : " AND d.cuenta_id = :cuenta";
		JdbcClient.StatementSpec consulta = this.jdbc.sql(RESUMEN_DOCUMENTO + filtro + " ORDER BY d.fecha DESC, d.id DESC LIMIT :limite OFFSET :desplazamiento")
				.param("usuario", usuario.valor())
				.param("limite", peticion.tamano())
				.param("desplazamiento", peticion.desplazamiento());
		JdbcClient.StatementSpec total = this.jdbc.sql("SELECT COUNT(*) FROM documento d WHERE d.usuario_id = :usuario" + filtro)
				.param("usuario", usuario.valor());
		if (cuenta != null) {
			consulta = consulta.param("cuenta", cuenta.valor());
			total = total.param("cuenta", cuenta.valor());
		}

		return Pagina.de(consulta.query(MAPEO_DOCUMENTO).list(), peticion, total.query(Long.class).single());
	}

	@Override
	public Optional<ResumenDocumento> resumen(UsuarioId usuario, DocumentoId documento) {
		return this.jdbc.sql(RESUMEN_DOCUMENTO + " AND d.id = :documento")
				.param("usuario", usuario.valor())
				.param("documento", documento.valor())
				.query(MAPEO_DOCUMENTO)
				.optional();
	}
}
