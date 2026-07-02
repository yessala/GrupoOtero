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
});