package es.gastos.identidad;

import es.gastos.compartido.pruebas.arquitectura.DocumentacionViva;
import es.gastos.compartido.pruebas.arquitectura.ReglasArquitectura;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** La arquitectura (capas, núcleo sin frameworks) y la documentación del dominio se verifican en cada build. */
class ArquitecturaTest {

	@Test
	void cumpleLasReglasDeArquitectura() {
		ReglasArquitectura.comprobar("es.gastos.identidad");
	}

	@Test
	void cadaPaqueteDeDominioExplicaSuPapel() {
		assertThat(DocumentacionViva.paquetesDeDominioSinDocumentar(Path.of("src/main/java"))).isEmpty();
	}
}
