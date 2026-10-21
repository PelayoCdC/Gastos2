package es.gastos.clasificacion.dominio;

import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.catalogo.NombreCatalogo;
import es.gastos.clasificacion.dominio.catalogo.Ocurrencias;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionTransaccion;
import es.gastos.clasificacion.dominio.clasificacion.ModoAutomatico;
import es.gastos.clasificacion.dominio.clasificacion.MotivoClasificacion;
import es.gastos.clasificacion.dominio.clasificacion.MotorClasificacion;
import es.gastos.clasificacion.dominio.clasificacion.OrigenAsignacion;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionClasificada;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionId;
import es.gastos.kernel.dominio.UsuarioId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de clasificación (core domain). Los primeros casos están portados de
 * gastos-movil/src/app/core/dominio/dominio.spec.ts; el resto cubre la prioridad (docs/adr/0014) y
 * el reetiquetado que respeta lo manual (docs/adr/0017).
 */
class MotorClasificacionTest {

	private static final UsuarioId USUARIO = new UsuarioId(UUID.randomUUID());

	private final Categoria alimentacion = categoria("Alimentacion", 1, "lupa");

	private final Categoria otros = categoria("Otros", 2, "lupa");

	private final Categoria ocio = categoria("Ocio y cultura", 3, "spotify");

	private final Categoria compras = categoria("Compras", 4, "paypal");

	private final Etiqueta supermercado = etiqueta("Supermercado", 1, alimentacion.id(), "lupa");

	private final Etiqueta bizum = etiqueta("Bizum", 2, null, "bizum");

	private final Etiqueta suscripciones = etiqueta("Suscripciones", 3, ocio.id(), "spotify");

	private final Etiqueta comprasOnline = etiqueta("Compras online", 4, compras.id(), "paypal");

	private final MotorClasificacion motor = MotorClasificacion.de(List.of(compras, ocio, otros, alimentacion),
			List.of(comprasOnline, bizum, supermercado, suscripciones));

	@Test
	void acumulaVariasEtiquetasSinDuplicarIgnorandoMayusculasYTildes() {
		ClasificacionTransaccion transaccion = conCategoriaYEtiquetas("BIZUM DESDE LÚPA", null, Map.of(this.bizum.id(), OrigenAsignacion.MANUAL));

		transaccion.clasificarAutomaticamente(this.motor, ModoAutomatico.ETIQUETAS, false);

		assertThat(transaccion.etiquetas().keySet()).containsExactly(this.bizum.id(), this.supermercado.id());
	}

	@Test
	void laCascadaHeredaLaCategoriaDeLaEtiquetaYNoPisaUnaYaAsignada() {
		ClasificacionTransaccion sinCategoria = nueva("COMPRA LUPA");
		sinCategoria.clasificarAutomaticamente(this.motor, ModoAutomatico.ETIQUETAS, false);
		assertThat(sinCategoria.categoria()).isEqualTo(this.alimentacion.id());
		assertThat(sinCategoria.origenCategoria()).isEqualTo(OrigenAsignacion.CASCADA);

		CategoriaId otra = new CategoriaId(UUID.randomUUID());
		ClasificacionTransaccion conCategoria = conCategoriaYEtiquetas("COMPRA LUPA", otra, Map.of());
		conCategoria.clasificarAutomaticamente(this.motor, ModoAutomatico.ETIQUETAS, false);
		assertThat(conCategoria.categoria()).isEqualTo(otra);
	}

	@Test
	void laCategorizacionDirectaNuncaPisaYGanaLaDeMayorPrioridad() {
		ClasificacionTransaccion sinCategoria = nueva("LUPA");
		ClasificacionTransaccion.Resultado resultado = sinCategoria.clasificarAutomaticamente(this.motor, ModoAutomatico.CATEGORIAS, false);
		assertThat(resultado.categorizada()).isTrue();
		assertThat(sinCategoria.categoria()).as("Alimentacion (1) gana a Otros (2)").isEqualTo(this.alimentacion.id());

		CategoriaId otra = new CategoriaId(UUID.randomUUID());
		ClasificacionTransaccion conCategoria = conCategoriaYEtiquetas("LUPA", otra, Map.of());
		assertThat(conCategoria.clasificarAutomaticamente(this.motor, ModoAutomatico.CATEGORIAS, false).categorizada()).isFalse();
		assertThat(conCategoria.categoria()).isEqualTo(otra);
	}

