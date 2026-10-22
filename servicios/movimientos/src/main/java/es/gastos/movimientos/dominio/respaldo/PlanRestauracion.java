package es.gastos.movimientos.dominio.respaldo;

import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Importe;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.Cuenta;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.cuenta.NombreCuenta;
import es.gastos.movimientos.dominio.documento.Documento;
import es.gastos.movimientos.dominio.documento.DocumentoId;
import es.gastos.movimientos.dominio.documento.NombreDocumento;
import es.gastos.movimientos.dominio.transaccion.Concepto;
import es.gastos.movimientos.dominio.transaccion.Transaccion;
import es.gastos.movimientos.dominio.transaccion.TransaccionId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traduce los movimientos de un respaldo a agregados válidos, conservando los ids originales, y anota
 * cada corrección en {@code anomalias}. Reglas:
 * <ul>
 *   <li><b>Cuentas con el mismo nombre normalizado</b> (ahora deben ser únicos, docs/adr/0013): se
 *   fusionan en la primera y sus documentos y transacciones pasan a ella.</li>
 *   <li><b>Documentos:</b> la cuenta puede faltar (el antiguo lo permitía); sin fecha, se usa la de hoy;
 *   sin nombre de fichero, el nombre del documento.</li>
 *   <li><b>Transacciones:</b> sin fecha contable o sin importe no se pueden restaurar y se descartan;
 *   una cuenta o un documento que no está en el respaldo se quita.</li>
 *   <li><b>Saldos a 0:</b> el back antiguo guardaba el saldo en un {@code float} y, si el banco no lo
 *   daba, quedaba a 0. Si todas las transacciones de un documento tienen saldo 0, se entiende que el
 *   banco no lo daba y quedan sin saldo; las que no son de ningún documento (altas a mano) nunca tienen
 *   saldo. Un 0 en medio de saldos reales se respeta.</li>
 *   <li>Un id repetido se queda con la primera aparición.</li>
 * </ul>
 */
public final class PlanRestauracion {

	private PlanRestauracion() {
	}

	public record CuentaRespaldo(UUID id, String nombre, String banco, String descripcion) {
	}

	public record DocumentoRespaldo(UUID id, String nombre, String nombreArchivo, LocalDate fecha, UUID cuentaId) {
	}

	public record TransaccionRespaldo(UUID id, String concepto, String nota, BigDecimal importe, BigDecimal saldo, int orden,
			int ordenDocumento, LocalDate fechaContable, LocalDate fechaValor, UUID cuentaId, UUID documentoId) {
	}

	/** Los agregados listos para guardar y lo que se ha corregido por el camino. */
	public record Plan(List<Cuenta> cuentas, List<Documento> documentos, List<Transaccion> transacciones, List<String> anomalias) {
	}

