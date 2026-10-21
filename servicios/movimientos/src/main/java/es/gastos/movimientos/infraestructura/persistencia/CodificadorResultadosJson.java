package es.gastos.movimientos.infraestructura.persistencia;

import es.gastos.movimientos.aplicacion.extractos.CodificadorResultados;
import es.gastos.movimientos.aplicacion.extractos.ResultadoExtracto;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Guarda el resultado de un registro de extracto como JSON en la columna {@code documento.resultado_registro}. */
@Component
class CodificadorResultadosJson implements CodificadorResultados {

	private final JsonMapper json;

	CodificadorResultadosJson(JsonMapper json) {
		this.json = json.rebuild().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
	}

	@Override
	public String codificar(ResultadoExtracto resultado) {
		return this.json.writeValueAsString(resultado);
	}

	@Override
	public ResultadoExtracto decodificar(String texto) {
		return this.json.readValue(texto, ResultadoExtracto.class);
	}
}
