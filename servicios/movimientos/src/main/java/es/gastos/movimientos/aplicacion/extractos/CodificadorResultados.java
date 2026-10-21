package es.gastos.movimientos.aplicacion.extractos;

/**
 * Puerto de salida: convierte el resultado de un registro en texto para guardarlo con el documento
 * (y de vuelta). Es un puerto para que la aplicación no dependa de la librería de JSON.
 */
public interface CodificadorResultados {

	String codificar(ResultadoExtracto resultado);

	ResultadoExtracto decodificar(String texto);
}
