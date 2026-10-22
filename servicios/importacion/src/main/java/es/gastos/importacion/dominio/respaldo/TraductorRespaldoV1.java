package es.gastos.importacion.dominio.respaldo;

import es.gastos.contratos.respaldo.v1.RespaldoV1;
import es.gastos.kernel.dominio.ExcepcionDominio;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Capa anticorrupción del formato v1: lee los ids y las fechas, que vienen como texto, y deja fuera lo
 * que no se puede restaurar explicándolo en anomalías. Una transacción sin id, fecha contable o importe
 * se descarta aquí, antes de repartir el respaldo, para que movimientos y clasificacion reciban
 * exactamente el mismo conjunto de transacciones.
 */
public final class TraductorRespaldoV1 {

	private TraductorRespaldoV1() {
	}

	public static RespaldoTraducido traducir(RespaldoV1 respaldo) {
		if (respaldo == null || respaldo.version() == null || respaldo.version() != 1) {
			throw new ExcepcionDominio("RESPALDO_NO_VALIDO", "El fichero no es un respaldo de Gastos version 1");
		}
		List<String> anomalias = new ArrayList<>();

		List<RespaldoTraducido.Cuenta> cuentas = new ArrayList<>();
		for (RespaldoV1.Cuenta c : respaldo.cuentas()) {
			UUID id = id(c.id(), "La cuenta '" + c.nombre() + "'", anomalias);
			if (id != null) {
				cuentas.add(new RespaldoTraducido.Cuenta(id, c.nombre(), c.banco(), c.descripcion()));
			}
		}

		List<RespaldoTraducido.Documento> documentos = new ArrayList<>();
		for (RespaldoV1.Documento d : respaldo.documentos()) {
			String descripcion = "El documento '" + d.nombre() + "'";
			UUID id = id(d.id(), descripcion, anomalias);
			if (id != null) {
				documentos.add(new RespaldoTraducido.Documento(id, d.nombre(), d.nombreArchivo(), fecha(d.fecha(), descripcion, anomalias),
						referencia(d.idCuenta(), descripcion, anomalias)));
			}
		}

		List<RespaldoTraducido.Transaccion> transacciones = new ArrayList<>();
		int descartadas = 0;
		for (RespaldoV1.Transaccion t : respaldo.transacciones()) {
			String descripcion = "La transaccion del " + t.fechaContable() + " '" + t.concepto() + "'";
			UUID id = id(t.id(), descripcion, anomalias);
			LocalDate fechaContable = fecha(t.fechaContable(), descripcion, anomalias);
			if (id == null || fechaContable == null || t.importe() == null) {
				descartadas++;
				continue;
			}
			List<UUID> etiquetas = new ArrayList<>();
			for (String etiqueta : t.idsEtiquetas() == null ? List.<String>of() : t.idsEtiquetas()) {
				UUID etiquetaId = referencia(etiqueta, descripcion, anomalias);
				if (etiquetaId != null) {
					etiquetas.add(etiquetaId);
				}
			}
			transacciones.add(new RespaldoTraducido.Transaccion(id, t.concepto(), t.nota(), t.importe(), t.saldo(),
					t.orden() == null ? 0 : t.orden(), t.ordenDocumento() == null ? 0 : t.ordenDocumento(), fechaContable,
					fecha(t.fechaValor(), descripcion, anomalias), referencia(t.idCuenta(), descripcion, anomalias),
					referencia(t.idDocumento(), descripcion, anomalias), referencia(t.idCategoria(), descripcion, anomalias),
					List.copyOf(etiquetas)));
		}
		if (descartadas > 0) {
			anomalias.add(descartadas + " transacciones sin id, fecha contable o importe no se pueden restaurar: se descartan");
		}

		List<RespaldoTraducido.Categoria> categorias = new ArrayList<>();
		for (RespaldoV1.Categoria c : respaldo.categorias()) {
			UUID id = id(c.id(), "La categoria '" + c.nombre() + "'", anomalias);
			if (id != null) {
				categorias.add(new RespaldoTraducido.Categoria(id, c.nombre(), c.color(), c.descripcion(), lista(c.ocurrencias())));
			}
		}

		List<RespaldoTraducido.Etiqueta> etiquetas = new ArrayList<>();
		for (RespaldoV1.Etiqueta e : respaldo.etiquetas()) {
			String descripcion = "La etiqueta '" + e.nombre() + "'";
			UUID id = id(e.id(), descripcion, anomalias);
			if (id != null) {
				etiquetas.add(new RespaldoTraducido.Etiqueta(id, e.nombre(), e.descripcion(), lista(e.ocurrencias()),
						referencia(e.idCategoria(), descripcion, anomalias)));
			}
		}

		return new RespaldoTraducido(List.copyOf(cuentas), List.copyOf(documentos), List.copyOf(transacciones), List.copyOf(categorias),
				List.copyOf(etiquetas), List.copyOf(respaldo.configuracionesImportacion()), List.copyOf(anomalias));
	}

	private static UUID id(String texto, String descripcion, List<String> anomalias) {
		UUID id = uuid(texto);
		if (id == null) {
			anomalias.add(descripcion + " no tiene un id valido (" + texto + "): se descarta");
		}
		return id;
	}

	private static UUID referencia(String texto, String descripcion, List<String> anomalias) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		UUID id = uuid(texto);
		if (id == null) {
			anomalias.add(descripcion + " tiene una referencia no valida (" + texto + "): se quita");
		}
		return id;
	}

	private static UUID uuid(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(texto.strip());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static LocalDate fecha(String texto, String descripcion, List<String> anomalias) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(texto.strip());
		} catch (DateTimeParseException e) {
			anomalias.add(descripcion + " tiene una fecha no valida (" + texto + "): se quita");
			return null;
		}
	}

	private static List<String> lista(List<String> lista) {
		return lista == null ? List.of() : lista;
	}
}
