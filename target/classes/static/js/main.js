/**
 * LÓGICA DE CONTROLADOR FRONTIER - MODULAR SGC v6.0
 * @author Autor: Yessalim Salazar
 */

// v6.0: Sincronizado con la ruta real de BolsonesController
const API_URL = 'http://localhost:8080/api/bolsones';

async function generarEnvase(tipo) {
    const panelLog = document.getElementById('panelLog');
    const logTitulo = document.getElementById('logTitulo');
    const logMensaje = document.getElementById('logMensaje');

    // Estado visual inicial de procesamiento en planta
    panelLog.className = "alert alert-warning shadow-sm animate-pulse";
    logTitulo.innerText = "⏳ COMUNICANDO CON EL SERVIDOR...";
    logMensaje.innerText = `Enviando orden de registro y ráfaga de impresión para un contenedor tipo ${tipo}.`;
    panelLog.classList.remove('d-none');

    try {
        // v6.0: Se usa /nuevo y el parámetro tipoEnvase que exige el backend
        const response = await fetch(`${API_URL}/nuevo?tipoEnvase=${tipo}`, { 
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        if (!response.ok) {
            throw new Error(`Código de estado HTTP: ${response.status}`);
        }
        
        const data = await response.json();

        // Respuesta Exitosa: El backend persistió y generó la imagen PNG
        panelLog.className = "alert alert-success shadow-sm";
        logTitulo.innerText = "✅ ¡REGISTRO EXITOSO E IMPRESO!";
        logMensaje.innerHTML = `
            <strong>DNI Asignado:</strong> <span class="badge bg-success fs-6">${data.idBolson}</span><br>
            <strong>Estado Inicial:</strong> ${data.estado}<br>
            <strong>Ubicación Base:</strong> ${data.ubicacionActual}<br>
            <small class="text-muted mt-2 d-block">💾 Archivo gráfico generado con éxito en /etiquetas/${data.idBolson}.png</small>
        `;

    } catch (error) {
        // Captura de errores de red o excepciones controladas del SGC
        panelLog.className = "alert alert-danger shadow-sm";
        logTitulo.innerText = "🚨 FALLO DE CONEXIÓN O VALIDACIÓN";
        logMensaje.innerText = `No se pudo procesar el alta del envase. Detalle: ${error.message}`;
    }
}