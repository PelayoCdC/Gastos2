package es.gastos.kernel.dominio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base de las raíces de agregado: frontera de consistencia que se carga y se guarda entera.
 *
 * <p>Cada agregado lleva dos versiones:
 * <ul>
 *   <li>{@code versionCargada}: la que tenía al leerse de la base de datos (0 si es nuevo). El
 *   repositorio la usa para el bloqueo optimista ({@code UPDATE ... WHERE version = ?}).</li>
 *   <li>{@code version}: la actual. Cada cambio con significado de negocio la incrementa con
 *   {@link #siguienteVersion()} y la estampa en el evento que registra, así cada evento lleva
 *   una versión distinta aunque se produzcan varios en la misma operación.</li>
 * </ul>
 * Los eventos se acumulan aquí y el caso de uso los extrae tras guardar para publicarlos en la
 * misma unidad de trabajo (outbox), lo que garantiza que no hay cambio sin evento ni evento sin
 * cambio.
 */
public abstract class RaizAgregado<ID> {

	private final long versionCargada;

	private long version;

	private final List<EventoDominio> eventos = new ArrayList<>();

	protected RaizAgregado(long versionCargada) {
		if (versionCargada < 0) {
			throw new IllegalArgumentException("La version no puede ser negativa");
		}

		this.versionCargada = versionCargada;
		this.version = versionCargada;
	}

	public abstract ID id();

	public abstract UsuarioId propietario();

	public long version() {
		return this.version;
	}

	public long versionCargada() {
		return this.versionCargada;
	}

	public boolean esNuevo() {
		return this.versionCargada == 0;
	}

	public boolean tieneCambios() {
		return this.version != this.versionCargada;
	}

	/** Incrementa la versión; se llama una vez por cada cambio que produce un evento. */
	protected long siguienteVersion() {
		return ++this.version;
	}

	protected void registrarEvento(EventoDominio evento) {
		this.eventos.add(evento);
	}

	public List<EventoDominio> eventosPendientes() {
		return Collections.unmodifiableList(this.eventos);
	}

	/** Devuelve los eventos registrados y vacía la lista (se publican una sola vez). */
	public List<EventoDominio> extraerEventos() {
		List<EventoDominio> extraidos = List.copyOf(this.eventos);
		this.eventos.clear();
		return extraidos;
	}

	/** Atajo para comprobar la propiedad: los recursos ajenos se tratan como inexistentes (404). */
	public boolean perteneceA(UsuarioId usuario) {
		return this.propietario().equals(usuario);
	}
}
