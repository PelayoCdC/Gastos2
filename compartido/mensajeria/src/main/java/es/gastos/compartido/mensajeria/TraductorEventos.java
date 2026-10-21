package es.gastos.compartido.mensajeria;

import es.gastos.contratos.EventoIntegracion;
import es.gastos.kernel.dominio.EventoDominio;

import java.util.Optional;

/**
 * Traduce los eventos de dominio de un servicio a eventos de integración (lenguaje publicado).
 *
 * <p>Cada servicio declara el suyo en su infraestructura. Es la frontera que permite cambiar el
 * modelo interno sin romper a los consumidores: el dominio no conoce los contratos. Un evento de
 * dominio sin interés fuera del servicio se traduce a {@link Optional#empty()} y no se publica.
 */
@FunctionalInterface
public interface TraductorEventos {

	Optional<EventoIntegracion> traducir(EventoDominio evento);
}
