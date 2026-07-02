/**
 * LÓGICA DE CONTROLADORA DE AUDITORÍA Y REIMPRESIÓN v1.0
 * @author Autor: Yessalim Salazar
 * @version 1.0 (Unobtrusive JS - Control de Trazabilidad)
 */

// =========================================================================
// 1. FUNCIÓN CORE: CONSULTA HISTÓRICO DE BASE DE DATOS
// =========================================================================
async function ejecutarConsulta(event) {
    event.preventDefault(); // Detiene recarga de pantalla

    const idBolson = document.getElementById('idBolson').value.trim().toUpperCase();
    const panelResultado = document.getElementById('panelResultado');
    const resultadoIcon = document.getElementById('resultadoIcon');
    const resultadoTitulo = document.getElementById('resultadoTitulo');
    const resultadoMensaje = document.getElementById('resultadoMensaje');

    // Validación de formato Regex estricto de planta
    const regexFormatoBolsón = /^ENV-\d{2}-\d{4}$/;
    if (!regexFormatoBolsón.test(idBolson)) {
        panelResultado.className = "alert alert-danger shadow-sm p-4 text-center h-100";
        resultadoIcon.innerText = "🚨";
        resultadoTitulo.innerText = "FORMATO ERRONEO";
        resultadoMensaje.innerText = `El código '${idBolson}' no cumple con la norma de planta (ENV-XX-XXXX).`;
        return;
    }

    // Feedback visual inmediato
    panelResultado.className = "alert alert-warning shadow-sm p-4 text-center h-100 animate-pulse";
    resultadoIcon.innerText = "⏳";
    resultadoTitulo.innerText = "BUSCANDO EN REGISTROS...";
    resultadoMensaje.innerText = "Localizando índices relacionales del loteo...";

    try {
        const response = await fetch(`http://localhost:8080/api/bolsones/consultar/${idBolson}`);
        const data = await response.json();

        if (!response.ok || data.status === "ERROR") {
            throw new Error(data.mensaje || "Error desconocido al auditar.");
        }

        // Si existe pero no se ha pesado todavía en báscula
        if (!data.loteActual) {
            panelResultado.className = "alert alert-info shadow-sm p-4 text-center h-100";
            resultadoIcon.innerText = "📦";
            resultadoTitulo.innerText = "CONTENEDOR VACÍO";
            resultadoMensaje.innerHTML = `
                <div class="text-start border-top pt-3 mt-2 fs-5">
                    <strong>ID Envase:</strong> <span class="badge bg-dark">${data.idBolson}</span><br>
                    <strong>Estado SGC:</strong> <span class="text-info fw-bold">DISPONIBLE / ALTA LIMPIA</span><br>
                    <hr class="my-2">
                    <p class="small text-muted mb-0">Este bolsón se encuentra en el sistema pero aún no registra pesaje ni discriminación de materiales en báscula.</p>
                </div>
            `;
            return;
        }

        // CASO ÉXITO: El bolsón tiene lote asignado. Mostramos ficha y habilitamos botón de reimpresión
        panelResultado.className = "alert alert-success shadow-sm p-4 text-center h-100";
        resultadoIcon.innerText = "📝";
        resultadoTitulo.innerText = "TRAZABILIDAD ENCONTRADA";
        resultadoMensaje.innerHTML = `
            <div class="text-start border-top pt-3 mt-2 fs-5">
                <strong>Contenedor ID:</strong> <span class="badge bg-dark">${data.idBolson}</span><br>
                <strong>Lote de Origen:</strong> <span class="badge bg-success">${data.loteActual.idLote}</span><br>
                <strong>Ubicación Actual:</strong> <span class="text-secondary fw-bold">${data.ubicacionActual}</span><br>
                <hr class="my-2">
                <button id="btnReimprimir" class="btn btn-warning w-100 fw-bold fs-5 py-2 mt-2" onclick="reimprimirLotePorDeterioro('${data.idBolson}')">
                    🖨️ REIMPRIMIR ETIQUETA DAÑADA
                </button>
            </div>
        `;

    } catch (error) {
        panelResultado.className = "alert alert-danger shadow-sm p-4 text-center h-100";
        resultadoIcon.innerText = "🚨";
        resultadoTitulo.innerText = "SIN REGISTROS";
        resultadoMensaje.innerText = error.message;
    }
}

