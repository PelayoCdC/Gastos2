package es.gastos.clasificacion.dominio.clasificacion;

/**
 * Por qué una transacción tiene una categoría o una etiqueta. Decide qué se conserva al reetiquetar
 * (docs/adr/0017): solo AUTOMATICA y CASCADA se consideran reglas reaplicables; lo demás es una
 * decisión del usuario (o, en MIGRACION, no se sabe y en la duda se conserva).
 */
public enum OrigenAsignacion {
	/** Una ocurrencia del catálogo coincidió con el concepto. */
	AUTOMATICA,
	/** Categoría heredada de una etiqueta. */
	CASCADA,
	/** El usuario la puso a mano en esa transacción. */
	MANUAL,
	/** Copiada de una transacción similar. */
	PROPAGACION,
	/** Asignada a una selección de transacciones. */
	MASIVA,
	/** Venía así del back antiguo. */
	MIGRACION;

	public boolean esAutomatica() {
		return this == AUTOMATICA || this == CASCADA;
	}
}
