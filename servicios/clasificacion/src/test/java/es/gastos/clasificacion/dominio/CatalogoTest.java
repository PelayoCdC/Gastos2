package es.gastos.clasificacion.dominio;

import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaGuardada;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Color;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaGuardada;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.catalogo.NombreCatalogo;
import es.gastos.clasificacion.dominio.catalogo.Ocurrencias;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.Normalizador;
import es.gastos.kernel.dominio.UsuarioId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogoTest {

	private static final UsuarioId USUARIO = new UsuarioId(UUID.randomUUID());

	@Test
	void lasOcurrenciasConservanSusEspaciosYNoSeRepiten() {
		Ocurrencias ocurrencias = Ocurrencias.de(Arrays.asList("bar ", " ", null, "BAR ", "Café", "cafe", " ibi"));

		assertThat(ocurrencias.textos()).containsExactly("bar ", "Café", " ibi");
	}

	@Test
	void unaOcurrenciaConEspaciosNoCasaDondeNoDebe() {
		Ocurrencias restauracion = Ocurrencias.de(List.of("bar "));
		Ocurrencias impuestos = Ocurrencias.de(List.of(" ibi"));

		assertThat(restauracion.coincideCon(Normalizador.normalizar("BAR PEPE"))).isTrue();
		assertThat(restauracion.coincideCon(Normalizador.normalizar("VIAJE A BARCELONA"))).isFalse();
		assertThat(impuestos.coincideCon(Normalizador.normalizar("RECIBO IBI AYTO"))).isTrue();
		assertThat(impuestos.coincideCon(Normalizador.normalizar("EXHIBICION"))).isFalse();
		assertThat(Ocurrencias.de(List.of("cafe")).coincideCon(Normalizador.normalizar("CAFETERÍA"))).isTrue();
	}

	@Test
	void elColorSeNormalizaYSeValida() {
		assertThat(Color.opcional(" #65A30D ")).isEqualTo(new Color("#65a30d"));
		assertThat(Color.opcional("")).isNull();
		assertThatThrownBy(() -> new Color("verde")).isInstanceOf(ExcepcionDominio.class);
	}

	@Test
	void unaCategoriaSoloAnunciaCambiosReales() {
		Categoria categoria = Categoria.crear(new CategoriaId(UUID.randomUUID()), USUARIO, new NombreCatalogo("Salud"),
				new Color("#dc2626"), null, Ocurrencias.de(List.of("farmacia")), 2);
		assertThat(categoria.extraerEventos()).singleElement().isInstanceOf(CategoriaGuardada.class);

		// Descripción en blanco = sin descripción, y las mismas ocurrencias: no hay cambio
		categoria.modificar(new NombreCatalogo(" Salud "), new Color("#DC2626"), " ", Ocurrencias.de(List.of("farmacia")));
		assertThat(categoria.extraerEventos()).isEmpty();

		categoria.cambiarPrioridad(1);
		assertThat(categoria.extraerEventos()).singleElement().isInstanceOfSatisfying(CategoriaGuardada.class,
				e -> assertThat(e.prioridad()).isEqualTo(1));
		assertThatThrownBy(() -> categoria.cambiarPrioridad(-1)).isInstanceOf(ExcepcionDominio.class);
	}

	@Test
	void unaEtiquetaPuedePerderSuCascadaSinDejarDeExistir() {
		CategoriaId salud = new CategoriaId(UUID.randomUUID());
		Etiqueta farmacia = Etiqueta.crear(new EtiquetaId(UUID.randomUUID()), USUARIO, new NombreCatalogo("Farmacia"), null,
				Ocurrencias.de(List.of("farmacia")), salud, 1);
		farmacia.extraerEventos();

		farmacia.quitarCategoria();

		assertThat(farmacia.categoriaId()).isNull();
		assertThat(farmacia.extraerEventos()).singleElement().isInstanceOfSatisfying(EtiquetaGuardada.class,
				e -> assertThat(e.categoriaId()).isNull());
	}
}