	@Test
	void conVariasEtiquetasLaCascadaUsaLaDeMayorPrioridad() {
		ClasificacionTransaccion transaccion = nueva("PAYPAL *SPOTIFY");

		transaccion.clasificarAutomaticamente(this.motor, ModoAutomatico.IMPORTACION, false);

		assertThat(transaccion.etiquetas().keySet()).containsExactly(this.suscripciones.id(), this.comprasOnline.id());
		assertThat(transaccion.categoria()).as("Suscripciones (3) va antes que Compras online (4)").isEqualTo(this.ocio.id());
		assertThat(transaccion.autoClasificada()).isTrue();
		assertThat(transaccion.extraerEventos()).singleElement().isInstanceOfSatisfying(TransaccionClasificada.class,
				e -> assertThat(e.motivo()).isEqualTo(MotivoClasificacion.AUTOMATICA));
	}

	@Test
	void reetiquetarQuitaLoAutomaticoPeroRespetaLoManual() {
		CategoriaId manual = new CategoriaId(UUID.randomUUID());
		ClasificacionTransaccion transaccion = conCategoriaYEtiquetas("COMPRA LUPA", manual,
				Map.of(this.bizum.id(), OrigenAsignacion.AUTOMATICA, this.comprasOnline.id(), OrigenAsignacion.MANUAL));

		transaccion.clasificarAutomaticamente(this.motor, ModoAutomatico.REINICIAR, false);

		assertThat(transaccion.categoria()).isEqualTo(manual);
		assertThat(transaccion.etiquetas()).containsOnlyKeys(this.comprasOnline.id(), this.supermercado.id());
		assertThat(transaccion.etiquetas().get(this.comprasOnline.id())).isEqualTo(OrigenAsignacion.MANUAL);
		assertThat(transaccion.etiquetas().get(this.supermercado.id())).isEqualTo(OrigenAsignacion.AUTOMATICA);
	}

	@Test
	void reetiquetarForzandoEmpiezaDeCeroYMarcaElMotivo() {
		ClasificacionTransaccion transaccion = conCategoriaYEtiquetas("COMPRA LUPA", new CategoriaId(UUID.randomUUID()),
				Map.of(this.bizum.id(), OrigenAsignacion.MANUAL));

		transaccion.clasificarAutomaticamente(this.motor, ModoAutomatico.REINICIAR, true);

		assertThat(transaccion.categoria()).isEqualTo(this.alimentacion.id());
		assertThat(transaccion.etiquetas().keySet()).containsExactly(this.supermercado.id());
		assertThat(transaccion.extraerEventos()).singleElement().isInstanceOfSatisfying(TransaccionClasificada.class,
				e -> assertThat(e.motivo()).isEqualTo(MotivoClasificacion.REINICIO));
	}

	@Test
	void sinCambiosNoHayEventoYConfirmarAManoLoAutomaticoTampocoLoEmite() {
		ClasificacionTransaccion transaccion = nueva("COMPRA LUPA");
		transaccion.clasificarAutomaticamente(this.motor, ModoAutomatico.IMPORTACION, false);
		transaccion.extraerEventos();

		transaccion.clasificarAutomaticamente(this.motor, ModoAutomatico.ETIQUETAS, false);
		assertThat(transaccion.extraerEventos()).isEmpty();

		long version = transaccion.version();
		boolean cambio = transaccion.asignarManualmente(this.alimentacion.id(), List.of(this.supermercado.id()));
		assertThat(cambio).isFalse();
		assertThat(transaccion.extraerEventos()).as("la clasificación no cambia para los demás").isEmpty();
		assertThat(transaccion.version()).as("pero los orígenes sí: se guarda").isGreaterThan(version);
		assertThat(transaccion.origenCategoria()).isEqualTo(OrigenAsignacion.MANUAL);
	}

