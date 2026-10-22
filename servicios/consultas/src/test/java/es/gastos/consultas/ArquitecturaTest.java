package es.gastos.consultas;

import es.gastos.compartido.pruebas.arquitectura.DocumentacionViva;
import es.gastos.compartido.pruebas.arquitectura.ReglasArquitectura;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArquitecturaTest {

	@Test
	void cumpleLasReglasDeArquitectura() {
		ReglasArquitectura.comprobar("es.gastos.consultas");
	}

	@Test
	void cadaPaqueteDeDominioExplicaSuPapel() {
		assertThat(DocumentacionViva.paquetesDeDominioSinDocumentar(Path.of("src/main/java"))).isEmpty();
	}
}
