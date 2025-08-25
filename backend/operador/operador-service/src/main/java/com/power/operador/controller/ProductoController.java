package com.power.operador.controller;

import com.power.operador.repo.ProductoRepository;
import com.power.operador.model.Producto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/productos")
public class ProductoController {

    private final ProductoRepository repo;

    public ProductoController(ProductoRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Producto> listar(@RequestParam(required = false) String categoria) {
        if (categoria != null && !categoria.isBlank()) {
            return repo.findByCategoriaIgnoreCaseOrSubcategoriaIgnoreCase(categoria, categoria);
        }
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Producto porId(@PathVariable Long id) {
        return repo.findById(id).orElse(null);
    }
}
