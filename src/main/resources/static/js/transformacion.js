/**
 * LÓGICA DE CONTROL DE CO-PROCESAMIENTO Y TRAZABILIDAD EN CADENA v1.1
 * @author Autor: Yessalim Salazar
 * @version 1.1 (Corrección de Referencias y Sincronización SGC)
 */

// =========================================================================
// 1. CONTROL DE INTERFAZ DINÁMICA POR PUESTO DE TRABAJO (UI)
// =========================================================================

document.getElementById('estacionTrabajo').addEventListener('change', function(e) {
    const estacion = e.target.value;
    const bloqueOrigen = document.getElementById('bloqueOrigen');
    const idBolsonOrigen = document.getElementById('idBolsonOrigen');
    const tipoEnvaseDestino = document.getElementById('tipoEnvaseDestino');

    // Restablecemos configuraciones base por defecto
    bloqueOrigen.classList.remove('d-none');
    idBolsonOrigen.required = true;
    idBolsonOrigen.value = "";
    tipoEnvaseDestino.disabled = false;

    // Ajustamos la pantalla milimétricamente según la regla de negocio de cada máquina
    switch (estacion) {
        case "TRITURADORA_COMUN":
            tipoEnvaseDestino.innerHTML = `<option value="ENV-03" selected>ENV-03 (Bolsón Scrap Triturado B/C)</option>`;
            tipoEnvaseDestino.disabled = true;
            idBolsonOrigen.placeholder = "ENV-01-XXXX";
            break;

        case "TRITURADORA_LAMINACION":
            tipoEnvaseDestino.innerHTML = `<option value="ENV-04" selected>ENV-04 (Bolsón Triturado tortas de laminación)</option>`;
            tipoEnvaseDestino.disabled = true;
            bloqueOrigen.classList.add('d-none');
            idBolsonOrigen.required = false;
            idBolsonOrigen.value = "TORTA";
            break;

        case "PELLETIZADORA_1":
            tipoEnvaseDestino.innerHTML = `
                <option value="" disabled selected>--- SELECCIONE CONTENIDO RESULTANTE ---</option>
                <option value="ENV-02">ENV-02 (Bolsón Pellet Recuperado B/C)</option>
                <option value="ENV-05">ENV-05 (Pellet de Laminación)</option>
            `;
            idBolsonOrigen.placeholder = "ENV-01-XXXX o ENV-03-XXXX";
            break;

        case "PELLETIZADORA_2":
            tipoEnvaseDestino.innerHTML = `
                <option value="" disabled selected>--- SELECCIONE CONTENIDO RESULTANTE ---</option>
                <option value="ENV-05">ENV-05 (Pellet de Laminación - RECOMENDADO)</option>
                <option value="ENV-02">ENV-02 (Bolsón Pellet Recuperado B/C)</option>
            `;
            idBolsonOrigen.placeholder = "ENV-04-XXXX, ENV-03-XXXX o ENV-01-XXXX";
            break;
    }
});

// =========================================================================
// 2. FUNCIÓN CORE: PROCESAMIENTO Y ENCADENAMIENTO ASÍNCRONO
// =========================================================================

