package es.gastos.clasificacion.infraestructura.eventos;

import es.gastos.clasificacion.dominio.catalogo.CategoriaId;
import es.gastos.clasificacion.dominio.catalogo.EtiquetaId;
import es.gastos.contratos.clasificacion.v1.TransaccionClasificada;
import es.gastos.compartido.mensajeria.TraductorEventos;
import es.gastos.contratos.EventoIntegracion;
import es.gastos.contratos.clasificacion.v1.CategoriaEliminada;
import es.gastos.contratos.clasificacion.v1.CategoriaGuardada;
import es.gastos.contratos.clasificacion.v1.EtiquetaEliminada;
import es.gastos.contratos.clasificacion.v1.EtiquetaGuardada;
import es.gastos.kernel.dominio.EventoDominio;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Traduce los eventos de dominio de clasificación a su lenguaje publicado (contratos clasificacion.v1). */
@Component
class TraductorEventosClasificacion implements TraductorEventos {

	@Override
	public Optional<EventoIntegracion> traducir(EventoDominio evento) {
		EventoIntegracion traducido = switch (evento) {
			case es.gastos.clasificacion.dominio.catalogo.CategoriaGuardada e -> new CategoriaGuardada(e.categoriaId().valor(),
					e.nombre().valor(), e.color() == null ? null : e.color().valor(), e.descripcion(), e.ocurrencias().textos(),
					e.prioridad());
			case es.gastos.clasificacion.dominio.catalogo.CategoriaEliminada e -> new CategoriaEliminada(e.categoriaId().valor());
			case es.gastos.clasificacion.dominio.catalogo.EtiquetaGuardada e -> new EtiquetaGuardada(e.etiquetaId().valor(),
					e.nombre().valor(), e.descripcion(), e.ocurrencias().textos(), categoria(e.categoriaId()), e.prioridad());
			case es.gastos.clasificacion.dominio.catalogo.EtiquetaEliminada e -> new EtiquetaEliminada(e.etiquetaId().valor());
			case es.gastos.clasificacion.dominio.clasificacion.TransaccionClasificada e -> new TransaccionClasificada(
					e.transaccionId().valor(), categoria(e.categoria()), e.etiquetas().stream().map(EtiquetaId::valor).toList(),
					e.motivo().name());
			default -> null;
		};
		return Optional.ofNullable(traducido);
	}

	private static UUID categoria(CategoriaId id) {
		return id == null ? null : id.valor();
	}
}
