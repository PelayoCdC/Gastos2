package es.gastos.clasificacion.infraestructura.kafka;

import es.gastos.clasificacion.aplicacion.sincronizacion.SincronizarTransacciones;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionId;
import es.gastos.compartido.mensajeria.LectorSobres;
import es.gastos.contratos.Topics;
import es.gastos.contratos.movimientos.v1.DocumentoEliminado;
import es.gastos.contratos.movimientos.v1.TransaccionCorregida;
import es.gastos.contratos.movimientos.v1.TransaccionEliminada;
import es.gastos.contratos.movimientos.v1.TransaccionRegistrada;
import es.gastos.kernel.dominio.UsuarioId;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada: mantiene la copia de las transacciones con los eventos de movimientos.
 *
 * <p>Procesa mensaje a mensaje (cada uno en su transacción). Cargar el catálogo por cada fila importada
 * es más trabajo que hacerlo una vez por lote, pero el error de un mensaje no arrastra a los demás y el
 * volumen de un extracto (decenas o cientos de filas) no lo hace notar. Si llegara a notarse, se
 * cambiaría a un listener por lotes.
 */
@Component
class ConsumidorMovimientos {

	private final LectorSobres lector;

	private final SincronizarTransacciones sincronizar;

	ConsumidorMovimientos(LectorSobres lector, SincronizarTransacciones sincronizar) {
		this.lector = lector;
		this.sincronizar = sincronizar;
	}

	@KafkaListener(topics = Topics.MOVIMIENTOS, groupId = "clasificacion")
	void recibir(String mensaje) {
		this.lector.leer(mensaje).ifPresent(sobre -> {
			UsuarioId usuario = new UsuarioId(sobre.usuarioId());
			switch (sobre.datos()) {
				case TransaccionRegistrada e -> this.sincronizar.registrada(usuario, new TransaccionId(e.transaccionId()), e.documentoId(),
						e.concepto(), e.origen(), sobre.versionAgregado());
				case TransaccionCorregida e -> this.sincronizar.corregida(usuario, new TransaccionId(e.transaccionId()), e.concepto(),
						sobre.versionAgregado());
				case TransaccionEliminada e -> this.sincronizar.eliminada(new TransaccionId(e.transaccionId()));
				case DocumentoEliminado e -> this.sincronizar.documentoEliminado(e.documentoId());
				default -> {
					// Cuentas y el resto de eventos de documentos no afectan a la clasificación
				}
			}
		});
	}
}