// =========================================================================
// 2. FUNCIÓN CORE: DISPARADOR INTERNO DE REIMPRIMIR
// =========================================================================
async function reimprimirLotePorDeterioro(idBolson) {
    const btn = document.getElementById('btnReimprimir');
    const textoOriginal = btn.innerHTML;

    btn.disabled = true;
    btn.className = "btn btn-secondary w-100 fw-bold fs-5 py-2 mt-2 animate-pulse";
    btn.innerText = "⚡ ENVIANDO A TICKETERA THERMAL...";

    try {
        const response = await fetch(`http://localhost:8080/api/bolsones/reimprimir/${idBolson}`);
        const data = await response.json();

        if (!response.ok || data.status === "ERROR") {
            throw new Error(data.mensaje || "Fallo en la cola de impresión local.");
        }

        btn.className = "btn btn-success w-100 fw-bold fs-5 py-2 mt-2";
        btn.innerText = "✅ ¡ETIQUETA EN COLA DE IMPRESIÓN!";

        setTimeout(() => {
            btn.disabled = false;
            btn.className = "btn btn-warning w-100 fw-bold fs-5 py-2 mt-2";
            btn.innerHTML = textoOriginal;
        }, 3000);

    } catch (error) {
        alert("ERROR DE IMPRESIÓN: " + error.message);
        btn.disabled = false;
        btn.className = "btn btn-warning w-100 fw-bold fs-5 py-2 mt-2";
        btn.innerHTML = textoOriginal;
    }
}

// =========================================================================
// 3. ENLACE SEGURO DE EVENTOS (UNOBTRUSIVE JS)
// =========================================================================
document.getElementById('formAuditoria').addEventListener('submit', ejecutarConsulta);

// Máscara ligera para mayúsculas automática sin trabar las pistolas ópticas
document.getElementById('idBolson').addEventListener('input', function (e) {
    e.target.value = e.target.value.toUpperCase();
});/**
 * LÓGICA DE CONTROLADORA DE AUDITORÍA Y TRAZABILIDAD AVANZADA v2.0
 * @author Autor: Yessalim Salazar
 * @version 2.0 (Filtros Temporales y Discriminación de Flujos SGC)
 */

// INTERRUPTOR DE INTERFAZ: Muestra/Oculta fechas según el tipo de reporte
document.getElementById('tipoReporte').addEventListener('change', function(e) {
    const seccionFechas = document.getElementById('seccionFechas');
    if (e.target.value === 'HISTORIAL') {
        seccionFechas.classList.remove('d-none');
    } else {
        seccionFechas.classList.add('d-none');
        document.getElementById('fechaDesde').value = '';
        document.getElementById('fechaHasta').value = '';
    }
});

