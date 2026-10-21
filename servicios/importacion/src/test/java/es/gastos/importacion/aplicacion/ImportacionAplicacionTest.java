package es.gastos.importacion.aplicacion;

import es.gastos.compartido.pruebas.dobles.GeneradorIdsSecuencial;
import es.gastos.compartido.pruebas.dobles.PublicadorEventosEnMemoria;
import es.gastos.compartido.pruebas.dobles.UnidadDeTrabajoDirecta;
import es.gastos.importacion.dominio.extracto.Celda;
import es.gastos.importacion.dominio.extracto.Hoja;
import es.gastos.importacion.dominio.formato.FormatoBancoEliminado;
import es.gastos.importacion.dominio.formato.FormatoBancoGuardado;
import es.gastos.importacion.dominio.formato.FormatoId;
import es.gastos.kernel.dominio.Conflicto;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.RecursoNoEncontrado;
import es.gastos.kernel.dominio.UsuarioId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Casos de uso de importación con dobles: sin base de datos, sin red y sin ficheros reales. */
class ImportacionAplicacionTest {

	private static final GestionFormatos.Datos UNICAJA = new GestionFormatos.Datos("Unicaja", 2, 1, 2, 0, null, 3, null);

	private final UsuarioId usuario = new UsuarioId(UUID.randomUUID());

	private final FormatosEnMemoria repositorio = new FormatosEnMemoria();

	private final PublicadorEventosEnMemoria publicador = new PublicadorEventosEnMemoria();

	private final GeneradorIdsSecuencial ids = new GeneradorIdsSecuencial();

	private final List<RegistroMovimientos.Extracto> enviados = new ArrayList<>();

	private final GestionFormatos formatos = new GestionFormatos(this.repositorio, this.publicador, new UnidadDeTrabajoDirecta(), this.ids);

	/** El lector "lee" cada línea del fichero como una fila separada por ';'. */
	private final LectorHojas lector = (nombre, contenido) -> new Hoja(new String(contenido).lines()
			.map(linea -> Arrays.stream(linea.split(";", -1)).map(Celda::texto).toList()).toList());

	private final RegistroMovimientos movimientos = (usuario, extracto) -> {
		this.enviados.add(extracto);
		return new RegistroMovimientos.Registro(extracto.extractoId(), "Julio", "julio.csv", extracto.lineas().size() - 1,
				List.of(new RegistroMovimientos.Duplicada(3, null, "NOMINA", null)));
	};

	private final ImportarExtractos importar = new ImportarExtractos(this.repositorio, this.lector, this.movimientos,
			new UnidadDeTrabajoDirecta(), this.ids);

	@Test
	void elNombreDelFormatoEsUnicoPorUsuarioEnSuFormaNormalizada() {
		GestionFormatos.DatosFormato creado = this.formatos.crear(this.usuario, UNICAJA);

		assertThat(creado.formatoFecha()).isEqualTo("dd/MM/yyyy");
		assertThatThrownBy(() -> this.formatos.crear(this.usuario, conNombre(" UNICAJA ")))
				.isInstanceOf(Conflicto.class).extracting(e -> ((Conflicto) e).codigo()).isEqualTo("NOMBRE_DUPLICADO");
		this.formatos.crear(new UsuarioId(UUID.randomUUID()), UNICAJA);

		// Modificarse a sí mismo con el mismo nombre no choca
		this.formatos.modificar(this.usuario, new FormatoId(creado.id()), conNombre("unicaja"));
		assertThat(this.publicador.deTipo(FormatoBancoGuardado.class)).hasSize(3);
	}

	@Test
	void losFormatosDeOtroUsuarioNoExisten() {
		FormatoId ajeno = new FormatoId(this.formatos.crear(new UsuarioId(UUID.randomUUID()), UNICAJA).id());

		assertThatThrownBy(() -> this.formatos.consultar(this.usuario, ajeno)).isInstanceOf(RecursoNoEncontrado.class);
		assertThatThrownBy(() -> this.formatos.eliminar(this.usuario, ajeno)).isInstanceOf(RecursoNoEncontrado.class);
		assertThatThrownBy(() -> this.importar.importar(comando(ajeno.valor(), "x"))).isInstanceOf(RecursoNoEncontrado.class);
		assertThat(this.enviados).isEmpty();
	}

	@Test
	void eliminarUnFormatoLoBorraYLoAnuncia() {
		FormatoId id = new FormatoId(this.formatos.crear(this.usuario, UNICAJA).id());

		this.formatos.eliminar(this.usuario, id);

		assertThat(this.formatos.listar(this.usuario)).isEmpty();
		assertThat(this.publicador.unico(FormatoBancoEliminado.class).formatoId()).isEqualTo(id);
	}

	@Test
	void importarEntregaLasLineasLimpiasAMovimientosYJuntaSusDuplicadosConLosErroresDeInterpretacion() {
		UUID formato = this.formatos.crear(this.usuario, UNICAJA).id();

		ImportarExtractos.Resultado resultado = this.importar.importar(comando(formato, """
				Fecha;Concepto;Importe;Saldo
				01/07/2026;COMPRA LUPA;-12,34;100
				02/07/2026;NOMINA;1.850,00;1950
				31/02/2026;IMPOSIBLE;-1;
				"""));

		RegistroMovimientos.Extracto enviado = this.enviados.getFirst();
		assertThat(enviado.lineas()).extracting(l -> l.concepto()).containsExactly("COMPRA LUPA", "NOMINA");
		assertThat(enviado.extractoId()).as("lo genera importacion: es la clave de idempotencia").isNotNull();
		assertThat(enviado.nombreArchivo()).isEqualTo("julio.csv");

		assertThat(resultado.documentoId()).isEqualTo(enviado.extractoId());
		assertThat(resultado.importadas()).isEqualTo(1);
		assertThat(resultado.duplicados()).extracting(RegistroMovimientos.Duplicada::fila).containsExactly(3);
		assertThat(resultado.errores()).extracting(e -> e.mensaje()).containsExactly("Fila 4: fecha con formato incorrecto: '31/02/2026'");
	}

	@Test
	void unFicheroVacioOSinCuentaSeRechazanAntesDeLlamarAMovimientos() {
		UUID formato = this.formatos.crear(this.usuario, UNICAJA).id();

		assertThatThrownBy(() -> this.importar.importar(comando(formato, ""))).isInstanceOf(ExcepcionDominio.class)
				.hasMessage("El fichero esta vacio");
		assertThatThrownBy(() -> this.importar.importar(new ImportarExtractos.Comando(this.usuario, formato, null, null, "a.csv",
				"x".getBytes()))).isInstanceOf(ExcepcionDominio.class);
		assertThat(this.enviados).isEmpty();
	}

	private ImportarExtractos.Comando comando(UUID formato, String contenido) {
		return new ImportarExtractos.Comando(this.usuario, formato, UUID.randomUUID(), null, "julio.csv", contenido.getBytes());
	}

	private static GestionFormatos.Datos conNombre(String nombre) {
		return new GestionFormatos.Datos(nombre, UNICAJA.filaInicio(), UNICAJA.columnaConcepto(), UNICAJA.columnaImporte(),
				UNICAJA.columnaFechaContable(), UNICAJA.columnaFechaValor(), UNICAJA.columnaSaldo(), UNICAJA.formatoFecha());
	}
}
