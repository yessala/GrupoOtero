package com.bolsanueva.exception;

// Heredamos de Exception para que Java nos obligue a controlar este error con un try-catch
public class ValidacionScrapException extends Exception {
    
    public ValidacionScrapException(String mensaje) {
        super(mensaje);
    }
}