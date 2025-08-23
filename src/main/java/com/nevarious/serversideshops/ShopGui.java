package com.nevarious.serversideshops;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ShopGui {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    public static void openCategories(ServerPlayerEntity player) {
        Inventory inv = new SimpleInventory(SIZE);

        int i = 0;
        for (ShopConfig.Category cat : ServersideShopsMod.CONFIG.categories) {
            String[] icon = cat.icon.split(":");
            Item iconItem = Registries.ITEM.get(Identifier.of(icon[0], icon[1]));
            ItemStack stack = new ItemStack(iconItem);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Category: " + cat.name));
            inv.setStack(i++, stack);
        }

        inv.setStack(53, navStack("Close"));
        openInventory(player, inv, Text.literal("Shop Categories"));
        ServersideShopsMod.SESSIONS.put(player.getUuid(),
                new ShopSession(player.getUuid(), null, 0, ShopSession.Mode.BUY));
    }

    public static void openCategory(ServerPlayerEntity player, String categoryId, int page, ShopSession.Mode mode) {
        ShopConfig.Category cat = ServersideShopsMod.CONFIG.categories.stream()
                .filter(c -> Objects.equals(c.id, categoryId)).findFirst().orElse(null);
        if (cat == null) {
            player.sendMessage(Text.literal("Unknown category: " + categoryId), false);
            return;
        }

        Inventory inv = new SimpleInventory(SIZE);
        List<String> ids = cat.items != null ? cat.items : new ArrayList<>();
        int start = page * 45;
        int end = Math.min(ids.size(), start + 45);
        int slot = 0;

        for (int idx = start; idx < end; idx++) {
            String itemId = ids.get(idx);
            ShopConfig.ShopItem def = ServersideShopsMod.CONFIG.items.get(itemId);
            if (def == null) continue;

            String[] icon = def.icon.split(":");
            Item iconItem = Registries.ITEM.get(Identifier.of(icon[0], icon[1]));
            ItemStack stack = new ItemStack(iconItem);
            long price = (mode == ShopSession.Mode.BUY) ? def.price_buy : (def.price_sell == null ? 0 : def.price_sell);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(itemId + " $" + price));
            inv.setStack(slot++, stack);
        }

        inv.setStack(45, navStack("« Prev"));
        inv.setStack(46, navStack("Back"));
        inv.setStack(52, navStack(mode == ShopSession.Mode.BUY ? "Mode: BUY" : "Mode: SELL"));
        inv.setStack(53, navStack("Next »"));

        openInventory(player, inv, Text.literal(cat.name + " (" + mode + ") p." + (page + 1)));
        ServersideShopsMod.SESSIONS.put(player.getUuid(),
                new ShopSession(player.getUuid(), categoryId, page, mode));
    }

    private static void openInventory(ServerPlayerEntity player, Inventory inv, Text title) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) ->
                new ShopScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, ROWS), title));
    }

    private static ItemStack navStack(String name) {
        ItemStack paper = new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", "paper")));
        paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return paper;
    }

    public static class ShopScreenHandler extends GenericContainerScreenHandler {
        public ShopScreenHandler(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, Inventory inventory, int rows) {
            super(type, syncId, playerInventory, inventory, rows);
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, net.minecraft.entity.player.PlayerEntity player) {
            if (!(player instanceof ServerPlayerEntity sp)) {
                super.onSlotClick(slotIndex, button, actionType, player);
                return;
            }
            if (slotIndex >= 0 && slotIndex < SIZE) {
                ItemStack clicked = this.getSlot(slotIndex).getStack();
                if (!clicked.isEmpty()) {
                    String name = clicked.getName().getString();
                    ShopSession session = ServersideShopsMod.SESSIONS.get(sp.getUuid());

                    if (name.startsWith("Category: ")) {
                        String catName = name.substring("Category: ".length());
                        String catId = null;
                        for (ShopConfig.Category c : ServersideShopsMod.CONFIG.categories) {
                            if (c.name.equals(catName)) { catId = c.id; break; }
                        }
                        if (catId != null) {
                            ShopGui.openCategory(sp, catId, 0, ShopSession.Mode.BUY);
                        }
                        return;
                    }
                    if ("Back".equals(name)) { ShopGui.openCategories(sp); return; }
                    if ("« Prev".equals(name)) {
                        ShopSession s = ServersideShopsMod.SESSIONS.get(sp.getUuid());
                        if (s != null && s.category != null && s.page > 0) {
                            ShopGui.openCategory(sp, s.category, s.page - 1, s.mode);
                        }
                        return;
                    }
                    if ("Next »".equals(name)) {
                        ShopSession s = ServersideShopsMod.SESSIONS.get(sp.getUuid());
                        if (s != null && s.category != null) {
                            ShopGui.openCategory(sp, s.category, s.page + 1, s.mode);
                        }
                        return;
                    }
                    if (name.startsWith("Mode: ")) {
                        ShopSession s = ServersideShopsMod.SESSIONS.get(sp.getUuid());
                        if (s != null && s.category != null) {
                            ShopSession.Mode newMode = (s.mode == ShopSession.Mode.BUY) ? ShopSession.Mode.SELL : ShopSession.Mode.BUY;
                            ShopGui.openCategory(sp, s.category, s.page, newMode);
                        }
                        return;
                    }

                    int space = name.indexOf(' ');
                    if (space > 0) {
                        String itemId = name.substring(0, space);
                        if (session != null && session.mode == ShopSession.Mode.SELL) {
                            ShopActions.sell(sp, itemId, 1);
                        } else {
                            ShopActions.buy(sp, itemId, 1);
                        }
                        return;
                    }
                }
                return; // cancel menu item pickup
            }
            super.onSlotClick(slotIndex, button, actionType, player);
        }

        @Override
        public boolean canUse(net.minecraft.entity.player.PlayerEntity player) { return true; }
    }
}
