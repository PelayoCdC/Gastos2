package es.gastos.clasificacion.infraestructura.catalogoinicial;

import es.gastos.clasificacion.aplicacion.siembra.FuenteCatalogoInicial;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Lee el catálogo inicial de {@code catalogo-inicial.json}. Es un fichero de datos (no código) para
 * poder revisarlo y cambiarlo sin tocar Java; su orden fija la prioridad inicial.
 */
@Component
class FuenteCatalogoInicialJson implements FuenteCatalogoInicial {

	private record Fichero(List<CategoriaInicial> categorias, List<EtiquetaInicial> etiquetas) {
	}

	private final Fichero fichero;

	FuenteCatalogoInicialJson(JsonMapper json) {
		try (InputStream entrada = new ClassPathResource("catalogo-inicial.json").getInputStream()) {
			this.fichero = json.readValue(entrada, Fichero.class);
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer catalogo-inicial.json", e);
		}
	}

	@Override
	public List<CategoriaInicial> categorias() {
		return this.fichero.categorias();
	}

	@Override
	public List<EtiquetaInicial> etiquetas() {
		return this.fichero.etiquetas();
	}
}
