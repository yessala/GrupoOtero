/**
 * LÓGICA DE CONTROLADORA TERMINAL BALANZA v6.5
 * @author Autor: Yessalim Salazar
 * @version 6.5 (Corrección de Mapeo de Objetos Anidados del SGC)
 */

async function registrarPesaje(event) {
    event.preventDefault();

    // Capturamos el ID y removemos posibles espacios
    const idBolson = document.getElementById('idBolson').value.trim().toUpperCase();
    const pesoNeto = document.getElementById('pesoNeto').value;
    
    // Captura limpia de los selectores obligatorios
    const material = document.getElementById('material').value;
    const procedencia = document.getElementById('procedencia').value;
    const laminado = document.getElementById('laminado').value;
    const tinta = document.getElementById('tinta').value;
    const color = document.getElementById('color').value;

    // BLINDAJE: Si no seleccionó un color válido, frena la transacción antes de llamar al servidor
    if (color.toLowerCase() === "color" || color === "") {
        statusBalanza.className = "alert alert-danger shadow-sm p-4 text-center h-100";
        statusIcon.innerText = "🚨";
        statusTitulo.innerText = "DATOS INCOMPLETOS";
        statusMensaje.innerText = "Por favor, seleccione un color válido (Blanco o Color) para estructurar el lote.";
        return; // Frena la ejecución
    }

    const statusBalanza = document.getElementById('statusBalanza');
    const statusIcon = document.getElementById('statusIcon');
    const statusTitulo = document.getElementById('statusTitulo');
    const statusMensaje = document.getElementById('statusMensaje');

    // Feedback visual inmediato para el operador de la báscula
    statusBalanza.className = "alert alert-warning shadow-sm p-4 text-center h-100 animate-pulse";
    statusIcon.innerText = "⏳";
    statusTitulo.innerText = "PROCESANDO TRANSACCIÓN...";
    statusMensaje.innerText = "Calculando estructura inmutable de lote según tipo de envase asignado...";

    try {
        // Construimos la URL enviando los parámetros desagregados que requiere el controlador
        const url = `http://localhost:8080/api/bolsones/llenar?idBolson=${idBolson}&peso=${pesoNeto}&material=${material}&procedencia=${procedencia}&laminado=${laminado}&tinta=${tinta}&color=${color}`;
        
        const response = await fetch(url, { 
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });
        
        const data = await response.json();

        // CORRECCIÓN: Validamos usando la nueva estructura anidada del SGC (loteActual)
        if (!response.ok || data.status === "ERROR" || !data.loteActual || !data.loteActual.idLote) {
            throw new Error(data.mensaje || "Fallo en la validación del SGC. Verifique estado del bolsón o conexión con báscula.");
        }

        // REGISTRO Y DISCRIMINACIÓN EXITOSA
        statusBalanza.className = "alert alert-success shadow-sm p-4 text-center h-100";
        statusIcon.innerText = "🚀";
        statusTitulo.innerText = "¡PESAJE REGISTRADO!";
        
        // CORRECCIÓN DE MAPEOS: Se cambió data.idLote por data.loteActual.idLote
        statusMensaje.innerHTML = `
            <div class="text-start border-top pt-3 mt-2 fs-5">
                <strong>Contenedor ID:</strong> <span class="badge bg-dark">${data.idBolson}</span><br>
                <strong>Destino / Destrucción:</strong> <span class="text-secondary">${data.ubicacionActual}</span><br>
                <strong>Lote Generado:</strong> <span class="badge bg-success fs-6">${data.loteActual.idLote}</span><br>
                <hr class="my-2">
                <small class="text-muted d-block text-center">📊 Código de barras y estampa temporal guardados en MySQL.</small>
            </div>
        `;

        // Limpieza automática del formulario para agilizar el próximo pesaje en línea
        document.getElementById('formBalanza').reset();
        document.getElementById('idBolson').focus();

    } catch (error) {
        // Control de excepciones (Ej: "ENVASE DESCARTADO - NO UTILIZAR")
        statusBalanza.className = "alert alert-danger shadow-sm p-4 text-center h-100";
        statusIcon.innerText = "🚨";
        statusTitulo.innerText = "OPERACIÓN RECHAZADA";
        statusMensaje.innerText = error.message;
    }
}