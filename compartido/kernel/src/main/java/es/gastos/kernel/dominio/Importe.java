package es.gastos.kernel.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Cantidad de dinero en euros con dos decimales exactos.
 *
 * <p>Sustituye al {@code float} del back antiguo, que acumulaba errores al sumar y por encima de
 * unos 131.000 € ya no representaba todos los céntimos (ver {@code docs/adr/0012}). Se guarda
 * siempre con escala 2 (redondeo HALF_UP), de modo que dos importes iguales son {@code equals}
 * aunque se hayan construido con escalas distintas ({@code 1.5} y {@code 1.50}).
 *
 * <p>El signo tiene significado de negocio: negativo es un gasto (cargo) y positivo un ingreso
 * (abono), tal y como vienen en los extractos bancarios.
 */
public record Importe(BigDecimal valor) implements Comparable<Importe> {

	public static final int ESCALA = 2;

	/** Precisión de la columna {@code DECIMAL(15,2)} en la que se persiste. */
	public static final int PRECISION_MAXIMA = 15;

	public static final Importe CERO = new Importe(BigDecimal.ZERO);

	public Importe {
		Objects.requireNonNull(valor, "El importe es obligatorio");
		valor = valor.setScale(ESCALA, RoundingMode.HALF_UP);

		if (valor.precision() > PRECISION_MAXIMA) {
			throw new ExcepcionDominio("IMPORTE_FUERA_DE_RANGO", "Importe fuera de rango: " + valor.toPlainString());
		}
	}

	public static Importe de(BigDecimal valor) {
		return new Importe(valor);
	}

	/** Lee un importe en formato canónico ({@code -1234.56}); no interpreta formatos bancarios. */
	public static Importe de(String texto) {
		try {
			return new Importe(new BigDecimal(texto.strip()));
		} catch (NumberFormatException | NullPointerException e) {
			throw new ExcepcionDominio("IMPORTE_INVALIDO", "Importe no valido: " + texto);
		}
	}

	public static Importe de(double valor) {
		// BigDecimal.valueOf usa la representación decimal más corta del double (no su valor binario exacto)
		return new Importe(BigDecimal.valueOf(valor));
	}

	public boolean esNegativo() {
		return this.valor.signum() < 0;
	}

	public boolean esPositivo() {
		return this.valor.signum() > 0;
	}

	public boolean esCero() {
		return this.valor.signum() == 0;
	}

	public Importe sumar(Importe otro) {
		return new Importe(this.valor.add(otro.valor));
	}

	public Importe restar(Importe otro) {
		return new Importe(this.valor.subtract(otro.valor));
	}

	public Importe negar() {
		return new Importe(this.valor.negate());
	}

	public Importe abs() {
		return new Importe(this.valor.abs());
	}

	/** Texto canónico sin exponente ({@code "-12.30"}), apto para claves y para mostrar. */
	public String comoTexto() {
		return this.valor.toPlainString();
	}

	@Override
	public int compareTo(Importe otro) {
		return this.valor.compareTo(otro.valor);
	}

	@Override
	public String toString() {
		return this.comoTexto();
	}
}
