package es.gastos.movimientos.dominio.cuenta;

import es.gastos.kernel.dominio.Normalizador;
import es.gastos.movimientos.dominio.comun.Textos;

/** Nombre de una cuenta: obligatorio, hasta 100 caracteres y único por usuario en su forma normalizada. */
public record NombreCuenta(String valor) {

	public static final int LONGITUD_MAXIMA = 100;

	public NombreCuenta {
		valor = Textos.obligatorio(valor, LONGITUD_MAXIMA, "NOMBRE_INVALIDO", "El nombre de la cuenta");
	}

	/** Forma con la que se compara la unicidad: "Nómina" y "nomina " son el mismo nombre. */
	public String normalizado() {
		return Normalizador.normalizarNombre(this.valor);
	}

	@Override
	public String toString() {
		return this.valor;
	}
}
