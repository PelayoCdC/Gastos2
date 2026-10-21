package es.gastos.compartido.mensajeria;

import es.gastos.contratos.CatalogoEventos;
import es.gastos.contratos.EventoIntegracion;
import es.gastos.contratos.Sobre;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Convierte el JSON de un mensaje de Kafka en un {@link Sobre} con los datos ya tipados según el
 * {@link CatalogoEventos}.
 *
 * <p>Es un <i>lector tolerante</i>: un tipo o una versión que el consumidor no conoce se ignora
 * (devuelve vacío) en vez de fallar, para que un productor pueda añadir eventos nuevos sin romper
 * a los consumidores existentes.
 */
public class LectorSobres {

	private static final TypeReference<Sobre<JsonNode>> SOBRE_GENERICO = new TypeReference<>() {
	};

	private final JsonMapper json;

	public LectorSobres(JsonMapper json) {
		this.json = json;
	}

	public Optional<Sobre<EventoIntegracion>> leer(String mensaje) {
		Sobre<JsonNode> sobre = this.json.readValue(mensaje, SOBRE_GENERICO);

		return CatalogoEventos.buscar(sobre.tipo(), sobre.version())
				.map(entrada -> sobre.conDatos(this.json.treeToValue(sobre.datos(), entrada.clase())));
	}
}
