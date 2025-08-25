package com.power.operador.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
public class OperacionController {

    @GetMapping("/operaciones")
    public List<Map<String, Object>> listarOperaciones() {
        return List.of(
                Map.of("id", 1, "tipo", "Compra", "monto", 500),
                Map.of("id", 2, "tipo", "Venta", "monto", 800)
        );
    }
}
