/**
 * LÓGICA DE CONTROLADORA DE AUDITORÍA Y TRAZABILIDAD AVANZADA v2.5
 * @author Autor: Yessalim Salazar
 * @version 2.5 (Filtros Temporales, Línea de Tiempo e Integración de Bajas SGC)
 */

// =========================================================================
// 1. CONTROLADORES DE INTERFAZ DINÁMICA (UI)
// =========================================================================

// INTERRUPTOR DE INTERFAZ: Muestra/Oculta rango de fechas según el reporte seleccionado
document.getElementById('tipoReporte').addEventListener('change', function(e) {
    const seccionFechas = document.getElementById('seccionFechas');
    if (e.target.value === 'HISTORIAL') {
        seccionFechas.classList.remove('d-none');
    } else {
        seccionFechas.classList.add('d-none');
        // Limpieza preventiva de calendarios al replegarse
        document.getElementById('fechaDesde').value = '';
        document.getElementById('fechaHasta').value = '';
    }
});

// =========================================================================
// 2. FUNCIÓN CORE: CONSULTA INTEGRADA DE REGISTROS (MÁSTER Y TRAZABILIDAD)
// =========================================================================
async function ejecutarConsulta(event) {
    event.preventDefault(); // Frena la recarga clásica del formulario

    // Instanciamos componentes lógicos de entrada
    const idBolson = document.getElementById('idBolson').value.trim().toUpperCase();
    const tipoReporte = document.getElementById('tipoReporte').value;
    const fechaDesde = document.getElementById('fechaDesde').value;
    const fechaHasta = document.getElementById('fechaHasta').value;

    // Instanciamos componentes del DOM para inyección visual
    const panelResultado = document.getElementById('panelResultado');
    const containerDinamico = document.getElementById('containerDinamico');
    const botonSubmit = event.target.querySelector('button[type="submit"]');

    // BLINDAJE UI: Desactivación preventiva del botón de búsqueda
    if (botonSubmit) {
        botonSubmit.disabled = true;
        botonSubmit.innerHTML = "⏳ BUSCANDO...";
    }

    // VALIDACIÓN LOCAL 1: Expresión regular reglamentaria del SGC
    const regexFormatoBolson = /^ENV-\d{2}-\d{4}$/;
    if (!regexFormatoBolson.test(idBolson)) {
        renderizarEstadoAlerta("🚨", "FORMATO ERRONEO", `El código '${idBolson}' no cumple con la norma de planta (ENV-XX-XXXX).`, "alert-danger");
        if (botonSubmit) {
            botonSubmit.disabled = false;
            botonSubmit.innerHTML = "🔎 CONSULTAR TRAZABILIDAD";
        }
        return;
    }

    // Feedback visual inmediato de procesamiento
    panelResultado.className = "alert alert-warning shadow-sm p-4 h-100 animate-pulse";
    containerDinamico.innerHTML = `
        <div class="fs-1 mb-3">⏳</div>
        <h4 class="fw-bold">EXTRAYENDO REGISTROS REGULATORIOS...</h4>
        <p class="mb-0 fs-5 text-muted">Consultando trazas e índices cronológicos en MySQL...</p>
    `;

    try {
        if (tipoReporte === "ACTUAL") {
            // FLUJO A: Estado actual unificado
            const response = await fetch(`http://localhost:8080/api/bolsones/consultar/${idBolson}`);
            const data = await response.json();

            if (!response.ok || data.status === "ERROR") throw new Error(data.mensaje);

            panelResultado.className = "alert alert-success shadow-sm p-4 h-100 text-start";

            // CASO A.1: El bulto ya está destruido/obsoleto
            if (data.estado === "OBSOLETO") {
                panelResultado.className = "alert alert-dark shadow-sm p-4 h-100 text-start";
                containerDinamico.innerHTML = `
                    <h4 class="fw-bold text-danger text-center mb-3">❌ ENVASE FUERA DE SERVICIO</h4>
                    <div class="border-top pt-3 fs-5">
                        <strong>ID Envase:</strong> <span class="badge bg-danger">${data.idBolson}</span><br>
                        <strong>Ubicación Actual:</strong> <span class="text-white bg-dark px-2 rounded">${data.ubicacionActual}</span><br>
                        <strong>Estado SGC:</strong> <span class="text-danger fw-bold">OBSOLETO / DECOMISADO</span>
                        <hr class="my-3">
                        <p class="small text-muted mb-0 text-center">Este contenedor fue retirado por control de calidad. Las llaves de pesaje están de baja.</p>
                    </div>
                `;
                return;
            }

            // CASO A.2: El contenedor está limpio y vacío (DISPONIBLE). Permitimos la baja.
            if (!data.loteActual) {
                containerDinamico.innerHTML = `
                    <h4 class="fw-bold text-success text-center mb-3">📦 CONTENEDOR DISPONIBLE</h4>
                    <div class="border-top pt-3 fs-5 mb-4">
                        <strong>ID Envase:</strong> <span class="badge bg-dark">${data.idBolson}</span><br>
                        <strong>Ubicación Actual:</strong> <span class="text-secondary fw-bold">${data.ubicacionActual}</span><br>
                        <strong>Estado de Carga:</strong> <span class="text-info fw-bold">ALTA LIMPIA / DISPONIBLE</span>
                    </div>
                    <button class="btn btn-danger btn-sm w-100 fw-bold py-2 shadow-sm" onclick="solicitarBajaContenedor('${data.idBolson}')">
                        🚨 DECOMISAR Y DAR DE BAJA ENVASE
                    </button>
                `;
            } else {
                // CASO A.3: El contenedor posee un lote activo. Bloqueamos la baja desde la interfaz.
                containerDinamico.innerHTML = `
                    <h4 class="fw-bold text-success text-center mb-3">📝 TRAZABILIDAD ENCONTRADA</h4>
                    <div class="border-top pt-3 fs-5 mb-4">
                        <strong>Contenedor ID:</strong> <span class="badge bg-dark">${data.idBolson}</span><br>
                        <strong>Lote Activo:</strong> <span class="badge bg-success">${data.loteActual.idLote}</span><br>
                        <strong>Peso Neto:</strong> <span class="fw-bold">${data.loteActual.pesoNeto} KG</span><br>
                        <strong>Ubicación Logística:</strong> <span class="text-secondary fw-bold">${data.ubicacionActual}</span>
                        <hr class="my-3">
                        <button id="btnReimprimir" class="btn btn-warning w-100 fw-bold fs-5 py-2" onclick="reimprimirLotePorDeterioro('${data.idBolson}')">
                            🖨️ REIMPRIMIR ETIQUETA ACTIVA
                        </button>
                    </div>
                    <div class="alert alert-warning border border-warning text-center small mb-0 py-2">
                        ⚠️ <strong>Restricción SGC:</strong> Este envase no puede ser dado de baja porque contiene material activo en tránsito. Debe registrar su vaciado en Tolva previamente.
                    </div>
                `;
            }

        } else {
            // -----------------------------------------------------------------
            // FLUJO B: HISTORIAL COMPLETO DE USOS (LÍNEA DE TIEMPO)
            // -----------------------------------------------------------------
            let url = `http://localhost:8080/api/bolsones/historial/${idBolson}`;
            if (fechaDesde && fechaHasta) {
                url += `?desde=${fechaDesde}&hasta=${fechaHasta}`;
            }

            const response = await fetch(url);
            const registros = await response.json();

            if (!response.ok) throw new Error("Fallo al recuperar la línea de tiempo del SGC.");

            if (registros.length === 0) {
                panelResultado.className = "alert alert-info shadow-sm p-4 h-100 text-center";
                containerDinamico.innerHTML = `
                    <div class="fs-1 mb-3">📆</div>
                    <h4 class="fw-bold">SIN MOVIMIENTOS EN EL PERIODO</h4>
                    <p class="mb-0 text-muted">El contenedor no registra operaciones entre las fechas seleccionadas en el calendario.</p>
                `;
                return;
            }

            // Cambiamos el contenedor a blanco con scroll para listas de flujo extensas
            panelResultado.className = "alert alert-light border shadow-sm p-4 h-100 text-start overflow-auto";

            let htmlTimeline = `<h4 class="fw-bold text-dark text-center mb-3">📊 HISTORIAL DE USOS (LÍNEA DE TIEMPO)</h4>`;
            htmlTimeline += `<div class="list-group shadow-sm">`;

            registros.forEach(log => {
                const fechaFormateada = new Date(log.fechaMovimiento).toLocaleString('es-AR');
                const loteBadge = log.loteAsociado ? `<span class="badge bg-success small">${log.loteAsociado.idLote}</span>` : `<span class="badge bg-secondary small">Sin Lote</span>`;

                htmlTimeline += `
                    <div class="list-group-item list-group-item-action py-3">
                        <div class="d-flex w-100 justify-content-between">
                            <h5 class="mb-1 text-primary fw-bold">⚙️ ${log.operacion}</h5>
                            <small class="text-muted font-monospace">${fechaFormateada}</small>
                        </div>
                        <p class="mb-1 text-dark small">${log.detallesAuditoria || log.detalleAuditoria}</p>
                        <small class="text-muted">
                            Lote: ${loteBadge} | 
                            Ruta: <strong>${log.ubicacionOrigen}</strong> ➡️ <strong>${log.ubicacionDestino}</strong>
                        </small>
                    </div>
                `;
            });

            htmlTimeline += `</div>`;
            containerDinamico.innerHTML = htmlTimeline;
        }

    } catch (error) {
        renderizarEstadoAlerta("🚨", "SIN REGISTROS", error.message, "alert-danger");
    } finally {
        // BUENAS PRÁCTICAS: El bloque finally siempre libera el control del botón al finalizar la red
        if (botonSubmit) {
            botonSubmit.disabled = false;
            botonSubmit.innerHTML = "🔎 CONSULTAR TRAZABILIDAD";
        }
    }
}

