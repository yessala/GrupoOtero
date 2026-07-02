package com.bolsanueva.model;

/**
 * OBJETO DE TRANSFERENCIA DE RESPUESTA SGC - TERMINAL DE BALANZA
 * @author Autor: Yessalim Salazar
 * @version 6.0 (Sincronizado con Arquitectura Relacional y Flujos de Planta)
 * * Esta clase sirve para estructurar y empaquetar la información que el backend
 * le devuelve a la interfaz web (UI) inmediatamente después de pesar y procesar
 * un contenedor en la planta. Proporciona los datos requeridos para la pantalla
 * del operario y los parámetros limpios para la impresión de la etiqueta.
 */
public class LoteResponse {

    private String idBolson;
    private String cadenaCode128;
    private String destinoTolva;
    private String estado;

    public LoteResponse() {
    }

    public LoteResponse(String idBolson, String cadenaCode128, String destinoTolva, String estado) {
        this.idBolson = idBolson;
        this.cadenaCode128 = cadenaCode128;
        this.destinoTolva = destinoTolva;
        this.estado = estado;
    }

    // ------------------------------------------------------------------------
    // MÉTODOS DE ACCESO ENCAPSULADOS (GETTERS Y SETTERS)
    // ------------------------------------------------------------------------

    public String getIdBolson() {
        return idBolson;
    }

    public void setIdBolson(String idBolson) {
        this.idBolson = idBolson;
    }

    public String getCadenaCode128() {
        return cadenaCode128;
    }

    public void setCadenaCode128(String cadenaCode128) {
        this.cadenaCode128 = cadenaCode128;
    }

    public String getDestinoTolva() {
        return destinoTolva;
    }

    public void setDestinoTolva(String destinoTolva) {
        this.destinoTolva = destinoTolva;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}