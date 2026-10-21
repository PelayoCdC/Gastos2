package es.gastos.clasificacion;

import es.gastos.compartido.pruebas.arquitectura.DocumentacionViva;
import es.gastos.compartido.pruebas.arquitectura.ReglasArquitectura;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArquitecturaTest {

	@Test
	void cumpleLasReglasDeArquitectura() {
		ReglasArquitectura.comprobar("es.gastos.clasificacion");
	}

	@Test
	void cadaPaqueteDeDominioExplicaSuPapel() {
		assertThat(DocumentacionViva.paquetesDeDominioSinDocumentar(Path.of("src/main/java"))).isEmpty();
	}
}
