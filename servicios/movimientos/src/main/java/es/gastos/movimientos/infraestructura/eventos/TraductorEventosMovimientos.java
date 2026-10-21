package es.gastos.movimientos.infraestructura.eventos;

import es.gastos.compartido.mensajeria.TraductorEventos;
import es.gastos.contratos.EventoIntegracion;
import es.gastos.contratos.movimientos.v1.CuentaEliminada;
import es.gastos.contratos.movimientos.v1.CuentaGuardada;
import es.gastos.contratos.movimientos.v1.DocumentoEliminado;
import es.gastos.contratos.movimientos.v1.DocumentoReasignado;
import es.gastos.contratos.movimientos.v1.DocumentoRenombrado;
import es.gastos.contratos.movimientos.v1.TransaccionCorregida;
import es.gastos.contratos.movimientos.v1.TransaccionEliminada;
import es.gastos.contratos.movimientos.v1.TransaccionRegistrada;
import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.Importe;
import es.gastos.movimientos.dominio.cuenta.CuentaId;
import es.gastos.movimientos.dominio.documento.DocumentoId;
import es.gastos.movimientos.dominio.transaccion.Transaccion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Traduce los eventos de dominio de movimientos a su lenguaje publicado (contratos movimientos.v1). */
@Component
class TraductorEventosMovimientos implements TraductorEventos {

	@Override
	public Optional<EventoIntegracion> traducir(EventoDominio evento) {
		EventoIntegracion traducido = switch (evento) {
			case es.gastos.movimientos.dominio.cuenta.CuentaGuardada e ->
					new CuentaGuardada(e.cuentaId().valor(), e.nombre().valor(), e.banco(), e.descripcion());
			case es.gastos.movimientos.dominio.cuenta.CuentaEliminada e -> new CuentaEliminada(e.cuentaId().valor());
			case es.gastos.movimientos.dominio.documento.DocumentoRenombrado e ->
					new DocumentoRenombrado(e.documentoId().valor(), e.nombre().valor());
			case es.gastos.movimientos.dominio.documento.DocumentoReasignado e ->
					new DocumentoReasignado(e.documentoId().valor(), cuenta(e.cuentaAnterior()), cuenta(e.cuentaNueva()));
			case es.gastos.movimientos.dominio.documento.DocumentoEliminado e -> new DocumentoEliminado(e.documentoId().valor());
			case es.gastos.movimientos.dominio.transaccion.TransaccionRegistrada e -> registrada(e.datos());
			case es.gastos.movimientos.dominio.transaccion.TransaccionCorregida e -> corregida(e.datos());
			case es.gastos.movimientos.dominio.transaccion.TransaccionEliminada e ->
					new TransaccionEliminada(e.transaccionId().valor(), documento(e.documentoId()));
			default -> null;
		};
		return Optional.ofNullable(traducido);
	}

	static TransaccionRegistrada registrada(Transaccion.Datos d) {
		return new TransaccionRegistrada(d.id().valor(), d.origen().name(), cuenta(d.cuentaId()), documento(d.documentoId()),
				d.fechaContable(), d.fechaValor(), d.concepto().valor(), d.nota(), valor(d.importe()), valor(d.saldo()),
				d.orden(), d.ordenDocumento());
	}

	private static TransaccionCorregida corregida(Transaccion.Datos d) {
		return new TransaccionCorregida(d.id().valor(), cuenta(d.cuentaId()), d.fechaContable(), d.fechaValor(),
				d.concepto().valor(), d.nota(), valor(d.importe()));
	}

	private static UUID cuenta(CuentaId id) {
		return id == null ? null : id.valor();
	}

	private static UUID documento(DocumentoId id) {
		return id == null ? null : id.valor();
	}

	private static BigDecimal valor(Importe importe) {
		return importe == null ? null : importe.valor();
	}
}
