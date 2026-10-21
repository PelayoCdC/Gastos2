package es.gastos.importacion.infraestructura.lectura;

import es.gastos.importacion.aplicacion.LectorHojas;
import es.gastos.importacion.dominio.extracto.Celda;
import es.gastos.importacion.dominio.extracto.Hoja;
import es.gastos.kernel.dominio.ExcepcionDominio;
import org.springframework.stereotype.Component;

/**
 * Adaptador del puerto {@link LectorHojas}: decide por la extensión si el fichero es CSV y, si no,
 * lo intenta como Excel. Cualquier fallo de lectura (fichero corrupto, otro formato, zip bomb...) es
 * un error del cliente con el mismo código, no un 500.
 */
@Component
class LectorHojasArchivo implements LectorHojas {

	@Override
	public Hoja leer(String nombreArchivo, byte[] contenido) {
		try {
			if (LectorCsv.esCsv(nombreArchivo)) {
				return new Hoja(LectorCsv.leer(contenido).stream().map(fila -> fila.stream().map(Celda::texto).toList()).toList());
			}
			return new Hoja(LectorExcel.leer(contenido));
		} catch (Exception e) {
			throw new ExcepcionDominio("FICHERO_ILEGIBLE", "No se pudo leer el archivo como Excel o CSV");
		}
	}
}