	public static Plan de(UsuarioId usuario, List<CuentaRespaldo> cuentas, List<DocumentoRespaldo> documentos,
			List<TransaccionRespaldo> transacciones, LocalDate hoy) {
		List<String> anomalias = new ArrayList<>();

		// Cuentas: una por nombre normalizado; las repetidas se fusionan en la primera
		Map<UUID, UUID> cuentaFinal = new HashMap<>();
		Map<String, Cuenta> porNombre = new LinkedHashMap<>();
		for (CuentaRespaldo c : cuentas) {
			if (c.id() == null || cuentaFinal.containsKey(c.id())) {
				anomalias.add("Cuenta " + c.id() + " repetida o sin id: se ignora");
				continue;
			}
			NombreCuenta nombre = nombre(c.nombre(), "Cuenta sin nombre", anomalias, "La cuenta " + c.id());
			Cuenta existente = porNombre.get(nombre.normalizado());
			if (existente != null) {
				anomalias.add("La cuenta '" + nombre.valor() + "' repite el nombre de '" + existente.nombre().valor() + "': se fusionan");
				cuentaFinal.put(c.id(), existente.id().valor());
				continue;
			}
			Cuenta cuenta = Cuenta.crear(new CuentaId(c.id()), usuario, nombre, recortar(c.banco(), Cuenta.LONGITUD_BANCO),
					recortar(c.descripcion(), Cuenta.LONGITUD_DESCRIPCION));
			porNombre.put(nombre.normalizado(), cuenta);
			cuentaFinal.put(c.id(), c.id());
		}

		// Documentos
		Map<UUID, Documento> documentosPorId = new LinkedHashMap<>();
		for (DocumentoRespaldo d : documentos) {
			if (d.id() == null || documentosPorId.containsKey(d.id())) {
				anomalias.add("Documento " + d.id() + " repetido o sin id: se ignora");
				continue;
			}
			UUID cuenta = cuenta(d.cuentaId(), cuentaFinal, anomalias, "El documento '" + d.nombre() + "'");
			LocalDate fecha = d.fecha();
			if (fecha == null) {
				anomalias.add("El documento '" + d.nombre() + "' no tiene fecha: se usa la de hoy");
				fecha = hoy;
			}
			String archivo = d.nombreArchivo() == null || d.nombreArchivo().isBlank() ? d.nombre() : d.nombreArchivo();
			NombreDocumento nombre = new NombreDocumento(textoODefecto(d.nombre(), archivo == null ? "Documento" : archivo));
			documentosPorId.put(d.id(), Documento.restaurar(new DocumentoId(d.id()), usuario, cuenta == null ? null : new CuentaId(cuenta),
					nombre, recortar(archivo == null ? nombre.valor() : archivo, NombreDocumento.LONGITUD_MAXIMA), fecha));
		}

		// Transacciones: primero se decide qué documentos traían saldo de verdad
		Set<UUID> documentosConSaldo = new HashSet<>();
		for (TransaccionRespaldo t : transacciones) {
			if (t.documentoId() != null && t.saldo() != null && t.saldo().signum() != 0) {
				documentosConSaldo.add(t.documentoId());
			}
		}
		List<Transaccion> restauradas = new ArrayList<>();
		Set<UUID> vistas = new HashSet<>();
		int sinSaldo = 0;
		for (TransaccionRespaldo t : transacciones) {
			String descripcion = "La transaccion del " + t.fechaContable() + " '" + t.concepto() + "'";
			if (t.id() == null || !vistas.add(t.id())) {
				anomalias.add(descripcion + " tiene el id repetido o vacio: se ignora");
				continue;
			}
			if (t.fechaContable() == null || t.importe() == null) {
				anomalias.add(descripcion + " no tiene fecha contable o importe: se descarta");
				continue;
			}
			UUID cuenta = cuenta(t.cuentaId(), cuentaFinal, anomalias, descripcion);
			UUID documento = t.documentoId();
			if (documento != null && !documentosPorId.containsKey(documento)) {
				anomalias.add(descripcion + " apunta a un documento que no esta en el respaldo: queda sin documento");
				documento = null;
			}
			BigDecimal saldo = t.saldo();
			if (saldo != null && saldo.signum() == 0 && (documento == null || !documentosConSaldo.contains(documento))) {
				saldo = null;
				sinSaldo++;
			}
			try {
				restauradas.add(Transaccion.restaurar(new TransaccionId(t.id()), usuario, cuenta == null ? null : new CuentaId(cuenta),
						documento == null ? null : new DocumentoId(documento), t.fechaContable(), t.fechaValor(),
						new Concepto(recortar(t.concepto(), Concepto.LONGITUD_MAXIMA)), recortar(t.nota(), Transaccion.LONGITUD_NOTA),
						Importe.de(t.importe()), saldo == null ? null : Importe.de(saldo), t.orden(), t.ordenDocumento()));
			} catch (ExcepcionDominio e) {
				anomalias.add(descripcion + " no es valida (" + e.getMessage() + "): se descarta");
			}
		}
		if (sinSaldo > 0) {
			anomalias.add(sinSaldo + " transacciones tenian saldo 0 porque el banco no lo daba (o eran altas a mano): quedan sin saldo");
		}

		return new Plan(List.copyOf(porNombre.values()), List.copyOf(documentosPorId.values()), List.copyOf(restauradas),
				List.copyOf(anomalias));
	}

	private static UUID cuenta(UUID cuentaId, Map<UUID, UUID> cuentaFinal, List<String> anomalias, String descripcion) {
		if (cuentaId == null) {
			return null;
		}
		UUID finalId = cuentaFinal.get(cuentaId);
		if (finalId == null) {
			anomalias.add(descripcion + " apunta a una cuenta que no esta en el respaldo: queda sin cuenta");
		}
		return finalId;
	}

	private static NombreCuenta nombre(String nombre, String porDefecto, List<String> anomalias, String descripcion) {
		if (nombre == null || nombre.isBlank()) {
			anomalias.add(descripcion + " no tiene nombre: se llama '" + porDefecto + "'");
			return new NombreCuenta(porDefecto);
		}
		return new NombreCuenta(recortar(nombre, NombreCuenta.LONGITUD_MAXIMA));
	}

	private static String textoODefecto(String texto, String porDefecto) {
		return recortar(texto == null || texto.isBlank() ? porDefecto : texto, NombreDocumento.LONGITUD_MAXIMA);
	}

	/** Los textos largos se recortan: mejor conservar el principio que perder la fila entera. */
	private static String recortar(String texto, int longitud) {
		if (texto == null) {
			return null;
		}
		String limpio = texto.strip();
		return limpio.length() <= longitud ? limpio : limpio.substring(0, longitud);
	}
}
