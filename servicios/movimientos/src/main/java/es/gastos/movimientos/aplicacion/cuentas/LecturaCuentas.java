package es.gastos.movimientos.aplicacion.cuentas;

import es.gastos.kernel.dominio.UsuarioId;
import es.gastos.movimientos.dominio.cuenta.CuentaId;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de lectura de cuentas: consultas que no necesitan cargar agregados (el saldo y el conteo
 * se calculan en la base de datos). Es el lado de lectura dentro del propio servicio; lo que combina
 * datos de varios contextos lo responde el servicio consultas.
 */
public interface LecturaCuentas {

	List<ResumenCuenta> listar(UsuarioId usuario);

	Optional<ResumenCuenta> resumen(UsuarioId usuario, CuentaId cuenta);
}