// =========================================================================
// 3. ACCIONES COMPLEMENTARIAS (REIMPRESIÓN Y BAJAS DEFINITIVAS)
// =========================================================================

/**
 * Dispara la orden de impresión térmica al servidor local de la planta.
 */
async function reimprimirLotePorDeterioro(idBolson) {
    const btn = document.getElementById('btnReimprimir');
    if (!btn) return;

    try {
        btn.disabled = true;
        btn.className = "btn btn-secondary w-100 fw-bold fs-5 py-2 mt-2 animate-pulse";
        btn.innerText = "⚡ ENVIANDO A TICKETERA THERMAL...";

        const response = await fetch(`http://localhost:8080/api/bolsones/reimprimir/${idBolson}`);
        if (!response.ok) throw new Error("Fallo en la cola de impresión local.");

        btn.className = "btn btn-success w-100 fw-bold fs-5 py-2 mt-2";
        btn.innerText = "✅ ¡ETIQUETA REGENERADA!";
    } catch (error) {
        alert("ERROR DE IMPRESIÓN: " + error.message);
    } finally {
        setTimeout(() => {
            if (btn) {
                btn.disabled = false;
                btn.className = "btn btn-warning w-100 fw-bold fs-5 py-2 mt-2";
                btn.innerText = "🖨️ REIMPRIMIR ETIQUETA ACTIVA";
            }
        }, 3000);
    }
}