	@Test
	void propagarSustituyeLaCategoriaYAnadeEtiquetas() {
		ClasificacionTransaccion base = conCategoriaYEtiquetas("GOGALIA 04 02 2024", this.ocio.id(), Map.of(this.bizum.id(), OrigenAsignacion.MANUAL));
		ClasificacionTransaccion similar = conCategoriaYEtiquetas("GOGALIA 15 10 2023", this.compras.id(),
				Map.of(this.supermercado.id(), OrigenAsignacion.AUTOMATICA));

		assertThat(similar.claveConcepto()).isEqualTo(base.claveConcepto());
		assertThat(similar.propagarDesde(base)).isTrue();
		assertThat(similar.categoria()).isEqualTo(this.ocio.id());
		assertThat(similar.etiquetas().keySet()).containsExactly(this.supermercado.id(), this.bizum.id());
		assertThat(similar.propagarDesde(base)).as("propagar otra vez no cambia nada").isFalse();
	}

	@Test
	void aplicarEnBloqueAnadeOSustituyeEtiquetasYNoBorraLaCategoriaSiNoSeIndica() {
		ClasificacionTransaccion transaccion = conCategoriaYEtiquetas("X", this.ocio.id(), Map.of(this.bizum.id(), OrigenAsignacion.MANUAL));

		transaccion.aplicarEnBloque(null, List.of(this.supermercado.id()), false);
		assertThat(transaccion.categoria()).isEqualTo(this.ocio.id());
		assertThat(transaccion.etiquetas().keySet()).containsExactly(this.bizum.id(), this.supermercado.id());

		transaccion.aplicarEnBloque(this.compras.id(), List.of(), true);
		assertThat(transaccion.categoria()).isEqualTo(this.compras.id());
		assertThat(transaccion.etiquetas()).as("sustituir por una lista vacía quita todas").isEmpty();
	}

	@Test
	void unEventoDeOrigenAntiguoNoPisaLaCopiaMasReciente() {
		ClasificacionTransaccion transaccion = ClasificacionTransaccion.conocer(new TransaccionId(UUID.randomUUID()), USUARIO, null, "NUEVO", 5);

		transaccion.sincronizarOrigen("VIEJO", null, 3);

		assertThat(transaccion.concepto()).isEqualTo("NUEVO");
	}

	private static ClasificacionTransaccion nueva(String concepto) {
		return ClasificacionTransaccion.conocer(new TransaccionId(UUID.randomUUID()), USUARIO, null, concepto, 1);
	}

	private static ClasificacionTransaccion conCategoriaYEtiquetas(String concepto, CategoriaId categoria,
			Map<EtiquetaId, OrigenAsignacion> etiquetas) {
		return ClasificacionTransaccion.reconstituir(new TransaccionId(UUID.randomUUID()), USUARIO, null, concepto, 1, categoria,
				categoria == null ? null : OrigenAsignacion.MANUAL, new java.util.LinkedHashMap<>(etiquetas), false, 1);
	}

	private static Categoria categoria(String nombre, int prioridad, String ocurrencia) {
		return Categoria.crear(new CategoriaId(UUID.randomUUID()), USUARIO, new NombreCatalogo(nombre), null, null,
				Ocurrencias.de(List.of(ocurrencia)), prioridad);
	}

	private static Etiqueta etiqueta(String nombre, int prioridad, CategoriaId cascada, String ocurrencia) {
		return Etiqueta.crear(new EtiquetaId(UUID.randomUUID()), USUARIO, new NombreCatalogo(nombre), null,
				Ocurrencias.de(List.of(ocurrencia)), cascada, prioridad);
	}
}
