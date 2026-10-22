package es.gastos.consultas.aplicacion.consulta;

import es.gastos.consultas.dominio.transacciones.FiltroTransacciones;
import es.gastos.consultas.dominio.transacciones.Ordenacion;
import es.gastos.kernel.aplicacion.Pagina;
import es.gastos.kernel.aplicacion.PeticionPagina;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: consultas sobre las transacciones proyectadas de un usuario (nunca ve las de otro). */
public interface LecturaTransacciones {

	Pagina<FilaTransaccion> buscar(UsuarioId usuario, FiltroTransacciones filtro, Ordenacion ordenacion, PeticionPagina pagina);

	/** Sin paginar, como mucho {@code limite} filas. */
	List<FilaTransaccion> todas(UsuarioId usuario, FiltroTransacciones filtro, Ordenacion ordenacion, int limite);

	Optional<FilaTransaccion> una(UsuarioId usuario, UUID id);
}
