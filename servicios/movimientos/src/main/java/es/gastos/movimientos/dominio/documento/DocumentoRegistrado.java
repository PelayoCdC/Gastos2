package es.gastos.movimientos.dominio.documento;

import es.gastos.kernel.dominio.EventoDominio;
import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;

import java.time.LocalDate;
import java.util.UUID;

/** Se ha importado un extracto y existe su documento. */
public record DocumentoRegistrado(DocumentoId documentoId, UsuarioId usuarioId, long versionAgregado, CuentaId cuentaId,
		NombreDocumento nombre, String nombreArchivo, LocalDate fecha) implements EventoDominio {

	@Override
	public UUID agregadoId() {
		return this.documentoId.valor();
	}
}
