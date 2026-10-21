package es.gastos.compartido.pruebas.spikes;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.ListCrudRepository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Aplicación mínima del spike de F0: ¿funciona el tipo {@code UUID} nativo de MariaDB con Spring
 * Data JDBC usando ids asignados por la aplicación y bloqueo optimista con {@code @Version}?
 * Resultado documentado en {@code docs/proceso/bitacora.md} (F0).
 */
@SpringBootApplication
@EnableJdbcRepositories(considerNestedRepositories = true)
public class SpikeAplicacion {

	@Table("fila_spike")
	public record FilaSpike(@Id UUID id, String nombre, BigDecimal importe, @Version Long version) {

		public FilaSpike conNombre(String otroNombre) {
			return new FilaSpike(this.id, otroNombre, this.importe, this.version);
		}
	}

	public interface FilaSpikeRepositorio extends ListCrudRepository<FilaSpike, UUID> {
	}
}
