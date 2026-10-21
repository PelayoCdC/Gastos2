package es.gastos.clasificacion.dominio.clasificacion;

/** Qué operación cambió la clasificación (viaja en el evento para que el consumidor sepa por qué). */
public enum MotivoClasificacion {
	MANUAL,
	AUTOMATICA,
	PROPAGACION,
	MASIVA,
	REINICIO,
	MIGRACION
}
