package com.bolsanueva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        // Inicia el backend y lo deja corriendo en el puerto 8080 de tu máquina
        SpringApplication.run(App.class, args);
        System.out.println("\n🚀 [SERVIDOR BACKEND DE LA RECUPERADORA ACTIVO EN PUERTO 8080]");
    }
}