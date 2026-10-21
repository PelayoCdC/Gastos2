package es.gastos.compartido.pruebas.arquitectura;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Comprobaciones que hacen fallar el build cuando la documentación se queda atrás respecto al
 * código ("documentación viva"). Ver {@code docs/proceso/guia-de-desarrollo.md}.
 */
public final class DocumentacionViva {

	private DocumentacionViva() {
	}

	/** Raíz del monorepo: el primer directorio ascendente que contiene {@code docs/README.md}. */
	public static Path raizDelRepositorio() {
		Path actual = Path.of("").toAbsolutePath();
		while (actual != null) {
			if (Files.isRegularFile(actual.resolve("docs").resolve("README.md"))) {
				return actual;
			}
			actual = actual.getParent();
		}
		throw new IllegalStateException("No se encuentra la raiz del repositorio (docs/README.md)");
	}

	/**
	 * Devuelve los paquetes de dominio (directorios bajo {@code src/main/java/**&#47;dominio}) que tienen
	 * clases pero no {@code package-info.java}. Cada paquete de dominio debe explicar su papel.
	 */
	public static List<Path> paquetesDeDominioSinDocumentar(Path fuentesJava) {
		if (!Files.isDirectory(fuentesJava)) {
			return List.of();
		}

		try (Stream<Path> directorios = Files.walk(fuentesJava)) {
			return directorios
					.filter(Files::isDirectory)
					.filter(d -> fuentesJava.relativize(d).toString().replace('\\', '/').matches("(.*/)?dominio(/.*)?"))
					.filter(DocumentacionViva::tieneClases)
					.filter(d -> !Files.exists(d.resolve("package-info.java")))
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Términos (por ejemplo, tipos de evento) que no aparecen en el documento indicado. */
	public static List<String> terminosNoDocumentados(Path documento, List<String> terminos) {
		try {
			String texto = Files.readString(documento);
			return terminos.stream().filter(t -> !texto.contains(t)).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean tieneClases(Path directorio) {
		try (Stream<Path> ficheros = Files.list(directorio)) {
			return ficheros.anyMatch(f -> f.toString().endsWith(".java") && !f.endsWith("package-info.java"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
