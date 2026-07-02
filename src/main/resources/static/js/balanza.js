/**
 * LÓGICA DE CONTROLADORA TERMINAL BALANZA v6.7
 * @author Autor: Yessalim Salazar
 * @version 6.7 (Corrección de Ámbito de Eventos y Desacoplamiento)
 */

// =========================================================================
// 1. FUNCIÓN CORE: PROCESAMIENTO ASÍNCRONO DEL PESAJE
// =========================================================================
async function registrarPesaje(event) {
    event.preventDefault(); // ¡CLAVE! Evita que la página se limpie/recargue al hacer clic

    // Instanciamos componentes del DOM para las alertas
    const statusBalanza = document.getElementById('statusBalanza');
    const statusIcon = document.getElementById('statusIcon');
    const statusTitulo = document.getElementById('statusTitulo');
    const statusMensaje = document.getElementById('statusMensaje');

    // Capturamos las entradas de los componentes de la báscula
    const idBolson = document.getElementById('idBolson').value.trim().toUpperCase();
    const pesoNeto = document.getElementById('pesoNeto').value;
    const material = document.getElementById('material').value;
    const procedencia = document.getElementById('procedencia').value;
    const laminado = document.getElementById('laminado').value;
    const tinta = document.getElementById('tinta').value;
    const color = document.getElementById('color').value;

    // VALIDACIÓN 1: Expresión regular del SGC para el bulto
    const regexFormatoBolsón = /^ENV-\d{2}-\d{4}$/;
    if (!regexFormatoBolsón.test(idBolson)) {
        statusBalanza.className = "alert alert-danger shadow-sm p-4 text-center h-100";
        statusIcon.innerText = "🚨";
        statusTitulo.innerText = "FORMATO INVÁLIDO";
        statusMensaje.innerText = `El código '${idBolson}' no cumple con la norma. Debe ser ENV-XX-XXXX (Ej: ENV-01-0001).`;
        return;
    }

    // VALIDACIÓN 2: Evitar placeholder de color
// VALIDACIÓN 2: Asegurar que el operario seleccionó una opción válida del maestro
    if (color === "" || idBolson === "") {
        statusBalanza.className = "alert alert-danger shadow-sm p-4 text-center h-100";
        statusIcon.innerText = "🚨";
        statusTitulo.innerText = "DATOS INCOMPLETOS";
        statusMensaje.innerText = "Por favor, seleccione todos los parámetros obligatorios del bulto.";
        return;
    }

    // Feedback visual de espera en la terminal
    statusBalanza.className = "alert alert-warning shadow-sm p-4 text-center h-100 animate-pulse";
    statusIcon.innerText = "⏳";
    statusTitulo.innerText = "PROCESANDO TRANSACCIÓN...";
    statusMensaje.innerText = "Calculando estructura inmutable de lote según tipo de envase asignado...";

    try {
        const url = `http://localhost:8080/api/bolsones/llenar?idBolson=${idBolson}&peso=${pesoNeto}&material=${material}&procedencia=${procedencia}&laminado=${laminado}&tinta=${tinta}&color=${color}`;

        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        const data = await response.json();

        if (!response.ok || data.status === "ERROR" || !data.loteActual || !data.loteActual.idLote) {
            throw new Error(data.mensaje || "Fallo en la validación del SGC. Verifique estado del bolsón.");
        }

        // RENDERIZADO DE ÉXITO EN EL PANEL VERDE
        statusBalanza.className = "alert alert-success shadow-sm p-4 text-center h-100";
        statusIcon.innerText = "🚀";
        statusTitulo.innerText = "¡PESAJE REGISTRADO!";
        statusMensaje.innerHTML = `
            <div class="text-start border-top pt-3 mt-2 fs-5">
                <strong>Contenedor ID:</strong> <span class="badge bg-dark">${data.idBolson}</span><br>
                <strong>Destino / Destrucción:</strong> <span class="text-secondary">${data.ubicacionActual}</span><br>
                <strong>Lote Generado:</strong> <span class="badge bg-success fs-6">${data.loteActual.idLote}</span><br>
                <hr class="my-2">
                <small class="text-muted d-block text-center">📊 Código de barras y estampa temporal guardados en MySQL.</small>
            </div>
        `;

        // El formulario solo se limpia si la operación en el servidor fue exitosa
        document.getElementById('formBalanza').reset();
        document.getElementById('idBolson').focus();

    } catch (error) {
        statusBalanza.className = "alert alert-danger shadow-sm p-4 text-center h-100";
        statusIcon.innerText = "🚨";
        statusTitulo.innerText = "OPERACIÓN RECHAZADA";
        statusMensaje.innerText = error.message;
    }
}

// =========================================================================
// 2. ENLACE DE EVENTOS DEL SISTEMA (AFUERA DE LA FUNCIÓN ASÍNCRONA)
// =========================================================================

// Escuchador moderno del submit del formulario
document.getElementById('formBalanza').addEventListener('submit', registrarPesaje);

// Filtro liviano para pasar a mayúsculas automático sin trabar la ráfaga de la pistola
document.getElementById('idBolson').addEventListener('input', function (e) {
    e.target.value = e.target.value.toUpperCase();
});