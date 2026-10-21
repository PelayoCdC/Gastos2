package es.gastos.contratos;

/**
 * Marca de los datos de un evento de integración (el campo {@code datos} del {@link Sobre}).
 *
 * <p>Cada evento es un record en un paquete versionado ({@code ...<contexto>.v1}) y se registra en
 * {@link CatalogoEventos} con su tipo, versión y topic. Un cambio incompatible crea una versión nueva
 * (un record nuevo en {@code v2}); los consumidores conviven con ambas mientras haga falta.
 */
public interface EventoIntegracion {
}
