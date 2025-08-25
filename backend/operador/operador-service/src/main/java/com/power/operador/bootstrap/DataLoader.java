package com.power.operador.bootstrap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.power.operador.repo.ProductoRepository;
import com.power.operador.model.Producto;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class DataLoader implements CommandLineRunner {

    private final ProductoRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataLoader(ProductoRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) throws Exception {
        if (repo.count() > 0) return; // ya hay datos

        // Lee el JSON como List<Map> para poder adaptar nombres de campos distintos
        InputStream is = new ClassPathResource("data/productos.json").getInputStream();
        List<Map<String, Object>> raw = mapper.readValue(is, new TypeReference<>() {});

        for (Map<String, Object> m : raw) {
            Producto p = new Producto();

            // Adapta nombres según tu JSON:
            // intenta varias variantes por si tu JSON usa otras claves
            Object idVal = m.getOrDefault("id", m.getOrDefault("ID", m.get("productId")));
            if (idVal == null) continue;
            p.setId(Long.valueOf(String.valueOf(idVal)));

            p.setNombre(str(m, "nombre", "title", "name"));
            p.setCategoria(str(m, "categoria", "category"));
            p.setSubcategoria(str(m, "subcategoria", "subcategory", "subCategoria"));
            p.setDescripcion(str(m, "descripcion", "description", "detalle"));
            p.setImagen(str(m, "imagen", "image", "img"));

            // precio puede venir como número o string
            String precioStr = str(m, "precio", "price");
            if (precioStr != null && !precioStr.isBlank()) {
                p.setPrecio(new BigDecimal(precioStr));
            } else {
                p.setPrecio(new BigDecimal("0.00"));
            }

            repo.save(p);
        }
        System.out.println("📦 Productos cargados en MySQL: " + repo.count());
    }

    // helper
    private String str(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return String.valueOf(v);
        }
        return null;
    }
}
