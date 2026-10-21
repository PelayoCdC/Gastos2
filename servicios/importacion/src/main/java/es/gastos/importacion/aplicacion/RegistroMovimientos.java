package es.gastos.importacion.aplicacion;

import es.gastos.importacion.dominio.extracto.Interpretacion;
import es.gastos.kernel.dominio.UsuarioId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de salida hacia movimientos (relación cliente/proveedor, docs/adr/0003): entrega las líneas
 * interpretadas para que movimientos las registre, descarte los duplicados y cree el documento.
 * El {@code extractoId} lo genera importacion y hace la llamada idempotente: si se reintenta tras un
 * corte, movimientos devuelve el mismo resultado sin importar dos veces.
 */
public interface RegistroMovimientos {

	Registro registrar(UsuarioId usuario, Extracto extracto);

	record Extracto(UUID extractoId, UUID cuentaId, String nombre, String nombreArchivo, List<Interpretacion.Linea> lineas) {
	}

	record Registro(UUID documentoId, String nombreDocumento, String nombreArchivo, int importadas, List<Duplicada> duplicados) {
	}

	record Duplicada(int fila, LocalDate fechaContable, String concepto, BigDecimal importe) {
	}
}
