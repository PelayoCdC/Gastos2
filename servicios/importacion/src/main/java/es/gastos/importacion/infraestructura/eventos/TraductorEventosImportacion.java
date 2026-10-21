package es.gastos.importacion.infraestructura.eventos;

import es.gastos.compartido.mensajeria.TraductorEventos;
import es.gastos.contratos.EventoIntegracion;
import es.gastos.contratos.importacion.v1.FormatoBancoEliminado;
import es.gastos.contratos.importacion.v1.FormatoBancoGuardado;
import es.gastos.importacion.dominio.formato.MapeoColumnas;
import es.gastos.kernel.dominio.EventoDominio;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Traduce los eventos de dominio de importación a su lenguaje publicado (contratos importacion.v1). */
@Component
class TraductorEventosImportacion implements TraductorEventos {

	@Override
	public Optional<EventoIntegracion> traducir(EventoDominio evento) {
		EventoIntegracion traducido = switch (evento) {
			case es.gastos.importacion.dominio.formato.FormatoBancoGuardado e -> guardado(e);
			case es.gastos.importacion.dominio.formato.FormatoBancoEliminado e -> new FormatoBancoEliminado(e.formatoId().valor());
			default -> null;
		};
		return Optional.ofNullable(traducido);
	}

	private static FormatoBancoGuardado guardado(es.gastos.importacion.dominio.formato.FormatoBancoGuardado e) {
		MapeoColumnas c = e.columnas();
		return new FormatoBancoGuardado(e.formatoId().valor(), e.nombre().valor(), e.filaInicio(), c.concepto(), c.importe(),
				c.fechaContable(), c.fechaValor(), c.saldo(), e.formatoFecha().patron());
	}
}
