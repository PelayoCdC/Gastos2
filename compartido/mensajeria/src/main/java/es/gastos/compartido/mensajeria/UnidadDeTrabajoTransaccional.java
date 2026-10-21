package es.gastos.compartido.mensajeria;

import es.gastos.kernel.aplicacion.UnidadDeTrabajo;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Adaptador de {@link UnidadDeTrabajo} sobre las transacciones de Spring (JDBC). */
public class UnidadDeTrabajoTransaccional implements UnidadDeTrabajo {

	private final TransactionTemplate escritura;

	private final TransactionTemplate lectura;

	public UnidadDeTrabajoTransaccional(PlatformTransactionManager gestor) {
		this.escritura = new TransactionTemplate(gestor);
		this.lectura = new TransactionTemplate(gestor);
		this.lectura.setReadOnly(true);
	}

	@Override
	public <T> T ejecutar(Supplier<T> trabajo) {
		return this.escritura.execute(estado -> trabajo.get());
	}

	@Override
	public <T> T ejecutarLectura(Supplier<T> trabajo) {
		return this.lectura.execute(estado -> trabajo.get());
	}
}
