package es.gastos.movimientos.infraestructura.persistencia;

import es.gastos.kernel.dominio.Importe;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;

/** Conversiones comunes entre columnas SQL y tipos del dominio. */
final class Columnas {

	private Columnas() {
	}

	static <T> T id(ResultSet rs, String columna, Function<UUID, T> tipo) throws SQLException {
		UUID valor = rs.getObject(columna, UUID.class);
		return valor == null ? null : tipo.apply(valor);
	}

	static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
		return rs.getObject(columna, LocalDate.class);
	}

	static Importe importe(ResultSet rs, String columna) throws SQLException {
		BigDecimal valor = rs.getBigDecimal(columna);
		return valor == null ? null : Importe.de(valor);
	}

	static BigDecimal valor(Importe importe) {
		return importe == null ? null : importe.valor();
	}

	static <T> UUID uuid(T id, Function<T, UUID> valor) {
		return id == null ? null : valor.apply(id);
	}
}
