#!/usr/bin/env node
// Compara dos respaldos v1 (docs/adr/0020): el exportado del sistema de origen y el exportado del de
// destino después de restaurarlo. Sirve para verificar una migración (back antiguo -> Gastos2) o una
// copia de seguridad (Gastos2 -> Gastos2).
//
// Uso: node scripts/comparar-respaldos.mjs origen.json destino.json
//
// Compara, en este orden:
//   1. conteos de cada sección;
//   2. ids que están en uno y no en el otro (las fusiones por nombre repetido hacen desaparecer ids, y se
//      listan para revisarlas);
//   3. suma de importes por cuenta y mes, al céntimo;
//   4. por transacción: hechos (fecha, concepto, importe) y asignaciones (categoría y etiquetas, con los
//      ids traducidos por nombre para que las fusiones no cuenten como diferencias);
//   5. catálogo: nombre, ocurrencias y cascada de cada categoría y etiqueta, y su orden (la prioridad).
// Sale con código 1 si hay diferencias que no se explican por anomalías conocidas.
import { readFileSync } from 'node:fs';

const [rutaOrigen, rutaDestino] = process.argv.slice(2);
if (!rutaOrigen || !rutaDestino) {
  console.error('Uso: node scripts/comparar-respaldos.mjs origen.json destino.json');
  process.exit(2);
}
const leer = (ruta) => JSON.parse(readFileSync(ruta, 'utf8').replace(/^﻿/, ''));
const origen = leer(rutaOrigen);
const destino = leer(rutaDestino);

let diferencias = 0;
const informar = (ok, texto) => {
  console.log(`${ok ? 'OK   ' : 'DIF  '} ${texto}`);
  if (!ok) diferencias++;
};
const normalizar = (texto) => (texto ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/\s+/g, ' ').trim();
const centimos = (valor) => Math.round(Number(valor ?? 0) * 100);

// 1. Conteos
const secciones = ['cuentas', 'categorias', 'etiquetas', 'configuracionesImportacion', 'documentos', 'transacciones'];
for (const s of secciones) {
  const a = (origen[s] ?? []).length;
  const b = (destino[s] ?? []).length;
  informar(a === b, `${s}: ${a} -> ${b}`);
}

// 2. Ids
for (const s of secciones) {
  const idsDestino = new Set((destino[s] ?? []).map((x) => x.id));
  const perdidos = (origen[s] ?? []).filter((x) => !idsDestino.has(x.id));
  if (perdidos.length > 0) {
    informar(false, `${s}: ${perdidos.length} ids del origen no estan en el destino: ${perdidos.slice(0, 5).map((x) => x.nombre ?? x.concepto ?? x.id).join(', ')}${perdidos.length > 5 ? '...' : ''}`);
  }
}

// 3. Sumas por cuenta (por nombre, para que una fusión de cuentas homónimas no cuente) y mes
const nombreCuenta = (respaldo) => new Map((respaldo.cuentas ?? []).map((c) => [c.id, normalizar(c.nombre)]));
const sumas = (respaldo) => {
  const cuentas = nombreCuenta(respaldo);
  const resultado = new Map();
  for (const t of respaldo.transacciones ?? []) {
    const clave = `${cuentas.get(t.idCuenta) ?? '(sin cuenta)'} | ${(t.fechaContable ?? '').slice(0, 7)}`;
    resultado.set(clave, (resultado.get(clave) ?? 0) + centimos(t.importe));
  }
  return resultado;
};
const sumasOrigen = sumas(origen);
const sumasDestino = sumas(destino);
const claves = [...new Set([...sumasOrigen.keys(), ...sumasDestino.keys()])].sort();
const sumasDistintas = claves.filter((k) => (sumasOrigen.get(k) ?? 0) !== (sumasDestino.get(k) ?? 0));
informar(sumasDistintas.length === 0, `sumas por cuenta y mes (${claves.length} grupos)${sumasDistintas.length ? ': ' + sumasDistintas.slice(0, 5).join('; ') : ''}`);
const total = (r) => (r.transacciones ?? []).reduce((s, t) => s + centimos(t.importe), 0) / 100;
informar(total(origen) === total(destino), `suma total: ${total(origen).toFixed(2)} -> ${total(destino).toFixed(2)}`);

// 4. Transacciones: hechos y asignaciones (por nombre de categoría y etiqueta)
const nombres = (respaldo, seccion) => new Map((respaldo[seccion] ?? []).map((x) => [x.id, normalizar(x.nombre)]));
const describir = (respaldo) => {
  const categorias = nombres(respaldo, 'categorias');
  const etiquetas = nombres(respaldo, 'etiquetas');
  return new Map((respaldo.transacciones ?? []).map((t) => [t.id, {
    hechos: `${t.fechaContable} | ${normalizar(t.concepto)} | ${centimos(t.importe)}`,
    categoria: categorias.get(t.idCategoria) ?? null,
    etiquetas: [...new Set((t.idsEtiquetas ?? []).map((e) => etiquetas.get(e)).filter(Boolean))].sort().join(', '),
  }]));
};
const transOrigen = describir(origen);
const transDestino = describir(destino);
let hechosDistintos = 0;
let categoriasDistintas = 0;
let etiquetasDistintas = 0;
const ejemplos = [];
for (const [id, a] of transOrigen) {
  const b = transDestino.get(id);
  if (!b) continue;
  if (a.hechos !== b.hechos) { hechosDistintos++; ejemplos.push(`hechos ${a.hechos} -> ${b.hechos}`); }
  if (a.categoria !== b.categoria) { categoriasDistintas++; ejemplos.push(`categoria de ${a.hechos}: ${a.categoria} -> ${b.categoria}`); }
  if (a.etiquetas !== b.etiquetas) { etiquetasDistintas++; ejemplos.push(`etiquetas de ${a.hechos}: [${a.etiquetas}] -> [${b.etiquetas}]`); }
}
informar(hechosDistintos === 0, `hechos de las transacciones (fecha, concepto, importe): ${hechosDistintos} distintas`);
informar(categoriasDistintas === 0, `categoria asignada: ${categoriasDistintas} distintas`);
informar(etiquetasDistintas === 0, `etiquetas asignadas: ${etiquetasDistintas} distintas`);
ejemplos.slice(0, 10).forEach((e) => console.log(`        ${e}`));

// 5. Catálogo: definición y orden (la posición es la prioridad)
const catalogo = (respaldo, seccion) => {
  const categorias = nombres(respaldo, 'categorias');
  return (respaldo[seccion] ?? []).map((x) =>
    `${normalizar(x.nombre)} [${(x.ocurrencias ?? []).map(normalizar).sort().join(', ')}]${seccion === 'etiquetas' ? ' -> ' + (categorias.get(x.idCategoria) ?? '-') : ''}`);
};
for (const seccion of ['categorias', 'etiquetas']) {
  const a = catalogo(origen, seccion);
  const b = catalogo(destino, seccion);
  const definiciones = JSON.stringify([...a].sort()) === JSON.stringify([...b].sort());
  informar(definiciones, `${seccion}: nombres, ocurrencias y cascada`);
  informar(JSON.stringify(a.map((x) => x.split(' [')[0])) === JSON.stringify(b.map((x) => x.split(' [')[0])), `${seccion}: mismo orden (prioridad)`);
}

console.log(diferencias === 0 ? '\nLos respaldos son equivalentes.' : `\n${diferencias} diferencias: revisalas junto a las anomalias de la restauracion.`);
process.exit(diferencias === 0 ? 0 : 1);
