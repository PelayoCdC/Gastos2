package es.gastos.compartido.pruebas.spikes;

import es.gastos.compartido.pruebas.contenedores.ContenedoresMariaDb;
import es.gastos.compartido.pruebas.spikes.SpikeAplicacion.FilaSpike;
import es.gastos.compartido.pruebas.spikes.SpikeAplicacion.FilaSpikeRepositorio;
import es.gastos.kernel.ids.Uuid7;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = SpikeAplicacion.class, properties = {
		"spring.sql.init.mode=always",
		"spring.sql.init.schema-locations=classpath:spike/esquema.sql"
})
@ImportTestcontainers(ContenedoresMariaDb.class)
class SpikeUuidMariaDbIT {

	@Autowired
	private FilaSpikeRepositorio repositorio;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void guardaYLeeUnUuidNativoConIdAsignadoYVersionNulaComoNuevo() {
		UUID id = Uuid7.generar();

		FilaSpike guardada = this.repositorio.save(new FilaSpike(id, "uno", new BigDecimal("-12.30"), null));

		assertThat(guardada.version()).isZero();
		FilaSpike leida = this.repositorio.findById(id).orElseThrow();
		assertThat(leida.id()).isEqualTo(id);
		assertThat(leida.importe()).isEqualByComparingTo("-12.30");
		assertThat(this.jdbc.sql("SELECT COUNT(*) FROM fila_spike WHERE id = ?").param(id).query(Long.class).single())
				.isEqualTo(1L);
	}

	@Test
	void elBloqueoOptimistaRechazaUnaVersionObsoleta() {
		UUID id = Uuid7.generar();
		FilaSpike v0 = this.repositorio.save(new FilaSpike(id, "uno", BigDecimal.ONE, null));

		FilaSpike v1 = this.repositorio.save(v0.conNombre("dos"));

		assertThat(v1.version()).isEqualTo(1);
		assertThatThrownBy(() -> this.repositorio.save(v0.conNombre("tres")))
				.isInstanceOf(OptimisticLockingFailureException.class);
	}
}
