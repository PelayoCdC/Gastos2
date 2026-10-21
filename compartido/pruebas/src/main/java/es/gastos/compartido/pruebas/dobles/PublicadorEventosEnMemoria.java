package es.gastos.compartido.pruebas.dobles;

import es.gastos.kernel.aplicacion.PublicadorEventos;
import es.gastos.kernel.dominio.EventoDominio;

import java.util.ArrayList;
import java.util.List;

/** Publicador para tests unitarios: guarda los eventos en memoria para poder comprobarlos. */
public class PublicadorEventosEnMemoria implements PublicadorEventos {

	private final List<EventoDominio> publicados = new ArrayList<>();

	@Override
	public void publicar(List<? extends EventoDominio> eventos) {
		this.publicados.addAll(eventos);
	}

	public List<EventoDominio> publicados() {
		return List.copyOf(this.publicados);
	}

	public <E extends EventoDominio> List<E> deTipo(Class<E> tipo) {
		return this.publicados.stream().filter(tipo::isInstance).map(tipo::cast).toList();
	}

	public <E extends EventoDominio> E unico(Class<E> tipo) {
		List<E> eventos = this.deTipo(tipo);
		if (eventos.size() != 1) {
			throw new AssertionError("Se esperaba un unico " + tipo.getSimpleName() + " y hay " + eventos.size());
		}
		return eventos.getFirst();
	}

	public void limpiar() {
		this.publicados.clear();
	}
}
