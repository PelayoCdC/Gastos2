package es.gastos.consultas.aplicacion;

import es.gastos.compartido.pruebas.dobles.UnidadDeTrabajoDirecta;
import es.gastos.consultas.aplicacion.consistencia.EsperarConsistencia;
import es.gastos.consultas.aplicacion.consistencia.PosicionesLectura;
import es.gastos.consultas.aplicacion.proyeccion.Proyeccion;
import es.gastos.consultas.aplicacion.proyeccion.ProyectarEventos;
import es.gastos.consultas.dominio.consistencia.SecuenciasEsperadas;
import es.gastos.contratos.EventoIntegracion;
import es.gastos.contratos.Sobre;
import es.gastos.contratos.clasificacion.v1.TransaccionClasificada;
import es.gastos.contratos.movimientos.v1.DocumentoEliminado;
import es.gastos.contratos.movimientos.v1.TransaccionRegistrada;
import es.gastos.kernel.dominio.UsuarioId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Casos de uso de consultas con dobles en memoria. */
class ConsultasAplicacionTest {

	private final UsuarioId ana = new UsuarioId(UUID.randomUUID());

	private final UsuarioId luis = new UsuarioId(UUID.randomUUID());

	private final PosicionesEnMemoria posiciones = new PosicionesEnMemoria();

	/** Proyección que anota las llamadas que recibe: método y argumentos. */
	private final List<Object[]> llamadas = new ArrayList<>();

	private final Proyeccion proyeccion = (Proyeccion) Proxy.newProxyInstance(Proyeccion.class.getClassLoader(),
			new Class<?>[]{Proyeccion.class}, (proxy, metodo, argumentos) -> {
				Object[] llamada = new Object[argumentos.length + 1];
				llamada[0] = metodo.getName();
				System.arraycopy(argumentos, 0, llamada, 1, argumentos.length);
				this.llamadas.add(llamada);
				return null;
			});

	private final ProyectarEventos proyectar = new ProyectarEventos(this.proyeccion, this.posiciones, new UnidadDeTrabajoDirecta());

	@Test
	void proyectaCadaEventoConSuVersionYNormalizaElConceptoParaBuscar() {
		UUID transaccion = UUID.randomUUID();

		this.proyectar.aplicar(List.of(sobre(this.ana, "movimientos", 10, 1, new TransaccionRegistrada(transaccion, "IMPORTACION", null,
						null, LocalDate.of(2026, 7, 1), null, "CAFETERÍA  Pepe 01 07 2026", null, new BigDecimal("-2.50"), null, 3, 1)),
				sobre(this.ana, "clasificacion", 4, 2, new TransaccionClasificada(transaccion, null, null, "AUTOMATICA"))));

		assertThat(this.llamadas).hasSize(2);
		Object[] registro = this.llamadas.getFirst();
		assertThat(registro[0]).isEqualTo("registrarTransaccion");
		Proyeccion.Hechos hechos = (Proyeccion.Hechos) registro[2];
		assertThat(hechos.conceptoNormalizado()).isEqualTo("cafeteria  pepe 01 07 2026");
		assertThat(hechos.claveConcepto()).isEqualTo("cafeteria pepe");
		assertThat(registro[4]).isEqualTo(1L);
		assertThat(this.llamadas.get(1)[4]).as("sin etiquetas es una lista vacía, no nula").isEqualTo(List.of());
	}

	@Test
	void laPosicionAvanzaALaMayorSecuenciaDeCadaUsuarioYContexto() {
		this.proyectar.aplicar(List.of(
				sobre(this.ana, "movimientos", 10, 1, new DocumentoEliminado(UUID.randomUUID())),
				sobre(this.ana, "movimientos", 12, 1, new DocumentoEliminado(UUID.randomUUID())),
				sobre(this.luis, "movimientos", 11, 1, new DocumentoEliminado(UUID.randomUUID())),
				sobre(this.ana, "clasificacion", 3, 1, new TransaccionClasificada(UUID.randomUUID(), null, List.of(), "MANUAL"))));

		assertThat(this.posiciones.de(this.ana)).isEqualTo(Map.of("movimientos", 12L, "clasificacion", 3L));
		assertThat(this.posiciones.de(this.luis)).isEqualTo(Map.of("movimientos", 11L));
	}

	@Test
	void esperaHastaQueLaProyeccionAlcanzaLaSecuenciaOAgotaElTope() {
		List<Duration> pausas = new ArrayList<>();
		EsperarConsistencia esperar = new EsperarConsistencia(this.posiciones, duracion -> {
			pausas.add(duracion);
			if (pausas.size() == 3) {
				this.posiciones.avanzar(this.ana, "movimientos", 50);
			}
		});

		assertThat(esperar.esperar(this.ana, SecuenciasEsperadas.de("movimientos=50"))).isTrue();
		assertThat(pausas).hasSize(3).allMatch(EsperarConsistencia.INTERVALO::equals);

		pausas.clear();
		assertThat(esperar.esperar(this.ana, SecuenciasEsperadas.de("movimientos=51"))).isFalse();
		assertThat(pausas).as("3 s en pasos de 50 ms").hasSize(60);

		pausas.clear();
		assertThat(esperar.esperar(this.ana, SecuenciasEsperadas.de("identidad=999"))).as("contexto no proyectado").isTrue();
		assertThat(pausas).isEmpty();
	}

	private static Sobre<EventoIntegracion> sobre(UsuarioId usuario, String contexto, long secuencia, long version, EventoIntegracion datos) {
		return new Sobre<>(UUID.randomUUID(), datos.getClass().getSimpleName(), contexto, 1, secuencia, Instant.parse("2026-10-22T10:00:00Z"),
				usuario.valor(), UUID.randomUUID(), version, datos);
	}

	private static final class PosicionesEnMemoria implements PosicionesLectura {

		private final Map<UsuarioId, Map<String, Long>> posiciones = new HashMap<>();

		@Override
		public void avanzar(UsuarioId usuario, String contexto, long secuencia) {
			this.posiciones.computeIfAbsent(usuario, u -> new HashMap<>()).merge(contexto, secuencia, Math::max);
		}

		@Override
		public Map<String, Long> de(UsuarioId usuario) {
			return Map.copyOf(this.posiciones.getOrDefault(usuario, Map.of()));
		}

		@Override
		public boolean proyecta(String contexto) {
			return contexto.equals("movimientos") || contexto.equals("clasificacion");
		}
	}
}
