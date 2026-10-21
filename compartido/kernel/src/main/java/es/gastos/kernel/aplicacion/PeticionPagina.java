package es.gastos.kernel.aplicacion;

import es.gastos.kernel.dominio.ExcepcionDominio;

/**
 * Página pedida por el cliente (empezando en 0). El tamaño está acotado para que una petición no
 * pueda traerse toda la base de datos de una vez.
 */
public record PeticionPagina(int pagina, int tamano) {

	public static final int TAMANO_POR_DEFECTO = 50;

	public static final int TAMANO_MAXIMO = 500;

	public PeticionPagina {
		if (pagina < 0) {
			throw new ExcepcionDominio("PAGINACION_INVALIDA", "La pagina no puede ser negativa");
		}

		if (tamano < 1 || tamano > TAMANO_MAXIMO) {
			throw new ExcepcionDominio("PAGINACION_INVALIDA", "El tamano de pagina debe estar entre 1 y " + TAMANO_MAXIMO);
		}
	}

	public static PeticionPagina primera() {
		return new PeticionPagina(0, TAMANO_POR_DEFECTO);
	}

	public long desplazamiento() {
		return (long) this.pagina * this.tamano;
	}
}
