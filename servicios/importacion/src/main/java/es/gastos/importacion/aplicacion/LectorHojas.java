package es.gastos.importacion.aplicacion;

import es.gastos.importacion.dominio.extracto.Hoja;

/**
 * Puerto de salida: convierte el fichero que sube el usuario (Excel o CSV) en una {@link Hoja}.
 * Lanza {@code ExcepcionDominio} con código {@code FICHERO_ILEGIBLE} si no se puede leer.
 */
public interface LectorHojas {

	Hoja leer(String nombreArchivo, byte[] contenido);
}
