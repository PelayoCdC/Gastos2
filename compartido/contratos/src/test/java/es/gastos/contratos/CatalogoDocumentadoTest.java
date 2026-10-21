package es.gastos.contratos;

import es.gastos.compartido.pruebas.arquitectura.DocumentacionViva;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Documentación viva: todo evento publicado aparece en docs/eventos/catalogo.md. */
class CatalogoDocumentadoTest {

	@Test
	void todosLosEventosEstanEnElCatalogoDocumentado() {
		Path catalogo = DocumentacionViva.raizDelRepositorio().resolve("docs/eventos/catalogo.md");
		List<String> tipos = CatalogoEventos.todos().stream().map(CatalogoEventos.Entrada::tipo).toList();

		assertThat(DocumentacionViva.terminosNoDocumentados(catalogo, tipos))
				.as("Eventos sin documentar en docs/eventos/catalogo.md")
				.isEmpty();
	}
}
