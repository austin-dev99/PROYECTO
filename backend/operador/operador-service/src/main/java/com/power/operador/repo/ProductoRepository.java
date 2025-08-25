package com.power.operador.repo;

import com.power.operador.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByCategoriaIgnoreCase(String categoria);
    List<Producto> findByCategoriaIgnoreCaseOrSubcategoriaIgnoreCase(String c1, String c2);
}
