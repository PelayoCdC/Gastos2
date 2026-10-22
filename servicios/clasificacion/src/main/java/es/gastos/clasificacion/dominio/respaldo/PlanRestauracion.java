package es.gastos.clasificacion.dominio.respaldo;

import es.gastos.clasificacion.dominio.catalogo.Categoria;
import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.Color;
import es.gastos.clasificacion.dominio.catalogo.Etiqueta;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.clasificacion.dominio.catalogo.NombreCatalogo;
import es.gastos.clasificacion.dominio.catalogo.Ocurrencia;
import es.gastos.clasificacion.dominio.catalogo.Ocurrencias;
import es.gastos.clasificacion.dominio.clasificacion.ClasificacionTransaccion;
import es.gastos.clasificacion.dominio.clasificacion.TransaccionId;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traduce el catálogo y las clasificaciones de un respaldo a agregados válidos. Reglas:
 * <ul>
 *   <li><b>Nombres repetidos</b> al normalizarlos (ahora deben ser únicos, docs/adr/0013): la categoría
 *   o etiqueta se fusiona en la primera; sus ocurrencias se suman y lo que la usaba pasa a la
 *   superviviente.</li>
 *   <li><b>Prioridad</b> = posición × 10, en el orden del respaldo.</li>
 *   <li>Un color que no es {@code #rrggbb} se quita; una ocurrencia vacía o demasiado larga, también.</li>
 *   <li>Una referencia a una categoría o etiqueta que no está en el respaldo se quita.</li>
 *   <li>Se crea la copia de <b>todas</b> las transacciones, tengan o no clasificación: clasificacion
 *   necesita conocerlas para clasificarlas después o propagar.</li>
 * </ul>
 */
public final class PlanRestauracion {

	static final int PASO_PRIORIDAD = 10;

	private PlanRestauracion() {
	}

	public record CategoriaRespaldo(UUID id, String nombre, String color, String descripcion, List<String> ocurrencias) {
	}

	public record EtiquetaRespaldo(UUID id, String nombre, String descripcion, List<String> ocurrencias, UUID categoriaId) {
	}

	public record TransaccionRespaldo(UUID id, UUID documentoId, String concepto, UUID categoriaId, List<UUID> etiquetaIds) {
	}

	public record Plan(List<Categoria> categorias, List<Etiqueta> etiquetas, List<ClasificacionTransaccion> clasificaciones,
			int clasificadas, List<String> anomalias) {
	}

	public static Plan de(UsuarioId usuario, List<CategoriaRespaldo> categorias, List<EtiquetaRespaldo> etiquetas,
			List<TransaccionRespaldo> transacciones) {
		List<String> anomalias = new ArrayList<>();

		// Categorías: una por nombre normalizado
		Map<UUID, UUID> categoriaFinal = new HashMap<>();
		Map<String, CategoriaRespaldo> categoriasPorNombre = new LinkedHashMap<>();
		Map<String, List<String>> ocurrenciasCategoria = new HashMap<>();
		for (CategoriaRespaldo c : categorias) {
			if (c.id() == null || categoriaFinal.containsKey(c.id())) {
				anomalias.add("Categoria " + c.id() + " repetida o sin id: se ignora");
				continue;
			}
			NombreCatalogo nombre = nombre(c.nombre(), "Categoria sin nombre", anomalias);
			CategoriaRespaldo existente = categoriasPorNombre.putIfAbsent(nombre.normalizado(), c);
			if (existente != null) {
				anomalias.add("La categoria '" + c.nombre() + "' repite el nombre de '" + existente.nombre() + "': se fusionan");
				categoriaFinal.put(c.id(), existente.id());
				ocurrenciasCategoria.get(nombre.normalizado()).addAll(lista(c.ocurrencias()));
				continue;
			}
			categoriaFinal.put(c.id(), c.id());
			ocurrenciasCategoria.put(nombre.normalizado(), new ArrayList<>(lista(c.ocurrencias())));
		}
		List<Categoria> categoriasFinales = new ArrayList<>();
		for (Map.Entry<String, CategoriaRespaldo> entrada : categoriasPorNombre.entrySet()) {
			CategoriaRespaldo c = entrada.getValue();
			categoriasFinales.add(Categoria.crear(new CategoriaId(c.id()), usuario, nombre(c.nombre(), "Categoria sin nombre", new ArrayList<>()),
					color(c.color(), c.nombre(), anomalias), recortar(c.descripcion(), Categoria.LONGITUD_DESCRIPCION),
					ocurrencias(ocurrenciasCategoria.get(entrada.getKey()), c.nombre(), anomalias),
					(categoriasFinales.size() + 1) * PASO_PRIORIDAD));
		}

		// Etiquetas: una por nombre normalizado, con la categoría ya fusionada
		Map<UUID, UUID> etiquetaFinal = new HashMap<>();
		Map<String, EtiquetaRespaldo> etiquetasPorNombre = new LinkedHashMap<>();
		Map<String, List<String>> ocurrenciasEtiqueta = new HashMap<>();
		for (EtiquetaRespaldo e : etiquetas) {
			if (e.id() == null || etiquetaFinal.containsKey(e.id())) {
				anomalias.add("Etiqueta " + e.id() + " repetida o sin id: se ignora");
				continue;
			}
			NombreCatalogo nombre = nombre(e.nombre(), "Etiqueta sin nombre", anomalias);
			EtiquetaRespaldo existente = etiquetasPorNombre.putIfAbsent(nombre.normalizado(), e);
			if (existente != null) {
				anomalias.add("La etiqueta '" + e.nombre() + "' repite el nombre de '" + existente.nombre() + "': se fusionan");
				etiquetaFinal.put(e.id(), existente.id());
				ocurrenciasEtiqueta.get(nombre.normalizado()).addAll(lista(e.ocurrencias()));
				continue;
			}
			etiquetaFinal.put(e.id(), e.id());
			ocurrenciasEtiqueta.put(nombre.normalizado(), new ArrayList<>(lista(e.ocurrencias())));
		}
		List<Etiqueta> etiquetasFinales = new ArrayList<>();
		for (Map.Entry<String, EtiquetaRespaldo> entrada : etiquetasPorNombre.entrySet()) {
			EtiquetaRespaldo e = entrada.getValue();
			UUID categoria = referencia(e.categoriaId(), categoriaFinal, anomalias, "La etiqueta '" + e.nombre() + "' usa una categoria");
			etiquetasFinales.add(Etiqueta.crear(new EtiquetaId(e.id()), usuario, nombre(e.nombre(), "Etiqueta sin nombre", new ArrayList<>()),
					recortar(e.descripcion(), Categoria.LONGITUD_DESCRIPCION), ocurrencias(ocurrenciasEtiqueta.get(entrada.getKey()), e.nombre(), anomalias),
					categoria == null ? null : new CategoriaId(categoria), (etiquetasFinales.size() + 1) * PASO_PRIORIDAD));
		}

		// Clasificaciones de todas las transacciones
		List<ClasificacionTransaccion> clasificaciones = new ArrayList<>();
		Set<UUID> vistas = new HashSet<>();
		int clasificadas = 0;
		int referenciasRotas = 0;
		for (TransaccionRespaldo t : transacciones) {
			if (t.id() == null || !vistas.add(t.id())) {
				continue;
			}
			UUID categoria = t.categoriaId() == null ? null : categoriaFinal.get(t.categoriaId());
			referenciasRotas += t.categoriaId() != null && categoria == null ? 1 : 0;
			Set<EtiquetaId> asignadas = new LinkedHashSet<>();
			for (UUID etiqueta : lista(t.etiquetaIds())) {
				UUID finalId = etiquetaFinal.get(etiqueta);
				if (finalId == null) {
					referenciasRotas++;
				} else {
					asignadas.add(new EtiquetaId(finalId));
				}
			}
			ClasificacionTransaccion clasificacion = ClasificacionTransaccion.restaurar(new TransaccionId(t.id()), usuario, t.documentoId(),
					t.concepto(), categoria == null ? null : new CategoriaId(categoria), List.copyOf(asignadas));
			clasificadas += clasificacion.eventosPendientes().isEmpty() ? 0 : 1;
			clasificaciones.add(clasificacion);
		}
		if (referenciasRotas > 0) {
			anomalias.add(referenciasRotas + " asignaciones a categorias o etiquetas que no estan en el respaldo: se quitan");
		}

		return new Plan(List.copyOf(categoriasFinales), List.copyOf(etiquetasFinales), List.copyOf(clasificaciones), clasificadas,
				List.copyOf(anomalias));
	}

	private static NombreCatalogo nombre(String nombre, String porDefecto, List<String> anomalias) {
		if (nombre == null || nombre.isBlank()) {
			anomalias.add("Hay un elemento sin nombre: se llama '" + porDefecto + "'");
			return new NombreCatalogo(porDefecto);
		}
		return new NombreCatalogo(recortar(nombre, NombreCatalogo.LONGITUD_MAXIMA));
	}

	private static Color color(String color, String nombre, List<String> anomalias) {
		try {
			return Color.opcional(color);
		} catch (ExcepcionDominio e) {
			anomalias.add("La categoria '" + nombre + "' tiene un color no valido (" + color + "): se quita");
			return null;
		}
	}

	private static Ocurrencias ocurrencias(List<String> textos, String nombre, List<String> anomalias) {
		List<Ocurrencia> validas = new ArrayList<>();
		for (String texto : textos) {
			if (texto == null || texto.isBlank()) {
				continue;
			}
			try {
				validas.add(new Ocurrencia(texto));
			} catch (ExcepcionDominio e) {
				anomalias.add("'" + nombre + "' tiene una ocurrencia no valida (" + e.getMessage() + "): se quita");
			}
		}
		return new Ocurrencias(validas);
	}

	private static UUID referencia(UUID id, Map<UUID, UUID> finales, List<String> anomalias, String descripcion) {
		if (id == null) {
			return null;
		}
		UUID finalId = finales.get(id);
		if (finalId == null) {
			anomalias.add(descripcion + " que no esta en el respaldo: se quita");
		}
		return finalId;
	}

	private static <T> List<T> lista(List<T> lista) {
		return lista == null ? List.of() : lista;
	}

	private static String recortar(String texto, int longitud) {
		if (texto == null) {
			return null;
		}
		String limpio = texto.strip();
		return limpio.length() <= longitud ? limpio : limpio.substring(0, longitud);
	}
}
