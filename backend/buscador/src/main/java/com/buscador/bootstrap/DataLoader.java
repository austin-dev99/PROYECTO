package com.buscador.bootstrap;

import com.buscador.service.IndexService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final IndexService indexService;

    public DataLoader(IndexService indexService) {
        this.indexService = indexService;
    }

    @Override
    public void run(String... args) {
        System.out.println("📦 Indexando productos automáticamente...");
        int total = indexService.reindexAll();
        System.out.println("✅ Productos indexados: " + total);
    }
}