/**
 * Procesa el decomiso definitivo de un bulto bajo norma de calidad.
 */
async function solicitarBajaContenedor(idBolson) {
    const confirmacion = confirm(`⚠️ ¿Está absolutamente seguro de retirar el bulto ${idBolson} del circuito productivo?\nEsta operación es inmutable.`);
    if (!confirmacion) return;

    const motivo = prompt("📋 Ingrese el motivo de descarte obligatorio para auditoría SGC:");
    if (!motivo || motivo.trim() === "") {
        alert("Operación cancelada: El motivo de descarte es requerido para la trazabilidad ISO.");
        return;
    }

    try {
        const response = await fetch(`http://localhost:8080/api/bolsones/baja?idBolson=${idBolson}&motivo=${encodeURIComponent(motivo)}`, {
            method: 'POST'
        });
        const data = await response.json();

        if (!response.ok || data.status === "ERROR") throw new Error(data.mensaje);

        alert(`✅ DECOMISO EXITOSO: ${data.mensaje}`);

        // Auto-ejecutamos la búsqueda para actualizar la pantalla al instante al estado OBSOLETO
        document.getElementById('formAuditoria').dispatchEvent(new Event('submit'));

    } catch (error) {
        alert("🚨 ERROR AL PROCESAR DECOMISO: " + error.message);
    }
}

// =========================================================================
// 4. HELPERS VISUALES Y ENLACES UNOBTRUSIVE JAVASCRIPT
// =========================================================================

function renderizarEstadoAlerta(icon, titulo, mensaje, claseBootstrap) {
    const panelResultado = document.getElementById('panelResultado');
    const containerDinamico = document.getElementById('containerDinamico');
    panelResultado.className = `alert ${claseBootstrap} shadow-sm p-4 text-center h-100`;
    containerDinamico.innerHTML = `
        <div class="fs-1 mb-3">${icon}</div>
        <h4 class="fw-bold">${titulo}</h4>
        <p class="mb-0 fs-5">${mensaje}</p>
    `;
}

// Enlace de escucha seguro al formulario
document.getElementById('formAuditoria').addEventListener('submit', ejecutarConsulta);

// Forzado automático a mayúsculas sin bloquear ráfagas de pistolas ópticas
document.getElementById('idBolson').addEventListener('input', e => e.target.value = e.target.value.toUpperCase());