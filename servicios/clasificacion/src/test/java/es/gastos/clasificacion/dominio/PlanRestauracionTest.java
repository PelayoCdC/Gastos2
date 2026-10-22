package es.gastos.clasificacion.dominio;

import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionTransaccion;
import es.gastos.clasificacion.dominio.clasificacion.OrigenAsignacion;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionClasificada;
import es.gastos.clasificacion.dominio.respaldo.PlanRestauracion;
import es.gastos.clasificacion.dominio.respaldo.PlanRestauracion.CategoriaRespaldo;
import es.gastos.clasificacion.dominio.respaldo.PlanRestauracion.EtiquetaRespaldo;
import es.gastos.clasificacion.dominio.respaldo.PlanRestauracion.TransaccionRespaldo;
import es.gastos.kernel.dominio.UsuarioId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanRestauracionTest {

	private final UsuarioId usuario = new UsuarioId(UUID.randomUUID());

	private final UUID ocio = UUID.randomUUID();

	private final UUID ocioRepetida = UUID.randomUUID();

	private final UUID salud = UUID.randomUUID();

	private final UUID cine = UUID.randomUUID();

	private final UUID cineRepetida = UUID.randomUUID();

	private final UUID farmacia = UUID.randomUUID();

	@Test
	void laPrioridadSaleDeLaPosicionYLosNombresRepetidosSeFusionan() {
		PlanRestauracion.Plan plan = this.plan(List.of());

		assertThat(plan.categorias()).extracting(c -> c.nombre().valor(), Categoria::prioridad)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("Ocio", 10), org.assertj.core.groups.Tuple.tuple("Salud", 20));
		assertThat(plan.categorias().getFirst().ocurrencias().textos()).as("las ocurrencias de la repetida se suman")
				.containsExactly("cine", "teatro");
		assertThat(plan.categorias().get(1).color()).as("color no valido").isNull();
		assertThat(plan.etiquetas()).extracting(e -> e.nombre().valor(), Etiqueta::prioridad)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("Cine", 10), org.assertj.core.groups.Tuple.tuple("Farmacia", 20));
		assertThat(plan.etiquetas().getFirst().categoriaId().valor()).as("la cascada apunta a la superviviente").isEqualTo(this.ocio);
		assertThat(plan.anomalias()).anyMatch(a -> a.contains("La categoria 'OCIO ' repite"))
				.anyMatch(a -> a.contains("color no valido"))
				.anyMatch(a -> a.contains("La etiqueta 'cine' repite"));
	}

	@Test
	void restauraLaClasificacionComoMigracionYConoceTambienLasSinClasificar() {
		UUID clasificada = UUID.randomUUID();
		UUID sinNada = UUID.randomUUID();
		PlanRestauracion.Plan plan = this.plan(List.of(
				new TransaccionRespaldo(clasificada, null, "CINE YELMO", this.ocioRepetida, List.of(this.cineRepetida, this.cine, UUID.randomUUID())),
				new TransaccionRespaldo(sinNada, null, "NADA", null, List.of())));

		assertThat(plan.clasificaciones()).hasSize(2);
		assertThat(plan.clasificadas()).isEqualTo(1);
		ClasificacionTransaccion primera = plan.clasificaciones().getFirst();
		assertThat(primera.categoria().valor()).isEqualTo(this.ocio);
		assertThat(primera.origenCategoria()).isEqualTo(OrigenAsignacion.MIGRACION);
		assertThat(primera.etiquetas()).hasSize(1).containsValue(OrigenAsignacion.MIGRACION);
		assertThat(primera.autoClasificada()).as("las reglas no se aplican encima de lo restaurado").isTrue();
		assertThat(primera.eventosPendientes()).singleElement().isInstanceOfSatisfying(TransaccionClasificada.class,
				e -> assertThat(e.motivo().name()).isEqualTo("MIGRACION"));
		assertThat(plan.clasificaciones().get(1).eventosPendientes()).as("sin clasificacion no hay evento").isEmpty();
		assertThat(plan.clasificaciones().get(1).version()).as("pero se guarda").isEqualTo(1);
		assertThat(plan.anomalias()).anyMatch(a -> a.startsWith("1 asignaciones"));
	}

	private PlanRestauracion.Plan plan(List<TransaccionRespaldo> transacciones) {
		return PlanRestauracion.de(this.usuario,
				List.of(new CategoriaRespaldo(this.ocio, "Ocio", "#FF0000", null, List.of("cine")),
						new CategoriaRespaldo(this.ocioRepetida, "OCIO ", null, null, List.of("teatro", " ")),
						new CategoriaRespaldo(this.salud, "Salud", "rojo", null, List.of())),
				List.of(new EtiquetaRespaldo(this.cine, "Cine", null, List.of("yelmo"), this.ocioRepetida),
						new EtiquetaRespaldo(this.cineRepetida, "cine", null, List.of(), null),
						new EtiquetaRespaldo(this.farmacia, "Farmacia", null, List.of("fcia"), this.salud)),
				transacciones);
	}
}
