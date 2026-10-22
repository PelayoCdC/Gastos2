package es.gastos.consultas.aplicacion.informes;

import es.gastos.consultas.dominio.informes.Apunte;
import es.gastos.consultas.dominio.informes.Periodo;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.List;

/** Puerto de salida: los movimientos visibles de un usuario en un periodo, con su clasificación. */
public interface LecturaApuntes {

	List<Apunte> de(UsuarioId usuario, Periodo periodo);
}
