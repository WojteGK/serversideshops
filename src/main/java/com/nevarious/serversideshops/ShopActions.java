package com.nevarious.serversideshops;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ShopActions {

    public static int buy(ServerPlayerEntity player, String itemId, int qty) {
        ShopConfig.ShopItem def = ServersideShopsMod.CONFIG.items.get(itemId);
        if (def == null) {
            player.sendMessage(Text.literal("Unknown item id: " + itemId), false);
            return 0;
        }
        long total = def.price_buy * (long)qty;
        if (!EconomyService.tryWithdraw(player, total)) {
            player.sendMessage(Text.literal("Not enough money. Need $" + total), false);
            return 0;
        }

        String base = def.icon;
        String comps = legacyNbtToComponents(def.nbt);
        String cmd = comps.isEmpty()
                ? ("give " + player.getGameProfile().getName() + " " + base + " " + qty)
                : ("give " + player.getGameProfile().getName() + " " + base + comps + " " + qty);
        
        MinecraftServer srv = player.getServer();
        srv.getCommandManager().executeWithPrefix(srv.getCommandSource().withLevel(2), cmd);

        player.sendMessage(Text.literal("Purchased " + qty + "x " + itemId + " for $" + total), false);
        return 1;
    }

    public static int sell(ServerPlayerEntity player, String itemId, int qty) {
        ShopConfig.ShopItem def = ServersideShopsMod.CONFIG.items.get(itemId);
        if (def == null || def.price_sell == null) {
            player.sendMessage(Text.literal("Item not sellable: " + itemId), false);
            return 0;
        }
        String[] parts = def.icon.split(":");
        Item item = Registries.ITEM.get(Identifier.of(parts[0], parts[1]));

        int available = countItems(player, item);
        if (available < qty) {
            player.sendMessage(Text.literal("You only have " + available + " items to sell."), false);
            return 0;
        }
        removeItems(player, item, qty);

        long total = def.price_sell * (long)qty;
        EconomyService.deposit(player, total);
        player.sendMessage(Text.literal("Sold " + qty + "x for $" + total), false);
        return 1;
    }

    private static int countItems(ServerPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private static void removeItems(ServerPlayerEntity player, Item item, int qty) {
        int toRemove = qty;
        for (int i = 0; i < player.getInventory().size() && toRemove > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int take = Math.min(stack.getCount(), toRemove);
                stack.decrement(take);
                toRemove -= take;
            }
        }
    }
    private static String legacyNbtToComponents(String legacy) {
        if (legacy == null || legacy.isBlank()) return "";

        java.util.List<String> components = new java.util.ArrayList<>();

        // 1. custom_name (display.Name:'{...}')
        java.util.regex.Matcher nameM = java.util.regex.Pattern
                .compile("display:\\{\\s*Name:'(\\{.*?\\})'\\s*\\}", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(legacy);
        if (nameM.find()) {
            String json = nameM.group(1).replace("\\\"", "\"");
            components.add("custom_name='" + json + "'");
        }

        // 2. Enchantments list
        java.util.Map<String,Integer> ench = new java.util.LinkedHashMap<>();
        java.util.regex.Matcher enchM = java.util.regex.Pattern
                .compile("Enchantments:\\[([^\\]]+)\\]", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(legacy);
        if (enchM.find()) {
            String list = enchM.group(1);
            java.util.regex.Matcher each = java.util.regex.Pattern
                .compile("\\{\\s*id:\\\"?([a-z0-9_:\\-]+)\\\"?\\s*,\\s*lvl:(\\d+)s?\\s*\\}")
                .matcher(list);
            while (each.find()) {
                ench.put(each.group(1), Integer.parseInt(each.group(2)));
            }
        }

        // 3. HideFlags
        boolean hide = false;
        java.util.regex.Matcher hideM = java.util.regex.Pattern
                .compile("HideFlags:\\s*([1-9]\\d*|true)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(legacy);
        if (hideM.find()) {
            hide = true;
        }

        // 4. Build Enchantment Component
        if (!ench.isEmpty()) {
            StringBuilder levels = new StringBuilder();
            boolean first = true;
            for (var e : ench.entrySet()) {
                if (!first) levels.append(",");
                levels.append("\"").append(e.getKey()).append("\":").append(e.getValue());
                first = false;
            }

            if (hide) {
                // Użyj formatu złożonego, jeśli musimy ukryć tooltip
                components.add("enchantments={levels:{" + levels + "}, show_in_tooltip:false}");
            } else {
                // Użyj formatu prostego
                components.add("enchantments={" + levels + "}");
            }
        }

        // 5. Złóż finalny string
        if (components.isEmpty()) return "";
        return "[" + String.join(",", components) + "]";
    }

}
