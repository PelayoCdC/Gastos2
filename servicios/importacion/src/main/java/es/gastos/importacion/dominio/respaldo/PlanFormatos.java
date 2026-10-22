package es.gastos.importacion.dominio.respaldo;

import es.gastos.contratos.respaldo.v1.RespaldoV1;
import es.gastos.importacion.dominio.formato.FormatoBanco;
import es.gastos.importacion.dominio.formato.FormatoFecha;
import es.gastos.importacion.dominio.formato.FormatoId;
import es.gastos.importacion.dominio.formato.MapeoColumnas;
import es.gastos.importacion.dominio.formato.NombreFormato;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Formatos de banco de un respaldo ("configuraciones de importación" en el v1). El back antiguo admitía
 * formatos sin la columna de concepto, importe o fecha (fallaban al importar); ahora se exigen al
 * guardar, así que esos se descartan con su anomalía. Un nombre repetido se queda con el primero; un
 * patrón de fecha no válido pasa a {@code dd/MM/yyyy}.
 */
public final class PlanFormatos {

	private PlanFormatos() {
	}

	public record Plan(List<FormatoBanco> formatos, List<String> anomalias) {
	}

	public static Plan de(UsuarioId usuario, List<RespaldoV1.Configuracion> configuraciones) {
		List<String> anomalias = new ArrayList<>();
		Map<String, FormatoBanco> porNombre = new LinkedHashMap<>();
		Set<UUID> ids = new HashSet<>();
		for (RespaldoV1.Configuracion c : configuraciones) {
			String descripcion = "El formato '" + c.nombre() + "'";
			UUID id = uuid(c.id());
			if (id == null || !ids.add(id)) {
				anomalias.add(descripcion + " no tiene un id valido o esta repetido: se descarta");
				continue;
			}
			try {
				NombreFormato nombre = new NombreFormato(c.nombre() == null || c.nombre().isBlank() ? "Formato " + porNombre.size() : c.nombre());
				if (porNombre.containsKey(nombre.normalizado())) {
					anomalias.add(descripcion + " repite el nombre de otro formato: se descarta");
					continue;
				}
				porNombre.put(nombre.normalizado(), FormatoBanco.crear(new FormatoId(id), usuario, nombre,
						c.filaInicio() == null || c.filaInicio() < 1 ? 1 : c.filaInicio(),
						new MapeoColumnas(c.columnaConcepto(), c.columnaImporte(), c.columnaFechaContable(), c.columnaFechaValor(),
								c.columnaSaldo()), formatoFecha(c.formatoFecha(), descripcion, anomalias)));
			} catch (ExcepcionDominio e) {
				anomalias.add(descripcion + " no es valido (" + e.getMessage() + "): se descarta");
			}
		}
		return new Plan(List.copyOf(porNombre.values()), List.copyOf(anomalias));
	}

	private static FormatoFecha formatoFecha(String patron, String descripcion, List<String> anomalias) {
		try {
			return new FormatoFecha(patron);
		} catch (ExcepcionDominio e) {
			anomalias.add(descripcion + " tiene un formato de fecha no valido (" + patron + "): se usa " + FormatoFecha.POR_DEFECTO);
			return new FormatoFecha(null);
		}
	}

	private static UUID uuid(String texto) {
		try {
			return texto == null ? null : UUID.fromString(texto.strip());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
