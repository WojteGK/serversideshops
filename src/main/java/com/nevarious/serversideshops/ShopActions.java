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

        String nbtPart = (def.nbt != null && !def.nbt.isEmpty()) ? def.nbt : "{}";
        String cmd = "give " + player.getGameProfile().getName() + " " + def.icon + nbtPart + " " + qty;

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
}
