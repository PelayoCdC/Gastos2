/**
 * Agregado {@code Usuario}: la persona registrada, dueña de todos sus datos en el resto de contextos.
 *
 * <p>Invariantes: el email es único en el sistema (en su forma canónica, minúsculas y sin espacios)
 * y la contraseña nunca se guarda en claro, solo su hash. Al registrarse se emite
 * {@link es.gastos.identidad.dominio.usuario.UsuarioRegistrado}, del que depende la siembra del
 * catálogo inicial en el contexto de clasificación.
 */
package es.gastos.identidad.dominio.usuario;
