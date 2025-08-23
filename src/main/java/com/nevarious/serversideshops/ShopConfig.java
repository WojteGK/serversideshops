package com.nevarious.serversideshops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ShopConfig {

    public String currencyObjective = ServersideShopsMod.OBJECTIVE;
    public List<Category> categories = new ArrayList<>();
    public Map<String, ShopItem> items = new HashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ShopConfig loadOrCreate() {
        try {
            Path dir = Path.of("config/serversideshops");
            Files.createDirectories(dir);
            Path file = dir.resolve("shop.json");
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    ShopConfig cfg = GSON.fromJson(r, ShopConfig.class);
                    if (cfg != null) return cfg;
                }
            }
            // Create default file
            ShopConfig def = defaults();
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(def, w);
            }
            return def;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    private static ShopConfig defaults() {
        ShopConfig c = new ShopConfig();
        // Minimal placeholder defaults
        c.categories.add(new Category("combat", "Combat", "minecraft:iron_sword", Arrays.asList("sharp7_sword")));
        c.categories.add(new Category("mining", "Mining", "minecraft:diamond_pickaxe", Arrays.asList("eff10_pick")));

        // Items left empty — they will be defined in shop.json
        return c;
    }

    public static class Category {
        public String id;
        public String name;
        public String icon;
        public List<String> items;

        public Category() {}
        public Category(String id, String name, String icon, List<String> items) {
            this.id = id; this.name = name; this.icon = icon; this.items = items;
        }
    }

    public static class ShopItem {
        public String icon;
        public long price_buy;
        public Long price_sell;
        public String nbt; // optional NBT string

        public ShopItem() {}
        public ShopItem(String icon, long price_buy, long price_sell, String nbt) {
            this.icon = icon; this.price_buy = price_buy; this.price_sell = price_sell; this.nbt = nbt;
        }
    }
}
