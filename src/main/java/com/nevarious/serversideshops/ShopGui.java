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

    // ----- Public API -----

    public static void openCategories(ServerPlayerEntity player) {
        Inventory inv = new SimpleInventory(SIZE);
        // Open once with a stable title to avoid mouse recenter
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, p) ->
                new ShopScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, ROWS), Text.literal("Shop")));
        // Initialize session and fill contents
        ServersideShopsMod.SESSIONS.put(player.getUuid(),
                new ShopSession(player.getUuid(), null, 0, ShopSession.Mode.BUY));
        fillCategories(inv);
        player.currentScreenHandler.sendContentUpdates();
    }

    public static void openCategory(ServerPlayerEntity player, String categoryId, int page, ShopSession.Mode mode) {
        // Keep the same handler/screen; just fill it
        if (!(player.currentScreenHandler instanceof ShopScreenHandler ssh)) {
            openCategories(player); // fallback if somehow not open
        }
        ServersideShopsMod.SESSIONS.put(player.getUuid(),
                new ShopSession(player.getUuid(), categoryId, page, mode));
        Inventory inv = ((ShopScreenHandler) player.currentScreenHandler).menuInventory;
        fillCategory(inv, categoryId, page, mode);
        player.currentScreenHandler.sendContentUpdates();
    }

    // ----- Fillers (mutate the existing inventory) -----

    private static void fillCategories(Inventory inv) {
        // clear
        for (int s = 0; s < SIZE; s++) inv.setStack(s, ItemStack.EMPTY);

        int i = 0;
        for (ShopConfig.Category cat : ServersideShopsMod.CONFIG.categories) {
            String[] icon = cat.icon.split(":");
            Item iconItem = Registries.ITEM.get(Identifier.of(icon[0], icon[1]));
            ItemStack stack = new ItemStack(iconItem);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Category: " + cat.name));
            inv.setStack(i++, stack);
        }
        // nav & hint row
        inv.setStack(53, navStack("Close"));
    }

    private static void fillCategory(Inventory inv, String categoryId, int page, ShopSession.Mode mode) {
        // clear
        for (int s = 0; s < SIZE; s++) inv.setStack(s, ItemStack.EMPTY);

        ShopConfig.Category cat = ServersideShopsMod.CONFIG.categories.stream()
                .filter(c -> Objects.equals(c.id, categoryId)).findFirst().orElse(null);
        if (cat == null) return;

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

        // Nav row
        inv.setStack(45, navStack("« Prev"));
        inv.setStack(46, navStack("Back"));
        inv.setStack(52, navStack(mode == ShopSession.Mode.BUY ? "Mode: BUY" : "Mode: SELL"));
        inv.setStack(53, navStack("Next »"));
    }

    private static ItemStack navStack(String name) {
        ItemStack paper = new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", "paper")));
        paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return paper;
    }

    // ----- ScreenHandler that keeps a reference to the menu inventory -----

    public static class ShopScreenHandler extends GenericContainerScreenHandler {
        final Inventory menuInventory;

        public ShopScreenHandler(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, Inventory inventory, int rows) {
            super(type, syncId, playerInventory, inventory, rows);
            this.menuInventory = inventory;
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
                    ShopSession s = ServersideShopsMod.SESSIONS.get(sp.getUuid());

                    if (name.startsWith("Category: ")) {
                        String catName = name.substring("Category: ".length());
                        String catId = null;
                        for (ShopConfig.Category c : ServersideShopsMod.CONFIG.categories) {
                            if (c.name.equals(catName)) { catId = c.id; break; }
                        }
                        if (catId != null) {
                            // mutate same inventory (no reopen)
                            ServersideShopsMod.SESSIONS.put(sp.getUuid(), new ShopSession(sp.getUuid(), catId, 0, ShopSession.Mode.BUY));
                            fillCategory(menuInventory, catId, 0, ShopSession.Mode.BUY);
                            this.sendContentUpdates();
                        }
                        return;
                    }
                    if ("Back".equals(name)) {
                        ServersideShopsMod.SESSIONS.put(sp.getUuid(), new ShopSession(sp.getUuid(), null, 0, ShopSession.Mode.BUY));
                        fillCategories(menuInventory);
                        this.sendContentUpdates();
                        return;
                    }
                    if ("« Prev".equals(name)) {
                        if (s != null && s.category != null && s.page > 0) {
                            s.page -= 1;
                            ServersideShopsMod.SESSIONS.put(sp.getUuid(), s);
                            fillCategory(menuInventory, s.category, s.page, s.mode);
                            this.sendContentUpdates();
                        }
                        return;
                    }
                    if ("Next »".equals(name)) {
                        if (s != null && s.category != null) {
                            s.page += 1;
                            ServersideShopsMod.SESSIONS.put(sp.getUuid(), s);
                            fillCategory(menuInventory, s.category, s.page, s.mode);
                            this.sendContentUpdates();
                        }
                        return;
                    }
                    if (name.startsWith("Mode: ")) {
                        if (s != null && s.category != null) {
                            s.mode = (s.mode == ShopSession.Mode.BUY) ? ShopSession.Mode.SELL : ShopSession.Mode.BUY;
                            ServersideShopsMod.SESSIONS.put(sp.getUuid(), s);
                            fillCategory(menuInventory, s.category, s.page, s.mode);
                            this.sendContentUpdates();
                        }
                        return;
                    }

                    int space = name.indexOf(' ');
                    if (space > 0) {
                        String itemId = name.substring(0, space);
                        if (s != null && s.mode == ShopSession.Mode.SELL) {
                            ShopActions.sell(sp, itemId, 1);
                        } else {
                            ShopActions.buy(sp, itemId, 1);
                        }
                        return;
                    }
                }
                return; // prevent taking menu items
            }
            super.onSlotClick(slotIndex, button, actionType, player);
        }

        @Override
        public boolean canUse(net.minecraft.entity.player.PlayerEntity player) { return true; }
    }
}