// FUNCIÓN PRINCIPAL DE CONSULTA
async function ejecutarConsulta(event) {
    event.preventDefault();

    const idBolson = document.getElementById('idBolson').value.trim().toUpperCase();
    const tipoReporte = document.getElementById('tipoReporte').value;
    const fechaDesde = document.getElementById('fechaDesde').value;
    const fechaHasta = document.getElementById('fechaHasta').value;

    const panelResultado = document.getElementById('panelResultado');
    const containerDinamico = document.getElementById('containerDinamico');

    // Validación Regex de Planta
    const regexFormatoBolsón = /^ENV-\d{2}-\d{4}$/;
    if (!regexFormatoBolsón.test(idBolson)) {
        renderizarEstadoAlerta("🚨", "FORMATO ERRÓNEO", `El código '${idBolson}' debe ser ENV-XX-XXXX.`, "alert-danger");
        return;
    }

    // Feedback visual inmediato
    panelResultado.className = "alert alert-warning shadow-sm p-4 h-100 animate-pulse";
    containerDinamico.innerHTML = `
        <div class="fs-1 mb-3">⏳</div>
        <h4 class="fw-bold">EXTRAYENDO REGISTROS REGULATORIOS...</h4>
        <p class="mb-0 fs-5 text-muted">Consultando trazas e índices temporales en MySQL...</p>
    `;

    try {
        if (tipoReporte === "ACTUAL") {
            // FLUJO 1: Estado actual unificado
            const response = await fetch(`http://localhost:8080/api/bolsones/consultar/${idBolson}`);
            const data = await response.json();

            if (!response.ok || data.status === "ERROR") throw new Error(data.mensaje);

            panelResultado.className = "alert alert-success shadow-sm p-4 h-100 text-start";

            if (!data.loteActual) {
                containerDinamico.innerHTML = `
                    <h4 class="fw-bold text-success text-center mb-3">📦 CONTENEDOR DISPONIBLE</h4>
                    <div class="border-top pt-3 fs-5">
                        <strong>ID Envase:</strong> <span class="badge bg-dark">${data.idBolson}</span><br>
                        <strong>Ubicación actual:</strong> <span class="text-secondary">${data.ubicacionActual}</span><br>
                        <strong>Estado de Carga:</strong> <span class="text-info fw-bold">ALTA LIMPIA / SIN PESAJE</span>
                    </div>
                `;
            } else {
                containerDinamico.innerHTML = `
                    <h4 class="fw-bold text-success text-center mb-3">📝 TRAZABILIDAD ENCONTRADA</h4>
                    <div class="border-top pt-3 fs-5">
                        <strong>Contenedor ID:</strong> <span class="badge bg-dark">${data.idBolson}</span><br>
                        <strong>Lote Activo:</strong> <span class="badge bg-success">${data.loteActual.idLote}</span><br>
                        <strong>Peso Neto:</strong> <span class="fw-bold">${data.loteActual.pesoNeto} KG</span><br>
                        <strong>Ubicación Logística:</strong> <span class="text-secondary fw-bold">${data.ubicacionActual}</span>
                        <hr class="my-3">
                        <button id="btnReimprimir" class="btn btn-warning w-100 fw-bold fs-5 py-2" onclick="reimprimirLotePorDeterioro('${data.idBolson}')">
                            🖨️ REIMPRIMIR ETIQUETA ACTIVA
                        </button>
                    </div>
                `;
            }

        } else {
            // FLUJO 2: Historial Completo de Movimientos (Nueva API con filtros)
            let url = `http://localhost:8080/api/bolsones/historial/${idBolson}`;
            if (fechaDesde && fechaHasta) {
                url += `?desde=${fechaDesde}&hasta=${fechaHasta}`;
            }

            const response = await fetch(url);
            const registros = await response.json();

            if (!response.ok) throw new Error("Fallo al recuperar la línea de tiempo.");

            if (registros.length === 0) {
                panelResultado.className = "alert alert-info shadow-sm p-4 h-100 text-center";
                containerDinamico.innerHTML = `
                    <div class="fs-1 mb-3">📆</div>
                    <h4 class="fw-bold">SIN MOVIMIENTOS EN EL PERIODO</h4>
                    <p class="mb-0 text-muted">El contenedor no registra operaciones entre las fechas seleccionadas.</p>
                `;
                return;
            }

            panelResultado.className = "alert alert-light border shadow-sm p-4 h-100 text-start overflow-auto";

            let htmlTimeline = `<h4 class="fw-bold text-dark text-center mb-3">📊 HISTORIAL DE USOS (LÍNEA DE TIEMPO)</h4>`;
            htmlTimeline += `<div class="list-group shadow-sm">`;

            registros.forEach(log => {
                // Formateamos la fecha ISO para la visual del operario
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
    }
}

// FUNCIÓN AUXILIAR REIMPRESIÓN
async function reimprimirLotePorDeterioro(idBolson) {
    const btn = document.getElementById('btnReimprimir');
    try {
        btn.disabled = true;
        btn.innerText = "⚡ ENVIANDO A TICKETERA THERMAL...";

        const response = await fetch(`http://localhost:8080/api/bolsones/reimprimir/${idBolson}`);
        if (!response.ok) throw new Error("Fallo en la cola de impresión local.");

        btn.className = "btn btn-success w-100 fw-bold fs-5 py-2";
        btn.innerText = "✅ ¡ETIQUETA ENVIADA!";
    } catch (error) {
        alert("ERROR: " + error.message);
    } finally {
        setTimeout(() => {
            btn.disabled = false;
            btn.className = "btn btn-warning w-100 fw-bold fs-5 py-2";
            btn.innerText = "🖨️ REIMPRIMIR ETIQUETA ACTIVA";
        }, 3000);
    }
}

// HELPER VISUAL
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

// ENLACE SEGURO
document.getElementById('formAuditoria').addEventListener('submit', ejecutarConsulta);
document.getElementById('idBolson').addEventListener('input', e => e.target.value = e.target.value.toUpperCase());