async function registrarTransformacion(event) {
    event.preventDefault();

    const botonSubmit = event.target.querySelector('button[type="submit"]');
    if (botonSubmit) {
        botonSubmit.disabled = true;
        botonSubmit.innerHTML = "⏳ CO-PROCESANDO LOTES...";
    }

    const statusProceso = document.getElementById('statusProceso');
    const statusIcon = document.getElementById('statusIcon');
    const statusTitulo = document.getElementById('statusTitulo');
    const statusMensaje = document.getElementById('statusMensaje');

    const estacionTrabajo = document.getElementById('estacionTrabajo').value;
    const idBolsonOrigen = document.getElementById('idBolsonOrigen').value.trim().toUpperCase();
    const tipoEnvaseDestino = document.getElementById('tipoEnvaseDestino').value;
    const idBolsonDestino = document.getElementById('idBolsonDestino').value.trim().toUpperCase();
    const pesoDestino = parseFloat(document.getElementById('pesoDestino').value);

    const regexFormatoBolsón = /^ENV-\d{2}-\d{4}$/;

    if (idBolsonOrigen !== "TORTA" && !regexFormatoBolsón.test(idBolsonOrigen)) {
        renderizarErrorUI("FORMATO ENTRADA INVÁLIDO", `El contenedor de entrada '${idBolsonOrigen}' debe cumplir la norma ENV-XX-XXXX.`, botonSubmit);
        return;
    }

    if (idBolsonDestino !== "" && !regexFormatoBolsón.test(idBolsonDestino)) {
        renderizarErrorUI("FORMATO SALIDA INVÁLIDO", `El contenedor de destino '${idBolsonDestino}' debe cumplir la norma ENV-XX-XXXX o dejarse vacío.`, botonSubmit);
        return;
    }

    statusProceso.className = "alert alert-warning shadow-sm p-4 text-center h-100 animate-pulse";
    statusIcon.innerText = "⏳";
    statusTitulo.innerText = "TRANSFORMANDO MATERIA PRIMA...";
    statusMensaje.innerText = "Calculando árbol de herencia, liberando tolva e inyectando lote hijo en MySQL...";

    const payloadDTO = {
        idBolsonOrigen: idBolsonOrigen,
        idBolsonDestino: idBolsonDestino === "" ? "AUTO" : idBolsonDestino,
        tipoEnvaseDestino: tipoEnvaseDestino,
        pesoDestino: pesoDestino,
        estacionTrabajo: estacionTrabajo
    };

    try {
        const response = await fetch('http://localhost:8080/api/bolsones/transformar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payloadDTO)
        });

        const data = await response.json();

        if (!response.ok || data.status === "ERROR") {
            throw new Error(data.mensaje || "Fallo crítico en el co-procesamiento del SGC.");
        }

        statusProceso.className = "alert alert-success shadow-sm p-4 text-center h-100";
        statusIcon.innerText = "🔄";
        statusTitulo.innerText = "¡PROCESO COMPLETADO!";
        statusMensaje.innerHTML = `
            <div class="text-start border-top pt-3 mt-2 fs-5">
                <div class="text-center mb-2"><span class="badge bg-primary px-3 py-1">Estación: ${estacionTrabajo}</span></div>
                <strong>Contenedor Origen:</strong> <span class="text-danger text-decoration-line-through fw-bold">${idBolsonOrigen}</span> ➡️ <span class="badge bg-light text-success border border-success fs-6 py-0">LIBRE / DISPONIBLE</span><br>
                <strong>Contenedor Destino:</strong> <span class="badge bg-dark">${data.idBolson}</span><br>
                <strong>Lote Hijo Generado:</strong> <span class="badge bg-success fs-6">${data.loteGenerado || data.loteGenerated}</span><br>
                <strong>Destino Físico:</strong> <span class="text-secondary fw-bold">${data.ubicacionActual}</span>
                <hr class="my-2">
                <small class="text-muted d-block text-center">🖨️ Etiquetas de barras impresas y guardadas bajo norma ISO.</small>
            </div>
        `;

        document.getElementById('idBolsonDestino').value = "";
        document.getElementById('pesoDestino').value = "";
        if (idBolsonOrigen !== "TORTA") document.getElementById('idBolsonOrigen').value = "";

        if (idBolsonOrigen !== "TORTA") {
            document.getElementById('idBolsonOrigen').focus();
        } else {
            document.getElementById('pesoDestino').focus();
        }

    } catch (error) {
        statusProceso.className = "alert alert-danger shadow-sm p-4 text-center h-100";
        statusIcon.innerText = "🚨";
        statusTitulo.innerText = "TRANSACCIÓN RECHAZADA";
        statusMensaje.innerText = error.message;
    } finally {
        if (botonSubmit) {
            botonSubmit.disabled = false;
            botonSubmit.innerHTML = "⚙️ PROCESAR Y ENCADENAR LOTES";
        }
    }
}

// =========================================================================
// 3. HELPERS DE INTERFAZ Y ENLACES UNOBTRUSIVE JAVASCRIPT
// =========================================================================

// CORRECCIÓN: Nombre unificado correctamente a la llamada superior
function renderizarErrorUI(titulo, mensaje, boton) {
    const statusProceso = document.getElementById('statusProceso');
    const statusIcon = document.getElementById('statusIcon');
    const statusTitulo = document.getElementById('statusTitulo');
    const statusMensaje = document.getElementById('statusMensaje');

    statusProceso.className = "alert alert-danger shadow-sm p-4 text-center h-100";
    statusIcon.innerText = "🚨";
    statusTitulo.innerText = titulo;
    statusMensaje.innerText = mensaje;

    if (boton) {
        boton.disabled = false;
        boton.innerHTML = "⚙️ PROCESAR Y ENCADENAR LOTES";
    }
}

document.getElementById('formTransformacion').addEventListener('submit', registrarTransformacion);
document.getElementById('idBolsonOrigen').addEventListener('input', e => e.target.value = e.target.value.toUpperCase());
document.getElementById('idBolsonDestino').addEventListener('input', e => e.target.value = e.target.value.toUpperCase());