package es.gastos.kernel.aplicacion;

import es.gastos.kernel.dominio.ExcepcionDominio;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginaTest {

	@Test
	void calculaElTotalDePaginasYConvierteElContenido() {
		Pagina<Integer> pagina = Pagina.de(List.of(1, 2), new PeticionPagina(1, 2), 5);

		assertThat(pagina.totalPaginas()).isEqualTo(3);
		assertThat(pagina.map(String::valueOf).contenido()).containsExactly("1", "2");
	}

	@Test
	void laPeticionAcotaElTamanoYCalculaElDesplazamiento() {
		assertThat(new PeticionPagina(3, 50).desplazamiento()).isEqualTo(150);
		assertThatThrownBy(() -> new PeticionPagina(0, 501)).isInstanceOf(ExcepcionDominio.class);
		assertThatThrownBy(() -> new PeticionPagina(-1, 10)).isInstanceOf(ExcepcionDominio.class);
	}
}
