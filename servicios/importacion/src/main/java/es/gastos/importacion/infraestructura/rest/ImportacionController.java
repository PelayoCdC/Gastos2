package es.gastos.importacion.infraestructura.rest;

import es.gastos.compartido.web.UsuarioActual;
import es.gastos.importacion.aplicacion.GestionFormatos;
import es.gastos.importacion.aplicacion.GestionFormatos.DatosFormato;
import es.gastos.importacion.aplicacion.ImportarExtractos;
import es.gastos.importacion.dominio.extracto.Previsualizacion;
import es.gastos.importacion.dominio.formato.FormatoId;
import es.gastos.kernel.dominio.ExcepcionDominio;
import es.gastos.kernel.dominio.UsuarioId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Formatos de banco, previsualización de un fichero e importación de extractos. Los ficheros llegan
 * como multipart (campo {@code archivo}); no se guardan en ningún sitio.
 */
@RestController
class ImportacionController {

	record PeticionFormato(@NotBlank String nombre, Integer filaInicio, Integer columnaConcepto, Integer columnaImporte,
			Integer columnaFechaContable, Integer columnaFechaValor, Integer columnaSaldo, String formatoFecha) {
	}

	private final GestionFormatos formatos;

	private final ImportarExtractos importar;

	ImportacionController(GestionFormatos formatos, ImportarExtractos importar) {
		this.formatos = formatos;
		this.importar = importar;
	}

	@GetMapping("/formatos")
	List<DatosFormato> formatos(@UsuarioActual UsuarioId usuario) {
		return this.formatos.listar(usuario);
	}

	@GetMapping("/formatos/{id}")
	DatosFormato formato(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		return this.formatos.consultar(usuario, new FormatoId(id));
	}

	@PostMapping("/formatos")
	ResponseEntity<DatosFormato> crearFormato(@UsuarioActual UsuarioId usuario, @Valid @RequestBody PeticionFormato peticion) {
		DatosFormato creado = this.formatos.crear(usuario, datos(peticion));
		return ResponseEntity.created(URI.create("/formatos/" + creado.id())).body(creado);
	}

	@PutMapping("/formatos/{id}")
	DatosFormato modificarFormato(@UsuarioActual UsuarioId usuario, @PathVariable UUID id,
			@Valid @RequestBody PeticionFormato peticion) {
		return this.formatos.modificar(usuario, new FormatoId(id), datos(peticion));
	}

	@DeleteMapping("/formatos/{id}")
	ResponseEntity<Void> eliminarFormato(@UsuarioActual UsuarioId usuario, @PathVariable UUID id) {
		this.formatos.eliminar(usuario, new FormatoId(id));
		return ResponseEntity.noContent().build();
	}

	/** Primeras filas del fichero para elegir columnas al definir un formato. No necesita formato ni cuenta. */
	@PostMapping(path = "/previsualizaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	Previsualizacion previsualizar(@RequestPart("archivo") MultipartFile archivo) {
		return this.importar.previsualizar(archivo.getOriginalFilename(), contenido(archivo));
	}

	/**
	 * Importa un extracto en una cuenta. El documento y las transacciones los crea movimientos; la
	 * respuesta lleva su id, cuántas filas se importaron, las duplicadas y las que tenían errores.
	 */
	@PostMapping(path = "/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ImportarExtractos.Resultado> importar(@UsuarioActual UsuarioId usuario,
			@RequestPart("archivo") MultipartFile archivo, @RequestParam UUID formatoId, @RequestParam UUID cuentaId,
			@RequestParam(required = false) String nombre) {
		ImportarExtractos.Resultado resultado = this.importar.importar(new ImportarExtractos.Comando(usuario, formatoId, cuentaId,
				nombre, archivo.getOriginalFilename(), contenido(archivo)));
		return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
	}

	private static GestionFormatos.Datos datos(PeticionFormato peticion) {
		return new GestionFormatos.Datos(peticion.nombre(), peticion.filaInicio(), peticion.columnaConcepto(),
				peticion.columnaImporte(), peticion.columnaFechaContable(), peticion.columnaFechaValor(), peticion.columnaSaldo(),
				peticion.formatoFecha());
	}

	private static byte[] contenido(MultipartFile archivo) {
		try {
			return archivo.getBytes();
		} catch (IOException e) {
			throw new ExcepcionDominio("FICHERO_ILEGIBLE", "No se pudo leer el archivo subido");
		}
	}
}
