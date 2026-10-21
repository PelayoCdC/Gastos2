package es.gastos.identidad.aplicacion;

import es.gastos.identidad.dominio.usuario.Usuario;

/** Puerto de salida: emisión del token de acceso (adaptador JWT RS256, docs/adr/0008). */
public interface EmisorTokens {

	TokenEmitido emitir(Usuario usuario);

	record TokenEmitido(String token, long expiraEnSegundos) {
	}
}
