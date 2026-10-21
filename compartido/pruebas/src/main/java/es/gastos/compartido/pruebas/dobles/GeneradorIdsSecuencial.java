package es.gastos.compartido.pruebas.dobles;

import es.gastos.kernel.aplicacion.GeneradorIds;

import java.util.UUID;

/** Generador determinista para tests: 00000000-0000-0000-0000-000000000001, ...0002, ... */
public class GeneradorIdsSecuencial implements GeneradorIds {

	private long siguiente = 1;

	@Override
	public UUID nuevo() {
		return id(this.siguiente++);
	}

	public static UUID id(long numero) {
		return new UUID(0, numero);
	}
}
